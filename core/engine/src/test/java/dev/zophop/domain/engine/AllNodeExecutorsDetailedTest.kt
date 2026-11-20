package dev.zophop.domain.engine

import dev.zophop.domain.engine.executors.*
import dev.zophop.domain.engine.plugin.BuiltInPluginRegistry
import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.WorkflowNode
import dev.zophop.plugin.api.ExecutionContext
import dev.zophop.plugin.api.NodeExecutionResult
import dev.zophop.plugin.gotify.GotifyActionExecutor
import dev.zophop.plugin.ntfy.NtfyActionExecutor
import dev.zophop.plugin.sms.SendSmsExecutor
import dev.zophop.plugin.sms.SmsTriggerExecutor
import dev.zophop.plugin.notification.NotificationTriggerExecutor
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllNodeExecutorsDetailedTest {

    @Test
    fun testNodeExecutorFactoryCreatesCorrectExecutors() {
        BuiltInPluginRegistry.registerModule(dev.zophop.plugin.sms.SmsPluginModule)
        BuiltInPluginRegistry.registerModule(dev.zophop.plugin.notification.NotificationPluginModule)
        BuiltInPluginRegistry.registerModule(dev.zophop.plugin.ntfy.NtfyPluginModule)
        BuiltInPluginRegistry.registerModule(dev.zophop.plugin.gotify.GotifyPluginModule)
        BuiltInPluginRegistry.registerModule(dev.zophop.plugin.call.CallPluginModule)
        BuiltInPluginRegistry.registerModule(dev.zophop.plugin.gps.GpsPluginModule)

        val factory = NodeExecutorFactory()

        assertEquals("SmsTriggerExecutor", factory.getExecutor("dev.zophop.plugin.sms.SMS_TRIGGER")::class.java.simpleName)
        assertEquals("NotificationTriggerExecutor", factory.getExecutor("dev.zophop.plugin.notification.NOTIFICATION_TRIGGER")::class.java.simpleName)
        assertEquals("ManualTriggerExecutor", factory.getExecutor(NodeType.MANUAL_TRIGGER)::class.java.simpleName)
        assertEquals("RegexParserExecutor", factory.getExecutor(NodeType.REGEX_PARSER)::class.java.simpleName)
        assertEquals("JsonParserExecutor", factory.getExecutor(NodeType.JSON_PARSER)::class.java.simpleName)
        assertEquals("TemplateFormatterExecutor", factory.getExecutor(NodeType.TEMPLATE_FORMATTER)::class.java.simpleName)
        assertEquals("ConditionExecutor", factory.getExecutor(NodeType.CONDITION)::class.java.simpleName)
        assertEquals("HttpActionExecutor", factory.getExecutor(NodeType.HTTP_ACTION)::class.java.simpleName)
        assertEquals("NtfyActionExecutor", factory.getExecutor(NodeType.NTFY_ACTION)::class.java.simpleName)
        assertEquals("GotifyActionExecutor", factory.getExecutor(NodeType.GOTIFY_ACTION)::class.java.simpleName)
        assertEquals("SendNotificationExecutor", factory.getExecutor(NodeType.SEND_NOTIFICATION_ACTION)::class.java.simpleName)
        assertEquals("SendSmsExecutor", factory.getExecutor("dev.zophop.plugin.sms.SEND_SMS_ACTION")::class.java.simpleName)
    }

    @Test
    fun testNtfyActionExecutorSuccess() = runTest {
        var capturedUrl = ""
        var capturedTitle = ""
        var capturedBody = ""

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                capturedTitle = req.header("Title") ?: ""
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody())
                    .build()
            }
            .build()

        val ntfyExecutor = NtfyActionExecutor(client)

        val node = WorkflowNode(
            id = "ntfy1",
            name = "ntfy Action",
            type = NodeType.NTFY_ACTION,
            isEnabled = true,
            configJson = """
                {
                    "serverUrl": "https://ntfy.sh",
                    "topic": "my-alerts",
                    "title": "Bank OTP Alert",
                    "priority": "5"
                }
            """.trimIndent()
        )

        val context = ExecutionContext(
            mapOf(
                "body" to "Your verification code is 123456"
            )
        )

        val result = ntfyExecutor.execute(node, context, appContext = null)

        assertTrue(result is NodeExecutionResult.Success)
        assertEquals("https://ntfy.sh/my-alerts", capturedUrl)
        assertEquals("Bank OTP Alert", capturedTitle)
        assertEquals("Your verification code is 123456", capturedBody)
    }

    @Test
    fun testGotifyActionExecutorSuccess() = runTest {
        var capturedUrl = ""
        var capturedBody = ""

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"id": 1}""".toResponseBody())
                    .build()
            }
            .build()

        val gotifyExecutor = GotifyActionExecutor(client)

        val node = WorkflowNode(
            id = "gotify1",
            name = "Gotify Action",
            type = NodeType.GOTIFY_ACTION,
            isEnabled = true,
            configJson = """
                {
                    "serverUrl": "https://gotify.example.com",
                    "appToken": "A1B2C3D4E5",
                    "title": "Server Alert",
                    "priority": 8
                }
            """.trimIndent()
        )

        val context = ExecutionContext(mapOf("body" to "Disk usage > 90%"))
        val result = gotifyExecutor.execute(node, context, appContext = null)

        assertTrue(result is NodeExecutionResult.Success)
        assertEquals("https://gotify.example.com/message?token=A1B2C3D4E5", capturedUrl)
        assertTrue(capturedBody.contains("Disk usage > 90%"))
        assertTrue(capturedBody.contains("Server Alert"))
    }

    @Test
    fun testNtfyActionExecutorFailsOnEmptyServerUrl() = runTest {
        val client = OkHttpClient.Builder().build()
        val ntfyExecutor = NtfyActionExecutor(client)

        val node = WorkflowNode("n1", "ntfy", NodeType.NTFY_ACTION, true, """{"serverUrl":"","topic":"test"}""")
        val context = ExecutionContext(mapOf("body" to "test"))

        val result = ntfyExecutor.execute(node, context, null)
        assertTrue(result is NodeExecutionResult.Failed)
    }

    @Test
    fun testGotifyActionExecutorFailsOnEmptyServerUrl() = runTest {
        val client = OkHttpClient.Builder().build()
        val gotifyExecutor = GotifyActionExecutor(client)

        val node = WorkflowNode("g1", "gotify", NodeType.GOTIFY_ACTION, true, """{"serverUrl":""}""")
        val context = ExecutionContext(mapOf("body" to "test"))

        val result = gotifyExecutor.execute(node, context, null)
        assertTrue(result is NodeExecutionResult.Failed)
    }
}
