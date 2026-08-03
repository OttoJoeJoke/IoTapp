package com.example.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.camera.core.ImageCapture
import com.example.scanner.ScanStatus
import com.example.scanner.ScanViewModel

@Composable
fun ScannerScreen(
    viewModel: ScanViewModel = viewModel()
) {
    val context = LocalContext.current

    val scanStatus by viewModel.scanStatus.collectAsState()
    val capturedFrames by viewModel.capturedFrames.collectAsState()
    val currentAngle by viewModel.currentRotationAngle.collectAsState()
    val showCompletionDialog by viewModel.showCompletionDialog.collectAsState()
    val showGalleryDialog by viewModel.showGalleryDialog.collectAsState()
    val isCapturing by viewModel.isCapturingImage.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        viewModel.initSensorManager(context)
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Trigger pending auto-captures when sensor passes 18-degree thresholds
    LaunchedEffect(currentAngle) {
        viewModel.triggerPendingCaptureIfReady(context, imageCaptureInstance)
    }

    // Progress calculation: 20 frames = 100%
    val frameCount = capturedFrames.size
    val progressFraction = (frameCount / 20f).coerceIn(0f, 1f)
    val progressPercentage = (progressFraction * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full screen camera preview or permission prompt
        if (hasCameraPermission) {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                onImageCaptureCreated = { imageCaptureInstance = it }
            )
        } else {
            // Permission missing layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kamera İzni Gerekli",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "3D nesne taraması ve fotoğraf çekimi için kamera iznine ihtiyaç duyulmaktadır.",
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("request_permission_button")
                    ) {
                        Text("İzin Ver", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Overlay: 3D Reticle / Scanning target box in center
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ScannerReticle(
                isScanning = scanStatus == ScanStatus.SCANNING,
                currentAngle = currentAngle,
                frameCount = frameCount
            )
        }

        // Top Header Info Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            TopStatusCard(
                scanStatus = scanStatus,
                currentAngle = currentAngle,
                frameCount = frameCount,
                serverUrl = serverUrl,
                onViewGallery = { viewModel.openGalleryDialog() },
                onUpdateUrl = { newUrl -> viewModel.updateServerUrl(newUrl) }
            )

            // Captured thumbnails strip
            if (capturedFrames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    items(capturedFrames) { frame ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7))
                                .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${frame.index}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Control Panel: Circular Progress Bar & Big 'Tarama Başlat' Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xCC0F172A))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status text above button
            Text(
                text = when (scanStatus) {
                    ScanStatus.IDLE -> "3D Taramaya Başlamak İçin Butona Basın"
                    ScanStatus.SCANNING -> "Telefonu Nesne Etrafında Yavaşça Döndürün"
                    ScanStatus.COMPLETED -> "Tarama Tamamlandı (%100)"
                },
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Scanner Control Unit with Circular Progress Bar around the Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp)
            ) {
                // Circular Progress Canvas (%0 to %100)
                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "scan_progress"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeftOffset = Offset(strokeWidth / 2, strokeWidth / 2)
                    val arcSize = Size(diameter, diameter)

                    // Track background
                    drawArc(
                        color = Color(0xFF1E293B),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeftOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )

                    // Progress arc (%0 to %100)
                    drawArc(
                        color = Color(0xFF38BDF8),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeftOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Inner Big 'Tarama Başlat' / Control Button
                Surface(
                    onClick = {
                        if (scanStatus == ScanStatus.SCANNING) {
                            viewModel.stopScan()
                        } else {
                            viewModel.startScan(context, imageCaptureInstance)
                        }
                    },
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .testTag("scan_start_stop_button"),
                    color = if (scanStatus == ScanStatus.SCANNING) Color(0xFFEF4444) else Color(0xFF0284C7),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (scanStatus == ScanStatus.SCANNING) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (scanStatus == ScanStatus.SCANNING) "Durdur" else "Tarama\nBaşlat",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Percentage badge & Frame count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "%$progressPercentage DOLDU",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "•   $frameCount / 20 Kare",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Manual Step Button (for testing / manual triggers)
            if (scanStatus == ScanStatus.SCANNING) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.manualAngleStep(context, imageCaptureInstance) },
                        modifier = Modifier.testTag("manual_step_button"),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF38BDF8))),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+18° Manuel Kare Çek", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Completion Dialog: "Tarama Tamamlandı, Veriler Birleştiriliyor"
        if (showCompletionDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissCompletionDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Tarama Tamamlandı",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Tarama Tamamlandı, Veriler Birleştiriliyor",
                            color = Color(0xFFE2E8F0),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Toplam 20 kare başarıyla çekildi ve cihaz önbelleğine kaydedildi.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissCompletionDialog()
                            viewModel.openGalleryDialog()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.testTag("dialog_view_frames_button")
                    ) {
                        Text("3D Görünümü İncele", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissCompletionDialog()
                            viewModel.resetScan(context)
                        },
                        modifier = Modifier.testTag("dialog_new_scan_button")
                    ) {
                        Text("Yeni Tarama", color = Color(0xFF94A3B8))
                    }
                },
                containerColor = Color(0xFF0F172A),
                modifier = Modifier.testTag("completion_alert_dialog")
            )
        }

        // 360 Gallery Dialog
        if (showGalleryDialog) {
            FrameGalleryDialog(
                frames = capturedFrames,
                onDismiss = { viewModel.dismissGalleryDialog() }
            )
        }
    }
}

