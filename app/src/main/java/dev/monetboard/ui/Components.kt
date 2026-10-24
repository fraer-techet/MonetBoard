package dev.monetboard.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Scalloped "cookie" blob used by the round action buttons (Material 3 Expressive look). */
private class ScallopShape(private val lobes: Int, private val amp: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val t = i * 2.0 * PI / steps
            val rr = r * (1f - amp + amp * cos(lobes * t).toFloat())
            val x = cx + rr * cos(t).toFloat()
            val y = cy + rr * sin(t).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val CookieShape: Shape = ScallopShape(9, 0.06f)

private fun vec(name: String, d: String, evenOdd: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        fill = SolidColor(Color.Black),
    ).build()

object KbIcons {
    val Backspace = vec(
        "backspace",
        "M22,3H7c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11c0.36,0.53 0.9,0.89 1.59,0.89h15c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z " +
            "M19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 12.59,12 9,8.41 10.41,7 14,10.59 17.59,7 19,8.41 15.41,12z",
        evenOdd = true,
    )
    val Shift = vec("shift", "M12,4 L3.5,13 H8.5 V20 H15.5 V13 H20.5 Z")
    val Enter = vec("enter", "M19,7v4H5.83l3.58,-3.59L8,6l-6,6 6,6 1.41,-1.41L5.83,13H21V7z")
    val Spark = vec("spark", "M12,2 L14.6,9.4 L22,12 L14.6,14.6 L12,22 L9.4,14.6 L2,12 L9.4,9.4 Z")
    val Mic = vec(
        "mic",
        "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z " +
            "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
    )
    val Clipboard = vec(
        "clipboard",
        "M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z " +
            "M12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1z M19,20H5V4h2v3h10V4h2v16z",
        evenOdd = true,
    )
}

// ------------------------------------------------------------------ buttons

@Composable
fun CookieButton(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    pulse: Boolean = false,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.45f, stiffness = 520f), label = "cookieScale")
    val bg by animateColorAsState(container, tween(220), label = "cookieBg")
    val pulseScale = if (pulse) {
        rememberInfiniteTransition(label = "cookiePulse").animateFloat(
            1f, 1.16f,
            infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "p",
        ).value
    } else 1f
    val click by rememberUpdatedState(onClick)
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale * pulseScale; scaleY = scale * pulseScale }
            .clip(CookieShape)
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { click() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(icon, animationSpec = tween(180), label = "cookieIcon") {
            Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun PillButton(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.5f, stiffness = 520f), label = "pillScale")
    val bg by animateColorAsState(container, tween(200), label = "pillBg")
    val fg by animateColorAsState(content, tween(200), label = "pillFg")
    val click by rememberUpdatedState(onClick)
    Box(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { click() })
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tappable card: squishes + morphs to a rounder shape while pressed, fires on tap. */
@Composable
fun TapTile(
    modifier: Modifier,
    color: Color,
    radius: Dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val r by animateDpAsState(if (pressed) radius + 10.dp else radius, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tileR")
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tileS")
    val bg by animateColorAsState(color, tween(220), label = "tileBg")
    val click by rememberUpdatedState(onClick)
    Box(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(r))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { click() })
            },
        content = content,
    )
}

// ------------------------------------------------------------------ entrance animations

/** Springs content in (scale + fade + rise) after [index] * 45 ms. */
@Composable
fun StaggerIn(index: Int, enabled: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay(index * 45L)
            a.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        }
    }
    Box(
        modifier.graphicsLayer {
            val v = a.value
            alpha = v.coerceIn(0f, 1f)
            val sc = 0.7f + 0.3f * v
            scaleX = sc; scaleY = sc
            translationY = (1f - v) * 24f
        },
    ) { content() }
}

class RevealCounter { var n = 0 }

val LocalReveal = compositionLocalOf<RevealCounter?> { null }
val LocalAnimations = compositionLocalOf { true }

/** Settings rows slide/fade in one after another (order = composition order within the page). */
@Composable
fun Modifier.reveal(): Modifier {
    val counter = LocalReveal.current
    val on = LocalAnimations.current && counter != null
    val idx = remember { counter?.let { it.n++ } ?: 0 }
    val a = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (on) {
            delay(minOf(idx, 14) * 42L)
            a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f))
        }
    }
    return this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 36f
    }
}
