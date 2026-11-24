package com.example.firebase

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.ai.DirectGeminiHttpException
import com.example.data.ApiKeyStore
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.math.ceil

private const val TAG = "GeminiResilience"

class ApiKeyRequiredException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

private data class AppCheckFetchAttempt(
    val token: String?,
    val errorDetail: String?
)

/**
 * Shared resilience layer for all Gemini calls (Direct REST API or Firebase AI Logic).
 * Guarantees:
 * 1) Completely bypasses Firebase App Check when a direct Gemini API key is configured.
 * 2) When running via Firebase AI Logic without an API key, checks App Check cleanly without getting stuck.
 * 3) Intelligent 429 quota cooldown parsing ("Please retry in Xs") and transient capacity backoff.
 */
object GeminiResilienceHelper {

    @Volatile
    private var lastAppCheckFailureReason: String? = null

    suspend fun ensureValidAppCheckToken(context: Context? = null): Boolean {
        if (ApiKeyStore.hasEffectiveApiKey()) {
            lastAppCheckFailureReason = null
            return true
        }
        if (context != null) {
            ensureAppCheckConfigured(context.applicationContext)
        }
        val firstAttempt = fetchAppCheckToken(forceRefresh = false)
        if (isLikelyValidJwt(firstAttempt.token)) {
            lastAppCheckFailureReason = null
            return true
        }
        lastAppCheckFailureReason = firstAttempt.errorDetail

        Log.w(
            TAG,
            "App Check token missing/invalid (${firstAttempt.errorDetail ?: "no detail"}); forcing refresh..."
        )
        val isAttestationRejected = firstAttempt.errorDetail?.let { detail ->
            val lower = detail.lowercase()
            lower.contains("attestation failed") || lower.contains("permission_denied") || lower.contains("403")
        } == true

        val refreshDelays = if (isAttestationRejected) {
            listOf(400L)
        } else {
            listOf(350L, 1100L)
        }
        for ((idx, waitMs) in refreshDelays.withIndex()) {
            delay(waitMs)
            if (isAttestationRejected || idx == 1) {
                resetAndReinstallAppCheck()
            }
            val refreshed = fetchAppCheckToken(forceRefresh = true)
            if (isLikelyValidJwt(refreshed.token)) {
                lastAppCheckFailureReason = null
                Log.d(TAG, "App Check token refreshed cleanly on attempt ${idx + 1}.")
                return true
            }
            if (!refreshed.errorDetail.isNullOrBlank()) {
                lastAppCheckFailureReason = refreshed.errorDetail
            }
        }
        return false
    }

    suspend fun forceRefreshAppCheckToken(): Boolean {
        if (ApiKeyStore.hasEffectiveApiKey()) {
            lastAppCheckFailureReason = null
            return true
        }
        val attempt1 = fetchAppCheckToken(forceRefresh = true)
        if (isLikelyValidJwt(attempt1.token)) {
            lastAppCheckFailureReason = null
            return true
        }
        lastAppCheckFailureReason = attempt1.errorDetail

        resetAndReinstallAppCheck()
        delay(600L)

        val attempt2 = fetchAppCheckToken(forceRefresh = true)
        if (isLikelyValidJwt(attempt2.token)) {
            lastAppCheckFailureReason = null
            return true
        }
        if (!attempt2.errorDetail.isNullOrBlank()) {
            lastAppCheckFailureReason = attempt2.errorDetail
        }
        return false
    }

    private suspend fun fetchAppCheckToken(forceRefresh: Boolean): AppCheckFetchAttempt {
        return try {
            withTimeout(15_000L) {
                suspendCancellableCoroutine { cont ->
                    FirebaseAppCheck.getInstance()
                        .getAppCheckToken(forceRefresh)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) {
                                cont.resume(AppCheckFetchAttempt(token = result.token, errorDetail = null))
                            }
                        }
                        .addOnFailureListener { err ->
                            val detail = buildString {
                                append(err.message ?: err::class.java.simpleName)
                                err.cause?.message?.takeIf { it.isNotBlank() }?.let {
                                    append(" -> ").append(it)
                                }
                            }
                            Log.w(TAG, "getAppCheckToken(force=$forceRefresh) failed: $detail")
                            if (cont.isActive) {
                                cont.resume(AppCheckFetchAttempt(token = null, errorDetail = detail))
                            }
                        }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchAppCheckToken exception: ${e.message}")
            AppCheckFetchAttempt(token = null, errorDetail = e.message)
        }
    }

