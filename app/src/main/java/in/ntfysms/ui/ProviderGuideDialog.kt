package `in`.ntfysms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ntfysms.data.ServerType

@Composable
fun ProviderGuideDialog(
    serverType: String,
    onDismiss: () -> Unit
) {
    val isGotify = serverType == ServerType.GOTIFY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isGotify) "🔔 How to Setup Gotify" else "🚀 How to Setup ntfy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isGotify) {
                    GotifySetupGuideContent()
                } else {
                    NtfySetupGuideContent()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It!")
            }
        }
    )
}

@Composable
fun NtfySetupGuideContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "ntfy allows sending push notifications via plain HTTP/HTTPS requests.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GuideStepItem(
            stepNumber = "1",
            title = "Choose Server URL",
            description = "Use the free public server (https://ntfy.sh) or enter your self-hosted ntfy server URL."
        )

        GuideStepItem(
            stepNumber = "2",
            title = "Set a Unique Topic Name",
            description = "Pick a unique topic string (e.g. my-private-otp-9823). Keep it long and unguessable so others cannot guess your feed!"
        )

        GuideStepItem(
            stepNumber = "3",
            title = "Subscribe to Notifications",
            description = "Open https://ntfy.sh/my-private-otp-9823 in your browser or install the official ntfy Android/iOS app and subscribe to your topic."
        )

        GuideStepItem(
            stepNumber = "4",
            title = "Optional Access Token",
            description = "If your self-hosted ntfy server uses authentication or ACL protection, generate a Bearer Token in ntfy Web UI and paste it into the Token field."
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "💡 Quick Verification Example:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "curl -d \"Test message\" https://ntfy.sh/my-private-otp-9823",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun GotifySetupGuideContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Gotify is a self-hosted notification server for sending and receiving messages in real time.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GuideStepItem(
            stepNumber = "1",
            title = "Deploy Gotify Server",
            description = "Run Gotify on your VPS or local home server via Docker:"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text(
                text = "docker run -p 8080:80 -v gotify_data:/app/data gotify/server",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF38BDF8)
            )
        }

        GuideStepItem(
            stepNumber = "2",
            title = "Log in to Web UI",
            description = "Open your Gotify Web UI (e.g. https://gotify.example.com) and log in using admin credentials."
        )

        GuideStepItem(
            stepNumber = "3",
            title = "Create an Application",
            description = "Navigate to 'Apps' → Click 'Create Application' → Enter name 'NtfySMS'."
        )

        GuideStepItem(
            stepNumber = "4",
            title = "Copy Application Token",
            description = "Copy the generated Application Token (X-Gotify-Key, e.g. A1b2C3d4E5f6) and paste it into the Gotify App Token field."
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "💡 Verification Curl Request:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "curl -X POST \"https://gotify.example.com/message?token=A1b2C3d4\" -F \"message=Test\"",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun GuideStepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = stepNumber,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}
