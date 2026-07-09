package com.example.fashionphotos

import android.content.ContentValues
import android.content.Context
import android.media.MediaActionSound
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHelper(private val context: Context) {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mediaActionSound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
    }

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: androidx.camera.view.PreviewView,
        isFrontCamera: Boolean,
        onInitialized: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                // Select camera lens
                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                // Preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // ImageCapture use case
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Unbind all use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                onInitialized()
            } catch (e: Exception) {
                Log.e("CameraHelper", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto(onPhotoSaved: (String?) -> Unit) {
        val imageCapture = this.imageCapture ?: run {
            Log.e("CameraHelper", "Image capture is not ready")
            onPhotoSaved(null)
            return
        }

        mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)

        // Create time-stamped name
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val cacheDir = context.cacheDir
        val photoFile = java.io.File(cacheDir, "OrliFashionPhotos_$name.jpg")

        // Create output options object
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraHelper", "Photo capture failed: ${exc.message}", exc)
                    onPhotoSaved(null)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = android.net.Uri.fromFile(photoFile)
                    Log.d("CameraHelper", "Photo capture succeeded: $savedUri")
                    onPhotoSaved(savedUri.toString())
                }
            }
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        mediaActionSound.release()
    }
}
