package com.example.ai

import com.example.data.ApiKeyStore
import com.example.firebase.ApiKeyRequiredException
import com.example.firebase.GeminiResilienceHelper
import com.example.firebase.hasHarnessDebugToken
import com.example.model.ClonedVoiceProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.pow

class VoiceCloneRepository {

    companion object {
        val OFFICIAL_GEMINI_VOICES = listOf(
            "Achernar", "Achird", "Algenib", "Algieba", "Alnilam",
            "Aoede", "Autonoe", "Callirrhoe", "Charon", "Despina",
            "Enceladus", "Erinome", "Fenrir", "Gacrux", "Iapetus",
            "Kore", "Laomedeia", "Leda", "Orus", "Puck",
            "Pulcherrima", "Rasalgethi", "Sadachbia", "Sadaltager", "Schedar",
            "Sulafat", "Umbriel", "Vindemiatrix", "Zephyr", "Zubenelgenubi"
        )
    }

    private val directModelCandidates = listOf(
        "gemini-3.8-flash",
        "gemini-2.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash-lite"
    )

    private val cloneSchema = Schema.obj(
        properties = mapOf(
            "matchedBaseVoice" to Schema.enumeration(
                values = OFFICIAL_GEMINI_VOICES,
                description = "Best matching Gemini neural voice timbre from the official voice roster matching gender, tone, texture, and warmth."
            ),
            "vocalDescription" to Schema.string(
                description = "Concise Burmese & English summary of the cloned speaker's vocal tone, depth, and rhythm."
            ),
            "recommendedSpeed" to Schema.double(
                description = "Speaking rate multiplier between 0.88 and 1.18 matching the sample's natural cadence."
            ),
            "pitchOffsetSemitones" to Schema.double(
                description = "Fine pitch calibration in semitones between -2.5 and +2.5 so synthesized speech closely matches speaker pitch register."
            )
        )
    )

    private fun cloneSchemaJson(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put(
            "properties",
            JSONObject()
                .put(
                    "matchedBaseVoice",
                    JSONObject()
                        .put("type", "STRING")
                        .put("enum", JSONArray(OFFICIAL_GEMINI_VOICES))
                )
                .put("vocalDescription", JSONObject().put("type", "STRING"))
                .put("recommendedSpeed", JSONObject().put("type", "NUMBER"))
                .put("pitchOffsetSemitones", JSONObject().put("type", "NUMBER"))
        )
        put(
            "required",
            JSONArray()
                .put("matchedBaseVoice")
                .put("vocalDescription")
                .put("recommendedSpeed")
                .put("pitchOffsetSemitones")
        )
    }

    private val systemPrompt = """
        You are an expert vocal acoustic profiler. Analyze the provided human voice sample carefully:
        - Gender & apparent age register
        - Vocal timbre & texture:
          * Male options: Alnilam (firm), Charon (informative natural), Orus (deep firm), Fenrir (excitable), Algenib (gravelly/raspy), Enceladus (breathy/soft), Achird (friendly), Puck (upbeat), Iapetus (clear), Schedar (even), Umbriel (easy-going), Zubenelgenubi (casual), Rasalgethi (informative), Sadachbia (lively), Sadaltager (knowledgeable).
          * Female options: Kore (clear firm), Sulafat (warm), Aoede (breezy), Leda (youthful), Gacrux (mature), Achernar (soft), Zephyr (bright), Erinome (clear), Despina (smooth), Callirrhoe (easy-going), Vindemiatrix (gentle), Laomedeia (upbeat), Autonoe (bright), Pulcherrima (forward).
        - Estimate recommendedSpeed (0.88..1.18) and fine pitchOffsetSemitones (-2.5..+2.5).
        Return ONLY valid JSON matching the schema.
    """.trimIndent()

