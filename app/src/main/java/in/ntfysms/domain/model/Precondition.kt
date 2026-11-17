package `in`.ntfysms.domain.model

enum class PreconditionType {
    INTERNET_CHECK,
    VPN_CHECK,
    HEALTH_CHECK,
    SIM_CHECK,
    GPS_CHECK,
    CHAINED_CHECK
}

sealed class Precondition(val type: PreconditionType) {
    data class InternetCheck(
        val isEnabled: Boolean = true
    ) : Precondition(PreconditionType.INTERNET_CHECK)

    data class VpnCheck(
        val isEnabled: Boolean = true
    ) : Precondition(PreconditionType.VPN_CHECK)

    data class HealthCheck(
        val isEnabled: Boolean = true,
        val targetUrl: String = "",
        val method: String = "GET",
        val headers: String = "",
        val expectedStatus: Int = 200
    ) : Precondition(PreconditionType.HEALTH_CHECK)

    data class SimCheck(
        val isEnabled: Boolean = true,
        val requiredCarrier: String = "",
        val requiredSlot: Int = -1 // -1 for any active SIM
    ) : Precondition(PreconditionType.SIM_CHECK)

    data class GpsCheck(
        val isEnabled: Boolean = true,
        val requireGpsEnabled: Boolean = true
    ) : Precondition(PreconditionType.GPS_CHECK)

    data class ChainCheck(
        val isEnabled: Boolean = true,
        val nodes: List<WorkflowNode> = emptyList(),
        val connections: List<WorkflowConnection> = emptyList()
    ) : Precondition(PreconditionType.CHAINED_CHECK)
}
