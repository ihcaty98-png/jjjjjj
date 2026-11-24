package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.VideoClipSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

data class TimestampedFrame(
    val timestampLabel: String,
    val bitmap: Bitmap
)

sealed interface PreparedVideoPayload {
    val durationLabel: String

    data class InlineVideo(
        val bytes: ByteArray,
        val mimeType: String,
        override val durationLabel: String
    ) : PreparedVideoPayload

    data class KeyframeSequence(
        val frames: List<TimestampedFrame>,
        override val durationLabel: String
    ) : PreparedVideoPayload
}

sealed interface PreparedDubInput {
    val durationMs: Long
    val durationLabel: String

    data class InlineFullVideo(
        val bytes: ByteArray,
        val mimeType: String,
        override val durationMs: Long,
        override val durationLabel: String
    ) : PreparedDubInput

    data class AudioPlusKeyframes(
        val audioBytes: ByteArray?,
        val audioMimeType: String,
        val frames: List<TimestampedFrame>,
        override val durationMs: Long,
        override val durationLabel: String
    ) : PreparedDubInput
}

object VideoProcessor {

    private const val MAX_EXTRACTED_AUDIO_BYTES = 8L * 1024L * 1024L

    suspend fun inspectVideo(context: Context, uri: Uri): VideoClipSummary = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "selected_clip.mp4"
        var sizeBytes = 0L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }

        if (sizeBytes <= 0L) {
            sizeBytes = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length.coerceAtLeast(0L) }
            }.getOrNull() ?: 0L
        }

        val mimeType = resolver.getType(uri) ?: "video/mp4"
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 1280
        var height = 720
        var hasAudio = false
        var thumbnail: Bitmap? = null

        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                width = rawH
                height = rawW
            } else {
                width = rawW
                height = rawH
            }
            hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val frameTimeUs = if (durationMs > 2000L) (durationMs * 1000L) / 5L else 500_000L
            val rawThumb = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            thumbnail = rawThumb?.let { scaleDownBitmap(it, maxWidth = 720) }
        } finally {
            runCatching { retriever.release() }
        }

        VideoClipSummary(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            thumbnail = thumbnail,
            width = width.coerceAtLeast(160),
            height = height.coerceAtLeast(160),
            hasAudio = hasAudio
        )
    }

    suspend fun prepareVideoPayload(
        context: Context,
        summary: VideoClipSummary,
        forceKeyframes: Boolean = false
    ): PreparedVideoPayload = withContext(Dispatchers.IO) {
        if (!forceKeyframes && summary.isInlineEligible) {
            val bytes = readBytesUpTo(context, summary.uri, VideoClipSummary.MAX_INLINE_BYTES)
            if (bytes != null && bytes.isNotEmpty()) {
                return@withContext PreparedVideoPayload.InlineVideo(
                    bytes = bytes,
                    mimeType = summary.mimeType,
                    durationLabel = summary.formattedDuration
                )
            }
        }

        val frames = extractKeyframeSequence(context, summary.uri, summary.durationMs, frameCount = 10)
        require(frames.isNotEmpty()) {
            "Unable to extract video frames from the selected clip. Please try a standard MP4/MOV video."
        }
        PreparedVideoPayload.KeyframeSequence(
            frames = frames,
            durationLabel = summary.formattedDuration
        )
    }

    suspend fun prepareDubInput(
        context: Context,
        summary: VideoClipSummary,
        forceSplitTracks: Boolean = false
    ): PreparedDubInput = withContext(Dispatchers.IO) {
        val safeDurationMs = summary.durationMs.coerceAtLeast(1000L)
        if (!forceSplitTracks && summary.isInlineEligible) {
            val bytes = readBytesUpTo(context, summary.uri, VideoClipSummary.MAX_INLINE_BYTES)
            if (bytes != null && bytes.isNotEmpty()) {
                return@withContext PreparedDubInput.InlineFullVideo(
                    bytes = bytes,
                    mimeType = summary.mimeType,
                    durationMs = safeDurationMs,
                    durationLabel = summary.formattedDuration
                )
            }
        }

        val extractedAudioBytes = if (summary.hasAudio) {
            extractAudioTrackBytes(context, summary.uri)
        } else {
            null
        }
        val dynamicFrameCount = ((safeDurationMs / 4_000L).toInt()).coerceIn(10, 18)
        val frames = extractKeyframeSequence(context, summary.uri, safeDurationMs, frameCount = dynamicFrameCount)
        require(extractedAudioBytes != null || frames.isNotEmpty()) {
            "Unable to read audio or video frames from this file. Please try another MP4 video."
        }

        PreparedDubInput.AudioPlusKeyframes(
            audioBytes = extractedAudioBytes,
            audioMimeType = "audio/mp4",
            frames = frames,
            durationMs = safeDurationMs,
            durationLabel = summary.formattedDuration
        )
    }

    suspend fun extractAudioSampleBytes(
        context: Context,
        uri: Uri
    ): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        if (mime.startsWith("audio/")) {
            val raw = readBytesUpTo(context, uri, MAX_EXTRACTED_AUDIO_BYTES)
            if (raw != null && raw.isNotEmpty()) {
                return@withContext raw to mime
            }
        }
        val extracted = extractAudioTrackBytes(context, uri)
        if (extracted != null && extracted.isNotEmpty()) {
            extracted to "audio/mp4"
        } else {
            null
        }
    }

    private fun readBytesUpTo(context: Context, uri: Uri, maxBytes: Long): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > maxBytes) {
                    return null
                }
            }
            buffer.toByteArray()
        }
    }

    private fun extractAudioTrackBytes(context: Context, uri: Uri): ByteArray? {
        val tempFile = File.createTempFile("dub_audio_", ".m4a", context.cacheDir)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(256 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxTrackIndex, buffer, bufferInfo)
                extractor.advance()
                if (tempFile.length() > MAX_EXTRACTED_AUDIO_BYTES) {
                    return null
                }
            }

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            val bytes = tempFile.readBytes()
            bytes.takeIf { it.isNotEmpty() && it.size <= MAX_EXTRACTED_AUDIO_BYTES }
        } catch (_: Exception) {
            null
        } finally {
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { tempFile.delete() }
        }
    }

    private fun extractKeyframeSequence(
        context: Context,
        uri: Uri,
        durationMs: Long,
        frameCount: Int
    ): List<TimestampedFrame> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<TimestampedFrame>()
        try {
            retriever.setDataSource(context, uri)
            val safeDurationMs = durationMs.takeIf { it > 0L }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 30_000L
            val count = frameCount.coerceAtLeast(2)
            val endTimeMs = (safeDurationMs - 350L).coerceAtLeast(0L)

            for (i in 0 until count) {
                val fraction = i.toDouble() / (count - 1).toDouble()
                val timeMs = (endTimeMs * fraction).toLong().coerceIn(0L, endTimeMs)
                val raw = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (raw != null) {
                    val scaled = scaleDownBitmap(raw, maxWidth = 540)
                    val totalSecs = timeMs / 1000L
                    val label = String.format("%02d:%02d", totalSecs / 60L, totalSecs % 60L)
                    frames.add(TimestampedFrame(timestampLabel = label, bitmap = scaled))
                }
            }
        } finally {
            runCatching { retriever.release() }
        }
        return frames
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)
    }
}