    private val primaryModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = cloneSchema
                temperature = 0.2f
            },
            systemInstruction = content { text(systemPrompt) }
        )
    }

    private val fallbackModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = cloneSchema
                temperature = 0.2f
            },
            systemInstruction = content { text(systemPrompt) }
        )
    }

    suspend fun createVoiceCloneFromSample(
        audioBytes: ByteArray,
        mimeType: String,
        profileName: String,
        sourceLabel: String,
        onStatusUpdate: (String) -> Unit = {}
    ): ClonedVoiceProfile {
        require(audioBytes.isNotEmpty()) { "Voice sample audio is empty." }
        val userPrompt = "Profile this speaker's voice timbre, pitch register, and cadence for Burmese voice cloning named '$profileName'."

        val effectiveKey = ApiKeyStore.getEffectiveApiKey()
        if (effectiveKey != null) {
            val parts = listOf(
                DirectPart.InlineBytes(audioBytes, mimeType),
                DirectPart.Text(userPrompt)
            )
            val schemaJson = cloneSchemaJson()
            return GeminiResilienceHelper.executeWithResilience(
                maxAttempts = 4,
                timeoutMs = 45_000L,
                useDirectApiKey = true,
                onStatusUpdate = onStatusUpdate
            ) { attemptIndex ->
                val modelName = directModelCandidates[attemptIndex.coerceAtMost(directModelCandidates.lastIndex)]
                val jsonText = DirectGeminiClient.generateStructuredJson(
                    apiKey = effectiveKey,
                    modelName = modelName,
                    systemPrompt = systemPrompt,
                    parts = parts,
                    responseSchemaJson = schemaJson,
                    temperature = 0.2f
                )
                parseProfileJson(jsonText, profileName, sourceLabel)
            }
        }

        if (!hasHarnessDebugToken()) {
            throw ApiKeyRequiredException(
                "Voice Clone ပြုလုပ်ရန် ထိပ်ဆုံး Bar ရှိ API Key ခလုတ်ကိုနှိပ်ပြီး ကိုယ်ပိုင် Gemini API Key ထည့်သွင်းပေးပါ။"
            )
        }

        val requestContent = content {
            inlineData(audioBytes, mimeType)
            text(userPrompt)
        }

        return GeminiResilienceHelper.executeWithResilience(
            maxAttempts = 4,
            timeoutMs = 45_000L,
            useDirectApiKey = false,
            onStatusUpdate = onStatusUpdate
        ) { attemptIndex ->
            val model = if (attemptIndex == 0) primaryModel else fallbackModel
            val response = model.generateContent(requestContent)
            val jsonText = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned empty vocal analysis.")
            parseProfileJson(jsonText, profileName, sourceLabel)
        }
    }

    private fun parseProfileJson(
        jsonText: String,
        profileName: String,
        sourceLabel: String
    ): ClonedVoiceProfile {
        val json = JSONObject(jsonText)
        val baseVoice = json.optString("matchedBaseVoice", "Charon")
            .takeIf { it in OFFICIAL_GEMINI_VOICES }
            ?: "Charon"
        val description = json.optString("vocalDescription", "သဘာဝကျ မြန်မာအသံ")
            .ifBlank { "သဘာဝကျ မြန်မာအသံ" }
        val speed = json.optDouble("recommendedSpeed", 1.0)
            .toFloat()
            .coerceIn(0.86f, 1.18f)
        val semitones = json.optDouble("pitchOffsetSemitones", 0.0)
            .toFloat()
            .coerceIn(-2.5f, 2.5f)
        val pitchRatio = (2.0.pow(semitones / 12.0)).toFloat().coerceIn(0.86f, 1.16f)

        return ClonedVoiceProfile(
            id = "clone_${UUID.randomUUID().toString().take(8)}",
            name = profileName.trim().ifBlank { "Custom Voice" }.take(28),
            matchedBaseVoice = baseVoice,
            vocalDescription = description,
            speakingSpeed = speed,
            pitchShiftRatio = pitchRatio,
            sourceLabel = sourceLabel
        )
    }
}
