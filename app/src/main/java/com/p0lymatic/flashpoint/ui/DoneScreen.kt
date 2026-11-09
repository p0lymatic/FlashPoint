package com.p0lymatic.flashpoint.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

data class DoneMetrics(
    val driveNameAndSize: String,
    val targetOrImageName: String,
    val schemeAndArchitecture: String,
    val averageSpeed: String,
    val elapsedTime: String
)

private class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val width: Float,
    val height: Float,
    val isCircle: Boolean,
    val color: Color,
    var rotation: Float,
    val rotationSpeed: Float,
    val wobbleSpeed: Float,
    var wobblePhase: Float,
    var alpha: Float = 1f
)

@Composable
fun DoneScreen(
    metrics: DoneMetrics,
    onFinish: () -> Unit
) {
    BackHandler(onBack = onFinish)

    val infiniteTransition = rememberInfiniteTransition(label = "doneGlowPulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    // Animated / glowing Checkmark icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                            modifier = Modifier.size(88.dp * glowScale)
                        ) {}

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Metrics Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            MetricRow(label = "Target Drive", value = metrics.driveNameAndSize)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            MetricRow(label = "Target / Image", value = metrics.targetOrImageName)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            MetricRow(label = "Scheme & Arch", value = metrics.schemeAndArchitecture)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            MetricRow(label = "Average Speed", value = metrics.averageSpeed)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                            MetricRow(label = "Elapsed Time", value = metrics.elapsedTime)
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, top = 24.dp)
                ) {
                    Button(
                        onClick = onFinish,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "Finish",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Lightweight Confetti Overlay
        ConfettiOverlay(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val confettiColors = listOf(
        Color(0xFFFFB703), // Golden amber
        Color(0xFF06D6A0), // Emerald teal
        Color(0xFFEF476F), // Vibrant coral
        Color(0xFF118AB2), // Ocean blue
        Color(0xFF8338EC), // Deep violet
        Color(0xFF3A86FF), // Bright azure
        Color(0xFFFF006E)  // Hot pink
    )

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(100f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(100f)

        val particles = remember(widthPx, heightPx) {
            val random = Random(42)
            List(75) { index ->
                val originType = index % 3
                val (startX, startY, initVx, initVy) = when (originType) {
                    0 -> {
                        // Top center burst
                        val x = random.nextFloat() * (widthPx * 0.6f) + (widthPx * 0.2f)
                        val y = -20f + random.nextFloat() * 40f
                        val vx = (random.nextFloat() - 0.5f) * 260f
                        val vy = random.nextFloat() * 180f + 60f
                        listOf(x, y, vx, vy)
                    }
                    1 -> {
                        // Left side burst
                        val x = random.nextFloat() * 60f - 20f
                        val y = random.nextFloat() * (heightPx * 0.35f)
                        val vx = random.nextFloat() * 240f + 100f
                        val vy = (random.nextFloat() - 0.4f) * 160f
                        listOf(x, y, vx, vy)
                    }
                    else -> {
                        // Right side burst
                        val x = widthPx - (random.nextFloat() * 60f - 20f)
                        val y = random.nextFloat() * (heightPx * 0.35f)
                        val vx = -(random.nextFloat() * 240f + 100f)
                        val vy = (random.nextFloat() - 0.4f) * 160f
                        listOf(x, y, vx, vy)
                    }
                }

                val isCircle = index % 4 == 0
                val pWidth = if (isCircle) random.nextFloat() * 6f + 6f else random.nextFloat() * 10f + 8f
                val pHeight = if (isCircle) pWidth else random.nextFloat() * 6f + 6f
                val color = confettiColors[index % confettiColors.size]

                ConfettiParticle(
                    x = startX,
                    y = startY,
                    vx = initVx,
                    vy = initVy,
                    width = pWidth,
                    height = pHeight,
                    isCircle = isCircle,
                    color = color,
                    rotation = random.nextFloat() * 360f,
                    rotationSpeed = (random.nextFloat() - 0.5f) * 360f,
                    wobbleSpeed = random.nextFloat() * 4f + 3f,
                    wobblePhase = random.nextFloat() * 6.28f
                )
            }
        }

        var frameTick by remember { mutableLongStateOf(0L) }

        LaunchedEffect(particles) {
            val startNanos = withFrameNanos { it }
            var prevNanos = startNanos
            val gravity = 360f // pixels / s^2

            while (true) {
                withFrameNanos { nowNanos ->
                    val dt = ((nowNanos - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    prevNanos = nowNanos
                    val totalSec = (nowNanos - startNanos) / 1_000_000_000f

                    for (p in particles) {
                        p.vy += gravity * dt
                        p.vx *= (1f - 0.35f * dt)
                        p.wobblePhase += p.wobbleSpeed * dt
                        val wobbleOffset = sin(p.wobblePhase) * 30f * dt

                        p.x += (p.vx * dt) + wobbleOffset
                        p.y += p.vy * dt
                        p.rotation += p.rotationSpeed * dt

                        // Fade out towards bottom or end of 3.5s duration
                        val timeFade = if (totalSec > 2.4f) {
                            ((3.5f - totalSec) / 1.1f).coerceIn(0f, 1f)
                        } else 1f

                        val yFade = if (p.y > heightPx * 0.8f) {
                            ((heightPx - p.y) / (heightPx * 0.2f)).coerceIn(0f, 1f)
                        } else 1f

                        p.alpha = (timeFade * yFade).coerceIn(0f, 1f)
                    }

                    frameTick = nowNanos

                    if (totalSec >= 3.6f) {
                        for (p in particles) {
                            p.alpha = 0f
                        }
                        return@withFrameNanos
                    }
                }
                val currentSec = (prevNanos - startNanos) / 1_000_000_000f
                if (currentSec >= 3.6f) break
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read frameTick to trigger redraw
            if (frameTick >= 0L) {
                for (p in particles) {
                    if (p.alpha <= 0.01f) continue
                    val drawColor = p.color.copy(alpha = p.alpha)

                    if (p.isCircle) {
                        drawCircle(
                            color = drawColor,
                            radius = p.width / 2f,
                            center = Offset(p.x, p.y)
                        )
                    } else {
                        rotate(degrees = p.rotation, pivot = Offset(p.x, p.y)) {
                            drawRoundRect(
                                color = drawColor,
                                topLeft = Offset(p.x - p.width / 2f, p.y - p.height / 2f),
                                size = Size(p.width, p.height),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }
                }
            }
        }
    }
}
