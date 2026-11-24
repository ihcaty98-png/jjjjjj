package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ApiKeySource {
    NONE,
    ENVIRONMENT,
    USER_CUSTOM
}

data class ApiKeyConfigState(
    val source: ApiKeySource = ApiKeySource.NONE,
    val maskedPreview: String = "",
    val hasCustomKey: Boolean = false
) {
    val hasEffectiveKey: Boolean
        get() = source != ApiKeySource.NONE
}

/**
 * Persists user-provided Gemini API keys in SharedPreferences (Android equivalent of localStorage)
 * and resolves the effective API key with priority:
 * 1) User-saved key from API Key / Settings modal
 * 2) Environment BuildConfig.GEMINI_API_KEY (from Secrets / .env)
 */
object ApiKeyStore {
    private const val PREFS_NAME = "recapscript_api_settings"
    private const val KEY_USER_GEMINI_API_KEY = "user_gemini_api_key"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var inMemoryCustomKey: String? = null

    private val _state = MutableStateFlow(buildState(null))
    val state: StateFlow<ApiKeyConfigState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
        val stored = prefs?.getString(KEY_USER_GEMINI_API_KEY, null)?.trim()?.takeIf { it.isNotBlank() }
        inMemoryCustomKey = stored
        _state.value = buildState(stored)
    }

    fun getUserCustomKey(): String? {
        val stored = prefs?.getString(KEY_USER_GEMINI_API_KEY, null) ?: inMemoryCustomKey
        return sanitizeKey(stored)
    }

    fun getEnvironmentKey(): String? {
        val raw = runCatching {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String
        }.getOrNull()
        return sanitizeKey(raw)
    }

    fun getEffectiveApiKey(): String? {
        return getUserCustomKey() ?: getEnvironmentKey()
    }

    fun hasEffectiveApiKey(): Boolean = getEffectiveApiKey() != null

    fun saveUserApiKey(rawKey: String) {
        val clean = sanitizeKey(rawKey)
        inMemoryCustomKey = clean
        prefs?.edit()?.apply {
            if (clean != null) {
                putString(KEY_USER_GEMINI_API_KEY, clean)
            } else {
                remove(KEY_USER_GEMINI_API_KEY)
            }
            commit()
        }
        _state.value = buildState(clean)
    }

    fun clearUserApiKey() {
        inMemoryCustomKey = null
        prefs?.edit()?.remove(KEY_USER_GEMINI_API_KEY)?.commit()
        _state.value = buildState(null)
    }

    internal fun sanitizeKey(raw: String?): String? {
        val trimmed = raw?.trim()
            ?.trim('"', '\'')
            ?.replace("\n", "")
            ?.replace("\r", "")
            ?.trim()
            .orEmpty()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase()
        if (lower.contains("your_") || lower.contains("default_value") || lower == "null") {
            return null
        }
        return trimmed
    }

    internal fun maskKey(key: String?): String {
        if (key.isNullOrBlank()) return ""
        return if (key.length <= 10) {
            "••••${key.takeLast(4)}"
        } else {
            "${key.take(6)}••••${key.takeLast(4)}"
        }
    }

    private fun buildState(customKey: String?): ApiKeyConfigState {
        val cleanCustom = sanitizeKey(customKey)
        if (cleanCustom != null) {
            return ApiKeyConfigState(
                source = ApiKeySource.USER_CUSTOM,
                maskedPreview = maskKey(cleanCustom),
                hasCustomKey = true
            )
        }
        val envKey = getEnvironmentKey()
        if (envKey != null) {
            return ApiKeyConfigState(
                source = ApiKeySource.ENVIRONMENT,
                maskedPreview = maskKey(envKey),
                hasCustomKey = false
            )
        }
        return ApiKeyConfigState(
            source = ApiKeySource.NONE,
            maskedPreview = "",
            hasCustomKey = false
        )
    }
}