    internal fun isLikelyValidJwt(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val parts = token.split('.')
        if (parts.size != 3 || parts[0].length < 4 || parts[1].length < 8) return false
        return try {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val payloadJson = String(payloadBytes, StandardCharsets.UTF_8)
            !payloadJson.contains("\"error\"")
        } catch (_: Exception) {
            !token.contains("error", ignoreCase = true)
        }
    }

    suspend fun <T> executeWithResilience(
        maxAttempts: Int = 5,
        timeoutMs: Long = 60_000L,
        useDirectApiKey: Boolean = ApiKeyStore.hasEffectiveApiKey(),
        onStatusUpdate: (String) -> Unit = {},
        block: suspend (attemptIndex: Int) -> T
    ): T {
        if (!useDirectApiKey) {
            val tokenReady = ensureValidAppCheckToken()
            if (!tokenReady) {
                throw ApiKeyRequiredException(
                    "Debug APK အတွက် ကိုယ်ပိုင် Gemini API Key ထည့်သွင်းပေးပါ (ထိပ်ဆုံး Bar မှ API Key ခလုတ်ကို နှိပ်ပါ)။"
                )
            }
        }

        var lastError: Exception? = null
        var attempt = 0
        while (attempt < maxAttempts) {
            try {
                return withTimeout(timeoutMs) {
                    block(attempt)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Gemini call timed out on attempt ${attempt + 1}", e)
                lastError = IllegalStateException("AI request timed out. Please try again.", e)
                if (attempt >= maxAttempts - 1) throw lastError
                onStatusUpdate("Connection နှေးနေသဖြင့် ထပ်မံချိတ်ဆက်ပေးနေပါတယ်...")
                delay(1500L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini call attempt ${attempt + 1} failed: ${e.message}", e)
                lastError = e

                if (attempt >= maxAttempts - 1 || isHardDailyQuotaError(e)) {
                    throw wrapUserFriendlyException(e)
                }

                when {
                    !useDirectApiKey && isAppCheckError(e) -> {
                        throw ApiKeyRequiredException(
                            "Debug APK အတွက် ကိုယ်ပိုင် Gemini API Key ထည့်သွင်းပေးပါ (ထိပ်ဆုံး Bar မှ API Key ခလုတ်ကို နှိပ်ပါ)။",
                            e
                        )
                    }
                    useDirectApiKey && isApiKeyRejectedError(e) -> {
                        throw wrapUserFriendlyException(e)
                    }
                    isQuotaOrRateLimitError(e) -> {
                        val waitMs = extractRetryDelayMs(e) ?: (22_000L + attempt * 5_000L).coerceAtMost(62_000L)
                        val waitSec = ceil(waitMs / 1000.0).toInt()
                        onStatusUpdate(
                            "AI Quota Limit ပြည့်နေသဖြင့် ${waitSec}s အလိုအလျောက် စောင့်ပြီး ဆက်လုပ်ပေးနေပါတယ်..."
                        )
                        delay(waitMs)
                    }
                    isTransientCapacityError(e) -> {
                        val waitMs = when (attempt) {
                            0 -> 1200L
                            1 -> 2500L
                            else -> 4000L
                        }
                        onStatusUpdate("Server ဝန်များနေသဖြင့် ခဏစောင့်ပြီး ပြန်လည်ချိတ်ဆက်နေပါတယ်...")
                        delay(waitMs)
                    }
                    else -> throw wrapUserFriendlyException(e)
                }
            }
            attempt++
        }
        throw wrapUserFriendlyException(lastError ?: IllegalStateException("AI request failed."))
    }

    fun isAppCheckError(e: Exception): Boolean {
        val msg = collectErrorMessage(e).lowercase()
        return msg.contains("app check") ||
            msg.contains("appcheck") ||
            msg.contains("attestation failed") ||
            msg.contains("token is invalid") ||
            (msg.contains("403") && (msg.contains("forbidden") || msg.contains("permission_denied")))
    }

    fun isApiKeyRejectedError(e: Exception): Boolean {
        if (e is DirectGeminiHttpException && (e.statusCode == 400 || e.statusCode == 401 || e.statusCode == 403)) {
            val lower = e.message.lowercase()
            return lower.contains("api key") || lower.contains("permission") || e.statusCode == 403 || e.statusCode == 401
        }
        val msg = collectErrorMessage(e).lowercase()
        return msg.contains("api key not valid") ||
            msg.contains("api_key_invalid") ||
            (msg.contains("403") && !msg.contains("app check"))
    }

    fun isFallbackEligibleError(e: Exception): Boolean {
        if (e is ApiKeyRequiredException) return true
        if (e is DirectGeminiHttpException && (e.statusCode == 400 || e.statusCode == 403 || e.statusCode == 401 || e.statusCode == 404 || e.statusCode == 429)) {
            return true
        }
        return isAppCheckError(e) ||
            isApiKeyRejectedError(e) ||
            isQuotaOrRateLimitError(e) ||
            collectErrorMessage(e).lowercase().contains("403")
    }

    fun isQuotaOrRateLimitError(e: Exception): Boolean {
        val msg = collectErrorMessage(e).lowercase()
        return msg.contains("quota") ||
            msg.contains("rate limit") ||
            msg.contains("rate-limit") ||
            msg.contains("429") ||
            msg.contains("resource exhausted") ||
            msg.contains("please retry in")
    }

    fun isTransientCapacityError(e: Exception): Boolean {
        val msg = collectErrorMessage(e).lowercase()
        return msg.contains("high demand") ||
            msg.contains("spikes in demand") ||
            msg.contains("temporarily") ||
            msg.contains("unavailable") ||
            msg.contains("overloaded") ||
            msg.contains("try again") ||
            msg.contains("503") ||
            msg.contains("500") ||
            msg.contains("404") ||
            msg.contains("not found") ||
            msg.contains("internal") ||
            msg.contains("timeout") ||
            msg.contains("connection reset")
    }

    fun isHardDailyQuotaError(e: Exception): Boolean {
        val msg = collectErrorMessage(e).lowercase()
        val hasZeroLimit = msg.contains("limit: 0")
        val isDailyExhausted = (msg.contains("per_day") || msg.contains("perday")) && extractRetryDelayMs(e) == null
        return hasZeroLimit || isDailyExhausted
    }

    fun extractRetryDelayMs(e: Exception): Long? {
        val msg = collectErrorMessage(e)
        val match = Regex("""retry\s+in\s+(\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE).find(msg)
        val sec = match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
        return ((sec * 1000.0).toLong() + 1800L).coerceIn(2500L, 65_000L)
    }

    fun wrapUserFriendlyException(e: Exception): Exception {
        if (e is ApiKeyRequiredException) return e
        val raw = e.message.orEmpty()
        return when {
            isAppCheckError(e) -> ApiKeyRequiredException(
                "ထိပ်ဆုံး Bar ရှိ API Key ခလုတ်ကိုနှိပ်ပြီး ကိုယ်ပိုင် Gemini API Key ထည့်သွင်းအသုံးပြုပါ (သို့မဟုတ် Video Dub တွင် Client-Side Fallback ဖြင့် ဆက်လုပ်နိုင်ပါသည်)။\n($raw)",
                e
            )
            isApiKeyRejectedError(e) -> ApiKeyRequiredException(
                "ထည့်သွင်းထားသော Gemini API Key ကို Google မှ လက်မခံပါ (403/Invalid)။ API Key / Settings တွင် Key အသစ်စစ်ဆေးထည့်သွင်းပါ။\n($raw)",
                e
            )
            isQuotaOrRateLimitError(e) -> {
                val sec = extractRetryDelayMs(e)?.let { ceil(it / 1000.0).toInt() }
                val waitHint = if (sec != null) "${sec} စက္ကန့်" else "၁ မိနစ်ခန့်"
                IllegalStateException(
                    "Free AI Quota တစ်မိနစ်အတွင်း အသုံးများသွားပါသည်။ $waitHint စောင့်ပြီး Try Again ထပ်နှိပ်ပေးပါ။",
                    e
                )
            }
            isTransientCapacityError(e) -> IllegalStateException(
                "AI Server ယာယီ ဝန်များနေပါသည်။ ခဏနေ Try Again ပြန်နှိပ်ပေးပါ။",
                e
            )
            else -> e
        }
    }

    private fun collectErrorMessage(e: Exception): String = buildString {
        var curr: Throwable? = e
        var depth = 0
        while (curr != null && depth < 5) {
            append(curr.message.orEmpty())
            append(' ')
            curr = curr.cause
            depth++
        }
    }
}
