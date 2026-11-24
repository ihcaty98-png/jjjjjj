package com.example.model

import android.net.Uri

enum class VoiceCategory(val label: String) {
    ALL("All"),
    MALE("Male"),
    FEMALE("Female"),
    CLONED("Voice Clone")
}

enum class DubVoicePreset(
    val id: String,
    val voiceName: String,
    val displayName: String,
    val burmeseLabel: String,
    val isMale: Boolean
) {
    PUCK(
        id = "puck",
        voiceName = "Puck",
        displayName = "Puck • Energetic Male",
        burmeseLabel = "သွက်လက်တက်ကြွ Recap အသံ",
        isMale = true
    ),
    CHARON(
        id = "charon",
        voiceName = "Charon",
        displayName = "Charon • Natural Male",
        burmeseLabel = "တကယ့်လူသဘာဝ အမျိုးသားအသံ",
        isMale = true
    ),
    ORUS(
        id = "orus",
        voiceName = "Orus",
        displayName = "Orus • Deep Cinema",
        burmeseLabel = "တည်ကြည်လေးနက် ရုပ်ရှင်အသံ",
        isMale = true
    ),
    FENRIR(
        id = "fenrir",
        voiceName = "Fenrir",
        displayName = "Fenrir • Action Narrator",
        burmeseLabel = "စိတ်လှုပ်ရှားဖွယ် Action အသံ",
        isMale = true
    ),
    ALGENIB(
        id = "algenib",
        voiceName = "Algenib",
        displayName = "Algenib • Raspy Thriller",
        burmeseLabel = "သည်းထိတ်ရင်ဖို အသံသြသြ",
        isMale = true
    ),
    ZUBENELGENUBI(
        id = "zubenelgenubi",
        voiceName = "Zubenelgenubi",
        displayName = "Zuben • Casual Storyteller",
        burmeseLabel = "ဘေးနားကပြောပြသလို ပေါ့ပေါ့ပါးပါး",
        isMale = true
    ),
    KORE(
        id = "kore",
        voiceName = "Kore",
        displayName = "Kore • Clear Female",
        burmeseLabel = "ကြည်လင်ပြတ်သား အမျိုးသမီးအသံ",
        isMale = false
    ),
    SULAFAT(
        id = "sulafat",
        voiceName = "Sulafat",
        displayName = "Sulafat • Warm Natural",
        burmeseLabel = "ချိုသာနွေးထွေး သဘာဝအသံ",
        isMale = false
    ),
    LEDA(
        id = "leda",
        voiceName = "Leda",
        displayName = "Leda • Youthful Female",
        burmeseLabel = "နုပျိုသွက်လက် မိန်းကလေးအသံ",
        isMale = false
    ),
    ZEPHYR(
        id = "zephyr",
        voiceName = "Zephyr",
        displayName = "Zephyr • Bright TikTok",
        burmeseLabel = "TikTok / Reels ခေတ်ဆန်အသံ",
        isMale = false
    )
}

