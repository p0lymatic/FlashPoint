package com.p0lymatic.flashpoint.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive 9-sided nonagram "cookie" shape with rounded petals.
 * Perfect for avatars in Hall of Fame.
 */
class NonagonCookieShape(
    private val points: Int = 9,
    private val innerRadiusRatio: Float = 0.82f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val outerRadius = minOf(centerX, centerY)
        val innerRadius = outerRadius * innerRadiusRatio

        val totalSteps = points * 2
        val angleStep = (2.0 * Math.PI) / totalSteps

        val vertices = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until totalSteps) {
            val angle = i * angleStep - Math.PI / 2.0
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val x = (centerX + r * cos(angle)).toFloat()
            val y = (centerY + r * sin(angle)).toFloat()
            vertices.add(x to y)
        }

        // Draw smooth rounded polygon path
        path.moveTo((vertices[0].first + vertices.last().first) / 2f, (vertices[0].second + vertices.last().second) / 2f)
        for (i in vertices.indices) {
            val curr = vertices[i]
            val next = vertices[(i + 1) % vertices.size]
            val midX = (curr.first + next.first) / 2f
            val midY = (curr.second + next.second) / 2f
            path.quadraticBezierTo(curr.first, curr.second, midX, midY)
        }
        path.close()

        return Outline.Generic(path)
    }
}
