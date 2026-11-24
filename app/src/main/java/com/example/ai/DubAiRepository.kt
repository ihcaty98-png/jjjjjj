package com.example.ai

import com.example.data.ApiKeyStore
import com.example.firebase.ApiKeyRequiredException
import com.example.firebase.GeminiResilienceHelper
import com.example.firebase.hasHarnessDebugToken
import com.example.media.PreparedDubInput
import com.example.model.DubNarrationStyle
import com.example.model.DubSegment
import com.example.model.DubTranslationPlan
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DubAiRepository {

    private val directModelCandidates = listOf(
        "gemini-3.8-flash",
        "gemini-2.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash-lite"
    )

    private val dubPlanSchema = Schema.obj(
        properties = mapOf(
            "detectedLanguage" to Schema.string(
                description = "Primary language spoken in the video, e.g., English, Chinese, Thai, or Silent/Visual."
            ),
            "videoSummaryBurmese" to Schema.string(
                description = "1-2 short sentences summarizing the clip in conversational Burmese."
            ),
            "segments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startSeconds" to Schema.double(
                            description = "Exact start time in seconds matching when this short utterance begins."
                        ),
                        "endSeconds" to Schema.double(
                            description = "Exact end time in seconds matching when this short utterance ends (1.4s to 3.8s per subtitle chunk)."
                        ),
                        "originalSpeech" to Schema.string(
                            description = "Short original spoken phrase (or concise visual action if silent)."
                        ),
                        "burmeseSubtitle" to Schema.string(
                            description = "Tight, crisp conversational Burmese translation/narration (စကားပြောဟန်), strictly pure Burmese (no English words), 6 to 14 words max so it syncs cleanly with continuous voiceover."
                        )
                    )
                ),
                description = "Chronological short, bite-sized dialogue and narration beats covering the ENTIRE video from 0.0s right through the final second."
            )
        )
    )

    private fun dubPlanSchemaJson(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put(
            "properties",
            JSONObject()
                .put("detectedLanguage", JSONObject().put("type", "STRING"))
                .put("videoSummaryBurmese", JSONObject().put("type", "STRING"))
                .put(
                    "segments",
                    JSONObject()
                        .put("type", "ARRAY")
                        .put(
                            "items",
                            JSONObject()
                                .put("type", "OBJECT")
                                .put(
                                    "properties",
                                    JSONObject()
                                        .put("startSeconds", JSONObject().put("type", "NUMBER"))
                                        .put("endSeconds", JSONObject().put("type", "NUMBER"))
                                        .put("originalSpeech", JSONObject().put("type", "STRING"))
                                        .put("burmeseSubtitle", JSONObject().put("type", "STRING"))
                                )
                                .put(
                                    "required",
                                    JSONArray()
                                        .put("startSeconds")
                                        .put("endSeconds")
                                        .put("originalSpeech")
                                        .put("burmeseSubtitle")
                                )
                        )
                )
        )
        put(
            "required",
            JSONArray()
                .put("detectedLanguage")
                .put("videoSummaryBurmese")
                .put("segments")
        )
    }

    private val systemPrompt = """
        သင်သည် YouTube နှင့် TikTok တွင် လူကြိုက်များသော ပရော်ဖက်ရှင်နယ် အင်္ဂလိပ်-မြန်မာ ရုပ်ရှင်အသံထပ်နှင့် စာတန်းထိုး ပညာရှင် (Pro Movie Dub & Continuous Narrator) ဖြစ်ပါသည်။
        ပေးထားသော ဗီဒီယို၏ အစ 00:00 မှ ဗီဒီယို အဆုံးသတ်စက္ကန့်အထိ တစ်စက္ကန့်မျှ တဝက်တပြက် မရပ်တန့်ဘဲ မြန်မာစကားပြောဟန် အသံထပ်နှင့် စာတန်းထိုးများကို အပြည့်အဝ ဖန်တီးပေးရပါမည်။

        မဖြစ်မနေ လိုက်နာရမည့် စည်းကမ်းချက်များ -
        ၁။ ဗီဒီယိုအစမှ အဆုံးထိ မရပ်တမ်း ပြောပြပါ (Full-Duration Continuous Coverage) -
           - စကားပြောနေသော အပိုင်းများကို တိကျစွာ ဘာသာပြန်ပေးပြီး စကားပြောတိတ်သွားသော အခန်းများ (သို့မဟုတ်) Action / Silent အခန်းများတွင်လည်း အသံမပြတ်စေရန် ဇာတ်ဝင်ခန်းကို စိတ်ဝင်စားဖွယ် မြန်မာနောက်ခံစကားပြော (Storyteller Narration) ဖြင့် ဆက်တိုက်ဖြည့်စွက်ပါ။
           - ဗီဒီယို တစ်ဝက်မှာ လုံးဝ မရပ်ပါနှင့်။ နောက်ဆုံး segment ၏ endSeconds သည် ဗီဒီယို အဆုံးနားအထိ ရောက်ရှိနေရမည်။
        ၂။ ၁၀၀% မြန်မာစကားသက်သက်သာ (Pure Conversational Burmese) -
           - burmeseSubtitle ထဲတွင် အင်္ဂလိပ်စာလုံး မပါစေရဘဲ သဘာဝကျသော မြန်မာစကားပြောဟန် ("မင်းဘယ်သွားမလို့လဲ", "အဲ့ဒီအချိန်မှာပဲ မထင်မှတ်ထားတာ ဖြစ်သွားတယ်", "နောက်ဆုံးမှာ ဘာဆက်ဖြစ်မလဲ ကြည့်လိုက်ရအောင်") ဖြင့်သာ ရေးပါ။
        ၃။ စာတန်းအရှည်ကြီး လုံးဝ မရေးရ -
           - စာတန်းတစ်ခုစီသည် ၁-၂ ကြောင်းစာ (မြန်မာစကားလုံး ၆ လုံးမှ ၁၃ လုံးခန့်၊ ၁.၆ စက္ကန့်မှ ၃.၆ စက္ကန့်အတွင်း) သာ ကျစ်ကျစ်လျစ်လျစ် ဖြတ်တောက်ပါ။
        ၄။ အချိန်ကိုက် ကွက်တိ (Exact Sync) - startSeconds < endSeconds ဖြစ်ရမည်။ တစ်ခုနှင့်တစ်ခု မထပ်စေရ။

        Return ONLY valid JSON matching the schema.
    """.trimIndent()

    private val primaryModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = dubPlanSchema
                temperature = 0.4f
                maxOutputTokens = 8192
            },
            systemInstruction = content { text(systemPrompt) }
        )
    }

    private val fallbackModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = dubPlanSchema
                temperature = 0.4f
                maxOutputTokens = 8192
            },
            systemInstruction = content { text(systemPrompt) }
        )
    }

    suspend fun translateVideoForDubbing(
        input: PreparedDubInput,
        clipTitleHint: String,
        conciseSubtitles: Boolean = true,
        continuousFullVideo: Boolean = true,
        narrationStyle: DubNarrationStyle = DubNarrationStyle.STORYTELLER,
        onStatusUpdate: (String) -> Unit = {}
    ): DubTranslationPlan {
        val totalSeconds = (input.durationMs / 1000.0).coerceAtLeast(1.0)
        val estimatedBeats = ((totalSeconds / 3.0).toInt()).coerceIn(2, 60)
        val promptInstructions = buildString {
            appendLine("Listen carefully to the spoken dialogue AND analyze every visual scene across the ENTIRE video clip.")
            appendLine("Total video duration: ${String.format("%.1f", totalSeconds)} seconds (${input.durationLabel}).")
            appendLine("Clip title hint: $clipTitleHint")
            appendLine("NARRATION STYLE: ${narrationStyle.displayName} — ${narrationStyle.promptDirective}")
            if (continuousFullVideo) {
                appendLine("MANDATORY FULL-VIDEO CONTINUITY:")
                appendLine("- Cover the COMPLETE timeline from 0.0s all the way to ${String.format("%.1f", totalSeconds)}s (target ~$estimatedBeats sequential beats).")
                appendLine("- Do NOT stop halfway or leave dead silence gaps longer than 0.6s. Whenever spoken dialogue pauses or ends early, continue narrating the visual action, character emotions, and suspense in natural conversational Burmese until the final second.")
                appendLine("- The final segment MUST end near ${String.format("%.1f", totalSeconds)}s.")
            }
            if (conciseSubtitles) {
                appendLine("STRICT SUBTITLE RULE: Split long sentences into crisp 1.6s-3.5s beats with SHORT, punchy pure Burmese (max ~50 characters / 6-13 words per segment) so subtitles look clean and fit the spoken audio tightly without cutting off words.")
            }
            appendLine("Ensure startSeconds < endSeconds, zero overlaps, and every timestamp stays strictly within [0.0, ${String.format("%.1f", totalSeconds)}].")
        }

        val initialPlan = executePlanRequest(
            input = input,
            promptInstructions = promptInstructions,
            totalDurationMs = input.durationMs,
            conciseSubtitles = conciseSubtitles,
            onStatusUpdate = onStatusUpdate
        )

        val extendedPlan = if (continuousFullVideo && input.durationMs > 4_500L) {
            continueDubPlanIfTruncated(
                initialPlan = initialPlan,
                input = input,
                clipTitleHint = clipTitleHint,
                conciseSubtitles = conciseSubtitles,
                narrationStyle = narrationStyle,
                onStatusUpdate = onStatusUpdate
            )
        } else {
            initialPlan
        }

        return if (continuousFullVideo) {
            val completeSegments = ensureContinuousCoverage(
                segments = extendedPlan.segments,
                totalDurationMs = input.durationMs,
                videoSummaryBurmese = extendedPlan.videoSummaryBurmese,
                clipTitleHint = clipTitleHint
            )
            extendedPlan.copy(segments = completeSegments)
        } else {
            extendedPlan
        }
    }

    private suspend fun continueDubPlanIfTruncated(
        initialPlan: DubTranslationPlan,
        input: PreparedDubInput,
        clipTitleHint: String,
        conciseSubtitles: Boolean,
        narrationStyle: DubNarrationStyle,
        onStatusUpdate: (String) -> Unit
    ): DubTranslationPlan {
        val lastEndMs = initialPlan.segments.maxOfOrNull { it.endMs } ?: 0L
        val missingTailMs = input.durationMs - lastEndMs
        if (missingTailMs <= 2_800L) return initialPlan

        val resumeSec = lastEndMs / 1000.0
        val totalSec = input.durationMs / 1000.0
        onStatusUpdate(
            "ဗီဒီယို အဆုံးပိုင်း (${DubSegment.formatTimestamp(lastEndMs)} → ${input.durationLabel}) ကို မရပ်တမ်း ဆက်လက်ဖြည့်စွက်နေပါတယ်..."
        )

        val continuationPrompt = buildString {
            appendLine("CONTINUATION TASK: Your previous segment ended at ${String.format("%.1f", resumeSec)}s, leaving ${String.format("%.1f", missingTailMs / 1000.0)}s un-narrated at the end of the clip.")
            appendLine("Clip title hint: $clipTitleHint")
            appendLine("Summary so far: ${initialPlan.videoSummaryBurmese}")
            appendLine("Previous last Burmese line: ${initialPlan.segments.lastOrNull()?.burmeseText.orEmpty()}")
            appendLine("Style: ${narrationStyle.promptDirective}")
            appendLine("Generate ONLY the remaining chronological segments strictly covering [${String.format("%.1f", resumeSec)}s .. ${String.format("%.1f", totalSec)}s] in pure conversational Burmese, with 1.6s-3.5s crisp beats reaching all the way to ${String.format("%.1f", totalSec)}s.")
        }

        val tailPlan = runCatching {
            executePlanRequest(
                input = input,
                promptInstructions = continuationPrompt,
                totalDurationMs = input.durationMs,
                conciseSubtitles = conciseSubtitles,
                onStatusUpdate = {}
            )
        }.getOrNull() ?: return initialPlan

        val appended = tailPlan.segments
            .filter { it.endMs > lastEndMs + 300L }
            .map { seg ->
                if (seg.startMs < lastEndMs) {
                    val adjStart = lastEndMs + 50L
                    seg.copy(
                        startMs = adjStart,
                        endMs = seg.endMs.coerceAtLeast(adjStart + 650L).coerceAtMost(input.durationMs)
                    )
                } else {
                    seg
                }
            }
            .filter { it.endMs > it.startMs + 450L && it.startMs < input.durationMs - 250L }

        if (appended.isEmpty()) return initialPlan
        return initialPlan.copy(
            segments = (initialPlan.segments + appended).sortedBy { it.startMs }
        )
    }

    private suspend fun executePlanRequest(
        input: PreparedDubInput,
        promptInstructions: String,
        totalDurationMs: Long,
        conciseSubtitles: Boolean,
        onStatusUpdate: (String) -> Unit
    ): DubTranslationPlan {
        val effectiveKey = ApiKeyStore.getEffectiveApiKey()
        if (effectiveKey != null) {
            val directParts = buildList {
                when (input) {
                    is PreparedDubInput.InlineFullVideo -> {
                        add(DirectPart.InlineBytes(input.bytes, input.mimeType))
                        add(DirectPart.Text(promptInstructions))
                    }
                    is PreparedDubInput.AudioPlusKeyframes -> {
                        if (input.audioBytes != null) {
                            add(DirectPart.Text("Below is the full extracted audio track of the video clip:"))
                            add(DirectPart.InlineBytes(input.audioBytes, input.audioMimeType))
                        }
                        if (input.frames.isNotEmpty()) {
                            add(DirectPart.Text("Below are visual keyframes spanning the full video timeline:"))
                            input.frames.forEach { frame ->
                                add(DirectPart.Text("Frame [${frame.timestampLabel}]:"))
                                add(DirectPart.JpegBitmap(frame.bitmap))
                            }
                        }
                        add(DirectPart.Text(promptInstructions))
                    }
                }
            }
            val schemaJson = dubPlanSchemaJson()

            return GeminiResilienceHelper.executeWithResilience(
                maxAttempts = 4,
                timeoutMs = 75_000L,
                useDirectApiKey = true,
                onStatusUpdate = onStatusUpdate
            ) { attemptIndex ->
                val modelName = directModelCandidates[attemptIndex.coerceAtMost(directModelCandidates.lastIndex)]
                if (attemptIndex > 0) {
                    onStatusUpdate("Direct Gemini Model ($modelName) ဖြင့် ဆက်လက် ဘာသာပြန်ပေးနေပါတယ်...")
                }
                val jsonText = DirectGeminiClient.generateStructuredJson(
                    apiKey = effectiveKey,
                    modelName = modelName,
                    systemPrompt = systemPrompt,
                    parts = directParts,
                    responseSchemaJson = schemaJson,
                    temperature = 0.4f
                )
                parseDubPlan(
                    jsonText = jsonText,
                    totalDurationMs = totalDurationMs,
                    conciseSubtitles = conciseSubtitles
                )
            }
        }

        if (!hasHarnessDebugToken()) {
            throw ApiKeyRequiredException(
                "Cloud AI ဘာသာပြန်စနစ်အတွက် API Key မရှိသေးပါ (Client-Side Fallback ဖြင့် ဆက်လုပ်နိုင်ပါသည်)။"
            )
        }

        val requestContent = content {
            when (input) {
                is PreparedDubInput.InlineFullVideo -> {
                    inlineData(input.bytes, input.mimeType)
                    text(promptInstructions)
                }
                is PreparedDubInput.AudioPlusKeyframes -> {
                    if (input.audioBytes != null) {
                        text("Below is the full extracted audio track of the video clip:")
                        inlineData(input.audioBytes, input.audioMimeType)
                    }
                    if (input.frames.isNotEmpty()) {
                        text("Below are visual keyframes spanning the full video timeline:")
                        input.frames.forEach { frame ->
                            text("Frame [${frame.timestampLabel}]:")
                            image(frame.bitmap)
                        }
                    }
                    text(promptInstructions)
                }
            }
        }

        return GeminiResilienceHelper.executeWithResilience(
            maxAttempts = 5,
            timeoutMs = 75_000L,
            useDirectApiKey = false,
            onStatusUpdate = onStatusUpdate
        ) { attemptIndex ->
            val model = if (attemptIndex == 0) primaryModel else fallbackModel
            if (attemptIndex == 1) {
                onStatusUpdate("မြန်ဆန်သော AI Model ဖြင့် ဆက်လက် ဘာသာပြန်ပေးနေပါတယ်...")
            }
            val response = model.generateContent(requestContent)
            val jsonText = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty translation.")
            parseDubPlan(
                jsonText = jsonText,
                totalDurationMs = totalDurationMs,
                conciseSubtitles = conciseSubtitles
            )
        }
    }

    private fun parseDubPlan(
        jsonText: String,
        totalDurationMs: Long,
        conciseSubtitles: Boolean
    ): DubTranslationPlan {
        val json = JSONObject(jsonText)
        val detectedLanguage = json.optString("detectedLanguage", "English")
            .ifBlank { "English" }
        val summaryBurmese = json.optString("videoSummaryBurmese", "")
            .trim()
        val array = json.optJSONArray("segments")

        val rawSegments = buildList {
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val startMs = (obj.optDouble("startSeconds", 0.0) * 1000.0)
                        .toLong()
                        .coerceIn(0L, (totalDurationMs - 400L).coerceAtLeast(0L))
                    val rawEndMs = (obj.optDouble("endSeconds", (startMs + 2200L) / 1000.0) * 1000.0)
                        .toLong()
                    val endMs = rawEndMs
                        .coerceAtLeast(startMs + 650L)
                        .coerceAtMost(totalDurationMs)
                    val original = obj.optString("originalSpeech", "").trim()
                    val burmese = obj.optString("burmeseSubtitle", "").trim()
                    if (burmese.isNotEmpty()) {
                        val candidate = DubSegment(
                            id = UUID.randomUUID().toString(),
                            startMs = startMs,
                            endMs = endMs,
                            originalSpeech = original,
                            burmeseText = burmese
                        )
                        if (conciseSubtitles && burmese.length > 68 && candidate.durationMs >= 2600L) {
                            addAll(splitOverlongSegment(candidate))
                        } else {
                            add(candidate)
                        }
                    }
                }
            }
        }.sortedBy { it.startMs }

        require(rawSegments.isNotEmpty()) {
            "No spoken lines or translatable scenes were detected in this clip."
        }

        val normalized = mutableListOf<DubSegment>()
        for (seg in rawSegments) {
            val prevEnd = normalized.lastOrNull()?.endMs ?: 0L
            val adjustedStart = seg.startMs.coerceAtLeast(prevEnd)
            if (adjustedStart >= totalDurationMs - 250L) continue
            val adjustedEnd = seg.endMs
                .coerceAtLeast(adjustedStart + 650L)
                .coerceAtMost(totalDurationMs)
            normalized.add(seg.copy(startMs = adjustedStart, endMs = adjustedEnd))
        }

        return DubTranslationPlan(
            detectedLanguage = detectedLanguage,
            videoSummaryBurmese = summaryBurmese,
            segments = normalized.ifEmpty { rawSegments.take(1) }
        )
    }

    private fun splitOverlongSegment(seg: DubSegment): List<DubSegment> {
        val text = seg.burmeseText
        val mid = text.length / 2
        val breakIndex = listOf('။', '၊', ' ')
            .flatMap { ch ->
                text.indices.filter { text[it] == ch && it in 14..(text.length - 12) }
            }
            .minByOrNull { kotlin.math.abs(it - mid) }

        if (breakIndex == null) return listOf(seg)
        val part1 = text.substring(0, breakIndex + 1).trim()
        val part2 = text.substring(breakIndex + 1).trim()
        if (part1.isEmpty() || part2.isEmpty()) return listOf(seg)

        val ratio = part1.length.toFloat() / (part1.length + part2.length).toFloat()
        val splitMs = (seg.startMs + (seg.durationMs * ratio).toLong())
            .coerceIn(seg.startMs + 600L, seg.endMs - 600L)

        return listOf(
            seg.copy(id = "${seg.id}_a", endMs = splitMs, burmeseText = part1),
            seg.copy(id = "${seg.id}_b", startMs = splitMs, originalSpeech = "", burmeseText = part2)
        )
    }

    companion object {
        private val FALLBACK_OPENERS = listOf(
            "ဒီဇာတ်ဝင်ခန်းအစမှာပဲ စိတ်ဝင်စားစရာ အဖြစ်အပျက် စတင်နေပါပြီ။",
            "အဖွင့်အခန်းမှာတင် ထူးခြားတဲ့ အရိပ်အယောင်တွေကို မြင်တွေ့နေရပါတယ်။"
        )

        private val FALLBACK_LINES = listOf(
            "သူတို့နှစ်ယောက်ကြားက အခြေအနေက တဖြည်းဖြည်း တင်းမာလာပါတယ်။",
            "အဲ့ဒီအချိန်မှာပဲ မထင်မှတ်ထားတဲ့ အပြောင်းအလဲတစ်ခု ဖြစ်လာခဲ့တယ်။",
            "ဇာတ်လိုက်က အခြေအနေကို သတိထားမိပြီး ချက်ချင်း တုံ့ပြန်လိုက်ပါတယ်။",
            "တစ်စက္ကန့်ထက်တစ်စက္ကန့် ဇာတ်ရှိန်က ပိုပြီး မြင့်တက်လာနေပါပြီ။",
            "သူမထင်ထားတဲ့ လှည့်ကွက်တစ်ခုက ရှေ့တည့်တည့်မှာ စောင့်ကြိုနေပါတယ်။",
            "အခန်းထဲက အဖြစ်အပျက်တွေကို အားလုံးက အာရုံစိုက်ကြည့်နေကြပါတယ်။",
            "ဘယ်သူမှ မထင်မှတ်ထားတဲ့ ဆုံးဖြတ်ချက်တစ်ခုကို ချလိုက်ရပါတော့တယ်။"
        )

        private val FALLBACK_CLOSERS = listOf(
            "နောက်ဆုံးမှာ ဘာဆက်ဖြစ်သွားမလဲ ဆိုတာကို အဆုံးထိ ကြည့်လိုက်ရအောင်။",
            "ဒီဇာတ်ဝင်ခန်းရဲ့ အဆုံးသတ်ကတော့ အားလုံးကို အံ့အားသင့်သွားစေမှာပါ။"
        )

        private val BRIDGE_FILLER_LINES = listOf(
            "အဲ့ဒီအချိန်မှာပဲ မထင်မှတ်ထားတဲ့ အခြေအနေတစ်ခု ဆက်ဖြစ်လာပါတယ်။",
            "ဇာတ်လိုက်ရဲ့ မျက်နှာအမူအရာက တဖြည်းဖြည်း စိုးရိမ်လာနေပါပြီ။",
            "နောက်ထပ် ဘာဆက်ဖြစ်မလဲဆိုတာ အတူတူ စောင့်ကြည့်လိုက်ရအောင်။",
            "ဒီအခန်းမှာ တင်းမာမှုတွေက ပိုပြီး ပြင်းထန်လာတာကို တွေ့ရပါတယ်။",
            "ဇာတ်ဝင်ခန်းက မထင်မှတ်တဲ့ဘက်ကို ဦးတည်ပြောင်းလဲနေပါပြီ။"
        )

        /**
         * Ensures the translated dubbing timeline covers the ENTIRE video smoothly from 00:00
         * through [totalDurationMs] without leaving silent dead zones or stopping halfway.
         */
        fun ensureContinuousCoverage(
            segments: List<DubSegment>,
            totalDurationMs: Long,
            videoSummaryBurmese: String = "",
            clipTitleHint: String = "Movie Clip"
        ): List<DubSegment> {
            val safeDurationMs = totalDurationMs.coerceAtLeast(1_500L)
            if (segments.isEmpty()) {
                return buildClientFallbackPlan(safeDurationMs, clipTitleHint).segments
            }

            val sorted = segments.sortedBy { it.startMs }
            val result = mutableListOf<DubSegment>()
            var fillerCounter = 0

            // 1. Bridge leading dead silence if video starts with > 1.8s silence
            val firstStart = sorted.first().startMs.coerceIn(0L, safeDurationMs)
            if (firstStart > 1_800L) {
                val leadCount = ((firstStart / 3_200L).toInt()).coerceIn(1, 8)
                val step = (firstStart - 100L) / leadCount
                for (i in 0 until leadCount) {
                    val s = (i * step).coerceAtMost(firstStart - 600L)
                    val e = if (i == leadCount - 1) firstStart - 80L else ((i + 1) * step) - 60L
                    if (e - s >= 550L) {
                        val line = if (i == 0 && videoSummaryBurmese.isNotBlank()) {
                            val cleanSummary = videoSummaryBurmese.substringBefore('။').trim().take(54)
                            if (cleanSummary.isNotEmpty()) "$cleanSummary။" else FALLBACK_OPENERS[0]
                        } else {
                            BRIDGE_FILLER_LINES[(fillerCounter++) % BRIDGE_FILLER_LINES.size]
                        }
                        result.add(
                            DubSegment(
                                id = "bridge_lead_${i}_${UUID.randomUUID().toString().take(5)}",
                                startMs = s,
                                endMs = e,
                                originalSpeech = "",
                                burmeseText = line
                            )
                        )
                    }
                }
            }

            // 2. Keep original segments & bridge any middle gap > 2.2s
            for (seg in sorted) {
                val prevEnd = result.lastOrNull()?.endMs ?: 0L
                val segStart = seg.startMs.coerceAtLeast(prevEnd).coerceAtMost(safeDurationMs)
                val gapMs = segStart - prevEnd
                if (result.isNotEmpty() && gapMs > 2_200L) {
                    val gapBeats = ((gapMs / 3_200L).toInt()).coerceIn(1, 12)
                    val usableGap = (gapMs - 140L).coerceAtLeast(650L)
                    val step = usableGap / gapBeats
                    for (g in 0 until gapBeats) {
                        val gs = prevEnd + 70L + (g * step)
                        val ge = (if (g == gapBeats - 1) segStart - 70L else gs + step - 60L)
                            .coerceAtMost(segStart - 50L)
                        if (ge - gs >= 550L) {
                            result.add(
                                DubSegment(
                                    id = "bridge_mid_${fillerCounter}_${UUID.randomUUID().toString().take(5)}",
                                    startMs = gs,
                                    endMs = ge,
                                    originalSpeech = "",
                                    burmeseText = BRIDGE_FILLER_LINES[(fillerCounter++) % BRIDGE_FILLER_LINES.size]
                                )
                            )
                        }
                    }
                }

                val finalStart = segStart.coerceAtLeast(result.lastOrNull()?.endMs ?: 0L)
                if (finalStart < safeDurationMs - 300L) {
                    val finalEnd = seg.endMs
                        .coerceAtLeast(finalStart + 550L)
                        .coerceAtMost(safeDurationMs)
                    result.add(seg.copy(startMs = finalStart, endMs = finalEnd))
                }
            }

            // 3. Bridge trailing cutoff if segments ended > 2.0s before the video end
            val lastEnd = result.lastOrNull()?.endMs ?: 0L
            val tailRemaining = safeDurationMs - lastEnd
            if (tailRemaining > 2_000L) {
                val tailBeats = ((tailRemaining / 3_200L).toInt()).coerceIn(1, 20)
                val usableTail = (safeDurationMs - lastEnd - 60L).coerceAtLeast(600L)
                val step = usableTail / tailBeats
                for (t in 0 until tailBeats) {
                    val ts = (lastEnd + 60L + (t * step)).coerceAtMost(safeDurationMs - 450L)
                    val te = if (t == tailBeats - 1) {
                        safeDurationMs
                    } else {
                        (ts + step - 60L).coerceIn(ts + 550L, safeDurationMs)
                    }
                    if (te - ts >= 450L) {
                        val line = if (t == tailBeats - 1) {
                            FALLBACK_CLOSERS[(fillerCounter++) % FALLBACK_CLOSERS.size]
                        } else {
                            BRIDGE_FILLER_LINES[(fillerCounter++) % BRIDGE_FILLER_LINES.size]
                        }
                        result.add(
                            DubSegment(
                                id = "bridge_tail_${t}_${UUID.randomUUID().toString().take(5)}",
                                startMs = ts,
                                endMs = te,
                                originalSpeech = "",
                                burmeseText = line
                            )
                        )
                    }
                }
            }

            return result.sortedBy { it.startMs }
        }

        /**
         * Client-side fallback timeline generator used when cloud translation returns HTTP 403
         * or when no Gemini API key is configured, ensuring full start-to-end continuous Burmese
         * subtitle rendering + TTS synthesis always complete cleanly across any video length.
         */
        fun buildClientFallbackPlan(
            totalDurationMs: Long,
            clipTitleHint: String
        ): DubTranslationPlan {
            val safeDurationMs = totalDurationMs.coerceAtLeast(2_000L)
            val titleLabel = clipTitleHint.trim().ifBlank { "Movie Clip" }.take(36)
            val targetBeatCount = ((safeDurationMs / 3_200L).toInt()).coerceIn(2, 60)
            val stepMs = (safeDurationMs / targetBeatCount).coerceAtLeast(1_000L)

            val segments = (0 until targetBeatCount).map { idx ->
                val startMs = (idx * stepMs).coerceAtMost(safeDurationMs - 650L)
                val rawEnd = if (idx == targetBeatCount - 1) safeDurationMs else ((idx + 1) * stepMs) - 60L
                val endMs = rawEnd.coerceIn(startMs + 650L, safeDurationMs)
                val line = when {
                    idx == 0 -> "\"$titleLabel\" ဇာတ်ဝင်ခန်း အဖွင့်မှာပဲ စိတ်လှုပ်ရှားစရာ စတင်နေပါပြီ။"
                    idx == targetBeatCount - 1 -> FALLBACK_CLOSERS[idx % FALLBACK_CLOSERS.size]
                    else -> FALLBACK_LINES[idx % FALLBACK_LINES.size]
                }
                DubSegment(
                    id = "fallback_${idx}_${UUID.randomUUID().toString().take(6)}",
                    startMs = startMs,
                    endMs = endMs,
                    originalSpeech = "Scene ${idx + 1}",
                    burmeseText = line
                )
            }

            return DubTranslationPlan(
                detectedLanguage = "Client Fallback",
                videoSummaryBurmese = "Client-Side Subtitle & Speech Fallback Mode ဖြင့် \"$titleLabel\" တစ်ပုဒ်လုံး အစမှအဆုံး စာတန်းနှင့် မြန်မာအသံ အပြည့်အဝ ပြင်ဆင်ပေးထားပါသည်။",
                segments = segments
            )
        }
    }
}
