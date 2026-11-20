package dev.zophop.plugin.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class NodeCategory : Parcelable {
    TRIGGER,
    EXTRACTION,
    LOGIC,
    ACTION
}

sealed class ConfigFieldSpec(
    open val key: String,
    open val label: String,
    open val tooltip: String?,
    open val isTestField: Boolean = false
) : Parcelable {
    @Parcelize
    data class TextField(
        override val key: String,
        override val label: String,
        override val tooltip: String? = null,
        val placeholder: String? = null,
        val singleLine: Boolean = true,
        val minLines: Int = 1,
        override val isTestField: Boolean = false
    ) : ConfigFieldSpec(key, label, tooltip, isTestField)

    @Parcelize
    data class DropdownField(
        override val key: String,
        override val label: String,
        override val tooltip: String? = null,
        val options: List<Pair<String, String>>,
        override val isTestField: Boolean = false
    ) : ConfigFieldSpec(key, label, tooltip, isTestField)

    @Parcelize
    data class CodeTemplateField(
        override val key: String,
        override val label: String,
        override val tooltip: String? = null,
        val minLines: Int = 3,
        val singleLine: Boolean = false,
        val maxLength: Int? = null,
        override val isTestField: Boolean = false
    ) : ConfigFieldSpec(key, label, tooltip, isTestField)
}

@Parcelize
data class NodePluginManifest(
    val typeId: String,
    val displayName: String,
    val description: String,
    val category: NodeCategory,
    val iconResName: String = "ic_node_plugin",
    val apiVersion: Int = CURRENT_API_VERSION,
    val requiredPermissions: List<String> = emptyList(),
    val requiredCredentialType: String? = null,
    val credentialSpec: CredentialSpec? = null,
    val setupActivity: String? = null,
    val triggerTestPayload: String? = null,
    val configSchema: List<ConfigFieldSpec> = emptyList()
) : Parcelable {
    fun isTriggerNode(): Boolean = (category == NodeCategory.TRIGGER)
    fun isRegularNode(): Boolean = (category != NodeCategory.TRIGGER)

    companion object {
        const val CURRENT_API_VERSION = 1
    }
}
