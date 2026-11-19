package `in`.ntfysms.ui.preconditions

import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.data.toDomain
import `in`.ntfysms.domain.model.Precondition
import `in`.ntfysms.domain.model.PreconditionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreconditionModelAndTypeTest {

    @Test
    fun testInternetPreconditionEntityToDomain() {
        val entity = PreconditionEntity(
            id = "test_1",
            name = "Test Internet",
            type = PreconditionType.INTERNET_CHECK.name,
            isEnabled = true,
            onFailureAction = "RETRY",
            maxRetries = 3,
            retryIntervalSeconds = 15
        )
        val domain = entity.toDomain()
        assertTrue(domain is Precondition.InternetCheck)
        assertEquals(3, domain.maxRetries)
        assertTrue(domain.isEnabled)
    }

    @Test
    fun testVpnPreconditionEntityToDomain() {
        val entity = PreconditionEntity(
            id = "test_2",
            name = "Test VPN",
            type = PreconditionType.VPN_CHECK.name,
            isEnabled = false
        )
        val domain = entity.toDomain()
        assertTrue(domain is Precondition.VpnCheck)
        assertEquals(false, domain.isEnabled)
    }
}
