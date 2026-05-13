package com.example.firsahayak

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SttEngine(private val context: Context) {

    /**
     * Transcribes speech using Android's built-in SpeechRecognizer.
     *
     * IMPORTANT: SpeechRecognizer MUST run on the main thread.
     * This function switches to Dispatchers.Main internally.
     *
     * Supports Hindi (hi-IN) with English fallback.
     * Requires RECORD_AUDIO permission to be granted before calling.
     * Requires internet for Google's cloud STT (best accuracy for Hindi).
     */
    suspend fun transcribeWithAndroidSTT(
        onPartialResult: (String) -> Unit = {}
    ): String = withContext(Dispatchers.Main) {   // ← MUST be Main thread

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw RuntimeException(
                "Speech recognition not available on this device. " +
                        "Make sure Google app is installed."
            )
        }

        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // Primary language: Hindi
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                // Also accept English (for mixed Hindi-English FIRs)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Allow longer speech — FIR narrations can be 1-2 minutes
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {

                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""


                    // ── ADD THIS LOG ──────────────────────────────────────────────────────
                    if (text.isNotBlank()) {
                        Log.d("STT_TO_GEMMA", "Final Transcription being sent to Gemma:")
                        Log.d("STT_TO_GEMMA", ">>> $text")
                    } else {
                        Log.w("STT_TO_GEMMA", "Transcription was empty!")
                    }


                    recognizer.destroy()
                    if (cont.isActive) cont.resume(text)
                }

                override fun onPartialResults(partial: Bundle) {
                    val matches = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { partialText ->
                        Log.d("STT", "Partial: $partialText")
                        onPartialResult(partialText)
                    }
                }

                override fun onError(errorCode: Int) {
                    val errorMsg = sttErrorMessage(errorCode)
                    Log.e("STT", "Error: $errorMsg (code $errorCode)")
                    recognizer.destroy()
                    if (cont.isActive) {
                        cont.resumeWithException(RuntimeException("STT Error: $errorMsg"))
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("STT", "Ready for speech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d("STT", "Speech started")
                }
                override fun onEndOfSpeech() {
                    Log.d("STT", "Speech ended")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            Log.d("STT", "Listening started...")

            // If coroutine is cancelled, stop listening cleanly
            cont.invokeOnCancellation {
                recognizer.stopListening()
                recognizer.destroy()
            }
        }
    }

    private fun sttErrorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO              -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT             -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted"
        SpeechRecognizer.ERROR_NETWORK            -> "Network error — internet required for Hindi STT"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT    -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH           -> "No speech recognised — please speak clearly"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY    -> "Recognizer busy — try again"
        SpeechRecognizer.ERROR_SERVER             -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT     -> "No speech detected — please try again"
        else                                      -> "Unknown error (code $code)"
    }
}