package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StudioBackgroundStyle(
    val id: String,
    val title: String,
    val shortLabel: String,
    val burmeseHint: String
) {
    PARTICLE_DOT_GRID(
        id = "particle_dot_grid",
        title = "Particle Dot Grid",
        shortLabel = "Dot Grid",
        burmeseHint = "အလင်းအစက် Grid နောက်ခံဒီဇိုင်း"
    ),
    DIGITAL_DOT_MATRIX(
        id = "digital_dot_matrix",
        title = "Digital Dot Matrix",
        shortLabel = "Dot Matrix",
        burmeseHint = "ဒစ်ဂျစ်တယ် Matrix လှိုင်းတန်း"
    ),
    ANIMATED_PARTICLE_GRID(
        id = "animated_particle_grid",
        title = "Animated Particle Grid",
        shortLabel = "Particles",
        burmeseHint = "လှုပ်ရှားနေသော အမှုန်အစက် Grid"
    ),
    INTERACTIVE_DOT_WAVE(
        id = "interactive_dot_wave",
        title = "Interactive Dot Wave",
        shortLabel = "Dot Wave",
        burmeseHint = "လက်ထိတိုင်း လှိုင်းထသော Dot Wave"
    ),
    REACTIVE_MESH_GRID(
        id = "reactive_mesh_grid",
        title = "Reactive Mesh Grid",
        shortLabel = "Mesh Grid",
        burmeseHint = "ချိတ်ဆက်ထားသော Cyber Mesh Grid"
    );

    companion object {
        fun fromId(id: String?): StudioBackgroundStyle =
            entries.firstOrNull { it.id == id } ?: INTERACTIVE_DOT_WAVE
    }
}

data class UserStudioPreferences(
    val backgroundStyle: StudioBackgroundStyle = StudioBackgroundStyle.INTERACTIVE_DOT_WAVE,
    val swarmAnimationEnabled: Boolean = true,
    val defaultMuteOriginalEnglish: Boolean = true,
    val defaultCopyrightShield: Boolean = true
)

object UserStudioPreferencesStore {
    private const val PREFS_NAME = "fire_recapscript_studio_prefs"
    private const val KEY_BG_STYLE = "bg_style"
    private const val KEY_SWARM_ANIM = "swarm_anim_enabled"
    private const val KEY_DEFAULT_MUTE_EN = "default_mute_en"
    private const val KEY_DEFAULT_COPYRIGHT = "default_copyright"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(UserStudioPreferences())
    val state: StateFlow<UserStudioPreferences> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
        val p = prefs ?: return
        _state.value = UserStudioPreferences(
            backgroundStyle = StudioBackgroundStyle.fromId(p.getString(KEY_BG_STYLE, null)),
            swarmAnimationEnabled = p.getBoolean(KEY_SWARM_ANIM, true),
            defaultMuteOriginalEnglish = p.getBoolean(KEY_DEFAULT_MUTE_EN, true),
            defaultCopyrightShield = p.getBoolean(KEY_DEFAULT_COPYRIGHT, true)
        )
    }

    fun setBackgroundStyle(style: StudioBackgroundStyle) {
        prefs?.edit()?.putString(KEY_BG_STYLE, style.id)?.apply()
        _state.value = _state.value.copy(backgroundStyle = style)
    }

    fun setSwarmAnimationEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SWARM_ANIM, enabled)?.apply()
        _state.value = _state.value.copy(swarmAnimationEnabled = enabled)
    }

    fun setDefaultMuteOriginalEnglish(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DEFAULT_MUTE_EN, enabled)?.apply()
        _state.value = _state.value.copy(defaultMuteOriginalEnglish = enabled)
    }

    fun setDefaultCopyrightShield(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DEFAULT_COPYRIGHT, enabled)?.apply()
        _state.value = _state.value.copy(defaultCopyrightShield = enabled)
    }
}
