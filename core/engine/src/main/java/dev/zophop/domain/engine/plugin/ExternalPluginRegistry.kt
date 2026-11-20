package dev.zophop.domain.engine.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dev.zophop.plugin.api.INodePluginService
import dev.zophop.plugin.api.NodePluginManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class ExternalPluginServiceInfo(
    val packageName: String,
    val serviceClassName: String,
    val manifest: NodePluginManifest,
    val apiVersion: Int
)

open class ExternalPluginRegistry(
    private val context: Context?
) {
    companion object {
        const val ACTION_NODE_SERVICE = "dev.zophop.plugin.ACTION_NODE_SERVICE"
    }

    private val discoveredPlugins = ConcurrentHashMap<String, ExternalPluginServiceInfo>()

    open fun getDiscoveredPlugins(): List<ExternalPluginServiceInfo> = discoveredPlugins.values.toList()

    open fun getPluginByTypeId(typeId: String): ExternalPluginServiceInfo? {
        val plugin = discoveredPlugins[typeId] ?: return null
        val ctx = context ?: return plugin
        val disabledPluginsStr = ctx.getSharedPreferences("zophop_encrypted_prefs", Context.MODE_PRIVATE)
            .getString("disabled_plugin_packages", "") ?: ""
        val disabledPackages = disabledPluginsStr.split(",").map { it.trim() }.toSet()
        if (disabledPackages.contains(plugin.packageName)) return null
        return plugin
    }

    open suspend fun discoverPlugins(): List<ExternalPluginServiceInfo> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext emptyList()
        val intent = Intent(ACTION_NODE_SERVICE)
        val resolveInfos = ctx.packageManager.queryIntentServices(intent, 0)

        val disabledPluginsStr = ctx.getSharedPreferences("zophop_encrypted_prefs", Context.MODE_PRIVATE)
            .getString("disabled_plugin_packages", "") ?: ""
        val disabledPackages = disabledPluginsStr.split(",").map { it.trim() }.toSet()

        val newMap = ConcurrentHashMap<String, ExternalPluginServiceInfo>()

        for (info in resolveInfos) {
            val serviceInfo = info.serviceInfo ?: continue
            if (serviceInfo.packageName == ctx.packageName) continue
            if (disabledPackages.contains(serviceInfo.packageName)) continue

            val componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)

            val serviceIntent = Intent().apply { component = componentName }
            try {
                val pluginInfoList = queryPluginServiceList(ctx, serviceIntent, serviceInfo.packageName, serviceInfo.name)
                for (pluginInfo in pluginInfoList) {
                    newMap[pluginInfo.manifest.typeId] = pluginInfo
                }
            } catch (_: Exception) {}
        }

        discoveredPlugins.clear()
        discoveredPlugins.putAll(newMap)
        discoveredPlugins.values.toList()
    }

    private suspend fun queryPluginServiceList(
        ctx: Context,
        intent: Intent,
        packageName: String,
        className: String
    ): List<ExternalPluginServiceInfo> = suspendCancellableCoroutine { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val binder = INodePluginService.Stub.asInterface(service)
                    val apiVersion = binder.pluginApiVersion
                    val manifests = binder.manifests

                    val infoList = manifests.map { manifest ->
                        ExternalPluginServiceInfo(packageName, className, manifest, apiVersion)
                    }
                    if (continuation.isActive) continuation.resume(infoList)
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (continuation.isActive) continuation.resume(emptyList())
                } finally {
                    try { ctx.unbindService(this) } catch (_: Exception) {}
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }

        val bound = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound && continuation.isActive) {
            continuation.resume(emptyList())
        }
    }
}
