package com.example.data

import android.content.Context
import com.example.model.ClonedVoiceProfile
import org.json.JSONArray
import org.json.JSONObject

class VoiceCloneStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadClonedVoices(): List<ClonedVoiceProfile> {
        val raw = prefs.getString(KEY_PROFILES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    val baseVoice = obj.optString("matchedBaseVoice")
                    if (id.isNotBlank() && name.isNotBlank() && baseVoice.isNotBlank()) {
                        add(
                            ClonedVoiceProfile(
                                id = id,
                                name = name,
                                matchedBaseVoice = baseVoice,
                                vocalDescription = obj.optString("vocalDescription", ""),
                                speakingSpeed = obj.optDouble("speakingSpeed", 1.0).toFloat().coerceIn(0.8f, 1.25f),
                                pitchShiftRatio = obj.optDouble("pitchShiftRatio", 1.0).toFloat().coerceIn(0.84f, 1.18f),
                                sourceLabel = obj.optString("sourceLabel", "Voice Sample"),
                                createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveClonedVoice(profile: ClonedVoiceProfile): List<ClonedVoiceProfile> {
        val existing = loadClonedVoices().filterNot { it.id == profile.id }
        val updated = listOf(profile) + existing
        persist(updated)
        return updated
    }

    fun deleteClonedVoice(profileId: String): List<ClonedVoiceProfile> {
        val updated = loadClonedVoices().filterNot { it.id == profileId }
        persist(updated)
        return updated
    }

    private fun persist(list: List<ClonedVoiceProfile>) {
        val array = JSONArray()
        list.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("matchedBaseVoice", p.matchedBaseVoice)
                    put("vocalDescription", p.vocalDescription)
                    put("speakingSpeed", p.speakingSpeed.toDouble())
                    put("pitchShiftRatio", p.pitchShiftRatio.toDouble())
                    put("sourceLabel", p.sourceLabel)
                    put("createdAtEpochMs", p.createdAtEpochMs)
                }
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "recapscript_voice_clones"
        private const val KEY_PROFILES = "cloned_voices_json"
    }
}
