package com.example.ai

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.ActiveVoiceSelection
import com.example.model.DubSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "LocalAndroidTts"

/**
 * Reliable client-side speech synthesis fallback powered by Android's on-device TextToSpeech
 * engine (with safe timed narration-tone synthesis if a device lacks an offline TTS pack).
 * Guarantees that Video Dubbing exports a valid, synced 24 kHz mono WAV track even when
 * cloud endpoints return HTTP 403 or no Gemini API key is set.
 */
object LocalAndroidTtsSynthesizer {

    suspend fun synthesizeTimelineWav(
        context: Context,
        segments: List<DubSegment>,
        totalDurationMs: Long,
        voiceSelection: ActiveVoiceSelection,
        userSpeedMultiplier: Float,
        pitchFineTuneRatio: Float,
        voiceVolumeGain: Float,
        outputWavFile: File,
        onProgress: (completedCount: Int, totalCount: Int, currentLine: String) -> Unit
    ): SyncedDubAudioResult = withContext(Dispatchers.IO) {
        val sampleRate = BurmeseTtsService.OUTPUT_SAMPLE_RATE
        val totalSamples = ((totalDurationMs.coerceAtLeast(1500L) * sampleRate) / 1000L).toInt()
        val mixFloat = FloatArray(totalSamples)
        val sorted = segments.sortedBy { it.startMs }
        val syncedSegments = mutableListOf<DubSegment>()

        val combinedPitch = (voiceSelection.pitchShiftRatio * pitchFineTuneRatio).coerceIn(0.84f, 1.18f)
        val combinedSpeed = (voiceSelection.defaultSpeed * userSpeedMultiplier).coerceIn(0.82f, 1.28f)

        val tts = initTextToSpeech(context.applicationContext, combinedPitch, combinedSpeed)
        val tempDir = File(context.cacheDir, "local_tts_segments").apply { mkdirs() }
        var cursorEndSample = 0

        try {
            sorted.forEachIndexed { index, segment ->
                onProgress(index, sorted.size, segment.burmeseText)
                val spokenText = segment.burmeseText.trim().ifBlank { segment.originalSpeech.trim() }

                val rawSlice = if (tts != null && spokenText.isNotBlank()) {
                    val segFile = File(tempDir, "seg_${index}_${UUID.randomUUID().toString().take(6)}.wav")
                    val synthesizedOk = synthesizeUtteranceToFile(tts, spokenText, segFile)
                    val parsed = if (synthesizedOk && segFile.exists() && segFile.length() > 44L) {
                        readWavAs24kMono(segFile, sampleRate)
                    } else {
                        null
                    }
                    runCatching { segFile.delete() }
                    parsed?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }

                val slice = if (rawSlice != null) {
                    BurmeseTtsService.trimSilenceSamples(rawSlice, sampleRate)
                        .takeIf { it.isNotEmpty() }
                        ?: generateSoftNarrationCuePcm(segment.durationMs, sampleRate, combinedPitch)
                } else {
                    generateSoftNarrationCuePcm(segment.durationMs, sampleRate, combinedPitch)
                }

                val desiredStartSample = ((segment.startMs * sampleRate) / 1000L).toInt()
                val startSample = desiredStartSample
                    .coerceAtLeast(if (cursorEndSample == 0) 0 else cursorEndSample + (sampleRate * 0.04f).toInt())
                    .coerceIn(0, (totalSamples - 480).coerceAtLeast(0))

                val nextPlannedStartMs = sorted.getOrNull(index + 1)?.startMs ?: totalDurationMs
                val roomMs = (nextPlannedStartMs - (startSample * 1000L / sampleRate))
                    .coerceAtLeast(segment.durationMs)
                    .coerceAtMost(totalDurationMs - (startSample * 1000L / sampleRate))
                val maxSlotSamples = ((roomMs * sampleRate) / 1000L)
                    .toInt()
                    .coerceIn(0, (totalSamples - startSample).coerceAtLeast(0))

                val fitted = if (maxSlotSamples > 0 && slice.size > maxSlotSamples) {
                    BurmeseTtsService.fitSamplesToSlot(slice, maxSlotSamples)
                } else if (maxSlotSamples > 0) {
                    slice
                } else {
                    ShortArray(0)
                }

                if (fitted.isNotEmpty()) {
                    BurmeseTtsService.fadeEdges(fitted, min(320, fitted.size / 6))
                    for (i in fitted.indices) {
                        val dst = startSample + i
                        if (dst < mixFloat.size) {
                            val amp = (fitted[i] / 32768f) * voiceVolumeGain
                            mixFloat[dst] = (mixFloat[dst] + amp).coerceIn(-0.98f, 0.98f)
                        }
                    }
                    val actualStartMs = (startSample * 1000L) / sampleRate
                    val actualEndMs = (((startSample + fitted.size) * 1000L) / sampleRate)
                        .coerceAtLeast(actualStartMs + 500L)
                        .coerceAtMost(totalDurationMs)
                    cursorEndSample = startSample + fitted.size
                    syncedSegments.add(segment.copy(startMs = actualStartMs, endMs = actualEndMs))
                } else {
                    syncedSegments.add(segment)
                }

                onProgress(index + 1, sorted.size, segment.burmeseText)
            }
        } finally {
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
        }

        val finalShorts = ShortArray(totalSamples) { idx ->
            (mixFloat[idx] * 32767f).roundToInt().coerceIn(-32767, 32767).toShort()
        }
        BurmeseTtsService.writeMonoWavFile(outputWavFile, finalShorts, sampleRate)
        SyncedDubAudioResult(wavFile = outputWavFile, syncedSegments = syncedSegments)
    }

