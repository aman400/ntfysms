package dev.zophop.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import dev.zophop.ui.credentials.CredentialsScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class ComposeUiScreensComprehensiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(application)
        } catch (_: Exception) {
        }
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
