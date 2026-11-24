package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.R
import com.example.model.CopyrightShieldConfig
import com.example.model.DubSegment
import com.example.model.SubtitlePositionOption
import com.example.model.SubtitleSizeOption
import com.example.model.SubtitleThemeStyle
import com.example.model.WatermarkBadgeStyle
import com.example.model.WatermarkConfig
import com.example.model.WatermarkMotionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

@OptIn(UnstableApi::class)
class BurmeseSubtitleOverlay(
    context: Context,
    private val segments: List<DubSegment>,
    videoWidth: Int,
    private val subtitleStyle: SubtitleThemeStyle,
    private val subtitleSize: SubtitleSizeOption,
    subtitlePosition: SubtitlePositionOption
) : BitmapOverlay() {

    private val canvasWidth = videoWidth.coerceIn(480, 1080)
    private val canvasHeight = (canvasWidth * 0.25f).toInt().coerceIn(136, 310)

    private val myanmarTypeface: Typeface =
        ResourcesCompat.getFont(context, R.font.noto_sans_myanmar) ?: Typeface.DEFAULT_BOLD

    private val overlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, subtitlePosition.anchorY)
        .setOverlayFrameAnchor(0f, -1f)
        .build()

    private val emptyBitmap: Bitmap = Bitmap.createBitmap(
        canvasWidth,
        canvasHeight,
        Bitmap.Config.ARGB_8888
    ).apply { eraseColor(Color.TRANSPARENT) }

    private var cachedSegmentId: String? = null
    private var cachedBitmap: Bitmap = emptyBitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = presentationTimeUs / 1000L
        val active = segments.firstOrNull { timeMs >= it.startMs && timeMs <= it.endMs }
            ?: segments.lastOrNull()?.takeIf { last ->
                timeMs > last.endMs && (timeMs - last.endMs) <= 350L
            }
        if (active == null) {
            cachedSegmentId = null
            return emptyBitmap
        }
        if (active.id == cachedSegmentId) {
            return cachedBitmap
        }
        cachedSegmentId = active.id
        cachedBitmap = renderSubtitleBitmap(active.burmeseText)
        return cachedBitmap
    }

    private fun renderSubtitleBitmap(rawText: String): Bitmap {
        val text = formatBalancedTwoLines(rawText.trim())
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val horizontalMargin = canvasWidth * 0.055f
        val innerPadH = canvasWidth * 0.036f
        val innerPadV = canvasHeight * 0.085f
        val maxTextWidth = (canvasWidth - (horizontalMargin * 2f) - (innerPadH * 2f)).toInt().coerceAtLeast(160)

        var textSizePx = (canvasWidth * 0.041f * subtitleSize.scaleFactor).coerceIn(18f, 50f)
        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = myanmarTypeface
            textSize = textSizePx
            color = when (subtitleStyle) {
                SubtitleThemeStyle.CINEMA_PILL -> Color.WHITE
                SubtitleThemeStyle.GOLD_CINEMA -> Color.rgb(255, 206, 54)
                SubtitleThemeStyle.CRIMSON_VIRAL -> Color.WHITE
                SubtitleThemeStyle.CLEAN_SHADOW -> Color.WHITE
            }
            setShadowLayer(4.5f, 0f, 2f, Color.argb(230, 0, 0, 0))
        }

        var layout = buildLayout(text, fillPaint, maxTextWidth)
        if (layout.lineCount > 2) {
            textSizePx *= 0.86f
            fillPaint.textSize = textSizePx
            layout = buildLayout(text, fillPaint, maxTextWidth)
        }
        if (layout.lineCount > 3) {
            textSizePx *= 0.85f
            fillPaint.textSize = textSizePx
            layout = buildLayout(text, fillPaint, maxTextWidth)
        }

        var maxLineWidth = 0f
        for (i in 0 until layout.lineCount) {
            maxLineWidth = maxOf(maxLineWidth, layout.getLineWidth(i))
        }

        val boxWidth = (maxLineWidth + innerPadH * 2f).coerceAtMost(canvasWidth - horizontalMargin)
        val boxHeight = (layout.height + innerPadV * 2f).coerceAtMost(canvasHeight - 8f)
        val left = (canvasWidth - boxWidth) / 2f
        val bottom = canvasHeight - 6f
        val top = (bottom - boxHeight).coerceAtLeast(0f)
        val rect = RectF(left, top, left + boxWidth, bottom)

        when (subtitleStyle) {
            SubtitleThemeStyle.CINEMA_PILL -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(212, 13, 15, 22)
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2.2f
                    color = Color.argb(145, 255, 183, 3)
                }
                val radius = (canvasHeight * 0.12f).coerceIn(14f, 26f)
                canvas.drawRoundRect(rect, radius, radius, bgPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
            }
            SubtitleThemeStyle.GOLD_CINEMA -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(110, 0, 0, 0)
                }
                val radius = (canvasHeight * 0.09f).coerceIn(10f, 20f)
                canvas.drawRoundRect(rect, radius, radius, bgPaint)
            }
            SubtitleThemeStyle.CRIMSON_VIRAL -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        rect.left, rect.top, rect.right, rect.bottom,
                        intArrayOf(Color.argb(225, 185, 24, 42), Color.argb(225, 230, 57, 70)),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2f
                    color = Color.argb(190, 255, 255, 255)
                }
                val radius = (canvasHeight * 0.12f).coerceIn(14f, 26f)
                canvas.drawRoundRect(rect, radius, radius, bgPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
            }
            SubtitleThemeStyle.CLEAN_SHADOW -> {
                // Pure high-contrast text with black outline/shadow, no heavy background box
            }
        }

        canvas.save()
        val textLeft = (canvasWidth - maxTextWidth) / 2f
        val textTop = rect.top + (rect.height() - layout.height) / 2f
        canvas.translate(textLeft, textTop)
        if (subtitleStyle == SubtitleThemeStyle.GOLD_CINEMA || subtitleStyle == SubtitleThemeStyle.CLEAN_SHADOW) {
            val strokePaint = TextPaint(fillPaint).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 5.5f
                color = Color.BLACK
                clearShadowLayer()
            }
            buildLayout(text, strokePaint, maxTextWidth).draw(canvas)
        }
        layout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun formatBalancedTwoLines(text: String): String {
        if (text.contains('\n') || text.length <= 28) return text
        val mid = text.length / 2
        val breakIdx = text.indices
            .filter { i ->
                (text[i] == ' ' || text[i] == '၊') && i in (text.length / 4)..(text.length * 3 / 4)
            }
            .minByOrNull { abs(it - mid) } ?: return text
        val line1 = text.substring(0, breakIdx + if (text[breakIdx] == '၊') 1 else 0).trim()
        val line2 = text.substring(breakIdx + 1).trim()
        return if (line1.isNotEmpty() && line2.isNotEmpty()) "$line1\n$line2" else text
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(3f, 1.14f)
            .setIncludePad(false)
            .build()
    }
}

