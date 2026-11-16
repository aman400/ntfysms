package `in`.ntfysms.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object ServerType {
    const val NTFY = "NTFY"
    const val GOTIFY = "GOTIFY"
}

data class AppSettings(
    val serverType: String = ServerType.NTFY,
    val serverUrl: String = "",
    val topic: String = "",
    val token: String = "",
    val ntfyServerUrl: String = "",
    val ntfyTopic: String = "",
    val ntfyToken: String = "",
    val gotifyServerUrl: String = "",
    val gotifyToken: String = "",
    val allowlist: String = ".*",
    val keywordRegex: String = ".*",
    val senderRegex: String = ".*",
    val bodyParseRegex: String = "\\b\\d{4,8}\\b",
    val customMessageTemplate: String = "\${body}",
    val expiryMinutes: Int = 10,
    val maxRetries: Int = 5,
    val isEnabled: Boolean = false,
    val allowInsecureHttp: Boolean = false,
    val forwardNotifications: Boolean = false,
    val autoClearEnabled: Boolean = false,
    val autoClearMinutes: Int = 1440,
    val requireInternet: Boolean = true,
    val requireVpn: Boolean = false,
    val requireHealthcheck: Boolean = false
) {
    val allowlistItems: List<String>
        get() = allowlist.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }

    val activeServerUrl: String
        get() = if (serverType == ServerType.GOTIFY) {
            gotifyServerUrl.ifBlank { serverUrl }
        } else {
            ntfyServerUrl.ifBlank { serverUrl }
        }

    val activeTopic: String
        get() = if (serverType == ServerType.GOTIFY) "" else ntfyTopic.ifBlank { topic }

    val activeToken: String
        get() = if (serverType == ServerType.GOTIFY) {
            gotifyToken.ifBlank { token }
        } else {
            ntfyToken.ifBlank { token }
        }
}

interface SettingsRepository {
    fun getSettings(): AppSettings
    fun saveSettings(settings: AppSettings)
    fun setEnabled(enabled: Boolean)
}

