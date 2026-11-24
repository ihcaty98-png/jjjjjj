package com.example.ai

import android.util.Log
import com.example.data.ApiKeyStore
import com.example.firebase.ApiKeyRequiredException
import com.example.firebase.GeminiResilienceHelper
import com.example.firebase.hasHarnessDebugToken
import com.example.model.ActiveVoiceSelection
import com.example.model.DubSegment
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "BurmeseTtsService"

data class SyncedDubAudioResult(
    val wavFile: File,
    val syncedSegments: List<DubSegment>
)

class BurmeseTtsService {

    companion object {
        const val OUTPUT_SAMPLE_RATE = 24_000
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val MAX_AUDITION_CACHE_ENTRIES = 24
        const val DEFAULT_PREVIEW_LINE = "မင်္ဂလာပါ၊ ဒီအသံနဲ့ ဗီဒီယိုကို မြန်မာဘာသာပြန် အသံထပ်ပေးမှာ ဖြစ်ပါတယ်။"

        private val auditionCache = object : LinkedHashMap<String, ShortArray>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShortArray>?): Boolean {
                return size > MAX_AUDITION_CACHE_ENTRIES
            }
        }
        private val auditionCacheLock = Any()

        /**
         * Groups subtitle segments into balanced, compact TTS batches (max 6 lines / 420 chars per request)
         * so Gemini TTS never truncates trailing lines or loses syllable boundary alignment.
         */
        internal fun groupSegmentsIntoBatches(segments: List<DubSegment>): List<List<DubSegment>> {
            if (segments.isEmpty()) return emptyList()
            val batches = mutableListOf<List<DubSegment>>()
            var current = mutableListOf<DubSegment>()
            var currentChars = 0
            for (seg in segments) {
                val len = seg.burmeseText.trim().length
                val wouldExceed = current.isNotEmpty() &&
                    (current.size >= 6 || currentChars + len > 420)
                if (wouldExceed) {
                    batches.add(current)
                    current = mutableListOf()
                    currentChars = 0
                }
                current.add(seg)
                currentChars += len
            }
            if (current.isNotEmpty()) {
                batches.add(current)
            }
            return batches
        }

        internal fun buildBatchTtsPrompt(batch: List<DubSegment>): String {
            return batch.joinToString(separator = "\n\n") { seg ->
                val clean = seg.burmeseText.trim()
                if (clean.isEmpty()) {
                    "။"
                } else if (clean.last() in charArrayOf('။', '?', '？', '!', '！', '.')) {
                    clean
                } else {
                    "$clean။"
                }
            }
        }

        internal fun estimateSpeechWeight(text: String): Float {
            val clean = text.trim()
            if (clean.isEmpty()) return 3f
            var weight = 0f
            for (ch in clean) {
                weight += when {
                    ch in '\u1000'..'\u102A' -> 1.15f // Burmese base consonants & independent vowels
                    ch.isLetterOrDigit() -> 0.85f
                    ch in charArrayOf('။', '?', '!', '.') -> 1.4f
                    ch == '၊' || ch == ',' -> 0.8f
                    else -> 0.25f
                }
            }
            return weight.coerceAtLeast(3f)
        }

        /**
         * Splits a synthesized multi-line TTS PCM buffer into exact per-segment audio slices by locking
         * each boundary onto the lowest RMS energy valley (breath pause) nearest its Burmese syllable weight.
         */
        internal fun splitBatchAudioBySilenceValleys(
            pcm: ShortArray,
            batch: List<DubSegment>,
            sampleRate: Int = OUTPUT_SAMPLE_RATE
        ): List<ShortArray> {
            if (batch.isEmpty()) return emptyList()
            val trimmed = trimSilenceInternal(pcm, sampleRate)
            if (batch.size == 1) return listOf(trimmed)
            if (trimmed.isEmpty()) return List(batch.size) { ShortArray(0) }

            val weights = batch.map { estimateSpeechWeight(it.burmeseText) }
            val totalWeight = weights.sum().coerceAtLeast(1f)
            val cumulativeWeights = FloatArray(batch.size)
            var running = 0f
            for (i in weights.indices) {
                running += weights[i]
                cumulativeWeights[i] = running
            }

            // Compute RMS energy envelope with 20ms window and 10ms hop
            val hop = (sampleRate / 100).coerceAtLeast(32)
            val win = (sampleRate / 50).coerceAtLeast(64)
            val frameCount = ((trimmed.size - win).coerceAtLeast(0) / hop) + 1
            val frameRms = FloatArray(frameCount.coerceAtLeast(1))
            for (f in frameRms.indices) {
                val start = f * hop
                val end = (start + win).coerceAtMost(trimmed.size)
                var sumSq = 0.0
                for (i in start until end) {
                    val v = trimmed[i].toDouble()
                    sumSq += v * v
                }
                val n = (end - start).coerceAtLeast(1)
                frameRms[f] = sqrt(sumSq / n).toFloat()
            }

            val boundaries = IntArray(batch.size + 1)
            boundaries[0] = 0
            boundaries[batch.size] = trimmed.size

            val avgSliceSamples = trimmed.size.toFloat() / batch.size.toFloat()
            val minSliceSamples = (sampleRate * 0.22f).toInt()
                .coerceAtMost((avgSliceSamples * 0.45f).toInt())
                .coerceAtLeast(1)
            val searchRadius = (avgSliceSamples * 0.45f).toInt()
                .coerceIn((sampleRate * 0.25f).toInt(), (sampleRate * 2.2f).toInt())

            var prevSplit = 0
            for (k in 1 until batch.size) {
                val targetSample = ((cumulativeWeights[k - 1] / totalWeight) * trimmed.size).roundToInt()
                val remainingCount = batch.size - k
                val minBound = (prevSplit + minSliceSamples).coerceAtMost(trimmed.size - remainingCount)
                val maxBound = (trimmed.size - remainingCount * minSliceSamples).coerceAtLeast(minBound)

                val searchStart = (targetSample - searchRadius).coerceIn(minBound, maxBound)
                val searchEnd = (targetSample + searchRadius).coerceIn(minBound, maxBound)

                val startFrame = (searchStart / hop).coerceIn(0, frameRms.lastIndex)
                val endFrame = (searchEnd / hop).coerceIn(startFrame, frameRms.lastIndex)

                var bestFrame = ((targetSample / hop).coerceIn(startFrame, endFrame))
                var bestScore = Float.MAX_VALUE
                for (f in startFrame..endFrame) {
                    val frameCenterSample = (f * hop + win / 2).coerceIn(minBound, maxBound)
                    val distancePenalty = abs(frameCenterSample - targetSample).toFloat() /
                        searchRadius.coerceAtLeast(1).toFloat()
                    // Strongly favor true quiet valleys while staying close to expected syllable timing
                    val score = frameRms[f] * (1f + 0.35f * distancePenalty)
                    if (score < bestScore) {
                        bestScore = score
                        bestFrame = f
                    }
                }

                // Refine inside the chosen silence frame to the sample with lowest local amplitude (clean splice)
                val winStart = (bestFrame * hop).coerceIn(minBound, maxBound)
                val winEnd = (winStart + win).coerceIn(winStart, maxBound)
                var chosenSample = ((winStart + winEnd) / 2).coerceIn(minBound, maxBound)
                var minAmp = Int.MAX_VALUE
                for (s in winStart..winEnd) {
                    val a = abs(trimmed[s].toInt())
                    if (a < minAmp) {
                        minAmp = a
                        chosenSample = s
                    }
                }

                boundaries[k] = chosenSample
                prevSplit = chosenSample
            }

            return List(batch.size) { idx ->
                val sliceStart = boundaries[idx].coerceIn(0, trimmed.size)
                val sliceEnd = boundaries[idx + 1].coerceIn(sliceStart, trimmed.size)
                if (sliceEnd <= sliceStart) {
                    ShortArray(0)
                } else {
                    trimSilenceInternal(trimmed.copyOfRange(sliceStart, sliceEnd), sampleRate)
                }
            }
        }

        private fun trimSilenceInternal(
            samples: ShortArray,
            sampleRate: Int = OUTPUT_SAMPLE_RATE,
            threshold: Int = 200
        ): ShortArray {
            if (samples.isEmpty()) return ShortArray(0)
            var start = 0
            while (start < samples.size && abs(samples[start].toInt()) < threshold) {
                start++
            }
            var end = samples.size - 1
            while (end > start && abs(samples[end].toInt()) < threshold) {
                end--
            }
            if (start >= end) return ShortArray(0)
            val pad = (sampleRate * 0.025f).toInt()
            val paddedStart = (start - pad).coerceAtLeast(0)
            val paddedEnd = (end + pad).coerceAtMost(samples.size - 1)
            return samples.copyOfRange(paddedStart, paddedEnd + 1)
        }

        internal fun trimSilenceSamples(
            samples: ShortArray,
            sampleRate: Int = OUTPUT_SAMPLE_RATE
        ): ShortArray = trimSilenceInternal(samples, sampleRate)

        internal fun fitSamplesToSlot(
            samples: ShortArray,
            maxSlotSamples: Int
        ): ShortArray = fitToSlotInternal(samples, maxSlotSamples)

        internal fun fadeEdges(samples: ShortArray, fadeSamples: Int) {
            if (fadeSamples <= 1 || samples.size < fadeSamples * 2) return
            for (i in 0 until fadeSamples) {
                val gain = i.toFloat() / fadeSamples.toFloat()
                samples[i] = (samples[i] * gain).roundToInt().toShort()
                val tailIdx = samples.size - 1 - i
                samples[tailIdx] = (samples[tailIdx] * gain).roundToInt().toShort()
            }
        }

        internal fun resampleAudioLinear(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
            if (input.isEmpty() || srcRate == dstRate) return input
            val outLen = ((input.size.toLong() * dstRate) / srcRate).toInt().coerceAtLeast(1)
            return resampleToLengthInternal(input, outLen)
        }

        internal fun writeMonoWavFile(file: File, samples: ShortArray, sampleRate: Int) {
            file.parentFile?.mkdirs()
            val byteRate = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
            val dataSize = samples.size * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(CHANNELS.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort((CHANNELS * (BITS_PER_SAMPLE / 8)).toShort())
                putShort(BITS_PER_SAMPLE.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
            }.array()

            val pcmBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                pcmBuffer.putShort(s)
            }

            FileOutputStream(file).use { out ->
                out.write(header)
                out.write(pcmBuffer.array())
            }
        }

        private fun fitToSlotInternal(samples: ShortArray, maxSlotSamples: Int): ShortArray {
            if (maxSlotSamples <= 0 || samples.isEmpty()) return ShortArray(0)
            if (samples.size <= maxSlotSamples) return samples

            val ratio = samples.size.toFloat() / maxSlotSamples.toFloat()
            return if (ratio <= 1.65f) {
                timeStretchOlaInternal(samples, maxSlotSamples)
            } else {
                val maxAllowed = (maxSlotSamples * 1.65f).roundToInt().coerceAtMost(samples.size)
                timeStretchOlaInternal(samples.copyOfRange(0, maxAllowed), maxSlotSamples)
            }
        }

        private fun timeStretchOlaInternal(input: ShortArray, targetLength: Int): ShortArray {
            if (input.isEmpty() || targetLength <= 0) return ShortArray(0)
            if (input.size < 1024 || targetLength == input.size) {
                return resampleToLengthInternal(input, targetLength)
            }
            val winSize = 960
            val hopOut = winSize / 2
            val speedFactor = input.size.toFloat() / targetLength.toFloat()
            val hopIn = (hopOut * speedFactor)

            val window = FloatArray(winSize) { i ->
                (0.5f * (1f - cos(2.0 * PI * i / (winSize - 1))).toFloat())
            }
            val acc = FloatArray(targetLength + winSize)
            val norm = FloatArray(targetLength + winSize)

            var inPos = 0f
            var outPos = 0
            while (outPos < targetLength) {
                val iStart = inPos.roundToInt().coerceIn(0, (input.size - winSize).coerceAtLeast(0))
                for (k in 0 until winSize) {
                    val srcIdx = iStart + k
                    if (srcIdx < input.size && outPos + k < acc.size) {
                        val w = window[k]
                        acc[outPos + k] += input[srcIdx] * w
                        norm[outPos + k] += w
                    }
                }
                outPos += hopOut
                inPos += hopIn
                if (inPos >= input.size) break
            }

            return ShortArray(targetLength) { i ->
                val n = norm[i]
                val value = if (n > 1e-4f) acc[i] / n else 0f
                value.roundToInt().coerceIn(-32767, 32767).toShort()
            }
        }

        private fun resampleToLengthInternal(input: ShortArray, outLen: Int): ShortArray {
            if (input.isEmpty() || outLen <= 0) return ShortArray(0)
            if (input.size == outLen) return input.copyOf()
            val output = ShortArray(outLen)
            val scale = (input.size - 1).toFloat() / (outLen - 1).coerceAtLeast(1).toFloat()
            for (i in 0 until outLen) {
                val srcPos = i * scale
                val i0 = srcPos.toInt().coerceIn(0, input.size - 1)
                val i1 = (i0 + 1).coerceIn(0, input.size - 1)
                val frac = srcPos - i0
                val interpolated = input[i0] * (1f - frac) + input[i1] * frac
                output[i] = interpolated.roundToInt().coerceIn(-32767, 32767).toShort()
            }
            return output
        }
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun synthesizeTimelineWav(
        segments: List<DubSegment>,
        totalDurationMs: Long,
        voiceSelection: ActiveVoiceSelection,
        userSpeedMultiplier: Float = 1.0f,
        pitchFineTuneRatio: Float = 1.0f,
        voiceVolumeGain: Float = 1.0f,
        outputWavFile: File,
        onStatusUpdate: (String) -> Unit = {},
        onProgress: (completedCount: Int, totalCount: Int, currentLine: String) -> Unit
    ): SyncedDubAudioResult = withContext(Dispatchers.IO) {
        require(segments.isNotEmpty()) { "Subtitle segments cannot be empty." }

        val effectiveKey = ApiKeyStore.getEffectiveApiKey()
        if (effectiveKey == null && !hasHarnessDebugToken()) {
            throw ApiKeyRequiredException("No Gemini API key configured for cloud TTS; switching to client-side speech fallback.")
        }

        val firebaseModel: GenerativeModel? = if (effectiveKey == null) {
            Firebase.ai.generativeModel(
                modelName = "gemini-3.1-flash-tts-preview",
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                    speechConfig = SpeechConfig(voice = Voice(voiceSelection.baseVoiceName))
                }
            )
        } else {
            null
        }

        val totalSamples = ((totalDurationMs.coerceAtLeast(1000L) * OUTPUT_SAMPLE_RATE) / 1000L).toInt()
        val mixFloat = FloatArray(totalSamples)
        val combinedPitchRatio = (voiceSelection.pitchShiftRatio * pitchFineTuneRatio).coerceIn(0.84f, 1.18f)
        val combinedSpeed = (voiceSelection.defaultSpeed * userSpeedMultiplier).coerceIn(0.82f, 1.28f)

        val sorted = segments.sortedBy { it.startMs }
        val batches = groupSegmentsIntoBatches(sorted)
        val syncedSegments = mutableListOf<DubSegment>()
        var cursorEndSample = 0
        var globalIndex = 0

        batches.forEachIndexed { batchIndex, batch ->
            onProgress(globalIndex, sorted.size, batch.firstOrNull()?.burmeseText.orEmpty())
            if (batches.size > 1) {
                onStatusUpdate("မြန်မာအသံ အပိုင်း (${batchIndex + 1}/${batches.size}) ကို ဖန်တီးနေပါတယ်...")
            }

            val batchPrompt = buildBatchTtsPrompt(batch)
            val (rawBatchPcm, sourceRate) = synthesizeTtsWithResilience(
                directApiKey = effectiveKey,
                voiceName = voiceSelection.baseVoiceName,
                firebaseModel = firebaseModel,
                promptText = batchPrompt,
                timeoutMs = 75_000L,
                onStatusUpdate = onStatusUpdate
            )
            val resampledBatchPcm = if (sourceRate != OUTPUT_SAMPLE_RATE) {
                resampleLinear(rawBatchPcm, sourceRate, OUTPUT_SAMPLE_RATE)
            } else {
                rawBatchPcm
            }

            val slicedPerSegment = splitBatchAudioBySilenceValleys(
                pcm = resampledBatchPcm,
                batch = batch,
                sampleRate = OUTPUT_SAMPLE_RATE
            )

            val minAudibleSliceSamples = (OUTPUT_SAMPLE_RATE * 0.16f).toInt()
            batch.forEachIndexed { localIdx, segment ->
                var processed = slicedPerSegment.getOrElse(localIdx) { ShortArray(0) }
                if (processed.size < minAudibleSliceSamples && segment.burmeseText.isNotBlank()) {
                    val singlePrompt = buildBatchTtsPrompt(listOf(segment))
                    val recovered = runCatching {
                        val (rawSingle, singleRate) = synthesizeTtsWithResilience(
                            directApiKey = effectiveKey,
                            voiceName = voiceSelection.baseVoiceName,
                            firebaseModel = firebaseModel,
                            promptText = singlePrompt,
                            timeoutMs = 35_000L,
                            onStatusUpdate = {}
                        )
                        val resampledSingle = if (singleRate != OUTPUT_SAMPLE_RATE) {
                            resampleLinear(rawSingle, singleRate, OUTPUT_SAMPLE_RATE)
                        } else {
                            rawSingle
                        }
                        trimSilenceInternal(resampledSingle, OUTPUT_SAMPLE_RATE)
                    }.getOrNull()
                    if (recovered != null && recovered.size > processed.size) {
                        processed = recovered
                    }
                }

                if (processed.isNotEmpty()) {
                    if (abs(combinedPitchRatio - 1.0f) > 0.015f) {
                        processed = shiftPitchPreserveDuration(processed, combinedPitchRatio)
                    }
                    if (abs(combinedSpeed - 1.0f) > 0.02f) {
                        val targetLen = (processed.size / combinedSpeed).roundToInt().coerceAtLeast(480)
                        processed = timeStretchOla(processed, targetLen)
                    }

                    val desiredStartSample = ((segment.startMs * OUTPUT_SAMPLE_RATE) / 1000L).toInt()
                    val startSample = desiredStartSample
                        .coerceAtLeast(if (cursorEndSample == 0) 0 else cursorEndSample + (OUTPUT_SAMPLE_RATE * 0.03f).toInt())
                        .coerceIn(0, (totalSamples - 480).coerceAtLeast(0))

                    val currentStartMs = (startSample * 1000L) / OUTPUT_SAMPLE_RATE
                    val remainingBeatsAfter = (sorted.size - 1 - globalIndex).coerceAtLeast(0)
                    val reserveTailMs = remainingBeatsAfter * 650L
                    val nextPlannedStartMs = sorted.getOrNull(globalIndex + 1)?.startMs ?: totalDurationMs
                    val generousRoomMs = maxOf(
                        nextPlannedStartMs - currentStartMs,
                        segment.durationMs
                    ).coerceAtMost((totalDurationMs - currentStartMs - reserveTailMs).coerceAtLeast(segment.durationMs))
                        .coerceAtMost(totalDurationMs - currentStartMs)
                    val maxSlotSamples = ((generousRoomMs * OUTPUT_SAMPLE_RATE) / 1000L)
                        .toInt()
                        .coerceAtMost(totalSamples - startSample)
                        .coerceAtLeast(0)

                    val fitted = fitToSlotPreservingPitch(processed, maxSlotSamples)
                    if (fitted.isNotEmpty()) {
                        applyEdgeFade(fitted, fadeSamples = min(320, fitted.size / 6))
                        for (i in fitted.indices) {
                            val dst = startSample + i
                            if (dst < mixFloat.size) {
                                val amp = (fitted[i] / 32768f) * voiceVolumeGain
                                mixFloat[dst] = (mixFloat[dst] + amp).coerceIn(-0.98f, 0.98f)
                            }
                        }

                        val actualStartMs = (startSample * 1000L) / OUTPUT_SAMPLE_RATE
                        val actualEndMs = (((startSample + fitted.size) * 1000L) / OUTPUT_SAMPLE_RATE)
                            .coerceAtLeast(actualStartMs + 500L)
                            .coerceAtMost(totalDurationMs)
                        cursorEndSample = startSample + fitted.size
                        syncedSegments.add(
                            segment.copy(
                                startMs = actualStartMs,
                                endMs = actualEndMs
                            )
                        )
                    } else {
                        syncedSegments.add(segment)
                    }
                } else {
                    syncedSegments.add(segment)
                }

                globalIndex++
                onProgress(globalIndex, sorted.size, segment.burmeseText)
            }
        }

        // Smooth short fade at clip end to avoid click artifacts
        val endFade = min(240, mixFloat.size / 12)
        for (i in 0 until endFade) {
            val idx = mixFloat.size - 1 - i
            mixFloat[idx] *= (i.toFloat() / endFade.coerceAtLeast(1).toFloat())
        }

        val finalShorts = ShortArray(totalSamples) { idx ->
            (mixFloat[idx] * 32767f).roundToInt().coerceIn(-32767, 32767).toShort()
        }
        writeWavFile(outputWavFile, finalShorts, OUTPUT_SAMPLE_RATE)
        SyncedDubAudioResult(wavFile = outputWavFile, syncedSegments = syncedSegments)
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun synthesizeVoicePreviewWav(
        voiceSelection: ActiveVoiceSelection,
        userSpeedMultiplier: Float = 1.0f,
        pitchFineTuneRatio: Float = 1.0f,
        text: String = DEFAULT_PREVIEW_LINE,
        outputWavFile: File,
        onStatusUpdate: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        val cacheKey = "${voiceSelection.baseVoiceName}|$cleanText"
        val cachedPcm = synchronized(auditionCacheLock) { auditionCache[cacheKey]?.copyOf() }

        var processed = if (cachedPcm != null) {
            cachedPcm
        } else {
            val effectiveKey = ApiKeyStore.getEffectiveApiKey()
            if (effectiveKey == null && !hasHarnessDebugToken()) {
                throw ApiKeyRequiredException("No Gemini API key configured for cloud TTS audition; using client-side speech preview.")
            }

            val firebaseModel: GenerativeModel? = if (effectiveKey == null) {
                Firebase.ai.generativeModel(
                    modelName = "gemini-3.1-flash-tts-preview",
                    generationConfig = generationConfig {
                        responseModalities = listOf(ResponseModality.AUDIO)
                        speechConfig = SpeechConfig(voice = Voice(voiceSelection.baseVoiceName))
                    }
                )
            } else {
                null
            }
            val (pcmShorts, sourceRate) = synthesizeTtsWithResilience(
                directApiKey = effectiveKey,
                voiceName = voiceSelection.baseVoiceName,
                firebaseModel = firebaseModel,
                promptText = cleanText,
                timeoutMs = 45_000L,
                onStatusUpdate = onStatusUpdate
            )
            val normalized = trimSilenceInternal(
                if (sourceRate != OUTPUT_SAMPLE_RATE) {
                    resampleLinear(pcmShorts, sourceRate, OUTPUT_SAMPLE_RATE)
                } else {
                    pcmShorts
                },
                OUTPUT_SAMPLE_RATE
            )
            if (normalized.isNotEmpty()) {
                synchronized(auditionCacheLock) {
                    auditionCache[cacheKey] = normalized.copyOf()
                }
            }
            normalized
        }

        val combinedPitchRatio = (voiceSelection.pitchShiftRatio * pitchFineTuneRatio).coerceIn(0.84f, 1.18f)
        val combinedSpeed = (voiceSelection.defaultSpeed * userSpeedMultiplier).coerceIn(0.82f, 1.28f)
        if (abs(combinedPitchRatio - 1.0f) > 0.015f) {
            processed = shiftPitchPreserveDuration(processed, combinedPitchRatio)
        }
        if (abs(combinedSpeed - 1.0f) > 0.02f) {
            val targetLen = (processed.size / combinedSpeed).roundToInt().coerceAtLeast(480)
            processed = timeStretchOla(processed, targetLen)
        }
        applyEdgeFade(processed, fadeSamples = min(240, processed.size / 8))
        writeWavFile(outputWavFile, processed, OUTPUT_SAMPLE_RATE)
        outputWavFile
    }

    private suspend fun synthesizeTtsWithResilience(
        directApiKey: String?,
        voiceName: String,
        firebaseModel: GenerativeModel?,
        promptText: String,
        timeoutMs: Long,
        onStatusUpdate: (String) -> Unit
    ): Pair<ShortArray, Int> {
        val cleanText = promptText.trim()
        val ttsModelCandidates = listOf(
            "gemini-3.1-flash-tts-preview",
            "gemini-3.1-flash-tts",
            "gemini-2.5-flash-preview-tts"
        )
        return GeminiResilienceHelper.executeWithResilience(
            maxAttempts = 4,
            timeoutMs = timeoutMs,
            useDirectApiKey = directApiKey != null,
            onStatusUpdate = onStatusUpdate
        ) { attemptIndex ->
            if (directApiKey != null) {
                val modelName = ttsModelCandidates[attemptIndex.coerceAtMost(ttsModelCandidates.lastIndex)]
                val directAudio = DirectGeminiClient.generateSpeechAudio(
                    apiKey = directApiKey,
                    modelName = modelName,
                    promptText = cleanText,
                    voiceName = voiceName
                )
                return@executeWithResilience decodeAudioBytesToPcm(
                    audioBytes = directAudio.audioBytes,
                    mimeType = directAudio.mimeType
                )
            }

            val model = requireNotNull(firebaseModel) { "TTS model unavailable." }
            val response = model.generateContent(cleanText)
            val inlinePart = response.inlineDataParts.firstOrNull()
                ?: response.candidates.firstOrNull()?.content?.parts
                    ?.filterIsInstance<InlineDataPart>()
                    ?.firstOrNull()
                ?: throw IllegalStateException("Gemini TTS returned no audio bytes.")

            decodeAudioBytesToPcm(
                audioBytes = inlinePart.inlineData,
                mimeType = inlinePart.mimeType
            )
        }
    }

    private fun decodeAudioBytesToPcm(audioBytes: ByteArray, mimeType: String): Pair<ShortArray, Int> {
        if (audioBytes.size < 2) {
            throw IllegalStateException("Gemini TTS returned empty audio buffer.")
        }
        // Strip RIFF/WAV header if the REST endpoint returned WAV bytes
        if (audioBytes.size > 44 &&
            audioBytes[0] == 'R'.code.toByte() &&
            audioBytes[1] == 'I'.code.toByte() &&
            audioBytes[2] == 'F'.code.toByte() &&
            audioBytes[3] == 'F'.code.toByte()
        ) {
            val wavSampleRate = ByteBuffer.wrap(audioBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                .takeIf { it in 8000..96000 } ?: OUTPUT_SAMPLE_RATE
            val pcmOnly = audioBytes.copyOfRange(44, audioBytes.size)
            return Pair(bytesToShortsLittleEndian(pcmOnly), wavSampleRate)
        }
        val sampleRate = parseSampleRate(mimeType)
        return Pair(bytesToShortsLittleEndian(audioBytes), sampleRate)
    }

    private fun parseSampleRate(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType.lowercase())
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 8000..96000 }
            ?: OUTPUT_SAMPLE_RATE
    }

    private fun bytesToShortsLittleEndian(bytes: ByteArray): ShortArray {
        val sampleCount = bytes.size / 2
        val shorts = ShortArray(sampleCount)
        ByteBuffer.wrap(bytes, 0, sampleCount * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shorts)
        return shorts
    }

    private fun fitToSlotPreservingPitch(samples: ShortArray, maxSlotSamples: Int): ShortArray {
        if (maxSlotSamples <= 0 || samples.isEmpty()) return ShortArray(0)
        if (samples.size <= maxSlotSamples) return samples

        val ratio = samples.size.toFloat() / maxSlotSamples.toFloat()
        return if (ratio <= 1.65f) {
            timeStretchOla(samples, maxSlotSamples)
        } else {
            val maxAllowed = (maxSlotSamples * 1.65f).roundToInt().coerceAtMost(samples.size)
            timeStretchOla(samples.copyOfRange(0, maxAllowed), maxSlotSamples)
        }
    }

    private fun shiftPitchPreserveDuration(input: ShortArray, pitchRatio: Float): ShortArray {
        if (input.size < 1024 || abs(pitchRatio - 1.0f) < 0.015f) return input
        val targetLen = input.size
        val shifted = resampleToLength(input, (input.size / pitchRatio).roundToInt().coerceAtLeast(512))
        return timeStretchOla(shifted, targetLen)
    }

    private fun timeStretchOla(input: ShortArray, targetLength: Int): ShortArray {
        if (input.isEmpty() || targetLength <= 0) return ShortArray(0)
        if (input.size < 1024 || targetLength == input.size) {
            return resampleToLength(input, targetLength)
        }
        val winSize = 960 // 40ms @ 24kHz
        val hopOut = winSize / 2
        val speedFactor = input.size.toFloat() / targetLength.toFloat()
        val hopIn = (hopOut * speedFactor)

        val window = FloatArray(winSize) { i ->
            (0.5f * (1f - cos(2.0 * PI * i / (winSize - 1))).toFloat())
        }
        val acc = FloatArray(targetLength + winSize)
        val norm = FloatArray(targetLength + winSize)

        var inPos = 0f
        var outPos = 0
        while (outPos < targetLength) {
            val iStart = inPos.roundToInt().coerceIn(0, (input.size - winSize).coerceAtLeast(0))
            for (k in 0 until winSize) {
                val srcIdx = iStart + k
                if (srcIdx < input.size && outPos + k < acc.size) {
                    val w = window[k]
                    acc[outPos + k] += input[srcIdx] * w
                    norm[outPos + k] += w
                }
            }
            outPos += hopOut
            inPos += hopIn
            if (inPos >= input.size) break
        }

        return ShortArray(targetLength) { i ->
            val n = norm[i]
            val value = if (n > 1e-4f) acc[i] / n else 0f
            value.roundToInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun resampleLinear(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate == dstRate) return input
        val outLen = ((input.size.toLong() * dstRate) / srcRate).toInt().coerceAtLeast(1)
        return resampleToLength(input, outLen)
    }

    private fun resampleToLength(input: ShortArray, outLen: Int): ShortArray {
        if (input.isEmpty() || outLen <= 0) return ShortArray(0)
        if (input.size == outLen) return input.copyOf()
        val output = ShortArray(outLen)
        val scale = (input.size - 1).toFloat() / (outLen - 1).coerceAtLeast(1).toFloat()
        for (i in 0 until outLen) {
            val srcPos = i * scale
            val i0 = srcPos.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceIn(0, input.size - 1)
            val frac = srcPos - i0
            val interpolated = input[i0] * (1f - frac) + input[i1] * frac
            output[i] = interpolated.roundToInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    private fun applyEdgeFade(samples: ShortArray, fadeSamples: Int) {
        if (fadeSamples <= 1 || samples.size < fadeSamples * 2) return
        for (i in 0 until fadeSamples) {
            val gain = i.toFloat() / fadeSamples.toFloat()
            samples[i] = (samples[i] * gain).roundToInt().toShort()
            val tailIdx = samples.size - 1 - i
            samples[tailIdx] = (samples[tailIdx] * gain).roundToInt().toShort()
        }
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        val byteRate = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(CHANNELS.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((CHANNELS * (BITS_PER_SAMPLE / 8)).toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        val pcmBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            pcmBuffer.putShort(s)
        }

        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcmBuffer.array())
        }
    }
}
