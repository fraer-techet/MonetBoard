package dev.monetboard.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import dev.monetboard.data.Prefs

class Accent(val name: String, val hue: Float)

val Accents = listOf(
    Accent("Мята", 155f), Accent("Бирюза", 185f), Accent("Небо", 210f), Accent("Лаванда", 265f),
    Accent("Роза", 335f), Accent("Коралл", 12f), Accent("Янтарь", 42f),
)

fun hsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

fun accentSwatch(h: Float) = hsl(h, .55f, .62f)

/** Hand-rolled tonal scheme from a hue (used when Monet is off or Android < 12). */
fun seedScheme(h: Float, dark: Boolean): ColorScheme {
    val t = h + 55f
    return if (dark) darkColorScheme(
        primary = hsl(h, .55f, .72f), onPrimary = hsl(h, .6f, .12f),
        primaryContainer = hsl(h, .45f, .26f), onPrimaryContainer = hsl(h, .7f, .88f),
        secondary = hsl(h, .25f, .70f), onSecondary = hsl(h, .3f, .13f),
        secondaryContainer = hsl(h, .22f, .27f), onSecondaryContainer = hsl(h, .4f, .88f),
        tertiary = hsl(t, .35f, .72f), onTertiary = hsl(t, .4f, .14f),
        tertiaryContainer = hsl(t, .28f, .28f), onTertiaryContainer = hsl(t, .5f, .88f),
        background = hsl(h, .25f, .07f), onBackground = hsl(h, .15f, .90f),
        surface = hsl(h, .25f, .07f), onSurface = hsl(h, .15f, .90f),
        surfaceVariant = hsl(h, .15f, .22f), onSurfaceVariant = hsl(h, .12f, .78f),
        outline = hsl(h, .10f, .55f), outlineVariant = hsl(h, .12f, .28f),
        surfaceContainerLowest = hsl(h, .25f, .05f), surfaceContainerLow = hsl(h, .25f, .09f),
        surfaceContainer = hsl(h, .24f, .11f), surfaceContainerHigh = hsl(h, .22f, .15f),
        surfaceContainerHighest = hsl(h, .20f, .19f),
    ) else lightColorScheme(
        primary = hsl(h, .55f, .32f), onPrimary = Color.White,
        primaryContainer = hsl(h, .60f, .86f), onPrimaryContainer = hsl(h, .70f, .11f),
        secondary = hsl(h, .25f, .38f), onSecondary = Color.White,
        secondaryContainer = hsl(h, .30f, .86f), onSecondaryContainer = hsl(h, .40f, .12f),
        tertiary = hsl(t, .30f, .38f), onTertiary = Color.White,
        tertiaryContainer = hsl(t, .40f, .86f), onTertiaryContainer = hsl(t, .50f, .12f),
        background = hsl(h, .30f, .97f), onBackground = hsl(h, .15f, .10f),
        surface = hsl(h, .30f, .97f), onSurface = hsl(h, .15f, .10f),
        surfaceVariant = hsl(h, .20f, .88f), onSurfaceVariant = hsl(h, .15f, .30f),
        outline = hsl(h, .10f, .45f), outlineVariant = hsl(h, .15f, .78f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = hsl(h, .30f, .95f),
        surfaceContainer = hsl(h, .28f, .92f), surfaceContainerHigh = hsl(h, .26f, .89f),
        surfaceContainerHighest = hsl(h, .24f, .86f),
    )
}

@Composable
fun MonetTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (prefs.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val dynamic = prefs.colorSource == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = if (dynamic) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        seedScheme(Accents[prefs.accent.coerceIn(0, Accents.lastIndex)].hue, dark)
    }
    val scheme = if (dark && prefs.amoled) base.copy(
        background = Color.Black, surface = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color(0xFF141414), surfaceContainerHighest = Color(0xFF1C1C1C),
    ) else base
    MaterialTheme(colorScheme = scheme, content = content)
}