@OptIn(UnstableApi::class)
class FloatingWatermarkOverlay(
    context: Context,
    videoWidth: Int,
    private val config: WatermarkConfig
) : BitmapOverlay() {

    private val watermarkText = config.text.trim().ifBlank { "FireRecapScript" }.take(28)
    private val displayTypeface: Typeface =
        ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT_BOLD

    private val watermarkBitmap: Bitmap by lazy { createWatermarkBitmap(videoWidth) }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = watermarkBitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val tSec = (presentationTimeUs / 1_000_000.0).coerceAtLeast(0.0)
        val (anchorX, anchorY) = when (config.motionStyle) {
            WatermarkMotionStyle.GLIDE_XY -> {
                // Smooth multi-axis triangle-wave glide: moves continuously Left <-> Right AND Top <-> Bottom
                val x = triangleWave(tSec, periodSec = 9.0, min = -0.68, max = 0.68).toFloat()
                val y = triangleWave(tSec + 2.25, periodSec = 14.0, min = -0.50, max = 0.78).toFloat()
                x to y
            }
            WatermarkMotionStyle.CORNER_HOP -> {
                val spots = listOf(
                    -0.66f to 0.76f,  // Top-Left
                    0.66f to 0.76f,   // Top-Right
                    0.66f to -0.45f,  // Lower-Right (above subtitles)
                    -0.66f to -0.45f, // Lower-Left (above subtitles)
                    0.0f to 0.80f     // Top-Center
                )
                val segDur = 3.2
                val idx = ((tSec / segDur).toInt()) % spots.size
                val nextIdx = (idx + 1) % spots.size
                val localFrac = ((tSec % segDur) / segDur)
                // Hold cleanly 70% of slot, glide swiftly in last 30%
                val smooth = if (localFrac < 0.72) 0.0 else ((localFrac - 0.72) / 0.28).let { p ->
                    p * p * (3.0 - 2.0 * p)
                }
                val (ax, ay) = spots[idx]
                val (bx, by) = spots[nextIdx]
                val x = (ax + (bx - ax) * smooth).toFloat()
                val y = (ay + (by - ay) * smooth).toFloat()
                x to y
            }
            WatermarkMotionStyle.FIXED_POSITION -> {
                config.customAnchorX.coerceIn(-0.78f, 0.78f) to
                    config.customAnchorY.coerceIn(-0.52f, 0.84f)
            }
        }
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(anchorX, anchorY)
            .setOverlayFrameAnchor(0f, 0f)
            .setAlphaScale(config.opacity.coerceIn(0.30f, 0.95f))
            .build()
    }

    private fun triangleWave(t: Double, periodSec: Double, min: Double, max: Double): Double {
        val phase = ((t % periodSec) + periodSec) % periodSec / periodSec
        val unit = if (phase < 0.5) phase * 2.0 else 2.0 - phase * 2.0
        return min + (max - min) * unit
    }

    private fun createWatermarkBitmap(videoWidth: Int): Bitmap {
        val scale = config.scale.coerceIn(0.75f, 1.40f)
        val textPx = (videoWidth.coerceIn(480, 1080) * 0.032f * scale).coerceIn(16f, 48f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = displayTypeface
            textSize = textPx
            color = Color.WHITE
            isFakeBoldText = true
            setShadowLayer(4f, 0f, 1.5f, Color.argb(220, 0, 0, 0))
        }
        val textW = textPaint.measureText(watermarkText)
        val fm = textPaint.fontMetrics
        val textH = fm.descent - fm.ascent

        val showBadgeBox = config.badgeStyle != WatermarkBadgeStyle.MINIMAL_TAG
        val dotRadius = if (showBadgeBox) textPx * 0.28f else 0f
        val padH = if (showBadgeBox) textPx * 0.7f else textPx * 0.35f
        val padV = if (showBadgeBox) textPx * 0.42f else textPx * 0.25f
        val iconGap = if (showBadgeBox) textPx * 0.45f else 0f
        val bmpW = (textW + padH * 2f + dotRadius * 2f + iconGap + 8f).toInt().coerceAtLeast(120)
        val bmpH = (textH + padV * 2f + 8f).toInt().coerceAtLeast(44)

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val pillRect = RectF(3f, 3f, bmpW - 3f, bmpH - 3f)
        val corner = bmpH / 2f

        when (config.badgeStyle) {
            WatermarkBadgeStyle.OBSIDIAN_GOLD -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(175, 15, 18, 28)
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.9f
                    color = Color.argb(200, 255, 183, 3)
                }
                canvas.drawRoundRect(pillRect, corner, corner, bgPaint)
                canvas.drawRoundRect(pillRect, corner, corner, borderPaint)
            }
            WatermarkBadgeStyle.CRIMSON_FIRE -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
                        intArrayOf(Color.argb(205, 185, 24, 42), Color.argb(205, 230, 57, 70)),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.8f
                    color = Color.argb(215, 255, 206, 54)
                }
                canvas.drawRoundRect(pillRect, corner, corner, bgPaint)
                canvas.drawRoundRect(pillRect, corner, corner, borderPaint)
            }
            WatermarkBadgeStyle.FROST_GLASS -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(115, 255, 255, 255)
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.6f
                    color = Color.argb(195, 255, 255, 255)
                }
                canvas.drawRoundRect(pillRect, corner, corner, bgPaint)
                canvas.drawRoundRect(pillRect, corner, corner, borderPaint)
            }
            WatermarkBadgeStyle.MINIMAL_TAG -> {
                // Shadow-only crisp text without container pill
            }
        }

        val centerY = pillRect.centerY()
        val textX: Float
        if (showBadgeBox) {
            val dotX = pillRect.left + padH + dotRadius
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (config.badgeStyle) {
                    WatermarkBadgeStyle.OBSIDIAN_GOLD -> Color.rgb(230, 57, 70)
                    WatermarkBadgeStyle.CRIMSON_FIRE -> Color.rgb(255, 206, 54)
                    WatermarkBadgeStyle.FROST_GLASS -> Color.rgb(230, 57, 70)
                    WatermarkBadgeStyle.MINIMAL_TAG -> Color.WHITE
                }
            }
            canvas.drawCircle(dotX, centerY, dotRadius, dotPaint)
            textX = dotX + dotRadius + iconGap
        } else {
            textX = pillRect.left + padH
        }

        val baselineY = centerY - (fm.ascent + fm.descent) / 2f
        canvas.drawText(watermarkText, textX, baselineY, textPaint)

        return bitmap
    }
}

