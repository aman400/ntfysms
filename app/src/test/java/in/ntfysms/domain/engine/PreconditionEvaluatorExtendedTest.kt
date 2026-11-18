package `in`.ntfysms.domain.engine

import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Precondition
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PreconditionEvaluatorExtendedTest {

    @Test
    fun testChainedPreconditionPipelineSuccess() = runTest {
        val triggerId = UUID.randomUUID().toString()
        val parserId = UUID.randomUUID().toString()
        val condId = UUID.randomUUID().toString()

        val triggerNode = WorkflowNode(
            id = triggerId,
            name = "Precondition Test Data",
            type = NodeType.MANUAL_TRIGGER,
            configJson = """{"testBody":"{\"status\":\"OK\",\"code\":200}"}"""
        )

        val parserNode = WorkflowNode(
            id = parserId,
            name = "JSON Parser",
            type = NodeType.JSON_PARSER,
            configJson = """{"jsonKey":"status","outputKey":"parsedStatus"}"""
        )

        val condNode = WorkflowNode(
            id = condId,
            name = "Condition Check",
            type = NodeType.CONDITION,
            configJson = """{"variable":"parsedStatus","operator":"EQUALS","value":"OK"}"""
        )

        val chainCheck = Precondition.ChainCheck(
            isEnabled = true,
            nodes = listOf(triggerNode, parserNode, condNode),
            connections = listOf(
                WorkflowConnection(fromNodeId = triggerId, toNodeId = parserId),
                WorkflowConnection(fromNodeId = parserId, toNodeId = condId)
            )
        )

        val evaluator = PreconditionEvaluator(context = null)
        var receivedSteps = emptyList<NodeExecutionStep>()

        val result = evaluator.evaluate(chainCheck) { steps ->
            receivedSteps = steps
        }

        assertTrue(result is PreconditionResult.Passed)
        assertTrue(receivedSteps.isNotEmpty())
        assertEquals(NodeStepState.SUCCESS, receivedSteps.last().state)
    }

    @Test
    fun testPreconditionRetryPolicyRecovery() = runTest {
        var attempts = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                attempts++
                if (attempts == 1) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("error".toResponseBody())
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("ok".toResponseBody())
                        .build()
                }
            }
            .build()

        val evaluator = PreconditionEvaluator(context = null, okHttpClient = client)
        val healthCheck = Precondition.HealthCheck(
            isEnabled = true,
            targetUrl = "https://example.com/health",
            expectedStatus = 200,
            onFailureAction = "RETRY",
            maxRetries = 2,
            retryIntervalSeconds = 1
        )

        val result = evaluator.evaluate(healthCheck)

        assertTrue(result is PreconditionResult.Passed)
        assertEquals(2, attempts)
    }

    @Test
    fun testPreconditionRetryExhaustionFails() = runTest {
        var attempts = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                attempts++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Server Error")
                    .body("error".toResponseBody())
                    .build()
            }
            .build()

        val evaluator = PreconditionEvaluator(context = null, okHttpClient = client)
        val healthCheck = Precondition.HealthCheck(
            isEnabled = true,
            targetUrl = "https://example.com/health",
            expectedStatus = 200,
            onFailureAction = "RETRY",
            maxRetries = 2,
            retryIntervalSeconds = 1
        )

        val result = evaluator.evaluate(healthCheck)

        assertTrue(result is PreconditionResult.Failed)
        val failed = result as PreconditionResult.Failed
        assertTrue(failed.reason.contains("Failed after 2 retries"))
        assertEquals(3, attempts) // 1 initial + 2 retries
    }
}
