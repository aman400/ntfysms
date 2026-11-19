package dev.zophop.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import dev.zophop.ui.credentials.CredentialsScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

class TestApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = TestApp::class)
class ComposeUiScreensComprehensiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSettingsScreenRendersSuccessfully() {
        val viewModel = MainViewModel(application)

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                hasSmsPermission = true,
                onRequestSmsPermission = {},
                hasNotificationPermission = true,
                onRequestNotificationPermission = {}
            )
        }

        composeTestRule.onNodeWithText("Global Automations Switch").assertExists()
    }

    @Test
    fun testCredentialsScreenRendersSuccessfully() {
        val viewModel = MainViewModel(application)

        composeTestRule.setContent {
            CredentialsScreen(
                viewModel = viewModel,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("No saved credentials found. Tap '+ Add Credential' below to add one.").assertExists()
    }

    @Test
    fun testMainAppScreenNavigationAndTabs() {
        val viewModel = MainViewModel(application)

        composeTestRule.setContent {
            MainAppScreen(
                viewModel = viewModel,
                hasSmsPermission = true,
                onRequestSmsPermission = {},
                hasNotificationPermission = true,
                onRequestNotificationPermission = {}
            )
        }

        composeTestRule.onAllNodesWithText("Workflows")[0].assertExists()
    }
}
