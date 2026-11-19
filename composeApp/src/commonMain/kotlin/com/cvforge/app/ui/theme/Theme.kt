package com.cvforge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cvforge.app.data.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F7B5F), onPrimary = Color.White,
    primaryContainer = Color(0xFFBDF0DC), onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4A635A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8DC), onSecondaryContainer = Color(0xFF062019),
    tertiary = Color(0xFF3F6375), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC3E8FC), onTertiaryContainer = Color(0xFF001F2B),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAF7), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FAF7), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF), onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9F0EC), surfaceContainerHigh = Color(0xFFE3EAE6),
    surfaceContainerHighest = Color(0xFFDDE4E0),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBFC9C4),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5FDBB6), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513F), onPrimaryContainer = Color(0xFFBDF0DC),
    secondary = Color(0xFFB0CCC0), onSecondary = Color(0xFF1C352D),
    secondaryContainer = Color(0xFF334B42), onSecondaryContainer = Color(0xFFCCE8DC),
    tertiary = Color(0xFFA7CCE0), onTertiary = Color(0xFF0B3446),
    tertiaryContainer = Color(0xFF254B5D), onTertiaryContainer = Color(0xFFC3E8FC),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF89938F), outlineVariant = Color(0xFF3F4945),
)

/** Cores semânticas que o Material 3 não traz (nota ATS boa / moderada). */
@Immutable
class ExtraColors(
    val success: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val warningContainer: Color, val onWarningContainer: Color,
)

private val LightExtra = ExtraColors(
    Color(0xFF1B7F4B), Color(0xFFD3F3DF), Color(0xFF0B3A22),
    Color(0xFFB26A00), Color(0xFFFFE8C2), Color(0xFF4A2C00),
)
private val DarkExtra = ExtraColors(
    Color(0xFF6BDC9A), Color(0xFF14432B), Color(0xFFD3F3DF),
    Color(0xFFFFB959), Color(0xFF5C3A00), Color(0xFFFFE8C2),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object CvTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CvForgeTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
