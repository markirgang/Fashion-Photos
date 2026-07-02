package com.example.fashionphotos

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class TTSHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    private val _availableVoices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<VoiceInfo>> = _availableVoices

    data class VoiceInfo(val id: String, val displayName: String)

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { ttsEngine ->
                // Set default language
                ttsEngine.language = Locale.UK
                
                // Get available voices
                val voicesList = try {
                    val rawVoices = ttsEngine.voices
                    if (!rawVoices.isNullOrEmpty()) {
                        rawVoices.map { voice ->
                            VoiceInfo(
                                id = voice.name,
                                displayName = "${voice.locale.displayName} (${voice.name.takeLast(8)})"
                            )
                        }.sortedBy { it.displayName }
                    } else {
                        // Fallback to Locales
                        listOf(
                            VoiceInfo("en_US", "English (United States)"),
                            VoiceInfo("en_GB", "English (United Kingdom)"),
                            VoiceInfo("fr_FR", "French (France)"),
                            VoiceInfo("de_DE", "German (Germany)"),
                            VoiceInfo("es_ES", "Spanish (Spain)"),
                            VoiceInfo("it_IT", "Italian (Italy)"),
                            VoiceInfo("ja_JP", "Japanese (Japan)")
                        )
                    }
                } catch (e: Exception) {
                    Log.e("TTSHelper", "Failed to retrieve voices", e)
                    // Hardcoded fallback list
                    listOf(
                        VoiceInfo("en_US", "English (United States)"),
                        VoiceInfo("en_GB", "English (United Kingdom)"),
                        VoiceInfo("fr_FR", "French (France)"),
                        VoiceInfo("de_DE", "German (Germany)"),
                        VoiceInfo("es_ES", "Spanish (Spain)")
                    )
                }
                
                _availableVoices.value = voicesList
                _isInitialized.value = true
                Log.d("TTSHelper", "TTS successfully initialized with ${voicesList.size} voices")
            }
        } else {
            Log.e("TTSHelper", "TTS Initialization failed")
        }
    }

    fun speak(text: String, voiceId: String? = null) {
        tts?.let { ttsEngine ->
            if (_isInitialized.value) {
                if (voiceId != null) {
                    try {
                        val voice = ttsEngine.voices?.find { it.name == voiceId }
                        if (voice != null) {
                            ttsEngine.voice = voice
                        } else {
                            // Fallback logic if voiceId is a locale tag (like "en_US")
                            val parts = voiceId.split("_")
                            if (parts.size >= 2) {
                                ttsEngine.language = Locale(parts[0], parts[1])
                            } else {
                                ttsEngine.language = Locale(voiceId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TTSHelper", "Error setting voice/language: $voiceId", e)
                    }
                }
                ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
