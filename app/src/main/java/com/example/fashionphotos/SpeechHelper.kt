package com.example.fashionphotos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechHelper(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var onWordHeardCallback: ((String) -> Unit)? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("SpeechHelper", "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d("SpeechHelper", "Speech beginning")
        }

        override fun onRmsChanged(rmsd: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d("SpeechHelper", "End of speech")
        }

        override fun onError(error: Int) {
            Log.e("SpeechHelper", "Speech recognizer error: $error")
            if (isListening) {
                restartListening()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                Log.d("SpeechHelper", "Results: $matches")
                for (match in matches) {
                    if (match.contains("shoot", ignoreCase = true) || 
                        match.contains("chute", ignoreCase = true) || 
                        match.contains("shute", ignoreCase = true) || 
                        match.contains("shoo", ignoreCase = true)) {
                        onWordHeardCallback?.invoke("shoot")
                        break
                    }
                }
            }
            if (isListening) {
                restartListening()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                Log.d("SpeechHelper", "Partial results: $matches")
                for (match in matches) {
                    if (match.contains("shoot", ignoreCase = true) || 
                        match.contains("chute", ignoreCase = true) || 
                        match.contains("shute", ignoreCase = true) || 
                        match.contains("shoo", ignoreCase = true)) {
                        onWordHeardCallback?.invoke("shoot")
                        break
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening(onWordHeard: (String) -> Unit) {
        if (isListening) return
        isListening = true
        onWordHeardCallback = onWordHeard

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(recognitionListener)
                    }
                    startRecognizerIntent()
                } else {
                    Log.e("SpeechHelper", "Speech recognition is not available on this device")
                }
            } catch (e: Exception) {
                Log.e("SpeechHelper", "Failed to start SpeechRecognizer", e)
            }
        }
    }

    private fun startRecognizerIntent() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            }
            speechRecognizer?.startListening(intent)
            Log.d("SpeechHelper", "Started listening intent")
        } catch (e: Exception) {
            Log.e("SpeechHelper", "Failed to start listening intent", e)
        }
    }

    private fun restartListening() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (isListening) {
                try {
                    speechRecognizer?.cancel()
                    startRecognizerIntent()
                } catch (e: Exception) {
                    Log.e("SpeechHelper", "Failed to restart listening", e)
                }
            }
        }
    }

    fun stopListening() {
        isListening = false
        onWordHeardCallback = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
                Log.d("SpeechHelper", "Stopped and destroyed SpeechRecognizer")
            } catch (e: Exception) {
                Log.e("SpeechHelper", "Failed to stop/destroy SpeechRecognizer", e)
            }
        }
    }
}
