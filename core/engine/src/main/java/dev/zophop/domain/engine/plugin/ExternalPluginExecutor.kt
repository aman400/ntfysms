package dev.zophop.domain.engine.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dev.zophop.plugin.api.ExecutionContext
import dev.zophop.plugin.api.NodeExecutionResult
import dev.zophop.plugin.api.NodeExecutor
import dev.zophop.domain.model.WorkflowNode
import dev.zophop.plugin.api.INodePluginService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

class ExternalPluginExecutor(
    private val pluginInfo: ExternalPluginServiceInfo
) : NodeExecutor {

    override suspend fun execute(
        node: WorkflowNode,
        context: ExecutionContext,
        appContext: Context?
    ): NodeExecutionResult = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext NodeExecutionResult.Failed("Context unavailable for plugin execution")

        val intent = Intent().apply {
            component = ComponentName(pluginInfo.packageName, pluginInfo.serviceClassName)
        }

        val inputContextJson = JSONObject(context.getAll()).toString()
        val resultJson = executeViaAidlService(ctx, intent, node.id, node.configJson, inputContextJson)
        if (resultJson.isBlank()) {
            return@withContext NodeExecutionResult.Failed("Failed to connect or receive response from plugin '${pluginInfo.manifest.displayName}'")
        }

        try {
            val json = JSONObject(resultJson)
            val isSuccess = json.optBoolean("isSuccess", false)
            val outputPort = json.optString("outputPort", "main")
            val logMessage = json.optString("logMessage", "Plugin '${pluginInfo.manifest.displayName}' executed")
            val errorReason = if (json.has("errorReason")) json.getString("errorReason") else "Plugin execution failed"

            val outputData = json.optJSONObject("outputDataMap")
            if (outputData != null) {
                val keys = outputData.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    context.set(k, outputData.optString(k, ""))
                }
            }

            if (isSuccess) {
                NodeExecutionResult.Success(outputPort = outputPort, logMessage = logMessage)
            } else {
                NodeExecutionResult.Failed(errorReason)
            }
        } catch (e: Exception) {
            NodeExecutionResult.Failed("Failed to parse response from plugin '${pluginInfo.manifest.displayName}': ${e.message}")
        }
    }

    private suspend fun executeViaAidlService(
        ctx: Context,
        intent: Intent,
        nodeId: String,
        configJson: String,
        inputContextJson: String
    ): String = suspendCancellableCoroutine { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val binder = INodePluginService.Stub.asInterface(service)
                    val response = binder.executeNode(nodeId, configJson, inputContextJson) ?: ""
                    if (continuation.isActive) continuation.resume(response)
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume("")
                } finally {
                    try { ctx.unbindService(this) } catch (_: Exception) {}
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (continuation.isActive) continuation.resume("")
            }
        }

        val bound = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound && continuation.isActive) {
            continuation.resume("")
        }
    }
}
