package dev.zophop.filter

import java.util.regex.PatternSyntaxException

object SmsFilter {

    /**
     * Converts a wildcard pattern (e.g. "*HDFC*", "ICICI-*", "BANK?")
     * into an anchored regular expression string.
     */
    fun wildcardToRegex(wildcard: String): String {
        val sb = StringBuilder("^")
        for (char in wildcard) {
            when (char) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> {
                    sb.append("\\").append(char)
                }
                else -> sb.append(char)
            }
        }
        sb.append("$")
        return sb.toString()
    }

    /**
     * Checks if sender matches any entry in the user-editable allowlist.
     */
    fun matchesAllowlist(sender: String, allowlist: List<String>): Boolean {
        val trimmedSender = sender.trim()
        if (trimmedSender.isEmpty()) return false

        val activeList = allowlist.map { it.trim() }.filter { it.isNotEmpty() }
        if (activeList.isEmpty()) return false

        return activeList.any { patternStr ->
            try {
                if (patternStr == ".*" || patternStr == "*") {
                    true
                } else {
                    val regexPattern = wildcardToRegex(patternStr)
                    Regex(regexPattern, RegexOption.IGNORE_CASE).matches(trimmedSender)
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Validates whether a keyword regex pattern string is syntactically valid.
     */
    fun isValidRegex(regexStr: String): Boolean {
        if (regexStr.isBlank()) return false
        return try {
            Regex(regexStr)
            true
        } catch (e: PatternSyntaxException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the message body matches the keyword regex.
     */
    fun matchesKeyword(body: String, keywordRegexStr: String): Boolean {
        if (!isValidRegex(keywordRegexStr)) return false
        return try {
            Regex(keywordRegexStr).containsMatchIn(body)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Evaluates if an incoming SMS should be forwarded based on both sender allowlist
     * AND body keyword matching.
     */
    fun shouldForward(
        sender: String,
        body: String,
        allowlist: List<String>,
        keywordRegexStr: String
    ): Boolean {
        return matchesAllowlist(sender, allowlist) && matchesKeyword(body, keywordRegexStr)
    }
}
