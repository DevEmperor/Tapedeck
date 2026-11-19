package net.devemperor.tapedeck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm coral on near-black: a nod to the red record button of an old tape deck.
private val Coral = Color(0xFFFF6B57)
private val CoralDeep = Color(0xFFE2483A)

private val Dark = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF2A0A05),
    primaryContainer = Color(0xFF4A1C15),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFF4B860),
    onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFF3B2C17),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFF7FD1C7),
    onTertiary = Color(0xFF00201C),
    background = Color(0xFF0E0D10),
    onBackground = Color(0xFFF1ECF2),
    surface = Color(0xFF0E0D10),
    onSurface = Color(0xFFF1ECF2),
    surfaceVariant = Color(0xFF26222A),
    onSurfaceVariant = Color(0xFFADA4B2),
    surfaceContainerLowest = Color(0xFF0A090C),
    surfaceContainerLow = Color(0xFF141217),
    surfaceContainer = Color(0xFF18161B),
    surfaceContainerHigh = Color(0xFF211E25),
    surfaceContainerHighest = Color(0xFF2B2730),
    outline = Color(0xFF4A4450),
    outlineVariant = Color(0xFF302B35),
    error = Color(0xFFFF6B7F),
    onError = Color(0xFF3A0010),
    errorContainer = Color(0xFF4D1420),
    onErrorContainer = Color(0xFFFFD9DE),
    inverseSurface = Color(0xFFEDE7EE),
    inverseOnSurface = Color(0xFF1C1A1F),
    inversePrimary = CoralDeep,
)

private val Light = lightColorScheme(
    primary = CoralDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF410200),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2A1A00),
    tertiary = Color(0xFF006A62),
    background = Color(0xFFFBF8F6),
    onBackground = Color(0xFF1C1A1E),
    surface = Color(0xFFFBF8F6),
    onSurface = Color(0xFF1C1A1E),
    surfaceVariant = Color(0xFFEFE8EA),
    onSurfaceVariant = Color(0xFF5F5862),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2F1),
    surfaceContainer = Color(0xFFF2ECEB),
    surfaceContainerHigh = Color(0xFFECE6E5),
    surfaceContainerHighest = Color(0xFFE6E0DF),
    outline = Color(0xFFCFC6C9),
    outlineVariant = Color(0xFFE3DBDD),
    error = Color(0xFFC62B44),
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF40000D),
    inversePrimary = Coral,
)

private val base = Typography()
private val TapedeckType = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val TapedeckShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun TapedeckTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = TapedeckType,
        shapes = TapedeckShapes,
        content = content,
    )
}
