package dev.zophop.domain.engine

import dev.zophop.domain.engine.executors.*
import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.WorkflowNode
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
        val factory = NodeExecutorFactory()

        assertTrue(factory.getExecutor(NodeType.SMS_TRIGGER) is SmsTriggerExecutor)
        assertTrue(factory.getExecutor(NodeType.NOTIFICATION_TRIGGER) is NotificationTriggerExecutor)
        assertTrue(factory.getExecutor(NodeType.MANUAL_TRIGGER) is ManualTriggerExecutor)
        assertTrue(factory.getExecutor(NodeType.REGEX_PARSER) is RegexParserExecutor)
        assertTrue(factory.getExecutor(NodeType.JSON_PARSER) is JsonParserExecutor)
        assertTrue(factory.getExecutor(NodeType.TEMPLATE_FORMATTER) is TemplateFormatterExecutor)
        assertTrue(factory.getExecutor(NodeType.CONDITION) is ConditionExecutor)
        assertTrue(factory.getExecutor(NodeType.HTTP_ACTION) is HttpActionExecutor)
        assertTrue(factory.getExecutor(NodeType.NTFY_ACTION) is NtfyActionExecutor)
        assertTrue(factory.getExecutor(NodeType.GOTIFY_ACTION) is GotifyActionExecutor)
        assertTrue(factory.getExecutor(NodeType.SEND_NOTIFICATION_ACTION) is SendNotificationExecutor)
        assertTrue(factory.getExecutor(NodeType.SEND_SMS_ACTION) is SendSmsExecutor)
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

        val httpExecutor = HttpActionExecutor(client)
        val ntfyExecutor = NtfyActionExecutor(httpExecutor)

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

        val httpExecutor = HttpActionExecutor(client)
        val gotifyExecutor = GotifyActionExecutor(httpExecutor)

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
        val ntfyExecutor = NtfyActionExecutor(HttpActionExecutor(client))

        val node = WorkflowNode("n1", "ntfy", NodeType.NTFY_ACTION, true, """{"serverUrl":"","topic":"test"}""")
        val context = ExecutionContext(mapOf("body" to "test"))

        val result = ntfyExecutor.execute(node, context, null)
        assertTrue(result is NodeExecutionResult.Failed)
    }

    @Test
    fun testGotifyActionExecutorFailsOnEmptyServerUrl() = runTest {
        val client = OkHttpClient.Builder().build()
        val gotifyExecutor = GotifyActionExecutor(HttpActionExecutor(client))

        val node = WorkflowNode("g1", "gotify", NodeType.GOTIFY_ACTION, true, """{"serverUrl":""}""")
        val context = ExecutionContext(mapOf("body" to "test"))

        val result = gotifyExecutor.execute(node, context, null)
        assertTrue(result is NodeExecutionResult.Failed)
    }
}
