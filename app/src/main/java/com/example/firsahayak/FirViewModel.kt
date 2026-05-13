package com.example.firsahayak

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class FirViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FirRepository(app)
    private var _activeInputTab = MutableStateFlow(0)  // 0=PDF, 1=Audio
    val activeInputTab: StateFlow<Int> = _activeInputTab

    sealed class UiState {
        /** Idle — model ready, waiting for user input */
        object Idle : UiState()

        /** Model not found — show download prompt */
        object NeedDownload : UiState()

        /** Model downloading */
        data class Downloading(val percent: Int) : UiState()

        data class TranscriptReview(val transcript: String) : UiState()


        /** OCR / STT / LLM running */
        data class Loading(val message: String) : UiState()

        /**
         * Extraction complete — show verification screen.
         * User can edit every field then confirm.
         */
        data class Verification(val entity: FirEntity) : UiState()

        // ADD inside UiState sealed class
        data class Streaming(
            val message: String,   // status e.g. "Analysing with Gemma…"
            val tokens : String    // accumulated tokens so far
        ) : UiState()

        /**
         * User confirmed the data — PDF has been generated.
         * [pdfFile] is the File in app private storage ready to share/open.
         */
        data class PdfReady(val entity: FirEntity, val pdfFile: File) : UiState()

        /** Legacy success view (raw JSON) — kept for PDF input path */
        data class Success(val result: JSONObject, val transcript: String? = null) : UiState()

        /** Error */
        data class Failure(val error: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    private var modelReady = false

    // ── Initialise ────────────────────────────────────────────────────────────

    fun checkAndInitialize() {
        if (modelReady) return
        val context = getApplication<Application>()
        val modelPath = ModelDownloader.getModelPath(context)
        if (modelPath != null) initializeWithPath(modelPath)
        else _state.value = UiState.NeedDownload
    }

    private fun initializeWithPath(modelPath: String) {
        if (modelReady) return
        viewModelScope.launch {
            _state.value = UiState.Loading("Loading Gemma model… (first run may take 30s)")
            try {
                repo.initialize(modelPath)
                modelReady = true
                _state.value = UiState.Idle
            } catch (e: Exception) {
                _state.value = UiState.Failure("Model load failed: ${e.message}")
            }
        }
    }

    fun downloadModel(context: Context) {
        viewModelScope.launch {
            try {
                ModelDownloader.downloadModel(context) { pct -> _state.value = UiState.Downloading(pct) }
                val modelPath = ModelDownloader.getModelPath(context)
                    ?: throw IllegalStateException("Model file not found after download")
                initializeWithPath(modelPath)
            } catch (e: Exception) {
                _state.value = UiState.Failure("Download failed: ${e.message}")
            }
        }
    }

    // ── Analyse PDF ───────────────────────────────────────────────────────────
    // PDF path goes to legacy Success state (shows raw extraction).
    // For audio we go through Verification.

    fun analyseFromPdf(uri: Uri) {
        _activeInputTab.value = 0
        if (!modelReady) { _state.value = UiState.NeedDownload; return }
        viewModelScope.launch {
            repo.analyseFromPdf(uri).collect { progress ->
                when (progress) {
                    is FirRepository.Progress.Status -> {
                        val currentTokens = (_state.value as? UiState.Streaming)?.tokens ?: ""
                        _state.value = UiState.Streaming(progress.message, currentTokens)  // preserve tokens
                    }
                    is FirRepository.Progress.Token  -> {
                        val current = (_state.value as? UiState.Streaming)?.tokens ?: ""
                        _state.value = UiState.Streaming(
                            message = "Analysing with Gemma…",
                            tokens  = current + progress.token
                        )
                    }
                    is FirRepository.Progress.Done   -> _state.value = UiState.Success(progress.result)
                    is FirRepository.Progress.Error  -> _state.value = UiState.Failure(progress.message)
                }
            }
        }
    }

    // ── Analyse Audio → Verification ──────────────────────────────────────────

    fun analyseFromAudio() {
        _activeInputTab.value = 1
        if (!modelReady) { _state.value = UiState.NeedDownload; return }
        viewModelScope.launch {
            try {
                _state.value = UiState.Loading("Listening… speak now")
                val transcript = repo.transcribeOnly()
                if (transcript.isBlank()) {
                    _state.value = UiState.Failure("No speech detected. Please try again.")
                } else {
                    _state.value = UiState.TranscriptReview(transcript)
                }
            } catch (e: Exception) {
                _state.value = UiState.Failure(e.message ?: "STT error")
            }
        }
    }

    fun confirmTranscript(transcript: String) {
        if (!modelReady) { _state.value = UiState.NeedDownload; return }
        viewModelScope.launch {
            repo.analyseTranscript(transcript).collect { progress ->
                when (progress) {
                    is FirRepository.Progress.Status -> {
                        val currentTokens = (_state.value as? UiState.Streaming)?.tokens ?: ""
                        _state.value = UiState.Streaming(progress.message, currentTokens)
                    }
                    is FirRepository.Progress.Token -> {
                        val current = (_state.value as? UiState.Streaming)?.tokens ?: ""
                        _state.value = UiState.Streaming(
                            message = "Analysing with Gemma…",
                            tokens  = current + progress.token
                        )
                    }
                    is FirRepository.Progress.Done -> {
                        val entity = progress.result.toFirEntity(transcript, source = "audio")
                        _state.value = UiState.Verification(entity)
                    }
                    is FirRepository.Progress.Error -> {
                        _state.value = UiState.Failure(progress.message)
                    }
                }
            }
        }
    }

    fun reRecord() {
        _state.value = UiState.Idle
    }

    // ── User confirmed edits → generate PDF ───────────────────────────────────

    fun generatePdf(entity: FirEntity) {
        viewModelScope.launch {
            _state.value = UiState.Loading("Generating FIR PDF…")
            try {
                val pdfFile = FirPdfGenerator.generate(getApplication(), entity)
                _state.value = UiState.PdfReady(entity, pdfFile)
            } catch (e: Exception) {
                _state.value = UiState.Failure("PDF generation failed: ${e.message}")
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    fun setError(message: String) { _state.value = UiState.Failure(message) }

    fun reset() { _state.value = if (modelReady) UiState.Idle else UiState.NeedDownload }

    /** Go back from Verification to idle without losing the entity */
    fun backFromVerification() { reset() }

    override fun onCleared() {
        super.onCleared()
        repo.release()
    }
}