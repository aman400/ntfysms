package `in`.ntfysms.domain.model

import java.util.UUID

enum class CredentialType {
    BEARER_TOKEN,
    BASIC_AUTH,
    HEADER_SET,
    NTFY_AUTH,
    GOTIFY_AUTH;

    fun displayName(): String {
        return when (this) {
            BEARER_TOKEN -> "Bearer Token (API Key)"
            BASIC_AUTH -> "Basic Auth (Username & Password)"
            HEADER_SET -> "Custom Headers"
            NTFY_AUTH -> "ntfy Server & Token"
            GOTIFY_AUTH -> "Gotify Server & App Token"
        }
    }

    fun isCompatibleWith(nodeType: NodeType): Boolean {
        return when (nodeType) {
            NodeType.NTFY_ACTION -> this == NTFY_AUTH || this == BEARER_TOKEN || this == HEADER_SET
            NodeType.GOTIFY_ACTION -> this == GOTIFY_AUTH
            NodeType.HTTP_ACTION -> this == BEARER_TOKEN || this == BASIC_AUTH || this == HEADER_SET
            else -> true
        }
    }
}

data class Credential(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: CredentialType,
    val dataMap: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
