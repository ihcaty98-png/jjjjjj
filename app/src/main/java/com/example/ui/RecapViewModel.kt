package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.RecapAiRepository
import com.example.data.RecapRepository
import com.example.media.VideoProcessor
import com.example.model.RecapPlatform
import com.example.model.RecapScript
import com.example.model.SceneBeat
import com.example.model.VideoClipSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VaultUiState {
    data object Loading : VaultUiState
    data class Success(val scripts: List<RecapScript>) : VaultUiState
    data class Error(val message: String) : VaultUiState
}

sealed interface StudioGenerationState {
    data object Idle : StudioGenerationState
    data class Processing(
        val stageMessage: String,
        val progressPercent: Int = 18
    ) : StudioGenerationState
    data class Generated(val script: RecapScript, val savedDocId: String? = null) : StudioGenerationState
    data class Error(val message: String) : StudioGenerationState
}

data class StudioUiState(
    val selectedVideo: VideoClipSummary? = null,
    val isInspectingVideo: Boolean = false,
    val selectedPlatform: RecapPlatform = RecapPlatform.YOUTUBE,
    val selectedGenre: String = "Auto-Detect",
    val directorNotes: String = "",
    val generationState: StudioGenerationState = StudioGenerationState.Idle,
    val isSaving: Boolean = false
)

