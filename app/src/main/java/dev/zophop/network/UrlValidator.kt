package dev.zophop.network

import java.net.URI

object UrlValidator {

    /**
     * Validates whether a URL meets security requirements.
     * Enforces HTTPS by default. HTTP is rejected except for localhost,
     * or private/Tailscale IP addresses if allowInsecurePrivateHttp is true.
     */
    fun isValidUrl(url: String, allowInsecurePrivateHttp: Boolean = false): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false

        val uri = try {
            URI.create(trimmed)
        } catch (e: Exception) {
            return false
        }

        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val cleanHost = host.removePrefix("[").removeSuffix("]")

        if (scheme == "https") {
            return true
        }

        if (scheme == "http") {
            // Localhost is always allowed for testing/development
            if (isLocalhost(cleanHost)) {
                return true
            }

            // Private IP or Tailscale domain allowed only if opt-in enabled
            if (allowInsecurePrivateHttp && (isPrivateIp(cleanHost) || isLocalDomain(cleanHost))) {
                return true
            }
        }

        return false
    }

    private fun isLocalhost(host: String): Boolean {
        return host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "::1"
    }

    private fun isLocalDomain(host: String): Boolean {
        return host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".internal")
    }

    private fun isPrivateIp(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val ipParts = parts.mapNotNull { it.toIntOrNull() }
        if (ipParts.size != 4) return false

        val (p1, p2, _, _) = ipParts

        // 10.0.0.0/8
        if (p1 == 10) return true

        // 172.16.0.0/12
        if (p1 == 172 && p2 in 16..31) return true

        // 192.168.0.0/16
        if (p1 == 192 && p2 == 168) return true

        // 100.64.0.0/10 (CGNAT / Tailscale)
        if (p1 == 100 && p2 in 64..127) return true

        return false
    }
}
