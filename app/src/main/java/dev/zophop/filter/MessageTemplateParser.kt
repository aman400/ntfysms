package dev.zophop.filter

import java.util.regex.Pattern

object MessageTemplateParser {

    /**
     * Auto-extracts the first 4-8 digit numeric code from the text.
     */
    fun extractOtpCode(body: String): String {
        val pattern = Pattern.compile("\\b\\d{4,8}\\b")
        val matcher = pattern.matcher(body)
        return if (matcher.find()) matcher.group() else ""
    }

    /**
     * Formats an outgoing message payload based on sender/body regex capture groups and a template string.
     */
    fun formatPayload(
        sender: String,
        body: String,
        senderRegex: String = ".*",
        bodyParseRegex: String = "\\b\\d{4,8}\\b",
        template: String = "\${body}"
    ): String {
        if (template.isBlank()) {
            return body
        }

        val extractedCode = extractOtpCode(body)
        var result = template
            .replace("\${sender}", sender)
            .replace("\${body}", body)
            .replace("\${otp}", extractedCode)
            .replace("\${code}", extractedCode)

        // Parse Sender Regex Groups
        if (senderRegex.isNotBlank() && senderRegex != ".*") {
            try {
                val pattern = Pattern.compile(senderRegex)
                val matcher = pattern.matcher(sender)
                if (matcher.find()) {
                    for (i in 1..matcher.groupCount()) {
                        val groupValue = matcher.group(i) ?: ""
                        result = result.replace("\${senderGroup$i}", groupValue)
                            .replace("\${sGroup$i}", groupValue)
                    }
                }
            } catch (_: Exception) {}
        }

        // Parse Body Regex Groups
        if (bodyParseRegex.isNotBlank()) {
            try {
                val pattern = Pattern.compile(bodyParseRegex)
                val matcher = pattern.matcher(body)
                if (matcher.find()) {
                    for (i in 0..matcher.groupCount()) {
                        val groupValue = matcher.group(i) ?: ""
                        if (i == 0) {
                            result = result.replace("\${match}", groupValue)
                        } else {
                            result = result.replace("\${group$i}", groupValue)
                                .replace("\${g$i}", groupValue)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return result
    }
}
