package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudioBackgroundStyle
import com.example.ui.theme.MyanmarScriptFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive 5-mode Particle Dot Grid / Digital Dot Matrix / Dot Wave / Reactive Mesh Grid
 * backdrop that sits smoothly beneath the Fire RecapScript UI and reacts to finger touch ripples
 * and active AI processing waves without blocking clicks or scrolling.
 */
@Composable
fun ReactiveStudioBackground(
    style: StudioBackgroundStyle,
    isProcessingActive: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var pointerPos by remember { mutableStateOf<Offset?>(null) }
    val infinite = rememberInfiniteTransition(label = "studio_particle_bg")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isProcessingActive) 4200 else 7600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bg_phase"
    )
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg_drift"
    )

    val crimson = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.colorScheme.secondary
    val electricBlue = Color(0xFF3B6EFF)
    val bgDeep = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgDeep)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            pointerPos = change.position
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val spacing = 22.dp.toPx()
            val cols = (w / spacing).toInt().coerceIn(8, 34)
            val rows = (h / spacing).toInt().coerceIn(12, 54)
            val offsetX = (w - (cols - 1) * spacing) * 0.5f
            val offsetY = (h - (rows - 1) * spacing) * 0.5f

            val focus = pointerPos ?: Offset(
                x = w * (0.5f + 0.16f * cos(phase)),
                y = h * (0.42f + 0.12f * sin(phase))
            )
            val maxDist = hypot(w * 0.65f, h * 0.55f).coerceAtLeast(1f)
            val activityBoost = if (isProcessingActive) 1.35f else 1.0f

            // Subtle ambient radial halo behind the active focus point
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        electricBlue.copy(alpha = 0.14f * activityBoost),
                        crimson.copy(alpha = 0.06f * activityBoost),
                        Color.Transparent
                    ),
                    center = focus,
                    radius = maxDist * 0.55f
                ),
                radius = maxDist * 0.55f,
                center = focus
            )

            // Reactive Mesh Grid lines (for REACTIVE_MESH_GRID & subtle matrix accent)
            if (style == StudioBackgroundStyle.REACTIVE_MESH_GRID) {
                val meshStep = 2
                for (r in 0 until rows step meshStep) {
                    val y = offsetY + r * spacing
                    for (c in 0 until cols - meshStep step meshStep) {
                        val x1 = offsetX + c * spacing
                        val x2 = offsetX + (c + meshStep) * spacing
                        val d = hypot((x1 + x2) * 0.5f - focus.x, y - focus.y)
                        val proximity = (1f - (d / (maxDist * 0.65f))).coerceIn(0f, 1f)
                        val lineAlpha = (0.06f + proximity * 0.24f) * activityBoost
                        drawLine(
                            color = electricBlue.copy(alpha = lineAlpha.coerceAtMost(0.4f)),
                            start = Offset(x1, y),
                            end = Offset(x2, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        if (r + meshStep < rows) {
                            val y2 = offsetY + (r + meshStep) * spacing
                            drawLine(
                                color = crimson.copy(alpha = (lineAlpha * 0.65f).coerceAtMost(0.3f)),
                                start = Offset(x1, y),
                                end = Offset(x1, y2),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            }

            for (r in 0 until rows) {
                val baseY = offsetY + r * spacing
                for (c in 0 until cols) {
                    val baseX = offsetX + c * spacing
                    val dist = hypot(baseX - focus.x, baseY - focus.y)
                    val normDist = (dist / maxDist).coerceIn(0f, 1f)

                    val (dotPos, radiusPx, dotColor, alpha) = when (style) {
                        StudioBackgroundStyle.PARTICLE_DOT_GRID -> {
                            val pulse = 0.5f + 0.5f * sin(phase - normDist * 7.5f)
                            val focusWeight = (1f - normDist * 1.15f).coerceIn(0f, 1f)
                            val rad = (1.1f + focusWeight * 3.4f + pulse * 0.9f).dp.toPx()
                            val col = if (focusWeight > 0.45f) electricBlue else if ((r + c) % 5 == 0) crimson else electricBlue
                            QuadData(Offset(baseX, baseY), rad, col, (0.07f + focusWeight * 0.46f + pulse * 0.08f).coerceIn(0.05f, 0.7f))
                        }

                        StudioBackgroundStyle.DIGITAL_DOT_MATRIX -> {
                            val scanCol = ((phase / (2.0 * PI)).toFloat() * cols) % cols
                            val colDist = kotlin.math.abs(c - scanCol).coerceAtMost(cols.toFloat())
                            val scanBoost = (1f - colDist / 4.5f).coerceIn(0f, 1f)
                            val focusWeight = (1f - normDist).coerceIn(0f, 1f)
                            val rad = (1.1f + scanBoost * 2.5f + focusWeight * 1.8f).dp.toPx()
                            val col = if (scanBoost > 0.5f) gold else electricBlue
                            QuadData(Offset(baseX, baseY), rad, col, (0.06f + scanBoost * 0.42f + focusWeight * 0.22f).coerceIn(0.05f, 0.65f))
                        }

                        StudioBackgroundStyle.ANIMATED_PARTICLE_GRID -> {
                            val jitterX = sin(phase + r * 0.55f + c * 0.2f) * 3.5.dp.toPx()
                            val jitterY = cos(phase + c * 0.55f + r * 0.2f) * 3.5.dp.toPx()
                            val focusWeight = (1f - normDist * 1.1f).coerceIn(0f, 1f)
                            val rad = (1.2f + focusWeight * 2.9f + drift * 0.8f).dp.toPx()
                            val col = when ((r * 3 + c) % 4) {
                                0 -> crimson
                                1 -> gold
                                else -> electricBlue
                            }
                            QuadData(Offset(baseX + jitterX, baseY + jitterY), rad, col, (0.08f + focusWeight * 0.44f).coerceIn(0.06f, 0.65f))
                        }

                        StudioBackgroundStyle.INTERACTIVE_DOT_WAVE -> {
                            val wave = sin(dist * 0.045f - phase * 1.4f)
                            val focusWeight = (1f - normDist * 1.05f).coerceIn(0f, 1f)
                            val push = wave * (2.6.dp.toPx() * (0.35f + focusWeight))
                            val rad = (1.1f + ((wave + 1f) * 0.5f) * 2.2f + focusWeight * 2.6f).dp.toPx()
                            val col = if (wave > 0.35f && focusWeight > 0.3f) gold else if (focusWeight > 0.25f) electricBlue else crimson
                            QuadData(Offset(baseX, baseY + push), rad, col, (0.07f + focusWeight * 0.45f + ((wave + 1f) * 0.08f)).coerceIn(0.05f, 0.72f))
                        }

                        StudioBackgroundStyle.REACTIVE_MESH_GRID -> {
                            val focusWeight = (1f - normDist * 1.1f).coerceIn(0f, 1f)
                            val pulse = 0.5f + 0.5f * cos(phase + normDist * 5f)
                            val rad = (1.2f + focusWeight * 3.1f + pulse * 0.7f).dp.toPx()
                            val col = if (focusWeight > 0.5f) crimson else electricBlue
                            QuadData(Offset(baseX, baseY), rad, col, (0.08f + focusWeight * 0.50f).coerceIn(0.06f, 0.72f))
                        }
                    }

                    drawCircle(
                        color = dotColor.copy(alpha = (alpha * activityBoost).coerceAtMost(0.75f)),
                        radius = radiusPx,
                        center = dotPos
                    )
                }
            }
        }
        content()
    }
}

private data class QuadData(
    val offset: Offset,
    val radius: Float,
    val color: Color,
    val alpha: Float
)

/**
 * Glowing 0% -> 100% Circular Progress Ring + Live 5-Agent Swarm Video Editing Timeline Card
 * inspired directly by multi-agent timeline workflows, displaying autonomous AI agents working
 * smoothly across Video, Script, Voice, Copyright Shield, and MP4 Render tracks.
 */
@Composable
fun SwarmVideoProgressCard(
    progressPercent: Int,
    stepHeadline: String,
    stageMessage: String,
    activeStepIndex: Int, // 1..3
    showSwarmAnimation: Boolean = true,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clampedTarget = progressPercent.coerceIn(0, 100)
    val animatedPercent by animateIntAsState(
        targetValue = clampedTarget,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "ring_percent"
    )
    val animatedSweep by animateFloatAsState(
        targetValue = (clampedTarget / 100f) * 360f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "ring_sweep"
    )

    val crimson = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.colorScheme.secondary
    val trackBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.4.dp,
            color = if (isCompleted) gold else crimson.copy(alpha = 0.65f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("swarm_progress_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 3-Step Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val steps = listOf(1 to "1. ဘာသာပြန်", 2 to "2. မြန်မာအသံ", 3 to "3. MP4 ထုတ်")
                steps.forEach { (num, label) ->
                    val isDone = isCompleted || activeStepIndex > num
                    val isCurrent = !isCompleted && activeStepIndex == num
                    Surface(
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                            isDone -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCurrent) gold else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isDone) "✓ $label" else label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MyanmarScriptFamily,
                                fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isCurrent) gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 0% -> 100% Circular Progress Ring + Stage Detail
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(92.dp)
                        .testTag("circular_progress_ring")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 9.dp.toPx()
                        val inset = strokeW / 2f
                        val arcSize = Size(size.width - strokeW, size.height - strokeW)
                        drawArc(
                            color = trackBg,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        if (animatedSweep > 0.5f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(crimson, gold, crimson)
                                ),
                                startAngle = -90f,
                                sweepAngle = animatedSweep,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                            val tipAngleRad = Math.toRadians((-90f + animatedSweep).toDouble())
                            val radius = (size.width - strokeW) / 2f
                            val tipX = size.width / 2f + (radius * cos(tipAngleRad)).toFloat()
                            val tipY = size.height / 2f + (radius * sin(tipAngleRad)).toFloat()
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(tipX, tipY)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$animatedPercent%",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp
                            ),
                            color = if (isCompleted) gold else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isCompleted) "DONE" else "AI SYNC",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = gold
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = gold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stepHeadline,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = MyanmarScriptFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = gold
                        )
                    }
                    Text(
                        text = stageMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MyanmarScriptFamily,
                            lineHeight = 19.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }

            if (showSwarmAnimation) {
                AiSwarmVideoTimelineCanvas(
                    progressFraction = (clampedTarget / 100f).coerceIn(0f, 1f),
                    isAnimating = !isCompleted
                )
            }
        }
    }
}