    suspend fun synthesizePreviewWav(
        context: Context,
        text: String,
        voiceSelection: ActiveVoiceSelection,
        userSpeedMultiplier: Float,
        pitchFineTuneRatio: Float,
        outputWavFile: File
    ): File = withContext(Dispatchers.IO) {
        val sampleRate = BurmeseTtsService.OUTPUT_SAMPLE_RATE
        val pitch = (voiceSelection.pitchShiftRatio * pitchFineTuneRatio).coerceIn(0.84f, 1.18f)
        val speed = (voiceSelection.defaultSpeed * userSpeedMultiplier).coerceIn(0.82f, 1.28f)
        val tts = initTextToSpeech(context.applicationContext, pitch, speed)
        val tmpFile = File(context.cacheDir, "local_preview_${UUID.randomUUID().toString().take(6)}.wav")

        val pcm = try {
            val ok = if (tts != null) synthesizeUtteranceToFile(tts, text, tmpFile) else false
            if (ok && tmpFile.exists() && tmpFile.length() > 44L) {
                readWavAs24kMono(tmpFile, sampleRate)?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } finally {
            runCatching {
                tts?.stop()
                tts?.shutdown()
                tmpFile.delete()
            }
        } ?: generateSoftNarrationCuePcm(1400L, sampleRate, pitch)

        BurmeseTtsService.fadeEdges(pcm, min(240, pcm.size / 8))
        BurmeseTtsService.writeMonoWavFile(outputWavFile, pcm, sampleRate)
        outputWavFile
    }

    private suspend fun initTextToSpeech(
        context: Context,
        pitch: Float,
        speed: Float
    ): TextToSpeech? = withTimeoutOrNull(4_500L) {
        suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context) { status ->
                if (!cont.isActive) {
                    runCatching { engine?.shutdown() }
                    return@TextToSpeech
                }
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "Android TextToSpeech init returned status=$status; using PCM cue fallback.")
                    runCatching { engine?.shutdown() }
                    cont.resume(null)
                    return@TextToSpeech
                }
                val instance = engine
                if (instance != null) {
                    runCatching {
                        val burmeseVoice = instance.voices?.firstOrNull { voice ->
                            voice.locale?.language.equals("my", ignoreCase = true)
                        }
                        if (burmeseVoice != null) {
                            instance.voice = burmeseVoice
                        } else {
                            val myMm = Locale.Builder().setLanguage("my").setRegion("MM").build()
                            val r1 = instance.setLanguage(myMm)
                            if (r1 == TextToSpeech.LANG_MISSING_DATA || r1 == TextToSpeech.LANG_NOT_SUPPORTED) {
                                val myOnly = Locale.Builder().setLanguage("my").build()
                                val r2 = instance.setLanguage(myOnly)
                                if (r2 == TextToSpeech.LANG_MISSING_DATA || r2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                                    val defResult = instance.setLanguage(Locale.getDefault())
                                    if (defResult == TextToSpeech.LANG_MISSING_DATA || defResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                                        instance.setLanguage(Locale.US)
                                    }
                                }
                            }
                        }
                        instance.setPitch(pitch.coerceIn(0.75f, 1.35f))
                        instance.setSpeechRate(speed.coerceIn(0.75f, 1.35f))
                    }
                }
                cont.resume(instance)
            }
            cont.invokeOnCancellation {
                runCatching { engine?.shutdown() }
            }
        }
    }

    private suspend fun synthesizeUtteranceToFile(
        tts: TextToSpeech,
        text: String,
        targetFile: File
    ): Boolean = withTimeoutOrNull(8_000L) {
        suspendCancellableCoroutine { cont ->
            val utteranceId = "utt_${UUID.randomUUID().toString().take(8)}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(false)
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(false)
                    }
                }
            })
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val code = tts.synthesizeToFile(text, params, targetFile, utteranceId)
            if (code != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume(false)
            }
        }
    } ?: false

    internal fun readWavAs24kMono(file: File, targetRate: Int): ShortArray? {
        return runCatching {
            val bytes = FileInputStream(file).use { it.readBytes() }
            if (bytes.size <= 44) return@runCatching null
            var offset = 12
            var channels = 1
            var sampleRate = targetRate
            var bitsPerSample = 16
            var dataStart = -1
            var dataSize = 0

            while (offset + 8 <= bytes.size) {
                val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .coerceAtLeast(0)
                val bodyStart = offset + 8
                if (chunkId == "fmt " && bodyStart + 16 <= bytes.size) {
                    val fmtBuf = ByteBuffer.wrap(bytes, bodyStart, 16).order(ByteOrder.LITTLE_ENDIAN)
                    fmtBuf.short // audioFormat
                    channels = fmtBuf.short.toInt().coerceIn(1, 2)
                    sampleRate = fmtBuf.int.coerceIn(8000, 96000)
                    fmtBuf.int // byteRate
                    fmtBuf.short // blockAlign
                    bitsPerSample = fmtBuf.short.toInt()
                } else if (chunkId == "data") {
                    dataStart = bodyStart
                    dataSize = chunkSize.coerceAtMost(bytes.size - bodyStart)
                    break
                }
                val padded = if (chunkSize % 2 == 1) chunkSize + 1 else chunkSize
                offset = bodyStart + padded
            }

            if (dataStart < 0 || dataSize < 2 || bitsPerSample != 16) return@runCatching null
            val frameCount = dataSize / (2 * channels)
            val rawBuf = ByteBuffer.wrap(bytes, dataStart, dataSize).order(ByteOrder.LITTLE_ENDIAN)
            val mono = ShortArray(frameCount) {
                if (channels == 1) {
                    rawBuf.short
                } else {
                    val l = rawBuf.short.toInt()
                    val r = rawBuf.short.toInt()
                    ((l + r) / 2).toShort()
                }
            }
            if (sampleRate != targetRate) {
                BurmeseTtsService.resampleAudioLinear(mono, sampleRate, targetRate)
            } else {
                mono
            }
        }.getOrNull()
    }

    internal fun generateSoftNarrationCuePcm(
        durationMs: Long,
        sampleRate: Int,
        pitchFactor: Float
    ): ShortArray {
        val clampedMs = durationMs.coerceIn(450L, 1400L)
        val count = ((clampedMs * sampleRate) / 1000L).toInt().coerceAtLeast(480)
        val baseFreq = (210f * pitchFactor).coerceIn(140f, 340f)
        return ShortArray(count) { i ->
            val t = i.toFloat() / sampleRate.toFloat()
            val progress = i.toFloat() / count.toFloat()
            val env = sin(PI * progress).toFloat()
            val signal = 0.55f * sin(2.0 * PI * baseFreq * t).toFloat() +
                0.25f * sin(2.0 * PI * (baseFreq * 1.5f) * t).toFloat()
            (signal * env * 2200f).roundToInt().toShort()
        }
    }
}
