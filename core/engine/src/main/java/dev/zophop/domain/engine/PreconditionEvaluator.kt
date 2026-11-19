package dev.zophop.domain.engine

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.Precondition
import dev.zophop.domain.model.PreconditionType
import dev.zophop.domain.model.Workflow
import dev.zophop.domain.model.WorkflowConnection
import dev.zophop.domain.model.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

sealed class PreconditionResult {
    data class Passed(val steps: List<NodeExecutionStep> = emptyList()) : PreconditionResult()
    data class Failed(val reason: String, val steps: List<NodeExecutionStep> = emptyList()) : PreconditionResult()
}

open class PreconditionEvaluator(
    private val context: Context?,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    private val evaluatingPreconditions = ThreadLocal.withInitial { mutableSetOf<Int>() }

    open suspend fun evaluateAll(
        preconditions: List<Precondition>,
        onStepUpdate: ((List<NodeExecutionStep>) -> Unit)? = null
    ): PreconditionResult = withContext(Dispatchers.IO) {
        val accumulatedSteps = mutableListOf<NodeExecutionStep>()
        for (precondition in preconditions) {
            val result = evaluate(precondition) { precSteps ->
                val liveList = accumulatedSteps + precSteps
                onStepUpdate?.invoke(liveList)
            }
            when (result) {
                is PreconditionResult.Passed -> {
                    accumulatedSteps.addAll(result.steps)
                    onStepUpdate?.invoke(accumulatedSteps.toList())
                }
                is PreconditionResult.Failed -> {
                    accumulatedSteps.addAll(result.steps)
                    onStepUpdate?.invoke(accumulatedSteps.toList())
                    return@withContext PreconditionResult.Failed(result.reason, steps = accumulatedSteps.toList())
                }
            }
        }
        PreconditionResult.Passed(steps = accumulatedSteps.toList())
    }

    open suspend fun evaluate(
        precondition: Precondition,
        onStepUpdate: ((List<NodeExecutionStep>) -> Unit)? = null
    ): PreconditionResult = withContext(Dispatchers.IO) {
        val precKey = precondition.hashCode()
        val activeSet = evaluatingPreconditions.get() ?: mutableSetOf()
        if (activeSet.contains(precKey)) {
            return@withContext PreconditionResult.Failed("Circular precondition loop detected")
        }
        activeSet.add(precKey)
        try {
            var result = evaluateSingleAttempt(precondition, onStepUpdate)

            val isRetryPolicy = precondition.onFailureAction.trim().contains("RETRY", ignoreCase = true)
            if (result is PreconditionResult.Failed && isRetryPolicy && precondition.maxRetries > 0) {
                val maxRetries = precondition.maxRetries
                val intervalSec = precondition.retryIntervalSeconds.coerceAtLeast(1)

                // Keep failed attempt steps intact as FAILED so they render red as earlier
                val historySteps = (result as? PreconditionResult.Failed)?.steps?.toMutableList() ?: mutableListOf()

                for (attempt in 1..maxRetries) {
                    ensureActive()

                    // Second-by-second live countdown ticker
                    for (sec in intervalSec downTo 1) {
                        ensureActive()
                        val retryNoticeStep = NodeExecutionStep(
                            nodeId = "retry_delay_$attempt",
                            nodeName = "⏳ Retrying ($attempt/$maxRetries)",
                            nodeType = NodeType.HTTP_ACTION,
                            state = NodeStepState.EXECUTING,
                            logMessage = "Precondition check failed. Waiting ${sec}s remaining before attempt #${attempt + 1}/${maxRetries + 1}..."
                        )
                        val currentProgress = historySteps + retryNoticeStep
                        onStepUpdate?.invoke(currentProgress)

                        kotlinx.coroutines.delay(1000L)
                    }

                    ensureActive()

                    val retryResult = evaluateSingleAttempt(precondition) { attemptSteps ->
                        val currentList = historySteps + attemptSteps
                        onStepUpdate?.invoke(currentList)
                    }

                    if (retryResult is PreconditionResult.Passed) {
                        historySteps.addAll(retryResult.steps)
                        result = PreconditionResult.Passed(steps = historySteps.toList())
                        onStepUpdate?.invoke(historySteps.toList())
                        break
                    } else if (retryResult is PreconditionResult.Failed) {
                        val failedReason = retryResult.reason
                        historySteps.addAll(retryResult.steps)

                        result = PreconditionResult.Failed(
                            reason = if (attempt == maxRetries) "$failedReason (Failed after $maxRetries retries)" else failedReason,
                            steps = historySteps.toList()
                        )
                        onStepUpdate?.invoke(historySteps.toList())
                    }
                }
            }

            if (result is PreconditionResult.Failed && precondition.showNotificationOnFailure) {
                postFailureNotification(precondition, result.reason)
            }

            result
        } finally {
            activeSet.remove(precKey)
        }
    }

    private suspend fun evaluateSingleAttempt(
        precondition: Precondition,
        onStepUpdate: ((List<NodeExecutionStep>) -> Unit)? = null
    ): PreconditionResult {
        return when (precondition) {
            is Precondition.InternetCheck -> {
                val isOk = !precondition.isEnabled || isInternetAvailable()
                val step = NodeExecutionStep(
                    nodeId = "internet_check",
                    nodeName = "🌐 Network / Internet Check",
                    nodeType = NodeType.HTTP_ACTION,
                    state = if (isOk) NodeStepState.SUCCESS else NodeStepState.FAILED,
                    logMessage = if (isOk) "Active Internet connection verified" else "Internet connection unavailable",
                    errorReason = if (!isOk) "Internet connection unavailable" else null
                )
                onStepUpdate?.invoke(listOf(step))
                if (isOk) PreconditionResult.Passed(steps = listOf(step))
                else PreconditionResult.Failed("Internet connection unavailable", steps = listOf(step))
            }
            is Precondition.VpnCheck -> {
                val isOk = !precondition.isEnabled || isVpnConnected()
                val step = NodeExecutionStep(
                    nodeId = "vpn_check",
                    nodeName = "🔐 VPN Connection Check",
                    nodeType = NodeType.HTTP_ACTION,
                    state = if (isOk) NodeStepState.SUCCESS else NodeStepState.FAILED,
                    logMessage = if (isOk) "Active VPN connection verified (Tailscale/WireGuard/OpenVPN)" else "VPN connection required (Awaiting Tailscale/WireGuard/VPN)",
                    errorReason = if (!isOk) "VPN connection required (Awaiting Tailscale/WireGuard/VPN)" else null
                )
                onStepUpdate?.invoke(listOf(step))
                if (isOk) PreconditionResult.Passed(steps = listOf(step))
                else PreconditionResult.Failed("VPN connection required (Awaiting Tailscale/WireGuard/VPN)", steps = listOf(step))
            }
            is Precondition.HealthCheck -> {
                if (!precondition.isEnabled) {
                    val step = NodeExecutionStep(nodeId = "health_check", nodeName = "🩺 HTTP Healthcheck", nodeType = NodeType.HTTP_ACTION, state = NodeStepState.SUCCESS, logMessage = "Healthcheck disabled, skipping.")
                    onStepUpdate?.invoke(listOf(step))
                    PreconditionResult.Passed(steps = listOf(step))
                } else {
                    val res = checkHealth(precondition)
                    val isOk = res is PreconditionResult.Passed
                    val failReason = (res as? PreconditionResult.Failed)?.reason
                    val step = NodeExecutionStep(
                        nodeId = "health_check",
                        nodeName = "🩺 HTTP Healthcheck (${precondition.method} ${precondition.targetUrl})",
                        nodeType = NodeType.HTTP_ACTION,
                        state = if (isOk) NodeStepState.SUCCESS else NodeStepState.FAILED,
                        logMessage = if (isOk) "HTTP ${precondition.method} -> ${precondition.targetUrl} returned expected status ${precondition.expectedStatus}" else (failReason ?: "Healthcheck failed"),
                        errorReason = failReason
                    )
                    onStepUpdate?.invoke(listOf(step))
                    if (isOk) PreconditionResult.Passed(steps = listOf(step)) else PreconditionResult.Failed(failReason ?: "Healthcheck failed", steps = listOf(step))
                }
            }
            is Precondition.SimCheck -> {
                if (!precondition.isEnabled) {
                    val step = NodeExecutionStep(nodeId = "sim_check", nodeName = "📱 SIM Card Active Check", nodeType = NodeType.HTTP_ACTION, state = NodeStepState.SUCCESS, logMessage = "SIM check disabled, skipping.")
                    onStepUpdate?.invoke(listOf(step))
                    PreconditionResult.Passed(steps = listOf(step))
                } else {
                    val res = checkSim(precondition)
                    val isOk = res is PreconditionResult.Passed
                    val failReason = (res as? PreconditionResult.Failed)?.reason
                    val step = NodeExecutionStep(
                        nodeId = "sim_check",
                        nodeName = "📱 SIM Card Active Check",
                        nodeType = NodeType.HTTP_ACTION,
                        state = if (isOk) NodeStepState.SUCCESS else NodeStepState.FAILED,
                        logMessage = if (isOk) "Active SIM card verified${if (precondition.requiredCarrier.isNotBlank()) " (Carrier: ${precondition.requiredCarrier})" else ""}" else (failReason ?: "SIM check failed"),
                        errorReason = failReason
                    )
                    onStepUpdate?.invoke(listOf(step))
                    if (isOk) PreconditionResult.Passed(steps = listOf(step)) else PreconditionResult.Failed(failReason ?: "SIM check failed", steps = listOf(step))
                }
            }
            is Precondition.GpsCheck -> {
                if (!precondition.isEnabled) {
                    val step = NodeExecutionStep(nodeId = "gps_check", nodeName = "📍 GPS / Location Check", nodeType = NodeType.HTTP_ACTION, state = NodeStepState.SUCCESS, logMessage = "GPS check disabled, skipping.")
                    onStepUpdate?.invoke(listOf(step))
                    PreconditionResult.Passed(steps = listOf(step))
                } else {
                    val res = checkGps(precondition)
                    val isOk = res is PreconditionResult.Passed
                    val failReason = (res as? PreconditionResult.Failed)?.reason
                    val step = NodeExecutionStep(
                        nodeId = "gps_check",
                        nodeName = "📍 GPS / Location Check",
                        nodeType = NodeType.HTTP_ACTION,
                        state = if (isOk) NodeStepState.SUCCESS else NodeStepState.FAILED,
                        logMessage = if (isOk) "Location permissions and GPS hardware enabled" else (failReason ?: "GPS check failed"),
                        errorReason = failReason
                    )
                    onStepUpdate?.invoke(listOf(step))
                    if (isOk) PreconditionResult.Passed(steps = listOf(step)) else PreconditionResult.Failed(failReason ?: "GPS check failed", steps = listOf(step))
                }
            }
            is Precondition.ChainCheck -> {
                if (precondition.isEnabled) {
                    evaluateChainCheck(precondition, onStepUpdate)
                } else {
                    val step = NodeExecutionStep(nodeId = "chain_check", nodeName = "⛓️ Chained Pipeline", nodeType = NodeType.HTTP_ACTION, state = NodeStepState.SUCCESS, logMessage = "Chained check disabled, skipping.")
                    onStepUpdate?.invoke(listOf(step))
                    PreconditionResult.Passed(steps = listOf(step))
                }
            }
        }
    }

    private fun postFailureNotification(precondition: Precondition, reason: String) {
        val ctx = context ?: return
        try {
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val channelId = "precondition_failure_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Precondition Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for failed precondition checks"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val (typeEmoji, typeTitle) = when (precondition.type) {
                PreconditionType.INTERNET_CHECK -> "🌐" to "Internet Check"
                PreconditionType.VPN_CHECK -> "🔐" to "VPN Check"
                PreconditionType.HEALTH_CHECK -> "🩺" to "Healthcheck"
                PreconditionType.SIM_CHECK -> "📱" to "SIM Card Check"
                PreconditionType.GPS_CHECK -> "📍" to "GPS Check"
                PreconditionType.CHAINED_CHECK -> "⛓️" to "Chained Pipeline"
            }

            val notification = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("$typeEmoji Precondition Failed: $typeTitle")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Precondition Failed:\n$reason"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (_: Exception) {}
    }

    private suspend fun evaluateChainCheck(
        check: Precondition.ChainCheck,
        onStepUpdate: ((List<NodeExecutionStep>) -> Unit)? = null
    ): PreconditionResult = withContext(Dispatchers.IO) {
        if (check.nodes.isEmpty()) {
            return@withContext PreconditionResult.Failed("Chained pipeline has no nodes")
        }
        val workflow = Workflow(
            id = "chain_prec",
            name = "Chained Precondition",
            nodes = check.nodes,
            connections = check.connections
        )
        val evaluator = PreconditionEvaluator(context, okHttpClient)
        val engine = WorkflowEngine(
            preconditionEvaluator = evaluator,
            credentialRepository = null,
            executorFactory = NodeExecutorFactory(okHttpClient)
        )
        var capturedSteps = emptyList<NodeExecutionStep>()

        val manualTrigger = check.nodes.firstOrNull { it.type == NodeType.MANUAL_TRIGGER }
        val triggerConfig = try { org.json.JSONObject(manualTrigger?.configJson ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
        val sampleBody = triggerConfig.optString("testBody", "{\"code\": \"849201\", \"status\": \"active\"}")
        val sampleSender = triggerConfig.optString("testSender", "+15551234567")

        val testContextMap = mutableMapOf<String, Any>(
            "sender" to sampleSender,
            "body" to sampleBody,
            "http_response" to sampleBody,
            "received_at" to System.currentTimeMillis()
        )

        val result = engine.executeWorkflowWithProgress(
            workflow = workflow,
            context = ExecutionContext(testContextMap),
            appContext = context,
            onStepUpdate = { steps ->
                capturedSteps = steps
                onStepUpdate?.invoke(steps)
            }
        )

        when (result) {
            is WorkflowExecutionResult.Success -> PreconditionResult.Passed(steps = capturedSteps)
            is WorkflowExecutionResult.PreconditionFailed -> PreconditionResult.Failed(
                reason = "Chained check failed at node '${result.nodeName}': ${result.reason}",
                steps = capturedSteps
            )
            is WorkflowExecutionResult.Failed -> PreconditionResult.Failed(
                reason = "Chained execution failed: ${result.reason}",
                steps = capturedSteps
            )
            is WorkflowExecutionResult.Cancelled -> PreconditionResult.Failed(
                reason = "Chained execution manually cancelled by user",
                steps = capturedSteps
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun isInternetAvailable(): Boolean {
        val ctx = context ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @SuppressLint("MissingPermission")
    fun isVpnConnected(): Boolean {
        val ctx = context
        if (ctx != null) {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true
                    }
                }
            }
        }

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp) {
                    val name = iface.name.lowercase()
                    if (name.contains("tun") || name.contains("ppp") || name.contains("tailscale") || name.contains("wireguard") || name.contains("utun")) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private suspend fun checkHealth(check: Precondition.HealthCheck): PreconditionResult = withContext(Dispatchers.IO) {
        if (check.targetUrl.isBlank()) {
            return@withContext PreconditionResult.Failed("Healthcheck URL is empty")
        }
        try {
            val requestBuilder = Request.Builder().url(check.targetUrl.trim())

            when (check.method.uppercase()) {
                "HEAD" -> requestBuilder.head()
                "POST" -> requestBuilder.post(ByteArray(0).toRequestBody(null))
                else -> requestBuilder.get()
            }

            if (check.headers.isNotBlank()) {
                check.headers.split("\n", ",").forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }

            val request = requestBuilder.build()
            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (code == check.expectedStatus || (check.expectedStatus == 200 && response.isSuccessful)) {
                    PreconditionResult.Passed()
                } else {
                    PreconditionResult.Failed("Healthcheck HTTP $code (expected ${check.expectedStatus})")
                }
            }
        } catch (e: Exception) {
            PreconditionResult.Failed("Healthcheck unreachable: ${e.message}")
        }
    }

    private fun checkSim(check: Precondition.SimCheck): PreconditionResult {
        val ctx = context ?: return PreconditionResult.Passed()

        val hasPhoneState = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPhoneState) {
            return PreconditionResult.Failed("Missing Phone State Permission (READ_PHONE_STATE)")
        }

        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return PreconditionResult.Failed("Telephony service unavailable")

        if (tm.simState != TelephonyManager.SIM_STATE_READY) {
            return PreconditionResult.Failed("SIM card not ready")
        }

        if (check.requiredCarrier.isNotBlank()) {
            val carrier = tm.simOperatorName ?: ""
            if (!carrier.contains(check.requiredCarrier, ignoreCase = true)) {
                return PreconditionResult.Failed("Carrier '$carrier' does not match required '${check.requiredCarrier}'")
            }
        }

        return PreconditionResult.Passed()
    }

    private fun checkGps(check: Precondition.GpsCheck): PreconditionResult {
        val ctx = context ?: return PreconditionResult.Passed()

        val hasFineLoc = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarseLoc = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasFineLoc && !hasCoarseLoc) {
            return PreconditionResult.Failed("Missing Location Permission (ACCESS_FINE_LOCATION)")
        }

        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return PreconditionResult.Failed("Location service unavailable")

        val isGpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return if (check.requireGpsEnabled && !isGpsOn) {
            PreconditionResult.Failed("GPS/Location is disabled on device")
        } else {
            PreconditionResult.Passed()
        }
    }
}
