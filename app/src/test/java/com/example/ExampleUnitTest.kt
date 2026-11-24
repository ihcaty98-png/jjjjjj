package com.example

import com.example.ai.BurmeseTtsService
import com.example.ai.DirectGeminiClient
import com.example.ai.DirectGeminiHttpException
import com.example.ai.DubAiRepository
import com.example.ai.LocalAndroidTtsSynthesizer
import com.example.data.ApiKeyStore
import com.example.firebase.ApiKeyRequiredException
import com.example.firebase.GeminiResilienceHelper
import com.example.model.AppConfig
import com.example.model.BillingConstants
import com.example.model.DubSegment
import com.example.model.GenerationQuotaDecision
import com.example.model.QuotaPolicy
import com.example.model.UserAccount
import com.example.model.VipTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local JVM unit tests validating direct Gemini API routing, API key sanitization/storage
 * helpers, 400/403/fallback routing, client-side subtitle plan generation, DSP audio math,
 * and Free 3-day trial / Coins / VIP V1/V2/V3 / Admin quota rules.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun apiKeySanitizeAndMaskWorkWithoutAndroidContext() {
        assertNull(ApiKeyStore.sanitizeKey("  "))
        assertNull(ApiKeyStore.sanitizeKey("GEMINI_API_KEY_DEFAULT_VALUE"))
        assertNull(ApiKeyStore.sanitizeKey("YOUR_API_KEY_HERE"))
        assertEquals("AIzaSyValidKey9876", ApiKeyStore.sanitizeKey("  \"AIzaSyValidKey9876\"  "))
        assertEquals("AIzaSy••••9876", ApiKeyStore.maskKey("AIzaSyTestExample9876"))
    }

    @Test
    fun directGeminiUrlAndEmptyKeyValidationWork() {
        val url = DirectGeminiClient.buildGenerateContentUrl("gemini-2.5-flash", "AIzaKey123")
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaKey123",
            url
        )

        val emptyRes = runBlocking { DirectGeminiClient.testApiKey("   ") }
        assertTrue(emptyRes.isFailure)
    }

    @Test
    fun fallbackEligibilityMatchesHttp400And403AndApiKeyErrors() {
        val err400 = DirectGeminiHttpException(400, "Gemini API HTTP 400: API key not valid.")
        assertTrue(GeminiResilienceHelper.isFallbackEligibleError(err400))
        assertTrue(GeminiResilienceHelper.isApiKeyRejectedError(err400))

        val err403 = DirectGeminiHttpException(403, "Gemini API HTTP 403: PERMISSION_DENIED")
        assertTrue(GeminiResilienceHelper.isFallbackEligibleError(err403))
        assertTrue(GeminiResilienceHelper.isApiKeyRejectedError(err403))

        val attestationErr = IllegalStateException("Error returned from API. code: 403 body: App attestation failed.")
        assertTrue(GeminiResilienceHelper.isFallbackEligibleError(attestationErr))

        val missingKey = ApiKeyRequiredException("API key required")
        assertTrue(GeminiResilienceHelper.isFallbackEligibleError(missingKey))

        val unrelated = IllegalArgumentException("Invalid subtitle format")
        assertFalse(GeminiResilienceHelper.isFallbackEligibleError(unrelated))
    }

    @Test
    fun clientFallbackPlanAndOfflinePcmCueAreValid() {
        val plan = DubAiRepository.buildClientFallbackPlan(
            totalDurationMs = 90_000L,
            clipTitleHint = "Sample 90s Clip"
        )
        assertEquals("Client Fallback", plan.detectedLanguage)
        assertTrue("Expected > 15 beats for a 90s video", plan.segments.size >= 15)
        for (i in 1 until plan.segments.size) {
            assertTrue(plan.segments[i].startMs >= plan.segments[i - 1].endMs)
        }
        assertEquals(90_000L, plan.segments.last().endMs)
        assertFalse(DubSegment.hasTrailingCutoff(plan.segments, 90_000L))
        assertTrue(DubSegment.computeTimelineCoveragePercent(plan.segments, 90_000L) >= 95)

        val cue = LocalAndroidTtsSynthesizer.generateSoftNarrationCuePcm(
            durationMs = 800L,
            sampleRate = 24_000,
            pitchFactor = 1.0f
        )
        assertTrue(cue.size >= 10_000)
        assertTrue(cue.any { it != 0.toShort() })
    }

    @Test
    fun ensureContinuousCoverageBridgesGapsAndTrailingCutoffs() {
        val sparseSegments = listOf(
            DubSegment("a", 4_000L, 6_500L, "Hello", "မင်္ဂလာပါ"),
            DubSegment("b", 15_000L, 18_000L, "Run!", "မြန်မြန်ပြေးကြစို့")
        )
        assertTrue(DubSegment.hasTrailingCutoff(sparseSegments, 45_000L))
        val covered = DubAiRepository.ensureContinuousCoverage(
            segments = sparseSegments,
            totalDurationMs = 45_000L,
            videoSummaryBurmese = "ဇာတ်လိုက်က ထွက်ပြေးဖို့ ကြိုးစားနေပါတယ်",
            clipTitleHint = "Action Chase"
        )
        assertFalse(DubSegment.hasTrailingCutoff(covered, 45_000L))
        assertTrue(covered.first().startMs <= 500L)
        assertTrue(covered.last().endMs >= 44_500L)
        assertTrue(DubSegment.computeTimelineCoveragePercent(covered, 45_000L) >= 90)
        for (i in 1 until covered.size) {
            assertTrue(covered[i].startMs >= covered[i - 1].endMs)
        }
    }

    @Test
    fun ttsBatchingAndSrtFormattingAreConsistent() {
        val segments = (1..35).map { idx ->
            DubSegment(
                id = "seg_$idx",
                startMs = (idx - 1) * 1800L,
                endMs = idx * 1800L,
                originalSpeech = "Line $idx",
                burmeseText = "ကျောင်းသားလေးက အခန်းထဲကို ဝင်ကြည့်လိုက်တယ်"
            )
        }
        val batches = BurmeseTtsService.groupSegmentsIntoBatches(segments)
        assertTrue(batches.all { it.size <= 6 && it.sumOf { s -> s.burmeseText.trim().length } <= 420 })
        assertEquals(35, batches.sumOf { it.size })

        // Verify non-truncating slot fitting up to 1.65x compression ratio
        val inputPcm = ShortArray(36_000) { i -> ((i % 200) * 80).toShort() }
        val fitted = BurmeseTtsService.fitSamplesToSlot(inputPcm, 24_000)
        assertEquals(24_000, fitted.size)
        assertTrue(fitted.any { it != 0.toShort() })

        val srt = DubSegment.toSrtString(segments.take(1))
        assertTrue(srt.contains("00:00:00,000 --> 00:00:01,800"))
    }

    @Test
    fun freeThreeDayTrialCoinsVipAndAdminQuotaRulesWork() {
        val config = AppConfig(coinCostPerVideo = 10)
        val dayMs = 24L * 60L * 60L * 1000L
        val day1Ms = 1_795_400_000_000L
        val day1Key = QuotaPolicy.todayDateKeyYangon(day1Ms)
        val day2Key = QuotaPolicy.todayDateKeyYangon(day1Ms + dayMs)
        val day3Key = QuotaPolicy.todayDateKeyYangon(day1Ms + 2 * dayMs)
        val day4Ms = day1Ms + 3 * dayMs

        // Day 1, 1st video allowed via Free Trial
        val freeAccount = UserAccount(
            userId = "u1",
            email = "creator@gmail.com",
            coins = 0,
            trialStartDate = day1Key,
            freeTrialDates = emptyList()
        )
        val d1First = QuotaPolicy.evaluateGenerationAccess(freeAccount, config, day1Ms)
        assertTrue(d1First is GenerationQuotaDecision.AllowedFreeTrial)

        // Day 1, 2nd video on same day is blocked when coins == 0
        val afterDay1 = freeAccount.copy(freeTrialDates = listOf(day1Key))
        val d1Second = QuotaPolicy.evaluateGenerationAccess(afterDay1, config, day1Ms)
        assertTrue(d1Second is GenerationQuotaDecision.Denied)
        assertFalse((d1Second as GenerationQuotaDecision.Denied).isTrialExpired)

        // Day 2 & Day 3 allowed once each
        assertTrue(QuotaPolicy.evaluateGenerationAccess(afterDay1, config, day1Ms + dayMs) is GenerationQuotaDecision.AllowedFreeTrial)
        val afterDay2 = afterDay1.copy(freeTrialDates = listOf(day1Key, day2Key))
        assertTrue(QuotaPolicy.evaluateGenerationAccess(afterDay2, config, day1Ms + 2 * dayMs) is GenerationQuotaDecision.AllowedFreeTrial)

        // Day 4 -> permanently expired, must buy Coins or VIP
        val afterDay3 = afterDay1.copy(freeTrialDates = listOf(day1Key, day2Key, day3Key))
        val d4Denied = QuotaPolicy.evaluateGenerationAccess(afterDay3, config, day4Ms)
        assertTrue(d4Denied is GenerationQuotaDecision.Denied)
        assertTrue((d4Denied as GenerationQuotaDecision.Denied).isTrialExpired)

        // Buying Coins allows generation and deducts costCoins
        val withCoins = afterDay3.copy(coins = 25)
        val coinDecision = QuotaPolicy.evaluateGenerationAccess(withCoins, config, day4Ms)
        assertTrue(coinDecision is GenerationQuotaDecision.AllowedCoins)
        assertEquals(15, (coinDecision as GenerationQuotaDecision.AllowedCoins).remainingCoinsAfter)

        // VIP V1 (1mo = 45,000), V2 (2mo = 56,000), V3 (7mo = 150,000)
        assertEquals(45_000, VipTier.VIP_V1.defaultPriceMmk)
        assertEquals(1, VipTier.VIP_V1.durationMonths)
        assertEquals(56_000, VipTier.VIP_V2.defaultPriceMmk)
        assertEquals(2, VipTier.VIP_V2.durationMonths)
        assertEquals(150_000, VipTier.VIP_V3.defaultPriceMmk)
        assertEquals(7, VipTier.VIP_V3.durationMonths)

        val v2Expiry = QuotaPolicy.computeExtendedVipExpiryMs(0L, VipTier.VIP_V2.durationMonths, day1Ms)
        val vipUser = afterDay3.copy(vipTier = VipTier.VIP_V2.firestoreValue, vipExpiresAtMs = v2Expiry)
        assertTrue(QuotaPolicy.evaluateGenerationAccess(vipUser, config, day4Ms) is GenerationQuotaDecision.AllowedVip)

        // Admin email gate
        assertTrue(QuotaPolicy.isAdminEmail("firechatmyanmar@gmail.com"))
        assertTrue(QuotaPolicy.isAdminEmail("FireChatMyanmar@GMAIL.COM"))
        assertFalse(QuotaPolicy.isAdminEmail("other@gmail.com"))
        val adminAcc = UserAccount(userId = "adm", email = BillingConstants.ADMIN_EMAIL)
        assertTrue(QuotaPolicy.evaluateGenerationAccess(adminAcc, config, day4Ms) is GenerationQuotaDecision.AllowedAdmin)
    }

    @Test
    fun studioBackgroundStylesCopyrightAndProgressAreValid() {
        assertEquals(5, com.example.data.StudioBackgroundStyle.entries.size)
        com.example.data.StudioBackgroundStyle.entries.forEach { style ->
            assertEquals(style, com.example.data.StudioBackgroundStyle.fromId(style.id))
        }

        val defaultShield = com.example.model.CopyrightShieldConfig()
        assertTrue(defaultShield.enabled)
        assertEquals(com.example.model.CopyrightShieldPreset.BALANCED_SAFE, defaultShield.preset)
        assertTrue(defaultShield.zoomScale > 1.0f)

        val step1 = com.example.ui.DubPipelineState.Translating("Translating...", progressPercent = 18)
        val step2 = com.example.ui.DubPipelineState.SynthesizingVoice(completed = 5, total = 10, currentLine = "Line")
        val step3Start = com.example.ui.DubPipelineState.RenderingVideo(progressPercent = 0)
        val step3End = com.example.ui.DubPipelineState.RenderingVideo(progressPercent = 100)
        assertEquals(18, step1.progressPercent)
        assertTrue(step2.overallProgressPercent in 34..70)
        assertEquals(70, step3Start.overallProgressPercent)
        assertEquals(100, step3End.overallProgressPercent)
    }
}