class RecapViewModel(
    private val repository: RecapRepository,
    private val aiRepository: RecapAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val vaultState: StateFlow<VaultUiState> = repository.observeScripts(currentUserId)
        .map<List<RecapScript>, VaultUiState> { VaultUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing saved scripts", rootError)
            emit(VaultUiState.Error(rootError.localizedMessage ?: "Failed to load saved scripts."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = VaultUiState.Loading
        )

    fun onVideoSelected(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _studioState.update { it.copy(isInspectingVideo = true) }
            try {
                val summary = VideoProcessor.inspectVideo(appContext, uri)
                _studioState.update {
                    it.copy(
                        selectedVideo = summary,
                        isInspectingVideo = false,
                        generationState = StudioGenerationState.Idle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect selected video", e)
                _studioState.update {
                    it.copy(
                        isInspectingVideo = false,
                        generationState = StudioGenerationState.Error(
                            e.localizedMessage ?: "Could not read selected video file."
                        )
                    )
                }
            }
        }
    }

    fun clearSelectedVideo() {
        _studioState.update {
            it.copy(selectedVideo = null, generationState = StudioGenerationState.Idle)
        }
    }

    fun selectPlatform(platform: RecapPlatform) {
        _studioState.update { it.copy(selectedPlatform = platform) }
    }

    fun selectGenre(genre: String) {
        _studioState.update { it.copy(selectedGenre = genre) }
    }

    fun updateDirectorNotes(notes: String) {
        _studioState.update { it.copy(directorNotes = notes.take(1000)) }
    }

    fun generateScript(
        context: Context,
        onGenerationSucceeded: suspend () -> Unit = {}
    ) {
        val current = _studioState.value
        val video = current.selectedVideo ?: run {
            _snackbarMessage.value = "Please select a movie clip first."
            return
        }
        val appContext = context.applicationContext

        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    generationState = StudioGenerationState.Processing(
                        stageMessage = if (video.isInlineEligible) {
                            "ဗီဒီယိုဖိုင်ကို စစ်ဆေးပြင်ဆင်နေပါတယ်..."
                        } else {
                            "ဗီဒီယိုမှ အဓိက Scene Keyframes များ ထုတ်ယူနေပါတယ်..."
                        },
                        progressPercent = 16
                    )
                )
            }
            try {
                val payload = VideoProcessor.prepareVideoPayload(appContext, video)
                _studioState.update {
                    it.copy(
                        generationState = StudioGenerationState.Processing(
                            stageMessage = "Gemini ဖြင့် မြန်မာ Movie Recap ဇာတ်ညွှန်း ရေးသားနေပါတယ်...",
                            progressPercent = 48
                        )
                    )
                }
                val updateStage: (String) -> Unit = { stageMsg ->
                    _studioState.update { state ->
                        val prevPct = (state.generationState as? StudioGenerationState.Processing)?.progressPercent ?: 48
                        val nextPct = (prevPct + 16).coerceIn(32, 92)
                        state.copy(
                            generationState = StudioGenerationState.Processing(
                                stageMessage = stageMsg,
                                progressPercent = nextPct
                            )
                        )
                    }
                }
                val script = try {
                    aiRepository.generateRecapScript(
                        payload = payload,
                        platform = current.selectedPlatform,
                        preferredGenre = current.selectedGenre,
                        directorNotes = current.directorNotes,
                        clipTitleHint = video.displayName.substringBeforeLast('.'),
                        onStatusUpdate = updateStage
                    )
                } catch (inlineErr: Exception) {
                    if (inlineErr is CancellationException) throw inlineErr
                    val msg = inlineErr.message.orEmpty().lowercase()
                    val shouldFallbackToKeyframes = video.isInlineEligible &&
                        (msg.contains("timed out") || msg.contains("413") || msg.contains("too large"))
                    if (!shouldFallbackToKeyframes) throw inlineErr

                    updateStage("ဖိုင်အရွယ်အစား ကြီးနေသဖြင့် Keyframe ပုံများဖြင့် ပြန်လည်စစ်ဆေးနေပါတယ်...")
                    val keyframePayload = VideoProcessor.prepareVideoPayload(
                        context = appContext,
                        summary = video,
                        forceKeyframes = true
                    )
                    aiRepository.generateRecapScript(
                        payload = keyframePayload,
                        platform = current.selectedPlatform,
                        preferredGenre = current.selectedGenre,
                        directorNotes = current.directorNotes,
                        clipTitleHint = video.displayName.substringBeforeLast('.'),
                        onStatusUpdate = updateStage
                    )
                }
                onGenerationSucceeded()
                _studioState.update {
                    it.copy(generationState = StudioGenerationState.Generated(script))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Script generation failed", e)
                _studioState.update {
                    it.copy(
                        generationState = StudioGenerationState.Error(
                            e.localizedMessage ?: "Failed to generate recap script."
                        )
                    )
                }
            }
        }
    }

    fun generateOfflineFallbackScript(
        onGenerationSucceeded: suspend () -> Unit = {}
    ) {
        val current = _studioState.value
        val video = current.selectedVideo ?: return
        viewModelScope.launch {
            val titleHint = video.displayName.substringBeforeLast('.').ifBlank { "Movie Clip" }
            val fallback = RecapScript(
                sourceTitle = "$titleHint — Burmese Recap",
                platform = current.selectedPlatform.firestoreValue,
                genre = if (current.selectedGenre == "Auto-Detect") "Drama" else current.selectedGenre,
                viralTitles = listOf(
                    "မထင်မှတ်ထားတဲ့ အဖြစ်အပျက်နဲ့ စတင်လာတဲ့ $titleHint ဇာတ်လမ်း",
                    "နောက်ဆုံးမှာ ဘယ်လိုလှည့်ကွက်မျိုး ကြုံရမလဲ",
                    "ကြည့်ရှုသူတိုင်း အံ့အားသင့်သွားစေမယ့် ဇာတ်ဝင်ခန်း"
                ),
                openingHook = "ဒီနေ့ပြောပြပေးမယ့် ဇာတ်လမ်းလေးကတော့ အစကတည်းက စိတ်လှုပ်ရှားစရာအပြည့်နဲ့ စတင်ထားပါတယ်၊ ဘာတွေဆက်ဖြစ်မလဲဆိုတာ အဆုံးထိ ကြည့်လိုက်ရအောင်နော်။",
                scenes = listOf(
                    SceneBeat(
                        timestamp = "00:00",
                        visualCue = "Opening scene of $titleHint",
                        voiceover = "ဇာတ်လမ်းအစမှာပဲ ကျွန်တော်တို့ရဲ့ ဇာတ်လိုက်ဟာ မထင်မှတ်ထားတဲ့ အခြေအနေတစ်ခုထဲကို ရောက်နေတာကို တွေ့ရပါတယ်။"
                    ),
                    SceneBeat(
                        timestamp = "00:15",
                        visualCue = "Rising tension and confrontation",
                        voiceover = "ဒါပေမဲ့ သူမထင်မှတ်ထားတာက အခြေအနေတွေက တစ်စက္ကန့်ထက်တစ်စက္ကန့် ပိုပြီးရှုပ်ထွေးလာနေတာပါပဲ။"
                    ),
                    SceneBeat(
                        timestamp = "00:30",
                        visualCue = "Climax and turning point",
                        voiceover = "တကယ်တမ်း ဖြစ်ပျက်သွားတာကတော့ ဘယ်သူမှ ကြိုတင်တွက်ဆလို့ မရတဲ့ လှည့်ကွက်ကြီးတစ်ခု ဖြစ်နေပါတော့တယ်။"
                    )
                ),
                outro = "ပရိသတ်ကြီးရော ဒီဇာတ်လမ်းအပေါ် ဘယ်လိုမြင်လဲ Comment မှာ ရေးသွားပေးဦးနော်။",
                estimatedDuration = video.formattedDuration,
                directorNotes = current.directorNotes.takeIf { it.isNotBlank() },
                favorite = false
            )
            onGenerationSucceeded()
            _studioState.update {
                it.copy(generationState = StudioGenerationState.Generated(fallback))
            }
            _snackbarMessage.value = "Generated editable Burmese Recap Template (Save & edit scenes anytime)"
        }
    }

    fun saveGeneratedScriptToVault() {
        val genState = _studioState.value.generationState as? StudioGenerationState.Generated ?: return
        if (genState.savedDocId != null || _studioState.value.isSaving) return

        viewModelScope.launch {
            _studioState.update { it.copy(isSaving = true) }
            val result = repository.saveScript(genState.script)
            result.onSuccess { docId ->
                _studioState.update {
                    it.copy(
                        isSaving = false,
                        generationState = genState.copy(savedDocId = docId)
                    )
                }
                _snackbarMessage.value = "Saved to your cloud Scripts Vault"
            }.onFailure { err ->
                _studioState.update { it.copy(isSaving = false) }
                _snackbarMessage.value = err.localizedMessage ?: "Failed to save script."
            }
        }
    }

    fun toggleFavorite(script: RecapScript) {
        if (script.id.isBlank()) return
        viewModelScope.launch {
            val result = repository.toggleFavorite(
                userId = currentUserId,
                scriptId = script.id,
                favorite = !script.favorite
            )
            result.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update favorite."
            }
        }
    }

    fun updateSavedScript(
        scriptId: String,
        sourceTitle: String,
        openingHook: String,
        outro: String,
        directorNotes: String?,
        viralTitles: List<String>,
        scenes: List<SceneBeat>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updateScriptDetails(
                userId = currentUserId,
                scriptId = scriptId,
                sourceTitle = sourceTitle,
                openingHook = openingHook,
                outro = outro,
                directorNotes = directorNotes,
                viralTitles = viralTitles,
                scenes = scenes
            )
            result.onSuccess {
                _snackbarMessage.value = "Script updated"
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update script."
            }
        }
    }

    fun deleteSavedScript(scriptId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteScript(currentUserId, scriptId)
            result.onSuccess {
                _snackbarMessage.value = "Script deleted"
                onDeleted()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to delete script."
            }
        }
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    companion object {
        private const val TAG = "RecapViewModel"
        val GENRE_OPTIONS = listOf(
            "Auto-Detect", "Thriller", "Horror", "Action", "Drama", "Romance", "Mystery", "Comedy"
        )
    }
}
