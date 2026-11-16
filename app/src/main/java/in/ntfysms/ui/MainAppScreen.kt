package `in`.ntfysms.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppTab {
    DASHBOARD,
    LOGS,
    SETTINGS
}

@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A)
            ) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.DASHBOARD,
                    onClick = { selectedTab = AppTab.DASHBOARD },
                    label = {
                        Text(
                            text = "Dashboard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    },
                    icon = { Text("📊", fontSize = 16.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF34D399),
                        selectedTextColor = Color(0xFF34D399),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.LOGS,
                    onClick = { selectedTab = AppTab.LOGS },
                    label = {
                        Text(
                            text = "Log History",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    },
                    icon = { Text("📜", fontSize = 16.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF34D399),
                        selectedTextColor = Color(0xFF34D399),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SETTINGS,
                    onClick = { selectedTab = AppTab.SETTINGS },
                    label = {
                        Text(
                            text = "Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    },
                    icon = { Text("⚙", fontSize = 16.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                AppTab.DASHBOARD -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = { selectedTab = AppTab.SETTINGS },
                        onNavigateToLogs = { selectedTab = AppTab.LOGS }
                    )
                }
                AppTab.LOGS -> {
                    LogsScreen(
                        viewModel = viewModel
                    )
                }
                AppTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        hasSmsPermission = hasSmsPermission,
                        onRequestSmsPermission = onRequestSmsPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestNotificationPermission = onRequestNotificationPermission
                    )
                }
            }
        }
    }
}