data class ClonedVoiceProfile(
    val id: String,
    val name: String,
    val matchedBaseVoice: String,
    val vocalDescription: String,
    val speakingSpeed: Float = 1.0f,
    val pitchShiftRatio: Float = 1.0f,
    val sourceLabel: String = "Voice Sample",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

sealed interface ActiveVoiceSelection {
    val displayTitle: String
    val baseVoiceName: String
    val pitchShiftRatio: Float
    val defaultSpeed: Float

    data class BuiltIn(val preset: DubVoicePreset) : ActiveVoiceSelection {
        override val displayTitle: String = preset.displayName
        override val baseVoiceName: String = preset.voiceName
        override val pitchShiftRatio: Float = 1.0f
        override val defaultSpeed: Float = 1.0f
    }

    data class Cloned(val profile: ClonedVoiceProfile) : ActiveVoiceSelection {
        override val displayTitle: String = "${profile.name} (Clone • ${profile.matchedBaseVoice})"
        override val baseVoiceName: String = profile.matchedBaseVoice
        override val pitchShiftRatio: Float = profile.pitchShiftRatio
        override val defaultSpeed: Float = profile.speakingSpeed
    }
}

enum class SubtitleThemeStyle(
    val id: String,
    val displayName: String,
    val burmeseLabel: String
) {
    CINEMA_PILL("pill", "Dark Pill", "အနက်ခံ စာတန်းဖြူ"),
    GOLD_CINEMA("gold", "Gold Cinema", "ရွှေဝါရောင် ရုပ်ရှင်"),
    CRIMSON_VIRAL("crimson", "Crimson Viral", "အနီရောင် TikTok"),
    CLEAN_SHADOW("clean", "Clean Shadow", "အဖြူစင် အရိပ်ပါ")
}

enum class SubtitleSizeOption(
    val id: String,
    val label: String,
    val scaleFactor: Float
) {
    COMPACT("compact", "Small", 0.86f),
    MEDIUM("medium", "Medium", 1.0f),
    LARGE("large", "Large", 1.15f)
}

enum class SubtitlePositionOption(
    val id: String,
    val label: String,
    val anchorY: Float
) {
    BOTTOM("bottom", "Bottom Safe", -0.92f),
    LOWER_MID("lower_mid", "Above Controls", -0.74f)
}

enum class WatermarkMotionStyle(
    val id: String,
    val displayName: String,
    val burmeseLabel: String
) {
    GLIDE_XY("glide", "All-Around Glide", "ဟိုဘက်ဒီဘက် အပေါ်အောက်"),
    CORNER_HOP("corner", "4-Corner Jump", "ထောင့် ၄ နေရာ ပြောင်း"),
    FIXED_POSITION("fixed", "Fixed / Drag", "တစ်နေရာတည်း ထားမည်")
}

enum class WatermarkPositionPreset(
    val id: String,
    val label: String,
    val anchorX: Float,
    val anchorY: Float
) {
    TOP_LEFT("top_left", "Top-L", -0.68f, 0.76f),
    TOP_RIGHT("top_right", "Top-R", 0.68f, 0.76f),
    CENTER("center", "Center", 0.0f, 0.12f),
    BOTTOM_LEFT("bot_left", "Bot-L", -0.68f, -0.46f),
    BOTTOM_RIGHT("bot_right", "Bot-R", 0.68f, -0.46f)
}

enum class WatermarkBadgeStyle(
    val id: String,
    val label: String
) {
    OBSIDIAN_GOLD("obsidian", "Obsidian Gold"),
    CRIMSON_FIRE("crimson", "Crimson Fire"),
    FROST_GLASS("frost", "Glass Frosted"),
    MINIMAL_TAG("minimal", "Clean Text")
}

data class WatermarkConfig(
    val enabled: Boolean = true,
    val text: String = "FireRecapScript",
    val motionStyle: WatermarkMotionStyle = WatermarkMotionStyle.GLIDE_XY,
    val customAnchorX: Float = WatermarkPositionPreset.TOP_RIGHT.anchorX,
    val customAnchorY: Float = WatermarkPositionPreset.TOP_RIGHT.anchorY,
    val badgeStyle: WatermarkBadgeStyle = WatermarkBadgeStyle.OBSIDIAN_GOLD,
    val scale: Float = 1.0f,
    val opacity: Float = 0.72f
)

enum class CopyrightShieldPreset(
    val id: String,
    val label: String,
    val burmeseLabel: String
) {
    BALANCED_SAFE("balanced", "Recap Safe", "မူပိုင်ခွင့်ကာကွယ် (Zoom + Color)"),
    PRO_ANTI_CLAIM("pro", "Strong Shield", "အမြင့်ဆုံးကာကွယ် (Flip + Zoom + Border)"),
    CUSTOM("custom", "Custom", "စိတ်ကြိုက်ချိန်ညှိမည်")
}

data class CopyrightShieldConfig(
    val enabled: Boolean = true,
    val preset: CopyrightShieldPreset = CopyrightShieldPreset.BALANCED_SAFE,
    val zoomScale: Float = 1.07f,
    val mirrorFlip: Boolean = false,
    val colorContrastBoost: Boolean = true,
    val cinemaFrameBorder: Boolean = true
)

enum class DubNarrationStyle(
    val id: String,
    val displayName: String,
    val burmeseLabel: String,
    val promptDirective: String
) {
    STORYTELLER(
        id = "storyteller",
        displayName = "Full Storyteller",
        burmeseLabel = "အစအဆုံး ဇာတ်လမ်းပြောဟန်",
        promptDirective = "Narrate continuously from 0.0s through the final second like an engaging Myanmar Movie Recap storyteller: translate every spoken dialogue line AND smoothly describe visual actions/suspense during silent scenes so there are zero silent gaps."
    ),
    DIALOGUE_PLUS_FILL(
        id = "dialogue_fill",
        displayName = "Dialogue + Bridge",
        burmeseLabel = "စကားပြော + အခန်းကူးဖြည့်",
        promptDirective = "Translate all spoken English lines naturally in conversational Burmese AND add short exciting Burmese narrative commentary in every pause longer than 1.5 seconds so the viewer never hears dead silence until the end."
    ),
    TIKTOK_FAST(
        id = "tiktok_fast",
        displayName = "Viral Fast Recap",
        burmeseLabel = "TikTok သွက်သွက်လက်လက်",
        promptDirective = "Keep a fast-paced, punchy TikTok/Reels Movie Recap rhythm from 0.0s to the end with rapid 1.8s-3.0s conversational Burmese beats and dramatic cliffhanger energy."
    )
}

data class DubSegment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val originalSpeech: String,
    val burmeseText: String
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(450L)

    val formattedTimeRange: String
        get() = "${formatTimestamp(startMs)} - ${formatTimestamp(endMs)}"

    companion object {
        fun formatTimestamp(ms: Long): String {
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format("%02d:%02d", minutes, seconds)
        }

        fun formatSrtTimestamp(ms: Long): String {
            val safe = ms.coerceAtLeast(0L)
            val hours = safe / 3_600_000L
            val mins = (safe % 3_600_000L) / 60_000L
            val secs = (safe % 60_000L) / 1000L
            val millis = safe % 1000L
            return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, millis)
        }

        fun toSrtString(segments: List<DubSegment>): String = buildString {
            segments.forEachIndexed { index, seg ->
                appendLine(index + 1)
                appendLine("${formatSrtTimestamp(seg.startMs)} --> ${formatSrtTimestamp(seg.endMs)}")
                appendLine(seg.burmeseText)
                appendLine()
            }
        }.trim()

        fun computeTimelineCoveragePercent(
            segments: List<DubSegment>,
            totalDurationMs: Long
        ): Int {
            val safeTotal = totalDurationMs.coerceAtLeast(1L)
            if (segments.isEmpty()) return 0
            val sorted = segments.sortedBy { it.startMs }
            var coveredMs = 0L
            var curStart = -1L
            var curEnd = -1L
            for (seg in sorted) {
                val s = seg.startMs.coerceIn(0L, safeTotal)
                val e = seg.endMs.coerceIn(s, safeTotal)
                if (curStart < 0L) {
                    curStart = s
                    curEnd = e
                } else if (s <= curEnd) {
                    curEnd = maxOf(curEnd, e)
                } else {
                    coveredMs += (curEnd - curStart)
                    curStart = s
                    curEnd = e
                }
            }
            if (curStart >= 0L) {
                coveredMs += (curEnd - curStart)
            }
            return ((coveredMs * 100L) / safeTotal).toInt().coerceIn(0, 100)
        }

        fun hasTrailingCutoff(
            segments: List<DubSegment>,
            totalDurationMs: Long
        ): Boolean {
            if (segments.isEmpty()) return true
            val maxEnd = segments.maxOf { it.endMs }
            return (totalDurationMs - maxEnd) > 2_200L
        }
    }
}

data class DubTranslationPlan(
    val detectedLanguage: String,
    val videoSummaryBurmese: String,
    val segments: List<DubSegment>
)

data class RenderedDubVideo(
    val filePath: String,
    val fileName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val savedGalleryUri: Uri? = null,
    val savedGalleryPathLabel: String? = null,
    val usedFallbackMode: Boolean = false,
    val fallbackReason: String? = null
)
