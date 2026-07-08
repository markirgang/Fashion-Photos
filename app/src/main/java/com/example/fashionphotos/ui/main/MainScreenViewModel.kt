package com.example.fashionphotos.ui.main

import android.app.Application
import android.content.ContentValues
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fashionphotos.CameraHelper
import com.example.fashionphotos.TTSHelper
import com.example.fashionphotos.SpeechHelper
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val _countdownTime = MutableStateFlow(10)
    val countdownTime = _countdownTime.asStateFlow()

    private val _photoCount = MutableStateFlow(5)
    val photoCount = _photoCount.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera = _isFrontCamera.asStateFlow()

    private val _selectedVoiceId = MutableStateFlow<String?>(null)
    val selectedVoiceId = _selectedVoiceId.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    private val _countdownState = MutableStateFlow<Int?>(null)
    val countdownState = _countdownState.asStateFlow()

    private val _currentPhotoNumber = MutableStateFlow<Int?>(null)
    val currentPhotoNumber = _currentPhotoNumber.asStateFlow()

    private val _lastPhotoUri = MutableStateFlow<String?>(null)
    val lastPhotoUri = _lastPhotoUri.asStateFlow()

    private val _capturedPhotoUris = MutableStateFlow<List<String>>(emptyList())
    val capturedPhotoUris = _capturedPhotoUris.asStateFlow()

    private val _isReviewing = MutableStateFlow(false)
    val isReviewing = _isReviewing.asStateFlow()

    private val _currentReviewIndex = MutableStateFlow(0)
    val currentReviewIndex = _currentReviewIndex.asStateFlow()

    private val _isVoiceTrigger = MutableStateFlow(false)
    val isVoiceTrigger = _isVoiceTrigger.asStateFlow()

    private val speechHelper = SpeechHelper(application)

    // TTS & Tone Generator helpers
    val ttsHelper = TTSHelper(application)
    private var toneGenerator: ToneGenerator? = null
    private var captureJob: Job? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Failed to initialize ToneGenerator", e)
        }

        // Auto-select British English as default and speak the welcome message once initialized
        viewModelScope.launch {
            var spoken = false
            ttsHelper.isInitialized.collect { ready ->
                if (ready && !spoken) {
                    spoken = true
                    val voicesList = ttsHelper.availableVoices.value
                    // Try to find a British English voice
                    val gbVoice = voicesList.find { voice ->
                        voice.id.contains("en_GB", ignoreCase = true) ||
                        voice.id.contains("en-GB", ignoreCase = true) ||
                        voice.displayName.contains("United Kingdom", ignoreCase = true) ||
                        voice.displayName.contains("Great Britain", ignoreCase = true)
                    }
                    if (gbVoice != null) {
                        _selectedVoiceId.value = gbVoice.id
                    } else {
                        val hasEnGb = voicesList.any { it.id == "en_GB" }
                        if (hasEnGb) {
                            _selectedVoiceId.value = "en_GB"
                        }
                    }

                    // Welcome greeting on startup
                    ttsHelper.speak(
                        "Welcome to Orli's Fashion Photos!",
                        _selectedVoiceId.value
                    )
                }
            }
        }
    }

    fun setCountdownTime(time: Int) {
        _countdownTime.value = time
    }

    fun setPhotoCount(count: Int) {
        _photoCount.value = count
    }

    fun setIsFrontCamera(isFront: Boolean) {
        _isFrontCamera.value = isFront
    }

    fun setSelectedVoiceId(voiceId: String?) {
        _selectedVoiceId.value = voiceId
    }

    fun setIsVoiceTrigger(isVoice: Boolean) {
        _isVoiceTrigger.value = isVoice
    }

    fun startCaptureFlow(cameraHelper: CameraHelper) {
        if (_isCapturing.value) return
        _isCapturing.value = true
        cleanupCacheDir()
        _capturedPhotoUris.value = emptyList() // clear previous session's photos

        if (_isVoiceTrigger.value) {
            _currentPhotoNumber.value = 1
            var isSingleCaptureInProgress = false

            captureJob = viewModelScope.launch {
                try {
                    speechHelper.startListening { word ->
                        if (word == "shoot" && _isCapturing.value && !isSingleCaptureInProgress) {
                            isSingleCaptureInProgress = true
                            val currentNum = _currentPhotoNumber.value ?: 1
                            
                            viewModelScope.launch {
                                delay(1000)
                                ttsHelper.speak("Smile and say cheese!", _selectedVoiceId.value)
                                delay(2500)
                                cameraHelper.takePhoto { uri ->
                                    try {
                                        if (uri != null) {
                                            _lastPhotoUri.value = uri
                                            _capturedPhotoUris.value = _capturedPhotoUris.value + uri
                                            _currentPhotoNumber.value = currentNum + 1
                                            ttsHelper.speak("Photo Taken!", _selectedVoiceId.value)
                                        }
                                    } finally {
                                        isSingleCaptureInProgress = false
                                    }
                                }
                            }
                        }
                    }

                    while (_isCapturing.value) {
                        delay(100)
                    }
                } catch (e: Exception) {
                    Log.e("MainScreenViewModel", "Voice trigger error", e)
                } finally {
                    speechHelper.stopListening()
                    _isCapturing.value = false
                    _countdownState.value = null
                    _currentPhotoNumber.value = null
                }
            }
        } else {
            captureJob = viewModelScope.launch {
                try {
                    // 1. Initial Countdown
                    val initialSeconds = _countdownTime.value
                    for (i in initialSeconds downTo 1) {
                        _countdownState.value = i
                        playBeep()
                        delay(1000)
                    }
                    _countdownState.value = null

                    // 2. Capture Loop
                    val total = _photoCount.value
                    for (i in total downTo 1) {
                        _currentPhotoNumber.value = i
                        
                        ttsHelper.speak("Smile and say cheese!", _selectedVoiceId.value)
                        
                        // Wait 2.5 seconds (2500ms) before taking the photo
                        delay(2500)
                        
                        cameraHelper.takePhoto { uri ->
                            if (uri != null) {
                                _lastPhotoUri.value = uri
                                _capturedPhotoUris.value = _capturedPhotoUris.value + uri
                                ttsHelper.speak("Photo Taken!", _selectedVoiceId.value)
                            }
                        }
                        
                        // Wait 2 seconds for TTS output and interval spacing
                        delay(2000)
                    }
                } catch (e: Exception) {
                    Log.e("MainScreenViewModel", "Capture flow error", e)
                } finally {
                    _isCapturing.value = false
                    _countdownState.value = null
                    _currentPhotoNumber.value = null
                }
            }
        }
    }

    fun cancelCaptureFlow() {
        captureJob?.cancel()
        speechHelper.stopListening()
        _isCapturing.value = false
        _countdownState.value = null
        _currentPhotoNumber.value = null
    }

    private fun playBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Failed to play beep", e)
        }
    }

    fun setReviewing(reviewing: Boolean) {
        _isReviewing.value = reviewing
        if (reviewing) {
            _currentReviewIndex.value = 0
        }
    }

    fun keepCurrentPhoto() {
        val uris = _capturedPhotoUris.value
        val index = _currentReviewIndex.value
        if (index in uris.indices) {
            val uriToKeep = uris[index]
            val savedUri = savePhotoToMediaStore(uriToKeep)
            if (savedUri != null) {
                _lastPhotoUri.value = savedUri
                android.widget.Toast.makeText(
                    getApplication(),
                    "Saved to Gallery",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                // Delete the temporary cache file since we exported it
                try {
                    val uri = android.net.Uri.parse(uriToKeep)
                    if (uri.scheme == "file") {
                        val file = java.io.File(uri.path ?: "")
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainScreenViewModel", "Failed to delete cached photo: $uriToKeep", e)
                }
            } else {
                android.widget.Toast.makeText(
                    getApplication(),
                    "Failed to save photo",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (index < uris.size - 1) {
            _currentReviewIndex.value = index + 1
        } else {
            _isReviewing.value = false
            _currentReviewIndex.value = 0
        }
    }

    fun deleteCurrentPhoto() {
        val uris = _capturedPhotoUris.value
        val index = _currentReviewIndex.value
        if (index in uris.indices) {
            val uriToDelete = uris[index]
            try {
                val uri = android.net.Uri.parse(uriToDelete)
                if (uri.scheme == "file") {
                    val file = java.io.File(uri.path ?: "")
                    if (file.exists()) {
                        file.delete()
                    }
                } else {
                    val contentResolver = getApplication<Application>().contentResolver
                    contentResolver.delete(uri, null, null)
                }
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Failed to delete photo: $uriToDelete", e)
            }

            val updatedList = uris.toMutableList().apply { removeAt(index) }
            _capturedPhotoUris.value = updatedList

            if (_lastPhotoUri.value == uriToDelete) {
                _lastPhotoUri.value = updatedList.lastOrNull()
            }

            if (updatedList.isEmpty()) {
                _isReviewing.value = false
                _currentReviewIndex.value = 0
            } else if (_currentReviewIndex.value >= updatedList.size) {
                _currentReviewIndex.value = updatedList.size - 1
            }
        }
    }

    private fun savePhotoToMediaStore(uriString: String): String? {
        try {
            val cacheUri = android.net.Uri.parse(uriString)
            val contentResolver = getApplication<Application>().contentResolver
            
            val inputStream = contentResolver.openInputStream(cacheUri) ?: return null
            
            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis())
                
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "OrliFashionPhotos_$name.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OrliFashionPhotos")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            val mediaUri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null
            
            val outputStream = contentResolver.openOutputStream(mediaUri) ?: return null
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                contentResolver.update(mediaUri, updateValues, null, null)
            }
            
            return mediaUri.toString()
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Failed to save photo to MediaStore", e)
            return null
        }
    }

    private fun cleanupCacheDir() {
        try {
            val cacheDir = getApplication<Application>().cacheDir
            val files = cacheDir.listFiles { _, name -> name.startsWith("OrliFashionPhotos_") && name.endsWith(".jpg") }
            files?.forEach { file ->
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Failed to clean up cache dir", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        speechHelper.stopListening()
        cleanupCacheDir()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.e("MainScreenViewModel", "Failed to release ToneGenerator", e)
        }
    }
}
