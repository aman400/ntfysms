package dev.zophop.plugin.telegram

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.zophop.domain.model.WorkflowNode
import dev.zophop.plugin.api.ExecutionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class TelegramNodePluginServiceTest {

    @Test
    fun testTelegramPluginManifestStructure() {
        val serviceController = Robolectric.buildService(TelegramNodePluginService::class.java).create()
        val service = serviceController.get()
        val binder = service.onBind(null) as dev.zophop.plugin.api.INodePluginService
        val manifests = binder.getManifests()

        assertEquals(1, manifests.size)
        val telegramAction = manifests.first()

        assertEquals("dev.zophop.plugin.telegram.TELEGRAM_ACTION", telegramAction.typeId)
        assertEquals(3, telegramAction.configSchema.size)
    }

    @Test
    fun testTelegramExecutorCreation() {
        val executor = TelegramPluginModule.createExecutor("dev.zophop.plugin.telegram.TELEGRAM_ACTION", okhttp3.OkHttpClient(), null)
        assertNotNull(executor)
        assertTrue(executor is TelegramActionExecutor)
    }

    @Test
    fun testTelegramManifestCredentialSpec() {
        val manifests = TelegramPluginModule.getManifests()
        val manifest = manifests.first()
        assertEquals("TELEGRAM_AUTH", manifest.requiredCredentialType)
        assertNotNull(manifest.credentialSpec)
        assertEquals("TELEGRAM_AUTH", manifest.credentialSpec?.typeId)
    }

    @Test
    fun testTelegramExecutorResolvesCredentialToken() = kotlinx.coroutines.test.runTest {
        val mockCredRepo = object {
            @Suppress("unused")
            fun getCredentialById(id: String): Any? {
                if (id == "cred_123") {
                    return object {
                        @Suppress("unused")
                        fun getConfigJson(): String = """{"token":"123456:mock-token"}"""
                    }
                }
                return null
            }
        }

        val executor = TelegramActionExecutor(mockCredRepo)
        val node = WorkflowNode(
            id = "t1",
            name = "Telegram",
            type = "dev.zophop.plugin.telegram.TELEGRAM_ACTION",
            configJson = """{"credentialId":"cred_123","chatId":"-100123","messageTemplate":"hello"}"""
        )
        val context = ExecutionContext(mapOf("body" to "test body"))
        assertNotNull(executor)
    }
}
