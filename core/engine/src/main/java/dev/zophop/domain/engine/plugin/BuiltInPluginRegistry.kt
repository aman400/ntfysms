package dev.zophop.domain.engine.plugin

import dev.zophop.data.CredentialRepository
import dev.zophop.plugin.api.CredentialSpec
import dev.zophop.plugin.api.NodeExecutor
import dev.zophop.plugin.api.NodePluginManifest
import dev.zophop.plugin.api.NodePluginModule
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.ServiceLoader
import java.util.concurrent.CopyOnWriteArrayList

object BuiltInPluginRegistry {

    private val registeredModules = CopyOnWriteArrayList<NodePluginModule>().apply {
        add(CoreEngineNodesModule)
    }

    init {
        discoverAndRegisterBundledModules()
    }

    fun registerModule(module: NodePluginModule) {
        if (registeredModules.none { it.moduleId == module.moduleId }) {
            registeredModules.add(module)
        }
    }

    fun unregisterModule(moduleId: String) {
        registeredModules.removeAll { it.moduleId == moduleId }
    }

    fun getRegisteredModules(): List<NodePluginModule> = registeredModules.toList()

    private fun discoverAndRegisterBundledModules() {
        try {
            val loader = ServiceLoader.load(NodePluginModule::class.java, NodePluginModule::class.java.classLoader)
            for (module in loader) {
                registerModule(module)
            }
        } catch (_: Throwable) {
        }

        val pluginNames = listOf("sms", "ntfy", "gotify", "gps", "call", "notification")
        for (name in pluginNames) {
            val capitalized = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val className = "dev.zophop.plugin.$name.${capitalized}PluginModule"
            try {
                val clazz = Class.forName(className)
                val instance = try {
                    clazz.getField("INSTANCE").get(null)
                } catch (_: Exception) {
                    clazz.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
                }
                if (instance is NodePluginModule) {
                    registerModule(instance)
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun createExecutor(
        typeId: String,
        okHttpClient: OkHttpClient,
        credentialRepository: CredentialRepository?
    ): NodeExecutor? {
        for (module in registeredModules) {
            val executor = module.createExecutor(typeId, okHttpClient, credentialRepository)
            if (executor != null) return executor
        }
        return null
    }

    fun getBuiltInManifests(): List<NodePluginManifest> {
        return registeredModules.flatMap { it.getManifests() }
    }

    fun getBuiltInCredentialSpecs(): List<CredentialSpec> {
        return registeredModules.flatMap { it.getCredentialSpecs() }
    }

    fun getBuiltInManifestByTypeId(typeId: String): NodePluginManifest? {
        return getBuiltInManifests().find { it.typeId == typeId }
    }
}
