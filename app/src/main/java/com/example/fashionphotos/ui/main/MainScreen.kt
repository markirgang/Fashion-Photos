package com.example.fashionphotos.ui.main

import android.Manifest
import android.app.Application
import android.util.Log
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import com.example.fashionphotos.CameraHelper
import com.example.fashionphotos.TTSHelper
import com.example.fashionphotos.theme.FashionPhotosTheme

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(app)
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    FashionPhotosTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (hasCameraPermission) {
                CameraAppContent(viewModel)
            } else {
                PermissionRequestScreen(onGrantRequest = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                })
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onGrantRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1E2C),
                        Color(0xFF0F0F1A)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color(0xFFFF4B72),
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp)
            )
            Text(
                text = "Camera Permission Required",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "OrliFashion Photos needs camera access to help you take stunning pictures automatically.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(
                onClick = onGrantRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B72)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
            ) {
                Text("Grant Camera Permission", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun CameraAppContent(viewModel: MainScreenViewModel) {
    val context = LocalContext.current
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val countdownTime by viewModel.countdownTime.collectAsState()
    val photoCount by viewModel.photoCount.collectAsState()
    val selectedVoiceId by viewModel.selectedVoiceId.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val countdownState by viewModel.countdownState.collectAsState()
    val currentPhotoNumber by viewModel.currentPhotoNumber.collectAsState()
    val lastPhotoUri by viewModel.lastPhotoUri.collectAsState()
    val capturedPhotoUris by viewModel.capturedPhotoUris.collectAsState()
    val isReviewing by viewModel.isReviewing.collectAsState()
    val currentReviewIndex by viewModel.currentReviewIndex.collectAsState()
    val isVoiceTrigger by viewModel.isVoiceTrigger.collectAsState()

    // Initialize CameraHelper once
    val cameraHelper = remember { CameraHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            cameraHelper.shutdown()
        }
    }

    val voices by viewModel.ttsHelper.availableVoices.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasAudioPermission = isGranted
            if (isGranted) {
                viewModel.startCaptureFlow(cameraHelper)
            } else {
                android.widget.Toast.makeText(context, "Voice trigger requires audio permission", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Camera Preview
        CameraPreview(
            isFrontCamera = isFrontCamera,
            cameraHelper = cameraHelper,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Translucent/Scaffold overlay containing settings and button (only visible when not capturing)
        AnimatedVisibility(
            visible = !isCapturing,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                // Title header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 20.dp, end = 20.dp)
                        .align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ORLIFASHION PHOTOS",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(
                            letterSpacing = 4.sp
                        ),
                        modifier = Modifier.shadow(8.dp)
                    )
                    Text(
                        text = "Interval capture assistant",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Settings glass card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(Color(0xE614141E))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Session Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    // Camera Switch (Front/Back)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Camera Source", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = if (isFrontCamera) "Front Selfie Camera" else "Rear Main Camera",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isFrontCamera,
                            onCheckedChange = { viewModel.setIsFrontCamera(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF4B72),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    // Trigger Mode Switch (Interval vs Voice)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Trigger Mode", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = if (isVoiceTrigger) "Voice Command ('shoot')" else "Automatic Interval Timer",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isVoiceTrigger,
                            onCheckedChange = { viewModel.setIsVoiceTrigger(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF4B72),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    if (!isVoiceTrigger) {
                        // Dropdown: Countdown Time
                        SettingsDropdown(
                            label = "Initial Countdown",
                            valueText = "$countdownTime seconds",
                            options = listOf(5, 10, 15, 20, 30),
                            optionText = { "$it seconds" },
                            onSelected = { viewModel.setCountdownTime(it) }
                        )
                    }

                    // Dropdown: Number of Photos
                    SettingsDropdown(
                        label = "Number of Photos",
                        valueText = "$photoCount photos",
                        options = listOf(1, 2, 3, 5, 10, 15, 20, 30, 50),
                        optionText = { "$it photos" },
                        onSelected = { viewModel.setPhotoCount(it) }
                    )

                    // Dropdown: TTS Voice select
                    SettingsDropdown(
                        label = "Announcement Voice / Language",
                        valueText = voices.find { it.id == selectedVoiceId }?.displayName ?: "Default Voice",
                        options = voices,
                        optionText = { it.displayName },
                        onSelected = { viewModel.setSelectedVoiceId(it.id) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (capturedPhotoUris.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.setReviewing(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3F51B5)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(12.dp, RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "REVIEW PHOTOS (${capturedPhotoUris.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    letterSpacing = 1.5.sp
                                )
                            )
                        }
                    }

                    // Start Action Button
                    Button(
                        onClick = {
                            if (isVoiceTrigger) {
                                if (hasAudioPermission) {
                                    viewModel.startCaptureFlow(cameraHelper)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                viewModel.startCaptureFlow(cameraHelper)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF4B72)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "START CAPTURING",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                }
            }
        }

        // 3. Capturing State Overlay (visible only when capturing)
        AnimatedVisibility(
            visible = isCapturing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                // Floating indicators and Cancel button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (countdownState != null) {
                        // Displaying Initial Countdown
                        Text(
                            text = "PREPARING",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Animated Countdown Number
                        AnimatedContent(
                            targetState = countdownState,
                            transitionSpec = {
                                (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()).togetherWith(
                                    scaleOut() + fadeOut()
                                )
                            },
                            label = "CountdownNumber"
                        ) { targetNumber ->
                            Text(
                                text = targetNumber?.toString() ?: "",
                                fontSize = 120.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF4B72),
                                textAlign = TextAlign.Center
                            )
                        }

                        Text(
                            text = "Beep and capture will start shortly",
                            fontSize = 14.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    } else if (currentPhotoNumber != null) {
                        if (isVoiceTrigger) {
                            Text(
                                text = "VOICE TRIGGER ACTIVE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3F51B5),
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.8f,
                                targetValue = 1.2f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )

                            Box(
                                modifier = Modifier
                                    .size((100 * scale).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3F51B5).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Listening",
                                    tint = Color(0xFF3F51B5),
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Say \"shoot\" to capture!",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "$currentPhotoNumber remaining",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            // Displaying Interval Capturing
                            Text(
                                text = "CAPTURING ACTIVE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF4B72),
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            CircularProgressIndicator(
                                color = Color(0xFFFF4B72),
                                trackColor = Color.White.copy(alpha = 0.1f),
                                strokeWidth = 6.dp,
                                modifier = Modifier
                                    .size(100.dp)
                                    .padding(8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Photo countdown count:",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "$currentPhotoNumber remaining",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(64.dp))

                    // Cancel Capture Button
                    OutlinedButton(
                        onClick = { viewModel.cancelCaptureFlow() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.2f))
                            )
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("CANCEL", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Gallery Thumbnail (Bottom Left)
        if (lastPhotoUri != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(start = 24.dp, bottom = if (isCapturing) 24.dp else 180.dp)
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(lastPhotoUri), "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("MainScreen", "Failed to open gallery image", e)
                        }
                    }
            ) {
                AsyncImage(
                    model = lastPhotoUri,
                    contentDescription = "Last captured photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 5. Photo Review Dialog
        if (isReviewing && capturedPhotoUris.isNotEmpty() && currentReviewIndex in capturedPhotoUris.indices) {
            Dialog(
                onDismissRequest = { viewModel.setReviewing(false) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xE614141E))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Title header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Review Photos",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Photo ${currentReviewIndex + 1} of ${capturedPhotoUris.size}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setReviewing(false) },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        // Photo viewer
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = capturedPhotoUris[currentReviewIndex],
                                contentDescription = "Review Photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Delete Button
                            Button(
                                onClick = { viewModel.deleteCurrentPhoto() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF3B30) // Coral red
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            // Keep Button
                            Button(
                                onClick = { viewModel.keepCurrentPhoto() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34C759) // Green/Teal
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Keep", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    isFrontCamera: Boolean,
    cameraHelper: CameraHelper,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewViewRef by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }

    LaunchedEffect(isFrontCamera, previewViewRef) {
        val pv = previewViewRef
        if (pv != null) {
            cameraHelper.bindCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = pv,
                isFrontCamera = isFrontCamera
            )
        }
    }

    AndroidView(
        factory = { context ->
            androidx.camera.view.PreviewView(context).apply {
                scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
            }.also {
                previewViewRef = it
            }
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraHelper.unbind()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsDropdown(
    label: String,
    valueText: String,
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = valueText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF4B72),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1E1E2C))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionText(option),
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = Color.White
                        )
                    )
                }
            }
        }
    }
}
