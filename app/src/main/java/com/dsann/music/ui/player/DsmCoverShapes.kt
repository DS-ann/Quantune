package com.dsann.music.ui.player

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Lightweight vector outlines for the album cover.
 *
 * Shapes are shared singletons so Compose doesn't allocate a new path definition on every recomposition.
 * The concave flower/cookie outlines are used for clipping only; callers should not cast expensive shadows from them.
 */
internal object DsmCoverShapes {
    fun of(name: String, rounded: Dp = 24.dp): Shape = when (name) {
        "Square" -> Square
        "Circle" -> CircleShape
        "Squircle" -> Squircle
        "Flower" -> Flower
        "Cookie" -> Cookie
        "Arch" -> Arch
        "Slanted" -> Slanted
        else -> RoundedCornerShape(rounded)
    }

    fun forPlayerLayout(playerLayout: String, artworkShapeName: String): Shape {
        // Vinyl/Cassette artwork treatments never override the independently selected
        // artwork clipping shape. Legacy Cover/Minimal presets are no longer supported.
        val roundedCorner = 24.dp
        return of(artworkShapeName, roundedCorner)
    }

    private val Square = GenericShape { size, _ ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }

    // Superellipse: softly curved corners while keeping long, almost-flat sides.
    private val Squircle = GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = 64
        for (i in 0 until steps) {
            val angle = 2.0 * PI * i / steps
            val x = cos(angle)
            val y = sin(angle)
            val px = cx + cx * sign(x).toFloat() * abs(x).pow(0.5).toFloat()
            val py = cy + cy * sign(y).toFloat() * abs(y).pow(0.5).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

    private fun scalloped(lobes: Int, depth: Float) = GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = lobes * 16
        for (i in 0 until steps) {
            val angle = 2.0 * PI * i / steps
            val radius = 1f - depth + depth * cos(lobes * angle).toFloat()
            val x = cx + cx * radius * cos(angle).toFloat()
            val y = cy + cy * radius * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private val Flower = scalloped(lobes = 8, depth = 0.11f)
    private val Cookie = scalloped(lobes = 12, depth = 0.045f)

    private val Arch = GenericShape { size, _ ->
        val width = size.width
        val height = size.height
        val radius = width / 2f
        moveTo(0f, height)
        lineTo(0f, radius)
        cubicTo(0f, radius * 0.45f, radius * 0.45f, 0f, radius, 0f)
        cubicTo(radius * 1.55f, 0f, width, radius * 0.45f, width, radius)
        lineTo(width, height)
        close()
    }

    private val Slanted = GenericShape { size, _ ->
        val width = size.width
        val height = size.height
        val lean = width * 0.10f
        moveTo(lean, 0f)
        lineTo(width, 0f)
        lineTo(width - lean, height)
        lineTo(0f, height)
        close()
    }
}
