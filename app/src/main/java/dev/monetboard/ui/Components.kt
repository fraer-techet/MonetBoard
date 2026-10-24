package dev.monetboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
}

@Composable
fun CookieButton(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(size).clip(CookieShape).background(container).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
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
    Box(
        modifier.height(height).clip(RoundedCornerShape(50)).background(container)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
