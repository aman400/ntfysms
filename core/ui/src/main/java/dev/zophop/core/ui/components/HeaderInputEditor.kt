package dev.zophop.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class KeyValueItem(val key: String, val value: String)

@Composable
fun KeyValueInputEditor(
    title: String = "Headers",
    pairSeparator: String = ": ",
    joinSeparator: String = "\n",
    exampleText: String = "Example: Content-Type: application/json",
    inputString: String,
    onInputChanged: (String) -> Unit
) {
    var items by remember(inputString) {
        mutableStateOf(
            if (inputString.isBlank()) {
                listOf(KeyValueItem("", ""))
            } else {
                val delimiter = if (inputString.contains("&")) '&' else '\n'
                inputString.split(delimiter, ',').map { line ->
                    val parts = line.split(Regex("[:=]"), limit = 2)
                    KeyValueItem(parts.getOrNull(0)?.trim() ?: "", parts.getOrNull(1)?.trim() ?: "")
                }.ifEmpty { listOf(KeyValueItem("", "")) }
            }
        )
    }

    var showInfo by remember { mutableStateOf(false) }

    fun updateString(currentItems: List<KeyValueItem>) {
        val formatted = currentItems.filter { it.key.isNotBlank() || it.value.isNotBlank() }
            .joinToString(joinSeparator) { "${it.key}$pairSeparator${it.value}" }
        onInputChanged(formatted)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { showInfo = !showInfo },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Examples",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            TextButton(
                onClick = {
                    val updated = items + KeyValueItem("", "")
                    items = updated
                    updateString(updated)
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Item", fontSize = 12.sp)
            }
        }

        AnimatedVisibility(visible = showInfo) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = exampleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = item.key,
                    onValueChange = { newKey ->
                        items = items.toMutableList().also { it[index] = item.copy(key = newKey) }
                        updateString(items)
                    },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.value,
                    onValueChange = { newVal ->
                        items = items.toMutableList().also { it[index] = item.copy(value = newVal) }
                        updateString(items)
                    },
                    label = { Text("Value") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        val updated = items.toMutableList().apply { removeAt(index) }
                        items = updated.ifEmpty { listOf(KeyValueItem("", "")) }
                        updateString(items)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderInputEditor(
    headersString: String,
    onHeadersChanged: (String) -> Unit
) {
    KeyValueInputEditor(
        title = "HTTP Headers",
        pairSeparator = ": ",
        joinSeparator = "\n",
        exampleText = "Examples:\n• Content-Type: application/json\n• Authorization: Bearer <token>",
        inputString = headersString,
        onInputChanged = onHeadersChanged
    )
}
