package `in`.ntfysms.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ntfysms.data.MessageEntity
import `in`.ntfysms.data.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogFilterType {
    ALL,
    DELIVERED,
    FAILED,
    QUEUED,
    EXPIRED
}

@Composable
fun LogsScreen(
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf(LogFilterType.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var previewEvent by remember { mutableStateOf<MessageEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredEvents = remember(allEvents, selectedFilter, searchQuery) {
        allEvents.filter { event ->
            val matchesFilter = when (selectedFilter) {
                LogFilterType.ALL -> true
                LogFilterType.DELIVERED -> event.status == MessageStatus.DELIVERED
                LogFilterType.FAILED -> event.status == MessageStatus.FAILED
                LogFilterType.QUEUED -> event.status == MessageStatus.QUEUED
                LogFilterType.EXPIRED -> event.status == MessageStatus.EXPIRED
            }
            val matchesSearch = searchQuery.isBlank() ||
                event.sender.contains(searchQuery, ignoreCase = true) ||
                (event.errorMessage != null && event.errorMessage.contains(searchQuery, ignoreCase = true))

            matchesFilter && matchesSearch
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by sender or error text...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Filter Chips Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(LogFilterType.entries.toTypedArray()) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = when (filter) {
                                        LogFilterType.ALL -> "All (${allEvents.size})"
                                        LogFilterType.DELIVERED -> "Delivered"
                                        LogFilterType.FAILED -> "Failed"
                                        LogFilterType.QUEUED -> "Queued"
                                        LogFilterType.EXPIRED -> "Expired"
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }

            if (filteredEvents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No message logs match '$searchQuery'" else "No log records found",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredEvents) { event ->
                    LogEventCardItem(
                        event = event,
                        isRetrying = uiState.retryingMessageId == event.id,
                        onRetry = {
                            viewModel.retryMessage(event.id) { success, message ->
                                Toast.makeText(context, message ?: "Retry completed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClick = { previewEvent = event }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(36.dp)) }
        }

        // Preview Event Dialog
        previewEvent?.let { event ->
            EventPreviewDialogWithRetry(
                event = event,
                isRetrying = uiState.retryingMessageId == event.id,
                onRetry = {
                    viewModel.retryMessage(event.id) { success, message ->
                        Toast.makeText(context, message ?: "Retry completed", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { previewEvent = null }
            )
        }

        // Clear Events Confirmation Dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear All Log Records") },
                text = { Text("Are you sure you want to delete all recorded log items from the local database?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllEvents()
                            showClearDialog = false
                        }
                    ) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun LogEventCardItem(
    event: MessageEntity,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss — dd MMM yyyy", Locale.getDefault()) }
    val formattedTime = remember(event.receivedAt) { dateFormat.format(Date(event.receivedAt)) }

    val (statusBg, statusFg) = when (event.status) {
        MessageStatus.DELIVERED -> AppStatusColors.successColors()
        MessageStatus.QUEUED -> AppStatusColors.warningColors()
        MessageStatus.EXPIRED -> AppStatusColors.expiredColors()
        else -> AppStatusColors.errorColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sender: ${event.sender}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Box(
                    modifier = Modifier
                        .background(statusBg, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = event.status,
                        color = statusFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            event.errorMessage?.let { note ->
                val isPreConditionWaiting = event.status == MessageStatus.QUEUED || note.startsWith("⏳")
                val noteBg = if (isPreConditionWaiting) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.errorContainer
                val noteFg = if (isPreConditionWaiting) Color(0xFF92400E) else MaterialTheme.colorScheme.onErrorContainer
                val noteTitle = if (isPreConditionWaiting) "⏳ Waiting on Pre-Condition:" else "⚠️ Delivery Error:"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(noteBg, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = noteTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = noteFg
                        )
                        Text(
                            text = note,
                            fontSize = 12.sp,
                            color = noteFg
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attempts: ${event.retryCount}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                if (event.status != MessageStatus.DELIVERED) {
                    OutlinedButton(
                        onClick = onRetry,
                        enabled = !isRetrying
                    ) {
                        if (isRetrying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retrying...", fontSize = 12.sp)
                        } else {
                            Text("Retry Delivery", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventPreviewDialogWithRetry(
    event: MessageEntity,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss — dd MMM yyyy", Locale.getDefault()) }
    val formattedTime = remember(event.receivedAt) { dateFormat.format(Date(event.receivedAt)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Event Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Sender: ${event.sender}", fontWeight = FontWeight.SemiBold)
                Text(text = "Received At: $formattedTime", fontSize = 13.sp)
                Text(text = "Status: ${event.status}", fontWeight = FontWeight.Bold)
                Text(text = "Retry Count: ${event.retryCount}", fontSize = 13.sp)

                event.errorMessage?.let { err ->
                    Text(text = "Error / Status Note:\n$err", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }

                OutlinedButton(
                    onClick = {
                        val details = "Sender: ${event.sender}\nTime: $formattedTime\nStatus: ${event.status}\nAttempts: ${event.retryCount}\nError/Note: ${event.errorMessage ?: "None"}"
                        clipboardManager.setText(AnnotatedString(details))
                        Toast.makeText(context, "Log details copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📋 Copy Event Details", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "Privacy Note: Message body text is deleted immediately upon delivery or expiry to satisfy security requirements.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.status != MessageStatus.DELIVERED) {
                    Button(
                        onClick = onRetry,
                        enabled = !isRetrying
                    ) {
                        if (isRetrying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retrying...")
                        } else {
                            Text("Retry Delivery")
                        }
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