@Composable
fun TopStatusCard(
    scanStatus: ScanStatus,
    currentAngle: Float,
    frameCount: Int,
    serverUrl: String,
    onViewGallery: () -> Unit,
    onUpdateUrl: (String) -> Unit
) {
    var isEditingUrl by remember { mutableStateOf(false) }
    var editedUrlText by remember { mutableStateOf(serverUrl) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (scanStatus) {
                                    ScanStatus.IDLE -> Color(0xFFF59E0B)
                                    ScanStatus.SCANNING -> Color(0xFF10B981)
                                    ScanStatus.COMPLETED -> Color(0xFF38BDF8)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (scanStatus) {
                                ScanStatus.IDLE -> "HAZIR"
                                ScanStatus.SCANNING -> "TARANIYOR..."
                                ScanStatus.COMPLETED -> "TAMAMLANDI"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Dönüş: ${currentAngle.toInt()}° / 360°",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isEditingUrl = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .testTag("server_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Sunucu Ayarı",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    if (frameCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onViewGallery,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("open_gallery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = "Galeri",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }

            // Server URL preview bar
            Text(
                text = "Sunucu: $serverUrl",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (isEditingUrl) {
        AlertDialog(
            onDismissRequest = { isEditingUrl = false },
            title = {
                Text("Sunucu Adresi (HTTP POST)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column {
                    Text(
                        text = "3D tarama verilerinin gönderileceği yerel ağ sunucu adresini girin:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editedUrlText,
                        onValueChange = { editedUrlText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("server_url_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateUrl(editedUrlText)
                        isEditingUrl = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.testTag("save_server_url_button")
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditingUrl = false }) {
                    Text("İptal", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
fun ScannerReticle(
    isScanning: Boolean,
    currentAngle: Float,
    frameCount: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reticle_anim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        // Scanning frame canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val cornerLength = 28.dp.toPx()
            val color = if (isScanning) Color(0xFF38BDF8) else Color(0x88FFFFFF)

            // Top-Left corner
            drawLine(color, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth)
            drawLine(color, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth)

            // Top-Right corner
            drawLine(color, Offset(size.width, 0f), Offset(size.width - cornerLength, 0f), strokeWidth)
            drawLine(color, Offset(size.width, 0f), Offset(size.width, cornerLength), strokeWidth)

            // Bottom-Left corner
            drawLine(color, Offset(0f, size.height), Offset(cornerLength, size.height), strokeWidth)
            drawLine(color, Offset(0f, size.height), Offset(0f, size.height - cornerLength), strokeWidth)

            // Bottom-Right corner
            drawLine(color, Offset(size.width, size.height), Offset(size.width - cornerLength, size.height), strokeWidth)
            drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - cornerLength), strokeWidth)
        }

        // Center target reticle
        Icon(
            imageVector = Icons.Default.CenterFocusWeak,
            contentDescription = null,
            tint = if (isScanning) Color(0xFF38BDF8).copy(alpha = pulseAlpha) else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
    }
}
