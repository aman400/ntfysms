package `in`.ntfysms.domain.engine.executors

import android.content.Context
import android.util.Base64
import `in`.ntfysms.data.CredentialRepository
import `in`.ntfysms.domain.engine.ExecutionContext
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.CredentialType
import `in`.ntfysms.domain.model.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Strategy executor for [NodeType.HTTP_ACTION].
 * Dispatches REST HTTP webhook requests (GET, POST, PUT, PATCH, DELETE, HEAD) with dynamic template substitution,
 * query parameter appending, header set injection, and credential resolution.
 */
class HttpActionExecutor(
    private val okHttpClient: OkHttpClient,
    private val credentialRepository: CredentialRepository? = null
) : NodeExecutor {

    override suspend fun execute(
        node: WorkflowNode,
        context: ExecutionContext,
        appContext: Context?
    ): NodeExecutionResult = withContext(Dispatchers.IO) {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }

        val rawUrl = config.optString("url", "")
        val method = config.optString("method", "GET").uppercase()
        val queryParams = config.optString("queryParams", "")
        val authType = config.optString("authType", "NONE").uppercase()
        val bearerToken = config.optString("bearerToken", "")
        val basicUser = config.optString("basicUser", "")
        val basicPassword = config.optString("basicPassword", "")
        val credentialId = config.optString("credentialId", "")

        val headers = config.optString("headers", "")
        val bodyType = config.optString("bodyType", "JSON").uppercase()
        val bodyTemplate = config.optString("bodyTemplate", "")
        val expectedStatus = config.optInt("expectedStatus", 200)

        var targetUrl = context.interpolate(rawUrl).trim()
        if (targetUrl.isBlank()) {
            return@withContext NodeExecutionResult.Failed("HTTP URL is empty")
        }

        // 1. Query parameters appending
        if (queryParams.isNotBlank()) {
            val interpolatedQuery = context.interpolate(queryParams)
            targetUrl = if (targetUrl.contains("?")) "$targetUrl&$interpolatedQuery" else "$targetUrl?$interpolatedQuery"
        }

        try {
            val requestBuilder = Request.Builder().url(targetUrl)

            // 2. Body construction
            val interpolatedBody = context.interpolate(bodyTemplate)
            val requestBody: RequestBody? = when (bodyType) {
                "NONE" -> null
                "FORM_DATA" -> {
                    val formBuilder = FormBody.Builder()
                    interpolatedBody.split("\n", "&").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) {
                            formBuilder.add(parts[0].trim(), parts[1].trim())
                        }
                    }
                    formBuilder.build()
                }
                "RAW" -> interpolatedBody.toRequestBody("text/plain; charset=utf-8".toMediaType())
                else -> interpolatedBody.toRequestBody("application/json; charset=utf-8".toMediaType()) // JSON
            }

            when (method) {
                "POST" -> requestBuilder.post(requestBody ?: ByteArray(0).toRequestBody(null))
                "PUT" -> requestBuilder.put(requestBody ?: ByteArray(0).toRequestBody(null))
                "PATCH" -> requestBuilder.patch(requestBody ?: ByteArray(0).toRequestBody(null))
                "DELETE" -> if (requestBody != null) requestBuilder.delete(requestBody) else requestBuilder.delete()
                "HEAD" -> requestBuilder.head()
                else -> requestBuilder.get()
            }

            // 3. Custom Headers
            if (headers.isNotBlank()) {
                context.interpolate(headers).split("\n", ",").forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }

            // 4. Authentication & Credential Store Resolution
            val resolvedCred: Credential? = if (credentialId.isNotBlank() && credentialRepository != null) {
                credentialRepository.getCredentialById(credentialId)
            } else null

            if (resolvedCred != null) {
                applyCredentialToRequest(requestBuilder, resolvedCred, context)
            } else {
                when (authType) {
                    "BEARER" -> {
                        val token = context.interpolate(bearerToken).trim()
                        if (token.isNotBlank()) {
                            requestBuilder.addHeader("Authorization", "Bearer $token")
                        }
                    }
                    "BASIC" -> {
                        val user = context.interpolate(basicUser).trim()
                        val pass = context.interpolate(basicPassword).trim()
                        val credentials = "$user:$pass"
                        val authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                        requestBuilder.addHeader("Authorization", authHeader)
                    }
                }
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val responseBody = resp.body?.string() ?: ""
                context.set("http_status", resp.code)
                context.set("http_response", responseBody)

                val responseHeaders = resp.headers.toString().trim()
                val detailedLog = buildString {
                    appendLine("HTTP $method $targetUrl")
                    appendLine("Status: ${resp.code} ${resp.message}")
                    if (responseHeaders.isNotBlank()) {
                        appendLine("--- Headers ---")
                        appendLine(responseHeaders)
                    }
                    if (responseBody.isNotBlank()) {
                        appendLine("--- Response Body ---")
                        appendLine(responseBody)
                    }
                }

                if (resp.code == expectedStatus || (expectedStatus == 200 && resp.isSuccessful)) {
                    NodeExecutionResult.Success(logMessage = detailedLog)
                } else {
                    NodeExecutionResult.Failed("HTTP request failed with status ${resp.code}\n\n$detailedLog")
                }
            }
        } catch (e: Exception) {
            NodeExecutionResult.Failed("HTTP request exception: ${e.message}")
        }
    }

    private fun applyCredentialToRequest(
        builder: Request.Builder,
        credential: Credential,
        context: ExecutionContext
    ) {
        val data = credential.dataMap
        when (credential.type) {
            CredentialType.BEARER_TOKEN -> {
                val token = context.interpolate(data["token"] ?: "").trim()
                if (token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
            }
            CredentialType.BASIC_AUTH -> {
                val user = context.interpolate(data["username"] ?: "").trim()
                val pass = context.interpolate(data["password"] ?: "").trim()
                val authHeader = "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
                builder.addHeader("Authorization", authHeader)
            }
            CredentialType.HEADER_SET -> {
                val headers = data["headers"] ?: ""
                context.interpolate(headers).split("\n", ",").forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        builder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }
            CredentialType.NTFY_AUTH -> {
                val token = context.interpolate(data["token"] ?: "").trim()
                if (token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
            }
            CredentialType.GOTIFY_AUTH -> {
                val appToken = context.interpolate(data["appToken"] ?: "").trim()
                if (appToken.isNotBlank()) {
                    builder.addHeader("X-Gotify-Key", appToken)
                }
            }
        }
    }
}
