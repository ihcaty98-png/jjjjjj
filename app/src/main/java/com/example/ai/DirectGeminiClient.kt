package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class DirectTtsAudio(
    val audioBytes: ByteArray,
    val mimeType: String
)

sealed interface DirectPart {
    data class Text(val value: String) : DirectPart
    data class InlineBytes(val bytes: ByteArray, val mimeType: String) : DirectPart
    data class JpegBitmap(val bitmap: Bitmap) : DirectPart
}

class DirectGeminiHttpException(
    val statusCode: Int,
    override val message: String
) : IllegalStateException(message)

/**
 * Direct Gemini Developer API REST client (OkHttp -> generativelanguage.googleapis.com).
 * Bypasses Firebase App Check / Play Integrity headers completely when a Gemini API key
 * is configured via API Key / Settings or BuildConfig.GEMINI_API_KEY.
 */
object DirectGeminiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    internal fun buildGenerateContentUrl(modelName: String, apiKey: String): String {
        val cleanModel = modelName.trim().removePrefix("models/")
        return "$BASE_URL/models/$cleanModel:generateContent?key=${apiKey.trim()}"
    }

    suspend fun testApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API key is empty."))
        }
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/models?pageSize=1&key=$cleanKey")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = extractGoogleErrorMessage(bodyStr)
                        ?: "HTTP ${resp.code} error"
                    throw DirectGeminiHttpException(resp.code, errMsg)
                }
                "Connected cleanly to Gemini Developer API"
            }
        }
    }

    suspend fun generateStructuredJson(
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        parts: List<DirectPart>,
        responseSchemaJson: JSONObject,
        temperature: Float
    ): String = withContext(Dispatchers.IO) {
        val partsArray = JSONArray()
        for (part in parts) {
            when (part) {
                is DirectPart.Text -> {
                    partsArray.put(JSONObject().put("text", part.value))
                }
                is DirectPart.InlineBytes -> {
                    val b64 = Base64.encodeToString(part.bytes, Base64.NO_WRAP)
                    partsArray.put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject()
                                .put("mimeType", part.mimeType)
                                .put("data", b64)
                        )
                    )
                }
                is DirectPart.JpegBitmap -> {
                    val baos = ByteArrayOutputStream()
                    part.bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    partsArray.put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject()
                                .put("mimeType", "image/jpeg")
                                .put("data", b64)
                        )
                    )
                }
            }
        }

        val payload = JSONObject().apply {
            if (systemPrompt.isNotBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemPrompt))
                    )
                )
            }
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", partsArray)
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", temperature.toDouble())
                    .put("maxOutputTokens", 8192)
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", responseSchemaJson)
            )
        }

        val responseJson = executeGenerateContent(apiKey, modelName, payload)
        extractFirstText(responseJson)
            ?: throw IllegalStateException("Gemini returned an empty JSON response.")
    }

    suspend fun generateSpeechAudio(
        apiKey: String,
        modelName: String,
        promptText: String,
        voiceName: String
    ): DirectTtsAudio = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", promptText))
                        )
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put(
                                "prebuiltVoiceConfig",
                                JSONObject().put("voiceName", voiceName)
                            )
                        )
                    )
            )
        }

        val responseJson = executeGenerateContent(apiKey, modelName, payload)
        val candidates = responseJson.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val partObj = parts.optJSONObject(i) ?: continue
                val inline = partObj.optJSONObject("inlineData") ?: continue
                val dataB64 = inline.optString("data", "")
                val mime = inline.optString("mimeType", "audio/pcm;rate=24000")
                if (dataB64.isNotBlank()) {
                    val bytes = Base64.decode(dataB64, Base64.DEFAULT)
                    if (bytes.size >= 2) {
                        return@withContext DirectTtsAudio(audioBytes = bytes, mimeType = mime)
                    }
                }
            }
        }
        throw IllegalStateException("Gemini TTS returned no audio bytes.")
    }

    private fun executeGenerateContent(
        apiKey: String,
        modelName: String,
        payload: JSONObject
    ): JSONObject {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotBlank()) { "Gemini API key is required." }

        val url = buildGenerateContentUrl(modelName, cleanKey)
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val respText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val googleMsg = extractGoogleErrorMessage(respText) ?: respText.take(280)
                throw DirectGeminiHttpException(
                    statusCode = resp.code,
                    message = "Gemini API HTTP ${resp.code}: $googleMsg"
                )
            }
            return JSONObject(respText)
        }
    }

    private fun extractFirstText(responseJson: JSONObject): String? {
        val candidates = responseJson.optJSONArray("candidates") ?: return null
        val first = candidates.optJSONObject(0) ?: return null
        val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val text = parts.optJSONObject(i)?.optString("text")
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun extractGoogleErrorMessage(body: String): String? {
        return runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
