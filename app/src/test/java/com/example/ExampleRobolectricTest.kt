package com.example

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ai.DirectGeminiClient
import com.example.ai.DirectGeminiHttpException
import com.example.ai.DubAiRepository
import com.example.ai.LocalAndroidTtsSynthesizer
import com.example.data.ApiKeyConfigState
import com.example.data.ApiKeySource
import com.example.data.ApiKeyStore
import com.example.firebase.ApiKeyRequiredException
import com.example.firebase.GeminiResilienceHelper
import com.example.model.ActiveVoiceSelection
import com.example.model.DubVoicePreset
import com.example.model.RecapPlatform
import com.example.model.RecapScript
import com.example.model.SceneBeat
import com.example.model.SubtitlePositionOption
import com.example.model.SubtitleSizeOption
import com.example.model.SubtitleThemeStyle
import com.example.model.VoiceCategory
import com.example.model.WatermarkConfig
import com.example.model.WatermarkPositionPreset
import com.example.ui.ApiKeySettingsDialog
import com.example.ui.VoiceProfileSelectionDialog
import com.example.ui.WatermarkVideoPreviewComponent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RecapScript", appName)
    }

    @Test
    fun `toCreateMap omits id and null directorNotes and sets userId`() {
        val script = RecapScript(
            id = "should_not_serialize",
            sourceTitle = "Mystery Train",
            platform = RecapPlatform.YOUTUBE.firestoreValue,
            genre = "Thriller",
            viralTitles = listOf("Title 1", "Title 2", "Title 3"),
            openingHook = "ဒီရထားပေါ်တက်မိတာ သူ့ဘဝရဲ့ အကြီးမားဆုံး အမှားဖြစ်သွားတယ်။",
            scenes = listOf(
                SceneBeat("00:15", "Passenger enters cabin", "ကျွန်တော်တို့ရဲ့ ဇာတ်လိုက်ကျော်က...")
            ),
            outro = "သင်သာဆိုရင် ဘယ်လိုလုပ်မလဲ Comment မှာ ပြောပြသွားဦးနော်။",
            directorNotes = null
        )

        val payload = script.toCreateMap("user_abc")
        assertFalse(payload.containsKey("id"))
        assertFalse(payload.containsKey("directorNotes"))
        assertEquals("user_abc", payload["userId"])
        assertEquals("YOUTUBE", payload["platform"])
    }

    @Test
    fun `formatFullScriptForExport contains all four Burmese sections`() {
        val script = RecapScript(
            viralTitles = listOf("Title A", "Title B", "Title C"),
            openingHook = "Hook line",
            scenes = listOf(SceneBeat("00:15", "Door opens", "Scene line")),
            outro = "Outro question?"
        )
        val formatted = script.formatFullScriptForExport()
        assertTrue(formatted.contains("Viral Titles"))
        assertTrue(formatted.contains("0:00 - 0:15 Opening Hook"))
        assertTrue(formatted.contains("[00:15] Scene line"))
        assertTrue(formatted.contains("Outro"))
    }

    @Test
    fun `dub segment formats timeline range cleanly`() {
        val seg = com.example.model.DubSegment(
            id = "seg1",
            startMs = 65_000L,
            endMs = 72_500L,
            originalSpeech = "We need to leave right now!",
            burmeseText = "ငါတို့ အခုချက်ချင်း ထွက်သွားရမယ်။"
        )
        assertEquals("01:05 - 01:12", seg.formattedTimeRange)
        assertEquals(7_500L, seg.durationMs)
    }

    @Test
    fun `cache export and temp audio paths live inside dub_exports`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheMp4 = com.example.media.VideoExportStorage.createCacheExportFile(context, "TestDub")
        val tempWav = com.example.media.VideoExportStorage.createTempAudioFile(context, "test.wav")
        assertTrue(cacheMp4.parentFile?.name == "dub_exports")
        assertTrue(cacheMp4.name.startsWith("TestDub_") && cacheMp4.name.endsWith(".mp4"))
        assertTrue(tempWav.parentFile?.name == "dub_exports")
    }

    @Test
    fun `dub segment toSrtString formats SRT timestamps and indices`() {
        val segments = listOf(
            com.example.model.DubSegment(
                id = "1",
                startMs = 65_000L,
                endMs = 72_500L,
                originalSpeech = "Hurry up!",
                burmeseText = "မြန်မြန်လာခဲ့ပါ။"
            )
        )
        val srt = com.example.model.DubSegment.toSrtString(segments)
        assertTrue(srt.contains("00:01:05,000 --> 00:01:12,500"))
        assertTrue(srt.contains("မြန်မြန်လာခဲ့ပါ။"))
    }

    @Test
    fun `voice clone store persists and deletes profiles and watermark defaults to FireRecapScript`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = com.example.data.VoiceCloneStore(context)
        val profile = com.example.model.ClonedVoiceProfile(
            id = "clone_test_1",
            name = "My Recap Voice",
            matchedBaseVoice = "Charon",
            vocalDescription = "Deep warm storyteller",
            speakingSpeed = 1.05f,
            pitchShiftRatio = 0.96f,
            sourceLabel = "Mic Recording"
        )
        val saved = store.saveClonedVoice(profile)
        assertEquals(1, saved.size)
        assertEquals("My Recap Voice", store.loadClonedVoices().first().name)

        val remaining = store.deleteClonedVoice("clone_test_1")
        assertTrue(remaining.isEmpty())

        val watermark = com.example.model.WatermarkConfig()
        assertTrue(watermark.enabled)
        assertEquals("FireRecapScript", watermark.text)
        assertEquals(com.example.model.WatermarkBadgeStyle.OBSIDIAN_GOLD, watermark.badgeStyle)
    }

    @Test
    fun `watermark component toggles, positions, and customizes FireRecapScript`() {
        val viewModel = com.example.ui.VideoDubViewModel()
        viewModel.setWatermarkEnabled(false)
        assertFalse(viewModel.uiState.value.watermarkConfig.enabled)

        viewModel.setWatermarkEnabled(true)
        viewModel.setWatermarkPositionPreset(com.example.model.WatermarkPositionPreset.BOTTOM_LEFT)
        assertEquals(
            com.example.model.WatermarkMotionStyle.FIXED_POSITION,
            viewModel.uiState.value.watermarkConfig.motionStyle
        )
        assertEquals(-0.68f, viewModel.uiState.value.watermarkConfig.customAnchorX, 0.01f)

        viewModel.setWatermarkBadgeStyle(com.example.model.WatermarkBadgeStyle.CRIMSON_FIRE)
        viewModel.setWatermarkScale(1.25f)
        viewModel.setWatermarkText("FireRecapScript HD")
        assertEquals(
            com.example.model.WatermarkBadgeStyle.CRIMSON_FIRE,
            viewModel.uiState.value.watermarkConfig.badgeStyle
        )
        assertEquals(1.25f, viewModel.uiState.value.watermarkConfig.scale, 0.01f)
        assertEquals("FireRecapScript HD", viewModel.uiState.value.watermarkConfig.text)
    }

    @Test
    fun `voice selection dialog renders AI profiles and Clone My Voice option`() {
        var cloneClicked = false
        var selectedPreset: DubVoicePreset? = null

        composeTestRule.setContent {
            com.example.ui.theme.RecapScriptTheme {
                VoiceProfileSelectionDialog(
                    selectedVoice = ActiveVoiceSelection.BuiltIn(DubVoicePreset.PUCK),
                    selectedCategory = VoiceCategory.ALL,
                    clonedVoices = emptyList(),
                    isPreviewingVoice = false,
                    previewingVoiceId = null,
                    onSelectCategory = {},
                    onSelectBuiltIn = { selectedPreset = it },
                    onSelectCloned = {},
                    onDeleteCloned = {},
                    onAuditionSpecificVoice = {},
                    onCloneMyVoiceClick = { cloneClicked = true },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("voice_profile_selection_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clone My Voice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_profile_list")
            .performScrollToNode(hasTestTag("dialog_preset_voice_kore"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_preset_voice_kore").performClick()
        assertEquals(DubVoicePreset.KORE, selectedPreset)

        composeTestRule.onNodeWithTag("dialog_clone_my_voice_option").performClick()
        assertTrue(cloneClicked)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun `watermark video preview component toggles and snaps position`() {
        var toggledEnabled: Boolean? = null
        var snappedPreset: WatermarkPositionPreset? = null

        composeTestRule.setContent {
            com.example.ui.theme.RecapScriptTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    WatermarkVideoPreviewComponent(
                        video = null,
                        subtitleStyle = SubtitleThemeStyle.CINEMA_PILL,
                        subtitleSize = SubtitleSizeOption.MEDIUM,
                        subtitlePosition = SubtitlePositionOption.BOTTOM,
                        burnSubtitles = true,
                        watermarkConfig = WatermarkConfig(),
                        isBusy = false,
                        onWatermarkEnabledChanged = { toggledEnabled = it },
                        onWatermarkTextChanged = {},
                        onWatermarkMotionSelected = {},
                        onWatermarkPresetSelected = { snappedPreset = it },
                        onWatermarkCustomAnchorChanged = { _, _ -> },
                        onWatermarkBadgeStyleSelected = {},
                        onWatermarkScaleChanged = {},
                        onWatermarkOpacityChanged = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("watermark_preview_studio_component").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_watermark_overlay_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_canvas_watermark_toggle").performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, toggledEnabled)

        composeTestRule.onNodeWithTag("watermark_pos_top_left").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(WatermarkPositionPreset.TOP_LEFT, snappedPreset)
    }

    @Test
    fun `resilience helper rejects Firebase dummy error App Check tokens and parses 429 cooldown delay`() {
        // Fake header.payload.sig with {"sub":"appcheck"} -> valid shape
        val validPayload = android.util.Base64.encodeToString(
            """{"sub":"appcheck","exp":9999999999}""".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        assertTrue(com.example.firebase.GeminiResilienceHelper.isLikelyValidJwt("eyJhbGciOiJFUzI1NiJ9.$validPayload.sig"))

        // Firebase SDK dummy error JWT contains {"error":...} in payload
        val dummyErrPayload = android.util.Base64.encodeToString(
            """{"error":"UNKNOWN_ERROR"}""".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        assertFalse(com.example.firebase.GeminiResilienceHelper.isLikelyValidJwt("eyJhbGciOiJub25lIn0.$dummyErrPayload.sig"))
        assertFalse(com.example.firebase.GeminiResilienceHelper.isLikelyValidJwt(""))

        val quotaException = IllegalStateException(
            "Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 3, model: gemini-3.1-flash-tts. Please retry in 19.480145092s."
        )
        assertTrue(com.example.firebase.GeminiResilienceHelper.isQuotaOrRateLimitError(quotaException))
        assertFalse(com.example.firebase.GeminiResilienceHelper.isHardDailyQuotaError(quotaException))
        val delayMs = com.example.firebase.GeminiResilienceHelper.extractRetryDelayMs(quotaException)
        assertEquals(21280L, delayMs)

        val attestationErr = IllegalStateException("Error returned from API. code: 403 body: App attestation failed.")
        assertTrue(com.example.firebase.GeminiResilienceHelper.isAppCheckError(attestationErr))
    }

    @Test
    fun `tts batching reduces 35 beats to at most 2 calls and DSP splits along silence valleys`() {
        val segments = (1..35).map { idx ->
            com.example.model.DubSegment(
                id = "seg_$idx",
                startMs = (idx - 1) * 1800L,
                endMs = idx * 1800L,
                originalSpeech = "Line $idx",
                burmeseText = "ကျောင်းသားလေးက အခန်းထဲကို ဝင်ကြည့်လိုက်တယ်"
            )
        }
        val batches = com.example.ai.BurmeseTtsService.groupSegmentsIntoBatches(segments)
        assertTrue("Expected <= 2 TTS batches for 35 beats, got ${batches.size}", batches.size <= 2)
        assertEquals(35, batches.sumOf { it.size })

        // Build synthetic 24kHz audio containing 3 voiced tone bursts separated by clear 300ms silence valleys
        val sampleRate = com.example.ai.BurmeseTtsService.OUTPUT_SAMPLE_RATE
        val burstSamples = sampleRate / 2 // 500ms voiced per segment
        val gapSamples = (sampleRate * 0.30f).toInt() // 300ms breath pause
        val total = burstSamples * 3 + gapSamples * 2
        val syntheticPcm = ShortArray(total)
        val burstStarts = listOf(0, burstSamples + gapSamples, (burstSamples + gapSamples) * 2)
        for (start in burstStarts) {
            for (i in 0 until burstSamples) {
                syntheticPcm[start + i] = (kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * i / sampleRate) * 9000.0).toInt().toShort()
            }
        }

        val threeSegments = segments.take(3)
        val slices = com.example.ai.BurmeseTtsService.splitBatchAudioBySilenceValleys(
            pcm = syntheticPcm,
            batch = threeSegments,
            sampleRate = sampleRate
        )
        assertEquals(3, slices.size)
        slices.forEach { slice ->
            assertTrue("Expected non-empty audio slice", slice.size > sampleRate / 4)
        }
    }

    @Test
    fun `api key store persists custom key cleanly and masks preview`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ApiKeyStore.init(context)
        ApiKeyStore.clearUserApiKey()

        assertNull(ApiKeyStore.sanitizeKey("  YOUR_GEMINI_API_KEY "))
        assertEquals("AIzaSyTest1234", ApiKeyStore.sanitizeKey("  AIzaSyTest1234  "))
        assertEquals("AIzaSy••••1234", ApiKeyStore.maskKey("AIzaSyTestKeyValue1234"))

        ApiKeyStore.saveUserApiKey("AIzaSyCustomApkKey9876")
        assertEquals("AIzaSyCustomApkKey9876", ApiKeyStore.getEffectiveApiKey())
        assertEquals(ApiKeySource.USER_CUSTOM, ApiKeyStore.state.value.source)
        assertTrue(ApiKeyStore.state.value.hasCustomKey)

        ApiKeyStore.clearUserApiKey()
        assertNull(ApiKeyStore.getUserCustomKey())
    }

    @Test
    fun `direct gemini client url builder and resilience fallback detection handle 403 cleanly`() {
        val url = DirectGeminiClient.buildGenerateContentUrl("gemini-2.5-flash", "AIzaKey123")
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaKey123",
            url
        )

        val emptyResult = runBlocking { DirectGeminiClient.testApiKey("   ") }
        assertTrue(emptyResult.isFailure)

        val err403 = DirectGeminiHttpException(403, "Gemini API HTTP 403: PERMISSION_DENIED")
        assertTrue(GeminiResilienceHelper.isFallbackEligibleError(err403))
        assertTrue(GeminiResilienceHelper.isApiKeyRejectedError(err403))

        val keyMissing = ApiKeyRequiredException("No API key")
        assertTrue(GeminiResilienceHelper.isFallbackEligibleError(keyMissing))
    }

    @Test
    fun `client fallback dubbing plan and soft narration PCM work offline`() {
        val plan = DubAiRepository.buildClientFallbackPlan(
            totalDurationMs = 12_000L,
            clipTitleHint = "Action Trailer"
        )
        assertEquals("Client Fallback", plan.detectedLanguage)
        assertTrue(plan.segments.isNotEmpty())
        for (i in 1 until plan.segments.size) {
            assertTrue(plan.segments[i].startMs >= plan.segments[i - 1].endMs)
        }
        assertTrue(plan.segments.last().endMs <= 12_000L)

        val cue = LocalAndroidTtsSynthesizer.generateSoftNarrationCuePcm(
            durationMs = 900L,
            sampleRate = 24_000,
            pitchFactor = 1.0f
        )
        assertTrue(cue.size >= 10_000)
        assertTrue(cue.any { it != 0.toShort() })
    }

    @Test
    fun `api key settings dialog saves pasted key into ApiKeyStore`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ApiKeyStore.init(context)
        ApiKeyStore.clearUserApiKey()

        var savedCallbackMsg: String? = null
        var dismissed = false

        composeTestRule.setContent {
            com.example.ui.theme.RecapScriptTheme {
                ApiKeySettingsDialog(
                    apiKeyState = ApiKeyConfigState(source = ApiKeySource.NONE),
                    onSaved = { savedCallbackMsg = it },
                    onCleared = {},
                    onDismiss = { dismissed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("api_key_settings_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("api_key_input").performTextInput("AIzaSyUiTestKey9999")
        composeTestRule.onNodeWithTag("save_api_key_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("AIzaSyUiTestKey9999", ApiKeyStore.getUserCustomKey())
        assertEquals("Gemini API Key saved locally", savedCallbackMsg)
        assertTrue(dismissed)

        ApiKeyStore.clearUserApiKey()
    }
}