class EncryptedSettingsRepository(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences = createEncryptedSharedPreferences(context)

    private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to standard SharedPreferences if Keystore issue occurs on legacy device
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    override fun getSettings(): AppSettings {
        val serverType = prefs.getString(KEY_SERVER_TYPE, ServerType.NTFY) ?: ServerType.NTFY
        val legacyUrl = prefs.getString(KEY_SERVER_URL, "") ?: ""
        val legacyTopic = prefs.getString(KEY_TOPIC, "") ?: ""
        val legacyToken = prefs.getString(KEY_TOKEN, "") ?: ""

        val ntfyUrl = prefs.getString(KEY_NTFY_SERVER_URL, "") ?: if (serverType == ServerType.NTFY) legacyUrl else ""
        val ntfyTop = prefs.getString(KEY_NTFY_TOPIC, "") ?: if (serverType == ServerType.NTFY) legacyTopic else ""
        val ntfyTok = prefs.getString(KEY_NTFY_TOKEN, "") ?: if (serverType == ServerType.NTFY) legacyToken else ""

        val gotifyUrl = prefs.getString(KEY_GOTIFY_SERVER_URL, "") ?: if (serverType == ServerType.GOTIFY) legacyUrl else ""
        val gotifyTok = prefs.getString(KEY_GOTIFY_TOKEN, "") ?: if (serverType == ServerType.GOTIFY) legacyToken else ""

        return AppSettings(
            serverType = serverType,
            serverUrl = legacyUrl,
            topic = legacyTopic,
            token = legacyToken,
            ntfyServerUrl = ntfyUrl,
            ntfyTopic = ntfyTop,
            ntfyToken = ntfyTok,
            gotifyServerUrl = gotifyUrl,
            gotifyToken = gotifyTok,
            allowlist = prefs.getString(KEY_ALLOWLIST, ".*") ?: ".*",
            keywordRegex = prefs.getString(KEY_KEYWORD_REGEX, ".*") ?: ".*",
            senderRegex = prefs.getString(KEY_SENDER_REGEX, ".*") ?: ".*",
            bodyParseRegex = prefs.getString(KEY_BODY_PARSE_REGEX, "\\b\\d{4,8}\\b") ?: "\\b\\d{4,8}\\b",
            customMessageTemplate = prefs.getString(KEY_CUSTOM_MESSAGE_TEMPLATE, "\${body}") ?: "\${body}",
            expiryMinutes = prefs.getInt(KEY_EXPIRY_MINUTES, 10),
            maxRetries = prefs.getInt(KEY_MAX_RETRIES, 5),
            isEnabled = prefs.getBoolean(KEY_IS_ENABLED, false),
            allowInsecureHttp = prefs.getBoolean(KEY_ALLOW_INSECURE_HTTP, false),
            forwardNotifications = prefs.getBoolean(KEY_FORWARD_NOTIFICATIONS, false),
            autoClearEnabled = prefs.getBoolean(KEY_AUTO_CLEAR_ENABLED, false),
            autoClearMinutes = prefs.getInt(KEY_AUTO_CLEAR_MINUTES, 1440),
            requireInternet = prefs.getBoolean(KEY_REQUIRE_INTERNET, true),
            requireVpn = prefs.getBoolean(KEY_REQUIRE_VPN, false),
            requireHealthcheck = prefs.getBoolean(KEY_REQUIRE_HEALTHCHECK, false)
        )
    }

    override fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_SERVER_TYPE, settings.serverType)
            .putString(KEY_SERVER_URL, settings.activeServerUrl)
            .putString(KEY_TOPIC, settings.activeTopic)
            .putString(KEY_TOKEN, settings.activeToken)
            .putString(KEY_NTFY_SERVER_URL, settings.ntfyServerUrl)
            .putString(KEY_NTFY_TOPIC, settings.ntfyTopic)
            .putString(KEY_NTFY_TOKEN, settings.ntfyToken)
            .putString(KEY_GOTIFY_SERVER_URL, settings.gotifyServerUrl)
            .putString(KEY_GOTIFY_TOKEN, settings.gotifyToken)
            .putString(KEY_ALLOWLIST, settings.allowlist)
            .putString(KEY_KEYWORD_REGEX, settings.keywordRegex)
            .putString(KEY_SENDER_REGEX, settings.senderRegex)
            .putString(KEY_BODY_PARSE_REGEX, settings.bodyParseRegex)
            .putString(KEY_CUSTOM_MESSAGE_TEMPLATE, settings.customMessageTemplate)
            .putInt(KEY_EXPIRY_MINUTES, settings.expiryMinutes)
            .putInt(KEY_MAX_RETRIES, settings.maxRetries)
            .putBoolean(KEY_IS_ENABLED, settings.isEnabled)
            .putBoolean(KEY_ALLOW_INSECURE_HTTP, settings.allowInsecureHttp)
            .putBoolean(KEY_FORWARD_NOTIFICATIONS, settings.forwardNotifications)
            .putBoolean(KEY_AUTO_CLEAR_ENABLED, settings.autoClearEnabled)
            .putInt(KEY_AUTO_CLEAR_MINUTES, settings.autoClearMinutes)
            .putBoolean(KEY_REQUIRE_INTERNET, settings.requireInternet)
            .putBoolean(KEY_REQUIRE_VPN, settings.requireVpn)
            .putBoolean(KEY_REQUIRE_HEALTHCHECK, settings.requireHealthcheck)
            .apply()
    }

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "ntfy_sms_encrypted_prefs"
        private const val KEY_SERVER_TYPE = "server_type"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOPIC = "topic"
        private const val KEY_TOKEN = "token"
        private const val KEY_NTFY_SERVER_URL = "ntfy_server_url"
        private const val KEY_NTFY_TOPIC = "ntfy_topic"
        private const val KEY_NTFY_TOKEN = "ntfy_token"
        private const val KEY_GOTIFY_SERVER_URL = "gotify_server_url"
        private const val KEY_GOTIFY_TOKEN = "gotify_token"
        private const val KEY_ALLOWLIST = "allowlist"
        private const val KEY_KEYWORD_REGEX = "keyword_regex"
        private const val KEY_SENDER_REGEX = "sender_regex"
        private const val KEY_BODY_PARSE_REGEX = "body_parse_regex"
        private const val KEY_CUSTOM_MESSAGE_TEMPLATE = "custom_message_template"
        private const val KEY_EXPIRY_MINUTES = "expiry_minutes"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_IS_ENABLED = "is_enabled"
        private const val KEY_ALLOW_INSECURE_HTTP = "allow_insecure_http"
        private const val KEY_FORWARD_NOTIFICATIONS = "forward_notifications"
        private const val KEY_AUTO_CLEAR_ENABLED = "auto_clear_enabled"
        private const val KEY_AUTO_CLEAR_MINUTES = "auto_clear_minutes"
        private const val KEY_REQUIRE_INTERNET = "require_internet"
        private const val KEY_REQUIRE_VPN = "require_vpn"
        private const val KEY_REQUIRE_HEALTHCHECK = "require_healthcheck"
    }
}
