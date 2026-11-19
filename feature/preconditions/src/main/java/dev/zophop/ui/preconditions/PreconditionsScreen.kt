package dev.zophop.ui.preconditions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zophop.data.PreconditionEntity
import dev.zophop.domain.engine.NodeExecutionStep
import dev.zophop.domain.engine.PreconditionResult
import dev.zophop.domain.model.Credential
import dev.zophop.domain.model.PreconditionType
import dev.zophop.domain.model.Workflow

private enum class PreconditionCategoryFilter {
    ALL,
    ACTIVE,
    NETWORK_VPN,
    HEALTHCHECK,
    HARDWARE_GPS,
    CHAINED
}

/**
 * Modernized Preconditions Screen.
 */
@Composable
fun PreconditionsScreen(
    preconditions: List<PreconditionEntity> = emptyList(),
    credentials: List<Credential> = emptyList(),
    workflows: List<Workflow> = emptyList(),
    onSavePrecondition: ((PreconditionEntity) -> Unit)? = null,
    onDeletePrecondition: ((String) -> Unit)? = null,
    onTestPreconditionWithProgress: ((PreconditionEntity, (List<NodeExecutionStep>) -> Unit, (PreconditionResult) -> Unit) -> Unit)? = null,
    onAddPrecondition: (() -> Unit)? = null,
    onEditPrecondition: ((PreconditionEntity) -> Unit)? = null
) {
    var editingPrecondition by remember { mutableStateOf<PreconditionEntity?>(null) }
    var pendingDeletePrecondition by remember { mutableStateOf<PreconditionEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf(PreconditionCategoryFilter.ALL) }

    val activeCount = preconditions.count { it.isEnabled }

    val filteredPreconditions = remember(preconditions, selectedCategoryFilter) {
        when (selectedCategoryFilter) {
            PreconditionCategoryFilter.ALL -> preconditions
            PreconditionCategoryFilter.ACTIVE -> preconditions.filter { it.isEnabled }
            PreconditionCategoryFilter.NETWORK_VPN -> preconditions.filter {
                it.type == PreconditionType.INTERNET_CHECK.name || it.type == PreconditionType.VPN_CHECK.name
            }
            PreconditionCategoryFilter.HEALTHCHECK -> preconditions.filter {
                it.type == PreconditionType.HEALTH_CHECK.name
            }
            PreconditionCategoryFilter.HARDWARE_GPS -> preconditions.filter {
                it.type == PreconditionType.SIM_CHECK.name || it.type == PreconditionType.GPS_CHECK.name
            }
            PreconditionCategoryFilter.CHAINED -> preconditions.filter {
                it.type == PreconditionType.CHAINED_CHECK.name
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == PreconditionCategoryFilter.ALL,
                            onClick = { selectedCategoryFilter = PreconditionCategoryFilter.ALL },
                            label = { Text("All (${preconditions.size})", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == PreconditionCategoryFilter.ACTIVE,
                            onClick = { selectedCategoryFilter = PreconditionCategoryFilter.ACTIVE },
                            label = { Text("Active (${activeCount})", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == PreconditionCategoryFilter.NETWORK_VPN,
                            onClick = { selectedCategoryFilter = PreconditionCategoryFilter.NETWORK_VPN },
                            label = { Text("Network & VPN", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == PreconditionCategoryFilter.HEALTHCHECK,
                            onClick = { selectedCategoryFilter = PreconditionCategoryFilter.HEALTHCHECK },
                            label = { Text("Healthchecks", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == PreconditionCategoryFilter.HARDWARE_GPS,
                            onClick = { selectedCategoryFilter = PreconditionCategoryFilter.HARDWARE_GPS },
                            label = { Text("SIM & GPS", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == PreconditionCategoryFilter.CHAINED,
                            onClick = { selectedCategoryFilter = PreconditionCategoryFilter.CHAINED },
                            label = { Text("Chained", fontSize = 12.sp) }
                        )
                    }
                }
            }

            // List of Precondition Cards
            if (filteredPreconditions.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (preconditions.isEmpty()) "No preconditions configured yet" else "No matching rules for filter",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (preconditions.isEmpty()) "Tap '+ Add Precondition' below to add a network, VPN, or healthcheck rule." else "Try selecting another category filter above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredPreconditions, key = { it.id }) { prec ->
                    val linkedWorkflows = remember(workflows, prec) {
                        workflows.filter { wf ->
                            wf.nodes.any { node -> node.preconditions.any { p -> p.id == prec.id } }
                        }
                    }

                    PreconditionCardItem(
                        precondition = prec,
                        linkedWorkflowCount = linkedWorkflows.size,
                        onTestPreconditionWithProgress = onTestPreconditionWithProgress,
                        onToggle = { enabled ->
                            onSavePrecondition?.invoke(prec.copy(isEnabled = enabled))
                        },
                        onEdit = {
                            if (onEditPrecondition != null) onEditPrecondition(prec) else editingPrecondition = prec
                        },
                        onDelete = { pendingDeletePrecondition = prec }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = {
                if (onAddPrecondition != null) onAddPrecondition() else showAddSheet = true
            },
            icon = { Icon(Icons.Default.Add, contentDescription = "Add Precondition") },
            text = { Text("Add Precondition") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    if (showAddSheet) {
        PreconditionEditorScreen(
            precondition = null,
            credentials = credentials,
            onTestPreconditionWithProgress = onTestPreconditionWithProgress,
            onDismiss = { showAddSheet = false },
            onSave = { newPrec ->
                onSavePrecondition?.invoke(newPrec)
                showAddSheet = false
            }
        )
        return
    }

    editingPrecondition?.let { prec ->
        PreconditionEditorScreen(
            precondition = prec,
            credentials = credentials,
            onTestPreconditionWithProgress = onTestPreconditionWithProgress,
            onDismiss = { editingPrecondition = null },
            onSave = { updated ->
                onSavePrecondition?.invoke(updated)
                editingPrecondition = null
            }
        )
        return
    }

    // Confirm Delete Precondition Dialog (Preventing deletion if used by workflows)
    pendingDeletePrecondition?.let { prec ->
        val linkedWorkflows = remember(workflows, prec) {
            workflows.filter { wf ->
                wf.nodes.any { node -> node.preconditions.any { p -> p.id == prec.id } }
            }
        }

        if (linkedWorkflows.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { pendingDeletePrecondition = null },
                title = { Text("Cannot Delete Precondition") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Precondition '${prec.name}' is currently used in ${linkedWorkflows.size} workflow(s):")
                        linkedWorkflows.forEach { wf ->
                            Text("• ${wf.name}", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Please remove this precondition from those workflows before deleting it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { pendingDeletePrecondition = null }) {
                        Text("OK")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingDeletePrecondition = null },
                title = { Text("Delete Precondition?") },
                text = { Text("Are you sure you want to delete precondition '${prec.name}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeletePrecondition?.invoke(prec.id)
                            pendingDeletePrecondition = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeletePrecondition = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
