package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.SubtitlePositionOption
import com.example.model.SubtitleSizeOption
import com.example.model.SubtitleThemeStyle
import com.example.model.VideoClipSummary
import com.example.model.WatermarkBadgeStyle
import com.example.model.WatermarkConfig
import com.example.model.WatermarkMotionStyle
import com.example.model.WatermarkPositionPreset
import com.example.ui.theme.MyanmarScriptFamily
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive video preview stage + dedicated 'FireRecapScript' watermark overlay studio
 * letting users toggle, position (glide / 4-corner / snap presets / direct drag), and customize
 * watermark text, badge style, size, and opacity directly on the preview screen.
 */
@Composable
fun WatermarkVideoPreviewComponent(
    video: VideoClipSummary?,
    subtitleStyle: SubtitleThemeStyle,
    subtitleSize: SubtitleSizeOption,
    subtitlePosition: SubtitlePositionOption,
    burnSubtitles: Boolean,
    watermarkConfig: WatermarkConfig,
    isBusy: Boolean,
    onWatermarkEnabledChanged: (Boolean) -> Unit,
    onWatermarkTextChanged: (String) -> Unit,
    onWatermarkMotionSelected: (WatermarkMotionStyle) -> Unit,
    onWatermarkPresetSelected: (WatermarkPositionPreset) -> Unit,
    onWatermarkCustomAnchorChanged: (Float, Float) -> Unit,
    onWatermarkBadgeStyleSelected: (WatermarkBadgeStyle) -> Unit,
    onWatermarkScaleChanged: (Float) -> Unit,
    onWatermarkOpacityChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var customizeExpanded by remember { mutableStateOf(true) }

    val transition = rememberInfiniteTransition(label = "watermark_live_motion")
    val animX by transition.animateFloat(
        initialValue = -0.64f,
        targetValue = 0.64f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wm_anim_x"
    )
    val animY by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = -0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wm_anim_y"
    )
    val cornerPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wm_corner_phase"
    )

    val (normX, normY) = when (watermarkConfig.motionStyle) {
        WatermarkMotionStyle.GLIDE_XY -> animX to animY
        WatermarkMotionStyle.CORNER_HOP -> {
            when (cornerPhase.toInt() % 4) {
                0 -> -0.64f to 0.72f
                1 -> 0.64f to 0.72f
                2 -> 0.64f to -0.42f
                else -> -0.64f to -0.42f
            }
        }
        WatermarkMotionStyle.FIXED_POSITION -> {
            watermarkConfig.customAnchorX to watermarkConfig.customAnchorY
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (watermarkConfig.enabled) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("watermark_preview_studio_component")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // 1. Live Video Stage with Interactive Watermark Overlay & On-Canvas Quick Toggle
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val density = LocalDensity.current
                val stageWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                val stageHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                var badgeSize by remember { mutableStateOf(IntSize.Zero) }

                val thumb = video?.thumbnail
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "Video preview with FireRecapScript watermark",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero),
                        contentDescription = "Video stage preview backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Subtle vignette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.34f),
                                    Color.Black.copy(alpha = 0.14f),
                                    Color.Black.copy(alpha = 0.46f)
                                )
                            )
                        )
                )

                // Top-left live stage tag
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenWith,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (watermarkConfig.enabled) {
                                "PREVIEW • DRAG WATERMARK TO POSITION"
                            } else {
                                "PREVIEW • WATERMARK OFF"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White
                        )
                    }
                }

                // Quick On-Canvas Watermark Visibility Toggle Pill
                Surface(
                    onClick = { if (!isBusy) onWatermarkEnabledChanged(!watermarkConfig.enabled) },
                    color = if (watermarkConfig.enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                    } else {
                        Color.Black.copy(alpha = 0.70f)
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .testTag("preview_canvas_watermark_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (watermarkConfig.enabled) {
                                Icons.Rounded.Visibility
                            } else {
                                Icons.Rounded.VisibilityOff
                            },
                            contentDescription = "Toggle watermark overlay",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (watermarkConfig.enabled) "WM ON" else "WM OFF",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                // Live Interactive Watermark Badge Overlay
                if (watermarkConfig.enabled) {
                    val centerOffsetX = ((normX + 1f) * 0.5f * stageWidthPx) - (badgeSize.width * 0.5f)
                    val centerOffsetY = ((1f - normY) * 0.5f * stageHeightPx) - (badgeSize.height * 0.5f)
                    val clampedX = centerOffsetX.coerceIn(8f, (stageWidthPx - badgeSize.width - 8f).coerceAtLeast(8f))
                    val clampedY = centerOffsetY.coerceIn(8f, (stageHeightPx - badgeSize.height - 8f).coerceAtLeast(8f))

                    LiveWatermarkPreviewBadge(
                        config = watermarkConfig,
                        modifier = Modifier
                            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                            .onSizeChanged { badgeSize = it }
                            .pointerInput(isBusy, stageWidthPx, stageHeightPx) {
                                if (!isBusy) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dxNorm = (dragAmount.x / (stageWidthPx * 0.5f))
                                        val dyNorm = -(dragAmount.y / (stageHeightPx * 0.5f))
                                        val nextX = (watermarkConfig.customAnchorX + dxNorm).coerceIn(-0.74f, 0.74f)
                                        val nextY = (watermarkConfig.customAnchorY + dyNorm).coerceIn(-0.48f, 0.80f)
                                        onWatermarkCustomAnchorChanged(nextX, nextY)
                                    }
                                }
                            }
                            .testTag("live_watermark_overlay_badge")
                    )
                }

                // Live Burmese Subtitle Sample
                if (burnSubtitles) {
                    PreviewSubtitleSample(
                        style = subtitleStyle,
                        size = subtitleSize,
                        position = subtitlePosition,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // 2. Docked Watermark Studio Control Strip
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FireRecapScript Watermark",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Toggle, drag/position & customize brand protection",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = watermarkConfig.enabled,
                            onCheckedChange = { if (!isBusy) onWatermarkEnabledChanged(it) },
                            modifier = Modifier.testTag("watermark_enabled_switch")
                        )
                        IconButtonCompatExpand(
                            expanded = customizeExpanded,
                            onClick = { customizeExpanded = !customizeExpanded }
                        )
                    }
                }

                AnimatedVisibility(visible = watermarkConfig.enabled && customizeExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Watermark Text Input + Reset
                        OutlinedTextField(
                            value = watermarkConfig.text,
                            onValueChange = { if (!isBusy) onWatermarkTextChanged(it) },
                            label = { Text("Watermark Text") },
                            placeholder = { Text("FireRecapScript") },
                            singleLine = true,
                            trailingIcon = {
                                if (watermarkConfig.text != "FireRecapScript") {
                                    TextButton(
                                        onClick = { if (!isBusy) onWatermarkTextChanged("FireRecapScript") },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("watermark_text_input")
                        )

                        // Motion / Placement Mode
                        Text(
                            text = "MOTION & POSITION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(WatermarkMotionStyle.entries) { motion ->
                                FilterChip(
                                    selected = watermarkConfig.motionStyle == motion,
                                    onClick = { if (!isBusy) onWatermarkMotionSelected(motion) },
                                    label = {
                                        Text(
                                            text = "${motion.displayName} • ${motion.burmeseLabel}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MyanmarScriptFamily)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("watermark_motion_${motion.id}")
                                )
                            }
                        }

                        // Quick Snap Position Presets (always available to jump straight to a spot)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(WatermarkPositionPreset.entries) { preset ->
                                val isPresetActive = watermarkConfig.motionStyle == WatermarkMotionStyle.FIXED_POSITION &&
                                    abs(watermarkConfig.customAnchorX - preset.anchorX) < 0.08f &&
                                    abs(watermarkConfig.customAnchorY - preset.anchorY) < 0.08f
                                FilterChip(
                                    selected = isPresetActive,
                                    onClick = { if (!isBusy) onWatermarkPresetSelected(preset) },
                                    label = { Text(preset.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("watermark_pos_${preset.id}")
                                )
                            }
                        }

                        // Badge Visual Style
                        Text(
                            text = "BADGE STYLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(WatermarkBadgeStyle.entries) { badge ->
                                FilterChip(
                                    selected = watermarkConfig.badgeStyle == badge,
                                    onClick = { if (!isBusy) onWatermarkBadgeStyleSelected(badge) },
                                    label = { Text(badge.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("watermark_badge_${badge.id}")
                                )
                            }
                        }

                        // Scale Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Watermark Size",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.2fx", watermarkConfig.scale),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = watermarkConfig.scale,
                            onValueChange = { if (!isBusy) onWatermarkScaleChanged(it) },
                            valueRange = 0.75f..1.40f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("watermark_scale_slider")
                        )

                        // Opacity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Watermark Visibility",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(watermarkConfig.opacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = watermarkConfig.opacity,
                            onValueChange = { if (!isBusy) onWatermarkOpacityChanged(it) },
                            valueRange = 0.30f..0.95f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("watermark_opacity_slider")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconButtonCompatExpand(
    expanded: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("watermark_customize_expand_button")
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Collapse watermark controls" else "Expand watermark controls",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LiveWatermarkPreviewBadge(
    config: WatermarkConfig,
    modifier: Modifier = Modifier
) {
    val label = config.text.trim().ifBlank { "FireRecapScript" }
    val scaledSp = (11f * config.scale.coerceIn(0.75f, 1.40f)).sp
    val dotSize = (6.5f * config.scale.coerceIn(0.75f, 1.40f)).dp

    when (config.badgeStyle) {
        WatermarkBadgeStyle.OBSIDIAN_GOLD -> {
            Surface(
                color = Color(0xB40F121C),
                shape = CircleShape,
                border = BorderStroke(1.2.dp, Color(0xCCFFB703)),
                modifier = modifier.alpha(config.opacity)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(Color(0xFFE63946))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = scaledSp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
        WatermarkBadgeStyle.CRIMSON_FIRE -> {
            Surface(
                color = Color.Transparent,
                shape = CircleShape,
                border = BorderStroke(1.2.dp, Color(0xDDFFCE36)),
                modifier = modifier
                    .alpha(config.opacity)
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xCCB9182A), Color(0xCCE63946))),
                        CircleShape
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(Color(0xFFFFCE36))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = scaledSp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
        WatermarkBadgeStyle.FROST_GLASS -> {
            Surface(
                color = Color(0x73FFFFFF),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xCCFFFFFF)),
                modifier = modifier.alpha(config.opacity)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(Color(0xFFE63946))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = scaledSp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
        WatermarkBadgeStyle.MINIMAL_TAG -> {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = scaledSp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White,
                modifier = modifier
                    .alpha(config.opacity)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PreviewSubtitleSample(
    style: SubtitleThemeStyle,
    size: SubtitleSizeOption,
    position: SubtitlePositionOption,
    modifier: Modifier = Modifier
) {
    val sampleLine = "မင်းဘာလုပ်နေတာလဲ၊\nအခုချက်ချင်း ထွက်သွားရအောင်။"
    val subModifier = modifier.padding(
        bottom = if (position == SubtitlePositionOption.LOWER_MID) 32.dp else 10.dp,
        start = 16.dp,
        end = 16.dp
    )
    val fontSp = (12.5f * size.scaleFactor).sp
    when (style) {
        SubtitleThemeStyle.CINEMA_PILL -> {
            Surface(
                color = Color(0xD20D0F16),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0x99FFB703)),
                modifier = subModifier
            ) {
                Text(
                    text = sampleLine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MyanmarScriptFamily,
                        fontSize = fontSp,
                        lineHeight = (fontSp.value * 1.38f).sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        SubtitleThemeStyle.GOLD_CINEMA -> {
            Surface(
                color = Color(0x66000000),
                shape = RoundedCornerShape(8.dp),
                modifier = subModifier
            ) {
                Text(
                    text = sampleLine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MyanmarScriptFamily,
                        fontSize = fontSp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (fontSp.value * 1.38f).sp
                    ),
                    color = Color(0xFFFFCE36),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        SubtitleThemeStyle.CRIMSON_VIRAL -> {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                modifier = subModifier.background(
                    Brush.horizontalGradient(listOf(Color(0xE0B9182A), Color(0xE0E63946))),
                    RoundedCornerShape(10.dp)
                )
            ) {
                Text(
                    text = sampleLine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MyanmarScriptFamily,
                        fontSize = fontSp,
                        lineHeight = (fontSp.value * 1.38f).sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        SubtitleThemeStyle.CLEAN_SHADOW -> {
            Text(
                text = sampleLine,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MyanmarScriptFamily,
                    fontSize = fontSp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (fontSp.value * 1.38f).sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = subModifier
            )
        }
    }
}
