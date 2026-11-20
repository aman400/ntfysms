package dev.zophop.ui.workflows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.zophop.core.ui.components.InfoTooltipField
import dev.zophop.plugin.api.ConfigFieldSpec
import dev.zophop.plugin.api.NodePluginManifest
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicNodeConfigEditor(
    manifest: NodePluginManifest,
    configJson: String,
    onConfigChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentConfig = remember(configJson) {
        try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
    }

    fun updateConfigKey(key: String, value: Any) {
        val newObj = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
        newObj.put(key, value)
        onConfigChanged(newObj.toString())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        manifest.configSchema.forEach { fieldSpec ->
            when (fieldSpec) {
                is ConfigFieldSpec.TextField -> {
                    val value = currentConfig.optString(fieldSpec.key, "")
                    InfoTooltipField(
                        value = value,
                        onValueChange = { updateConfigKey(fieldSpec.key, it) },
                        label = fieldSpec.label,
                        tooltipText = fieldSpec.tooltip ?: fieldSpec.label,
                        placeholder = fieldSpec.placeholder,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = fieldSpec.singleLine,
                        minLines = fieldSpec.minLines
                    )
                }
                is ConfigFieldSpec.DropdownField -> {
                    val selectedVal = currentConfig.optString(fieldSpec.key, fieldSpec.options.firstOrNull()?.first ?: "")
                    val selectedLabel = fieldSpec.options.find { it.first == selectedVal }?.second ?: selectedVal
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InfoTooltipField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = fieldSpec.label,
                            tooltipText = fieldSpec.tooltip ?: fieldSpec.label,
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            singleLine = true,
                            additionalTrailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            fieldSpec.options.forEach { (optionVal, optionLabel) ->
                                DropdownMenuItem(
                                    text = { Text(optionLabel) },
                                    onClick = {
                                        updateConfigKey(fieldSpec.key, optionVal)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                is ConfigFieldSpec.CodeTemplateField -> {
                    val value = currentConfig.optString(fieldSpec.key, "")
                    AutocompleteTextField(
                        value = value,
                        onValueChange = { updateConfigKey(fieldSpec.key, it) },
                        label = fieldSpec.label,
                        tooltipText = fieldSpec.tooltip ?: fieldSpec.label,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = fieldSpec.minLines
                    )
                }
            }
        }
    }
}
