package `in`.ntfysms.domain.engine

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import `in`.ntfysms.domain.model.Precondition
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
import kotlinx.coroutines.Dispatchers
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

    open suspend fun evaluateAll(
        preconditions: List<Precondition>,
        onStepUpdate: ((List<NodeExecutionStep>) -> Unit)? = null
    ): PreconditionResult = withContext(Dispatchers.IO) {
        for (precondition in preconditions) {
            val result = evaluate(precondition, onStepUpdate)
            if (result is PreconditionResult.Failed) {
                return@withContext result
            }
        }
        PreconditionResult.Passed()
    }

    open suspend fun evaluate(
        precondition: Precondition,
        onStepUpdate: ((List<NodeExecutionStep>) -> Unit)? = null
    ): PreconditionResult = withContext(Dispatchers.IO) {
        when (precondition) {
            is Precondition.InternetCheck -> {
                if (precondition.isEnabled && !isInternetAvailable()) {
                    PreconditionResult.Failed("Internet connection unavailable")
                } else PreconditionResult.Passed()
            }
            is Precondition.VpnCheck -> {
                if (precondition.isEnabled && !isVpnConnected()) {
                    PreconditionResult.Failed("VPN connection required (Awaiting Tailscale/WireGuard/VPN)")
                } else PreconditionResult.Passed()
            }
            is Precondition.HealthCheck -> {
                if (precondition.isEnabled) {
                    checkHealth(precondition)
                } else PreconditionResult.Passed()
            }
            is Precondition.SimCheck -> {
                if (precondition.isEnabled) {
                    checkSim(precondition)
                } else PreconditionResult.Passed()
            }
            is Precondition.GpsCheck -> {
                if (precondition.isEnabled) {
                    checkGps(precondition)
                } else PreconditionResult.Passed()
            }
            is Precondition.ChainCheck -> {
                if (precondition.isEnabled) {
                    evaluateChainCheck(precondition, onStepUpdate)
                } else PreconditionResult.Passed()
            }
        }
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
        val engine = WorkflowEngine(evaluator)
        var capturedSteps = emptyList<NodeExecutionStep>()

        val testContextMap = mutableMapOf<String, Any>(
            "sender" to "+15551234567",
            "body" to "Test manual payload data 849201",
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
        }
    }

    fun isInternetAvailable(): Boolean {
        val ctx = context ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

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
