package com.example.ai

import com.example.data.ApiKeyStore
import com.example.firebase.ApiKeyRequiredException
import com.example.firebase.GeminiResilienceHelper
import com.example.firebase.hasHarnessDebugToken
import com.example.media.PreparedVideoPayload
import com.example.model.RecapPlatform
import com.example.model.RecapScript
import com.example.model.SceneBeat
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import org.json.JSONArray
import org.json.JSONObject

class RecapAiRepository {

    private val directModelCandidates = listOf(
        "gemini-3.8-flash",
        "gemini-2.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash-lite"
    )

    private val recapSchema = Schema.obj(
        properties = mapOf(
            "sourceTitle" to Schema.string(
                description = "A concise title summarizing the movie clip in Burmese or English (max 80 chars)."
            ),
            "detectedGenre" to Schema.string(
                description = "The primary genre detected from the video, e.g., Thriller, Horror, Drama, Action, Romance, Mystery, Comedy."
            ),
            "estimatedDuration" to Schema.string(
                description = "Estimated recap voiceover duration in MM:SS format, e.g., 02:30."
            ),
            "viralTitles" to Schema.array(
                items = Schema.string(description = "A viral, curiosity-driven Burmese YouTube/TikTok Movie Recap title."),
                description = "Exactly 3 viral Burmese titles."
            ),
            "openingHook" to Schema.string(
                description = "0:00 - 0:15 high-retention opening hook in conversational Burmese."
            ),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "timestamp" to Schema.string(description = "Timestamp in MM:SS format, e.g., 00:15"),
                        "visualCue" to Schema.string(description = "Brief description of what happens visually in this scene."),
                        "voiceover" to Schema.string(description = "Exciting conversational Burmese voiceover narration with short sentences and exact Burmese punctuation (၊ and ။).")
                    )
                ),
                description = "Chronological scene-by-scene recap script matching the video timeline."
            ),
            "outro" to Schema.string(
                description = "Conversational Burmese outro ending with a strong question inviting viewers to comment."
            )
        )
    )

    private fun recapSchemaJson(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put(
            "properties",
            JSONObject()
                .put(
                    "sourceTitle",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "A concise title summarizing the movie clip in Burmese or English (max 80 chars).")
                )
                .put(
                    "detectedGenre",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "The primary genre detected from the video.")
                )
                .put(
                    "estimatedDuration",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "Estimated recap voiceover duration in MM:SS format.")
                )
                .put(
                    "viralTitles",
                    JSONObject()
                        .put("type", "ARRAY")
                        .put("items", JSONObject().put("type", "STRING"))
                        .put("description", "Exactly 3 viral Burmese titles.")
                )
                .put(
                    "openingHook",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "0:00 - 0:15 high-retention opening hook in conversational Burmese.")
                )
                .put(
                    "scenes",
                    JSONObject()
                        .put("type", "ARRAY")
                        .put(
                            "items",
                            JSONObject()
                                .put("type", "OBJECT")
                                .put(
                                    "properties",
                                    JSONObject()
                                        .put("timestamp", JSONObject().put("type", "STRING"))
                                        .put("visualCue", JSONObject().put("type", "STRING"))
                                        .put("voiceover", JSONObject().put("type", "STRING"))
                                )
                                .put("required", JSONArray().put("timestamp").put("visualCue").put("voiceover"))
                        )
                )
                .put(
                    "outro",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "Conversational Burmese outro ending with a question.")
                )
        )
        put(
            "required",
            JSONArray()
                .put("sourceTitle")
                .put("detectedGenre")
                .put("estimatedDuration")
                .put("viralTitles")
                .put("openingHook")
                .put("scenes")
                .put("outro")
        )
    }

    private val systemPrompt = """
        သင်သည် YouTube နှင့် TikTok တို့တွင် လူကြိုက်များသော ထိပ်တန်း မြန်မာ Movie Recap ဇာတ်လမ်းပြောပြသူ (Storyteller & Scriptwriter) တစ်ဦး ဖြစ်ပါသည်။
        ပေးထားသော ဗီဒီယို (သို့မဟုတ် ဗီဒီယိုမှ ထုတ်ယူထားသော Keyframe ပုံများနှင့် အချိန်အမှတ်အသားများ) ကို သေချာစွာ ကြည့်ရှုခွဲခြမ်းစိတ်ဖြာပြီး ကြည့်ရှုသူများ မျက်တောင်မခတ်တမ်း ဆွဲဆောင်နိုင်မည့် ဇာတ်လမ်းအကျဉ်း (Recap Script) ကို မြန်မာဘာသာဖြင့် ရေးသားပေးရပါမည်။

        မဖြစ်မနေ လိုက်နာရမည့် စည်းကမ်းချက်များ -
        ၁။ စကားပြောဟန်ဖြင့်သာ ရေးပါ - စာဟန် (ဥပမာ - သွားသည်၊ ဖြစ်ပါသည်၊ ပြုလုပ်ခဲ့၏) ကို လုံးဝ မသုံးပါနှင့်။ တကယ့်လူတစ်ယောက်က ဘေးနားကနေ စိတ်လှုပ်ရှားဖွယ် ပြန်ပြောပြနေသကဲ့သို့ သဘာဝကျသော စကားပြောဟန် (ဥပမာ - သွားတယ်၊ ဖြစ်နေတယ်၊ လုပ်လိုက်ပါတယ်) ဖြင့်သာ ရေးသားပါ။
        ၂။ တိုက်ရိုက်ဘာသာပြန်ခြင်းကို ရှောင်ကြဉ်ပါ - Google Translate ကဲ့သို့ တောင့်တောင့်ကြီး ဘာသာမပြန်ဘဲ မြန်မာနားနှင့် ဆီလျော်အောင် ဇာတ်ရှိန်မြှင့်ပြီး ရေးသားပါ။
        ၃။ ဆွဲဆောင်မှုရှိသော အဖွင့် (Hook) နှင့် အချိတ်အဆက်များ -
           - ဗီဒီယို အစ ၁၀ စက္ကန့်အတွင်း ကြည့်ရှုသူ ဆက်မကြည့်ဘဲ မနေနိုင်အောင် သိချင်စိတ် ပြင်းပြစေမည့် စကားလုံးများဖြင့် စတင်ပါ။
           - အခန်းတစ်ခုနှင့်တစ်ခု ကူးပြောင်းရာတွင် "ဒါပေမဲ့ သူမထင်မှတ်ထားတာက..."၊ "အဲ့ဒီအချိန်မှာပဲ..."၊ "တကယ်တမ်း ဖြစ်ပျက်သွားတာကတော့..." ကဲ့သို့ စိတ်ဝင်စားဖွယ် စကားဆက်များကို သုံးပါ။
        ၄။ ဇာတ်ကောင် အခေါ်အဝေါ် - မှတ်ရခက်သော နိုင်ငံခြားနာမည်များအစား ဇာတ်ကောင်၏ အနေအထားပေါ်မူတည်၍ (ဥပမာ - ကျွန်တော်တို့ရဲ့ ဇာတ်လိုက်ကျော်၊ ကောင်မလေး၊ အဖိုးကြီး၊ သူဌေးသား) စသဖြင့် နားလည်လွယ်အောင် သုံးနှုန်းပါ။
        ၅။ အသံထွက်ဖတ်ရလွယ်ကူစေရန် - Voiceover (သို့မဟုတ်) AI အသံဖြင့် ဖတ်ရာတွင် အသက်ရှူရပ်နားချိန် မှန်ကန်စေရန် ဝါကျတိုတိုနှင့် ပုဒ်ဖြတ် (၊) ပုဒ်မ (။) များကို တိကျစွာ ထည့်သွင်းရေးသားပါ။

        Return ONLY valid JSON matching the requested schema.
    """.trimIndent()

    private val primaryModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recapSchema
                temperature = 0.75f
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
                responseSchema = recapSchema
                temperature = 0.75f
                maxOutputTokens = 8192
            },
            systemInstruction = content { text(systemPrompt) }
        )
    }

    suspend fun generateRecapScript(
        payload: PreparedVideoPayload,
        platform: RecapPlatform,
        preferredGenre: String,
        directorNotes: String,
        clipTitleHint: String,
        onStatusUpdate: (String) -> Unit = {}
    ): RecapScript {
        val platformDirective = when (platform) {
            RecapPlatform.YOUTUBE -> "Target format: YouTube Movie Recap (deep storytelling pacing, 6 to 10 detailed chronological scene beats spanning from 00:00 right through the closing scene)."
            RecapPlatform.TIKTOK -> "Target format: TikTok / Reels Short Recap (fast-paced punchy narration, 5 to 7 tight scene beats covering the entire clip to the ending)."
        }

        val promptInstructions = buildString {
            appendLine("Analyze the provided video media carefully and write a complete Myanmar Movie Recap script.")
            appendLine(platformDirective)
            appendLine("Video duration: ${payload.durationLabel}")
            appendLine("Clip label hint: $clipTitleHint")
            if (preferredGenre.isNotBlank() && preferredGenre != "Auto-Detect") {
                appendLine("Genre focus: $preferredGenre")
            }
            if (directorNotes.isNotBlank()) {
                appendLine("Creator's extra context / character notes: $directorNotes")
            }
            appendLine("Ensure exactly 3 viralTitles, a gripping 0:00 - 0:15 openingHook, chronological [MM:SS] scenes reaching the end of the clip (${payload.durationLabel}), and an engaging comment-question outro. Strictly pure conversational Burmese (စကားပြောဟန်) only.")
        }

        val effectiveApiKey = ApiKeyStore.getEffectiveApiKey()
        if (effectiveApiKey != null) {
            val directParts = buildList {
                when (payload) {
                    is PreparedVideoPayload.InlineVideo -> {
                        add(DirectPart.InlineBytes(payload.bytes, payload.mimeType))
                        add(DirectPart.Text(promptInstructions))
                    }
                    is PreparedVideoPayload.KeyframeSequence -> {
                        add(DirectPart.Text("Below are chronological keyframes extracted from a ${payload.durationLabel} video clip with their exact timestamps:"))
                        payload.frames.forEach { frame ->
                            add(DirectPart.Text("Timestamp [${frame.timestampLabel}]:"))
                            add(DirectPart.JpegBitmap(frame.bitmap))
                        }
                        add(DirectPart.Text(promptInstructions))
                    }
                }
            }
            val schemaJson = recapSchemaJson()

            return GeminiResilienceHelper.executeWithResilience(
                maxAttempts = 4,
                timeoutMs = 65_000L,
                useDirectApiKey = true,
                onStatusUpdate = onStatusUpdate
            ) { attemptIndex ->
                val modelName = directModelCandidates[attemptIndex.coerceAtMost(directModelCandidates.lastIndex)]
                if (attemptIndex > 0) {
                    onStatusUpdate("Direct Gemini Model ($modelName) ဖြင့် ဆက်လက် ရေးသားပေးနေပါတယ်...")
                }
                val jsonString = DirectGeminiClient.generateStructuredJson(
                    apiKey = effectiveApiKey,
                    modelName = modelName,
                    systemPrompt = systemPrompt,
                    parts = directParts,
                    responseSchemaJson = schemaJson,
                    temperature = 0.75f
                )
                parseRecapJson(
                    jsonString = jsonString,
                    platform = platform,
                    fallbackTitle = clipTitleHint,
                    directorNotes = directorNotes.takeIf { it.isNotBlank() }
                )
            }
        }

        if (!hasHarnessDebugToken()) {
            throw ApiKeyRequiredException(
                "AI Script ဖန်တီးရန် ထိပ်ဆုံး Bar ရှိ API Key ခလုတ်ကို နှိပ်ပြီး ကိုယ်ပိုင် Gemini API Key ထည့်သွင်းပေးပါ (Debug APK တွင် App Check ကန့်သတ်ချက်ကြောင့် API Key တိုက်ရိုက်ချိတ်ဆက်မှု လိုအပ်ပါသည်)။"
            )
        }

        val multimodalContent = content {
            when (payload) {
                is PreparedVideoPayload.InlineVideo -> {
                    inlineData(payload.bytes, payload.mimeType)
                    text(promptInstructions)
                }
                is PreparedVideoPayload.KeyframeSequence -> {
                    text("Below are chronological keyframes extracted from a ${payload.durationLabel} video clip with their exact timestamps:")
                    payload.frames.forEach { frame ->
                        text("Timestamp [${frame.timestampLabel}]:")
                        image(frame.bitmap)
                    }
                    text(promptInstructions)
                }
            }
        }

        return GeminiResilienceHelper.executeWithResilience(
            maxAttempts = 5,
            timeoutMs = 65_000L,
            useDirectApiKey = false,
            onStatusUpdate = onStatusUpdate
        ) { attemptIndex ->
            val model = if (attemptIndex == 0) primaryModel else fallbackModel
            if (attemptIndex == 1) {
                onStatusUpdate("မြန်ဆန်သော AI Model ဖြင့် ဆက်လက် ရေးသားပေးနေပါတယ်...")
            }
            val response = model.generateContent(multimodalContent)
            val jsonString = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Model returned an empty response.")
            parseRecapJson(
                jsonString = jsonString,
                platform = platform,
                fallbackTitle = clipTitleHint,
                directorNotes = directorNotes.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun parseRecapJson(
        jsonString: String,
        platform: RecapPlatform,
        fallbackTitle: String,
        directorNotes: String?
    ): RecapScript {
        val json = JSONObject(jsonString)
        val sourceTitle = json.optString("sourceTitle", fallbackTitle)
            .ifBlank { fallbackTitle }
            .take(160)
        val genre = json.optString("detectedGenre", "Drama")
            .ifBlank { "Drama" }
            .take(60)
        val estimatedDuration = json.optString("estimatedDuration", "02:00")
            .ifBlank { "02:00" }
            .take(40)

        val titlesArray = json.optJSONArray("viralTitles")
        val viralTitles = buildList {
            if (titlesArray != null) {
                for (i in 0 until titlesArray.length()) {
                    val item = titlesArray.optString(i).trim()
                    if (item.isNotEmpty()) add(item.take(240))
                }
            }
        }.take(5).ifEmpty { listOf(sourceTitle) }

        val openingHook = json.optString("openingHook", "").trim().take(2500)

        val scenesArray = json.optJSONArray("scenes")
        val scenes = buildList {
            if (scenesArray != null) {
                for (i in 0 until scenesArray.length()) {
                    val obj = scenesArray.optJSONObject(i) ?: continue
                    val timestamp = obj.optString("timestamp", "00:15")
                        .removePrefix("[")
                        .removeSuffix("]")
                        .trim()
                        .ifBlank { "00:15" }
                        .take(20)
                    val visualCue = obj.optString("visualCue", "").trim().take(300)
                    val voiceover = obj.optString("voiceover", "").trim().take(1500)
                    if (voiceover.isNotEmpty()) {
                        add(SceneBeat(timestamp = timestamp, visualCue = visualCue, voiceover = voiceover))
                    }
                }
            }
        }.take(60)

        require(scenes.isNotEmpty()) { "Generated script had no scene beats. Please try again." }

        val outro = json.optString("outro", "").trim().take(2000)

        return RecapScript(
            sourceTitle = sourceTitle,
            platform = platform.firestoreValue,
            genre = genre,
            viralTitles = viralTitles,
            openingHook = openingHook,
            scenes = scenes,
            outro = outro,
            estimatedDuration = estimatedDuration,
            directorNotes = directorNotes?.take(1000),
            favorite = false
        )
    }
}