private data class SwarmAgentSpec(
    val name: String,
    val badgeColor: Color,
    val trackIndex: Int,
    val baseX: Float,
    val ampX: Float,
    val freqMul: Float,
    val phaseOffset: Float
)

/**
 * Multi-Agent AI Swarm Video Editing Timeline Stage:
 * Shows a dark preview monitor header, 4 colored timeline editing tracks, moving vertical playhead,
 * and 5 animated pointer-cursor AI Agents ("Agent 1" .. "Agent 5") actively collaborating.
 */
@Composable
fun AiSwarmVideoTimelineCanvas(
    progressFraction: Float,
    isAnimating: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "ai_swarm_motion")
    val animT by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swarm_t"
    )
    val t = if (isAnimating) animT else progressFraction * (2.0 * PI).toFloat()

    val agents = remember {
        listOf(
            SwarmAgentSpec("Agent 1 • Scan", Color(0xFF3B82F6), 0, 0.22f, 0.14f, 1f, 0.2f),
            SwarmAgentSpec("Agent 2 • Script", Color(0xFFF59E0B), 1, 0.34f, 0.16f, 1.3f, 1.4f),
            SwarmAgentSpec("Agent 3 • Voice", Color(0xFF14B8A6), 0, 0.56f, 0.12f, 1.1f, 2.7f),
            SwarmAgentSpec("Agent 4 • Sub", Color(0xFFEF4444), 2, 0.68f, 0.15f, 1.4f, 4.0f),
            SwarmAgentSpec("Agent 5 • Export", Color(0xFF8B5CF6), 1, 0.84f, 0.10f, 0.9f, 5.1f)
        )
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0C12))
            .padding(10.dp)
            .testTag("ai_swarm_video_canvas"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Mini Cinema Preview Box (as shown in reference video swarm mockup)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF161923), Color(0xFF1D2230), Color(0xFF141720))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color(0x33E63946),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "SWARM AI • 5 AGENTS LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFFFB703),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "${(progressFraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF38BDF8)
                )
            }
        }

        // Multi-Track Timeline + 5 Animated Swarm Cursor Agents
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(114.dp)
                .onSizeChanged { boxSize = it }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackCount = 4
                val rowGap = 6.dp.toPx()
                val trackHeight = (size.height - rowGap * (trackCount - 1)) / trackCount
                val trackColors = listOf(
                    listOf(Color(0xFF3B82F6), Color(0xFF7C3AED), Color(0xFF3B82F6), Color(0xFF6366F1)),
                    listOf(Color(0xFFF59E0B), Color(0xFFB45309), Color(0xFFF97316), Color(0xFFF59E0B)),
                    listOf(Color(0xFF14B8A6), Color(0xFF0D9488), Color(0xFF06B6D4), Color(0xFF14B8A6)),
                    listOf(Color(0xFF22C55E), Color(0xFF16A34A))
                )

                for (row in 0 until trackCount) {
                    val y = row * (trackHeight + rowGap)
                    drawRoundRect(
                        color = Color(0xFF151923),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, trackHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    val blocks = trackColors[row]
                    val segW = size.width / blocks.size
                    val innerGap = 5.dp.toPx()
                    blocks.forEachIndexed { idx, col ->
                        val bx = idx * segW + if (row == 2 && idx == 0) size.width * 0.08f else 2.dp.toPx()
                        val bw = (segW - innerGap * 1.4f - if (row == 2 && idx == 0) size.width * 0.08f else 0f)
                            .coerceAtLeast(14.dp.toPx())
                        drawRoundRect(
                            color = col.copy(alpha = 0.85f),
                            topLeft = Offset(bx, y + 2.dp.toPx()),
                            size = Size(bw, trackHeight - 4.dp.toPx()),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }
                }

                // Vertical moving white playhead line
                val playX = (progressFraction.coerceIn(0.05f, 0.96f) * size.width)
                drawLine(
                    color = Color.White,
                    start = Offset(playX, 0f),
                    end = Offset(playX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }

            if (boxSize.width > 0 && boxSize.height > 0) {
                val trackStepPx = boxSize.height / 4f
                val badgePadPx = with(density) { 86.dp.toPx() }
                agents.forEach { spec ->
                    val animXNorm = (spec.baseX + sin(t * spec.freqMul + spec.phaseOffset) * spec.ampX)
                        .coerceIn(0.06f, 0.84f)
                    val animYFloat = (spec.trackIndex * trackStepPx + sin(t * 1.2f + spec.phaseOffset) * 10f)
                        .coerceIn(4f, (boxSize.height - 34f).coerceAtLeast(4f))
                    val xPx = (animXNorm * (boxSize.width - badgePadPx)).coerceAtLeast(2f)

                    SwarmAgentCursorBadge(
                        label = spec.name,
                        color = spec.badgeColor,
                        modifier = Modifier.offset {
                            IntOffset(xPx.roundToInt(), animYFloat.roundToInt())
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwarmAgentCursorBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width * 0.92f, size.height * 0.45f)
                lineTo(size.width * 0.48f, size.height * 0.56f)
                lineTo(size.width * 0.34f, size.height * 0.96f)
                close()
            }
            drawPath(path = path, color = color)
            drawPath(path = path, color = Color.White, style = Stroke(width = 1.4.dp.toPx()))
        }
        Surface(
            color = color,
            shape = RoundedCornerShape(7.dp),
            shadowElevation = 4.dp,
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.75f)),
            modifier = Modifier.offset(x = 8.dp, y = (-2).dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
