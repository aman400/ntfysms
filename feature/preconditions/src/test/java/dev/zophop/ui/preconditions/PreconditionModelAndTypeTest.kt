package dev.zophop.ui.preconditions

import dev.zophop.data.PreconditionEntity
import dev.zophop.data.toDomain
import dev.zophop.domain.model.Precondition
import dev.zophop.domain.model.PreconditionType
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
