package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class RecapPlatform(
    val firestoreValue: String,
    val displayLabel: String,
    val burmeseHint: String
) {
    YOUTUBE("YOUTUBE", "YouTube Recap", "၂ မိနစ် - ၅ မိနစ် ဇာတ်လမ်းအပြည့်"),
    TIKTOK("TIKTOK", "TikTok / Reels", "၆၀ စက္ကန့် အမြန်ဆွဲဆောင်ဟန်");

    companion object {
        fun fromRaw(raw: String): RecapPlatform =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: YOUTUBE
    }
}

@Keep
@IgnoreExtraProperties
data class SceneBeat(
    val timestamp: String = "",
    val visualCue: String = "",
    val voiceover: String = ""
)

@Keep
@IgnoreExtraProperties
data class RecapScript(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceTitle: String = "",
    val platform: String = RecapPlatform.YOUTUBE.firestoreValue,
    val genre: String = "Thriller",
    val viralTitles: List<String> = emptyList(),
    val openingHook: String = "",
    val scenes: List<SceneBeat> = emptyList(),
    val outro: String = "",
    val estimatedDuration: String = "02:00",
    val directorNotes: String? = null,
    val favorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun platformEnum(): RecapPlatform = RecapPlatform.fromRaw(platform)

    fun toCreateMap(uid: String): Map<String, Any> {
        val cleanTitle = sourceTitle.trim().ifBlank { "Untitled Recap" }.take(160)
        val cleanGenre = genre.trim().ifBlank { "Drama" }.take(60)
        val cleanTitles = viralTitles
            .map { it.trim().take(240) }
            .filter { it.isNotEmpty() }
            .take(5)
            .ifEmpty { listOf(cleanTitle) }
        val cleanHook = openingHook.trim().ifBlank { "ဇာတ်လမ်းအဖွင့်" }.take(2500)
        val cleanScenes = scenes
            .filter { it.voiceover.isNotBlank() }
            .take(60)
            .map { beat ->
                mapOf(
                    "timestamp" to beat.timestamp.trim().ifBlank { "00:15" }.take(20),
                    "visualCue" to beat.visualCue.trim().take(300),
                    "voiceover" to beat.voiceover.trim().take(1500)
                )
            }
            .ifEmpty {
                listOf(
                    mapOf(
                        "timestamp" to "00:15",
                        "visualCue" to "Opening scene",
                        "voiceover" to cleanHook
                    )
                )
            }
        val cleanOutro = outro.trim().ifBlank { "သင်သာဆိုရင် ဘယ်လိုလုပ်မလဲ Comment မှာ ပြောပြသွားဦးနော်။" }.take(2000)
        val cleanDuration = estimatedDuration.trim().ifBlank { "02:00" }.take(40)
        val cleanNotes = directorNotes?.trim()?.takeIf { it.isNotEmpty() }?.take(1000)

        @Suppress("UNCHECKED_CAST")
        return mapOf(
            "userId" to uid,
            "sourceTitle" to cleanTitle,
            "platform" to RecapPlatform.fromRaw(platform).firestoreValue,
            "genre" to cleanGenre,
            "viralTitles" to cleanTitles,
            "openingHook" to cleanHook,
            "scenes" to cleanScenes,
            "outro" to cleanOutro,
            "estimatedDuration" to cleanDuration,
            "directorNotes" to cleanNotes,
            "favorite" to favorite,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun formatFullScriptForExport(): String = buildString {
        appendLine("၁။ ဆွဲဆောင်မှုရှိသော ခေါင်းစဉ် ၃ ခု (Viral Titles)")
        viralTitles.forEachIndexed { idx, title ->
            appendLine("   ${idx + 1}. $title")
        }
        appendLine()
        appendLine("၂။ အဖွင့်စကားလုံး (0:00 - 0:15 Opening Hook)")
        appendLine(openingHook)
        appendLine()
        appendLine("၃။ အချိန်အလိုက် ဇာတ်လမ်းပြောပြချက် (Scene-by-Scene Script)")
        scenes.forEach { beat ->
            val ts = if (beat.timestamp.startsWith("[")) beat.timestamp else "[${beat.timestamp}]"
            appendLine("$ts ${beat.voiceover}")
            if (beat.visualCue.isNotBlank()) {
                appendLine("   (Visual: ${beat.visualCue})")
            }
            appendLine()
        }
        appendLine("၄။ အပိတ်စကား (Outro)")
        append(outro)
    }
}

data class VideoClipSummary(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val thumbnail: Bitmap?,
    val width: Int = 1280,
    val height: Int = 720,
    val hasAudio: Boolean = true
) {
    val isInlineEligible: Boolean
        get() = sizeBytes in 1L..MAX_INLINE_BYTES

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) String.format("%.1f MB", mb) else "${(sizeBytes / 1024L).coerceAtLeast(1L)} KB"
        }

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
            val mins = totalSeconds / 60L
            val secs = totalSeconds % 60L
            return String.format("%02d:%02d", mins, secs)
        }

    companion object {
        const val MAX_INLINE_BYTES: Long = 10L * 1024L * 1024L
    }
}
