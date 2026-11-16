package `in`.ntfysms.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Core Brand Colors
val PrimaryIndigo = Color(0xFF4F46E5)
val PrimaryIndigoDark = Color(0xFF3730A3)
val PrimaryIndigoLight = Color(0xFF818CF8)

val SecondaryCyan = Color(0xFF0EA5E9)
val SecondaryCyanLight = Color(0xFF38BDF8)

val TertiaryViolet = Color(0xFF7C3AED)
val TertiaryVioletLight = Color(0xFFC084FC)

// Status Colors (Light Theme)
val StatusSuccessBgLight = Color(0xFFDCFCE7)
val StatusSuccessFgLight = Color(0xFF15803D)

val StatusWarningBgLight = Color(0xFFFEF3C7)
val StatusWarningFgLight = Color(0xFFB45309)

val StatusExpiredBgLight = Color(0xFFFFEDD5)
val StatusExpiredFgLight = Color(0xFFC2410C)

val StatusErrorBgLight = Color(0xFFFEE2E2)
val StatusErrorFgLight = Color(0xFFB91C1C)

// Status Colors (Dark Theme)
val StatusSuccessBgDark = Color(0xFF064E3B)
val StatusSuccessFgDark = Color(0xFF34D399)

val StatusWarningBgDark = Color(0xFF78350F)
val StatusWarningFgDark = Color(0xFFFBBF24)

val StatusExpiredBgDark = Color(0xFF7C2D12)
val StatusExpiredFgDark = Color(0xFFFB923C)

val StatusErrorBgDark = Color(0xFF7F1D1D)
val StatusErrorFgDark = Color(0xFFF87171)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigoLight,
    onPrimary = Color(0xFF0B0F19),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = SecondaryCyanLight,
    onSecondary = Color(0xFF0B0F19),
    secondaryContainer = Color(0xFF075985),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = TertiaryVioletLight,
    onTertiary = Color(0xFF0B0F19),
    background = Color(0xFF0B0F19),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFD1D5DB),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = SecondaryCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = TertiaryViolet,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun NtfySmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

object AppStatusColors {
    @Composable
    fun successColors(): Pair<Color, Color> {
        val isDark = isSystemInDarkTheme()
        return if (isDark) StatusSuccessBgDark to StatusSuccessFgDark else StatusSuccessBgLight to StatusSuccessFgLight
    }

    @Composable
    fun warningColors(): Pair<Color, Color> {
        val isDark = isSystemInDarkTheme()
        return if (isDark) StatusWarningBgDark to StatusWarningFgDark else StatusWarningBgLight to StatusWarningFgLight
    }

    @Composable
    fun expiredColors(): Pair<Color, Color> {
        val isDark = isSystemInDarkTheme()
        return if (isDark) StatusExpiredBgDark to StatusExpiredFgDark else StatusExpiredBgLight to StatusExpiredFgLight
    }

    @Composable
    fun errorColors(): Pair<Color, Color> {
        val isDark = isSystemInDarkTheme()
        return if (isDark) StatusErrorBgDark to StatusErrorFgDark else StatusErrorBgLight to StatusErrorFgLight
    }
}
