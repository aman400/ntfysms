package `in`.ntfysms.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test

class PreCheckManagerTest {

    @Test
    fun testServerHealthcheckSuccess() = runTest {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{\"status\":\"pass\"}".toResponseBody())
                    .build()
            }
            .build()

        val manager = PreCheckManager(context = null, okHttpClient = okHttpClient)
        val result = manager.checkServerHealth("https://ntfy.sh")
        assertTrue(result is PreCheckResult.Passed)
    }

    @Test
    fun testServerHealthcheckErrorReturnsFailed() = runTest {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("error".toResponseBody())
                    .build()
            }
            .build()

        val manager = PreCheckManager(context = null, okHttpClient = okHttpClient)
        val result = manager.checkServerHealth("https://ntfy.sh")
        assertTrue(result is PreCheckResult.Failed)
        val failed = result as PreCheckResult.Failed
        assertTrue(failed.reason.contains("Healthcheck HTTP status: 503"))
    }
}