@OptIn(UnstableApi::class)
class CopyrightBorderOverlay(
    videoWidth: Int,
    videoHeight: Int
) : BitmapOverlay() {

    private val frameBitmap: Bitmap by lazy {
        val w = videoWidth.coerceIn(480, 1080)
        val h = videoHeight.coerceIn(360, 1920)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val borderPx = (minOf(w, h) * 0.016f).coerceIn(8f, 20f)
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderPx
            color = Color.argb(215, 10, 12, 18)
        }
        val innerGoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            color = Color.argb(130, 255, 183, 3)
        }
        canvas.drawRect(borderPx * 0.5f, borderPx * 0.5f, w - borderPx * 0.5f, h - borderPx * 0.5f, rimPaint)
        canvas.drawRect(borderPx + 2f, borderPx + 2f, w - borderPx - 2f, h - borderPx - 2f, innerGoldPaint)
        bmp
    }

    private val settings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, 0f)
        .setOverlayFrameAnchor(0f, 0f)
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap = frameBitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}

object VideoDubExporter {

    @OptIn(UnstableApi::class)
    suspend fun exportDubbedVideo(
        context: Context,
        sourceVideoUri: Uri,
        videoWidth: Int,
        videoHeight: Int,
        hasOriginalAudio: Boolean,
        dubWavFile: File?,
        segments: List<DubSegment>,
        burnSubtitles: Boolean,
        subtitleStyle: SubtitleThemeStyle,
        subtitleSize: SubtitleSizeOption,
        subtitlePosition: SubtitlePositionOption,
        watermarkConfig: WatermarkConfig,
        copyrightShieldConfig: CopyrightShieldConfig = CopyrightShieldConfig(),
        keepSoftOriginalBgm: Boolean,
        outputMp4File: File,
        onProgress: (Int) -> Unit
    ): File {
        val tryBgm = dubWavFile != null && keepSoftOriginalBgm && hasOriginalAudio
        return if (tryBgm) {
            try {
                exportOnce(
                    context = context,
                    sourceVideoUri = sourceVideoUri,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    useSoftBgm = true,
                    dubWavFile = dubWavFile,
                    segments = segments,
                    burnSubtitles = burnSubtitles,
                    subtitleStyle = subtitleStyle,
                    subtitleSize = subtitleSize,
                    subtitlePosition = subtitlePosition,
                    watermarkConfig = watermarkConfig,
                    copyrightShieldConfig = copyrightShieldConfig,
                    outputMp4File = outputMp4File,
                    onProgress = onProgress
                )
            } catch (e: ExportException) {
                exportOnce(
                    context = context,
                    sourceVideoUri = sourceVideoUri,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    useSoftBgm = false,
                    dubWavFile = dubWavFile,
                    segments = segments,
                    burnSubtitles = burnSubtitles,
                    subtitleStyle = subtitleStyle,
                    subtitleSize = subtitleSize,
                    subtitlePosition = subtitlePosition,
                    watermarkConfig = watermarkConfig,
                    copyrightShieldConfig = copyrightShieldConfig,
                    outputMp4File = outputMp4File,
                    onProgress = onProgress
                )
            }
        } else {
            exportOnce(
                context = context,
                sourceVideoUri = sourceVideoUri,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                useSoftBgm = false,
                dubWavFile = dubWavFile,
                segments = segments,
                burnSubtitles = burnSubtitles,
                subtitleStyle = subtitleStyle,
                subtitleSize = subtitleSize,
                subtitlePosition = subtitlePosition,
                watermarkConfig = watermarkConfig,
                copyrightShieldConfig = copyrightShieldConfig,
                outputMp4File = outputMp4File,
                onProgress = onProgress
            )
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun exportOnce(
        context: Context,
        sourceVideoUri: Uri,
        videoWidth: Int,
        videoHeight: Int,
        useSoftBgm: Boolean,
        dubWavFile: File?,
        segments: List<DubSegment>,
        burnSubtitles: Boolean,
        subtitleStyle: SubtitleThemeStyle,
        subtitleSize: SubtitleSizeOption,
        subtitlePosition: SubtitlePositionOption,
        watermarkConfig: WatermarkConfig,
        copyrightShieldConfig: CopyrightShieldConfig,
        outputMp4File: File,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.Main) {
        if (outputMp4File.exists()) {
            outputMp4File.delete()
        }
        outputMp4File.parentFile?.mkdirs()

        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var finished = false

            val videoEffects = mutableListOf<Effect>()
            if (copyrightShieldConfig.enabled) {
                val safeZoom = copyrightShieldConfig.zoomScale.coerceIn(1.0f, 1.15f)
                if (safeZoom > 1.01f) {
                    videoEffects.add(
                        ScaleAndRotateTransformation.Builder()
                            .setScale(safeZoom, safeZoom)
                            .build()
                    )
                }
                if (copyrightShieldConfig.mirrorFlip) {
                    videoEffects.add(
                        MatrixTransformation {
                            android.graphics.Matrix().apply { setScale(-1f, 1f) }
                        }
                    )
                }
                if (copyrightShieldConfig.colorContrastBoost) {
                    videoEffects.add(Contrast(0.07f))
                }
            }

            val overlays = mutableListOf<TextureOverlay>()
            if (copyrightShieldConfig.enabled && copyrightShieldConfig.cinemaFrameBorder) {
                overlays.add(CopyrightBorderOverlay(videoWidth = videoWidth, videoHeight = videoHeight))
            }
            if (burnSubtitles && segments.isNotEmpty()) {
                overlays.add(
                    BurmeseSubtitleOverlay(
                        context = context,
                        segments = segments,
                        videoWidth = videoWidth,
                        subtitleStyle = subtitleStyle,
                        subtitleSize = subtitleSize,
                        subtitlePosition = subtitlePosition
                    )
                )
            }
            if (watermarkConfig.enabled) {
                overlays.add(
                    FloatingWatermarkOverlay(
                        context = context,
                        videoWidth = videoWidth,
                        config = watermarkConfig
                    )
                )
            }

            if (overlays.isNotEmpty()) {
                videoEffects.add(OverlayEffect(overlays))
            }

            val videoMediaItem = MediaItem.fromUri(sourceVideoUri)

            val videoSequence = if (useSoftBgm) {
                val bgmAttenuator = ChannelMixingAudioProcessor().apply {
                    putChannelMixingMatrix(
                        ChannelMixingMatrix.createForConstantGain(1, 1).scaleBy(0.16f)
                    )
                    putChannelMixingMatrix(
                        ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(0.16f)
                    )
                    putChannelMixingMatrix(
                        ChannelMixingMatrix.createForConstantGain(6, 6).scaleBy(0.16f)
                    )
                }
                val editedVideo = EditedMediaItem.Builder(videoMediaItem)
                    .setEffects(Effects(listOf(bgmAttenuator), videoEffects))
                    .build()
                EditedMediaItemSequence.withAudioAndVideoFrom(listOf(editedVideo))
            } else {
                val editedVideo = EditedMediaItem.Builder(videoMediaItem)
                    .setRemoveAudio(true)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
                EditedMediaItemSequence.withVideoFrom(listOf(editedVideo))
            }

            val sequences = if (dubWavFile != null && dubWavFile.exists() && dubWavFile.length() > 44L) {
                val dubAudioMediaItem = MediaItem.fromUri(Uri.fromFile(dubWavFile))
                val editedDubAudio = EditedMediaItem.Builder(dubAudioMediaItem).build()
                val dubAudioSequence = EditedMediaItemSequence.withAudioFrom(listOf(editedDubAudio))
                listOf(videoSequence, dubAudioSequence)
            } else {
                listOf(videoSequence)
            }

            val composition = Composition.Builder(sequences)
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()

            var progressRunnable: Runnable? = null

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (finished) return
                    finished = true
                    progressRunnable?.let { mainHandler.removeCallbacks(it) }
                    onProgress(100)
                    if (continuation.isActive) {
                        continuation.resume(outputMp4File)
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException
                ) {
                    if (finished) return
                    finished = true
                    progressRunnable?.let { mainHandler.removeCallbacks(it) }
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            }

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()

            transformer.start(composition, outputMp4File.absolutePath)

            val runnable = object : Runnable {
                override fun run() {
                    if (finished) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 99))
                    }
                    if (!finished && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.postDelayed(this, 250L)
                    }
                }
            }
            progressRunnable = runnable
            mainHandler.post(runnable)

            continuation.invokeOnCancellation {
                finished = true
                mainHandler.removeCallbacks(runnable)
                mainHandler.post {
                    runCatching { transformer.cancel() }
                }
            }
        }
    }
}
