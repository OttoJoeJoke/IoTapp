package com.example.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class UploadState {
    object IDLE : UploadState()
    data class UPLOADING(val progressText: String = "Ana Beyin'e Aktarılıyor...") : UploadState()
    data class SUCCESS(val message: String = "Veriler Başarıyla İletildi ve 3D İşlem Başladı") : UploadState()
    data class ERROR(val errorMessage: String = "Bağlantı Hatası") : UploadState()
}

class ScanViewModel : ViewModel() {

    private val _scanStatus = MutableStateFlow(ScanStatus.IDLE)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _capturedFrames = MutableStateFlow<List<CapturedFrame>>(emptyList())
    val capturedFrames: StateFlow<List<CapturedFrame>> = _capturedFrames.asStateFlow()

    private val _currentRotationAngle = MutableStateFlow(0f)
    val currentRotationAngle: StateFlow<Float> = _currentRotationAngle.asStateFlow()

    private val _showCompletionDialog = MutableStateFlow(false)
    val showCompletionDialog: StateFlow<Boolean> = _showCompletionDialog.asStateFlow()

    private val _showGalleryDialog = MutableStateFlow(false)
    val showGalleryDialog: StateFlow<Boolean> = _showGalleryDialog.asStateFlow()

    private val _isCapturingImage = MutableStateFlow(false)
    val isCapturingImage: StateFlow<Boolean> = _isCapturingImage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.IDLE)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // Güncel IP adresinle (178) doğrudan başlatıldı
    private val _serverUrl = MutableStateFlow("http://192.168.1.178:5000/api/v1/3d-scan")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
    }

    val totalTargetFrames = 20
    val angleStepDegrees = 18f

    var gyroscopeSensorManager: GyroscopeSensorManager? = null
        private set

    fun initSensorManager(context: Context) {
        if (gyroscopeSensorManager == null) {
            gyroscopeSensorManager = GyroscopeSensorManager(context.applicationContext)
            viewModelScope.launch {
                gyroscopeSensorManager?.currentRotationDegrees?.collect { degrees ->
                    _currentRotationAngle.value = degrees
                    checkAutoCaptureTrigger()
                }
            }
        }
    }

    fun startScan(context: Context, imageCapture: ImageCapture?) {
        clearCache(context)
        _capturedFrames.value = emptyList()
        _showCompletionDialog.value = false
        _showGalleryDialog.value = false
        _scanStatus.value = ScanStatus.SCANNING

        gyroscopeSensorManager?.startListening()
        gyroscopeSensorManager?.resetAngle()

        captureFrame(context, imageCapture, targetAngle = 0f)
    }

    fun stopScan() {
        _scanStatus.value = ScanStatus.IDLE
        gyroscopeSensorManager?.stopListening()
    }

    fun resetScan(context: Context) {
        stopScan()
        clearCache(context)
        _capturedFrames.value = emptyList()
        _currentRotationAngle.value = 0f
        _showCompletionDialog.value = false
        _showGalleryDialog.value = false
    }

    private fun checkAutoCaptureTrigger() {
        if (_scanStatus.value != ScanStatus.SCANNING || _isCapturingImage.value) return

        val count = _capturedFrames.value.size
        if (count >= totalTargetFrames) return

        val requiredAngleForNextFrame = count * angleStepDegrees
        if (_currentRotationAngle.value >= requiredAngleForNextFrame) {
            _pendingAutoCaptureAngle = requiredAngleForNextFrame
        }
    }

    private var _pendingAutoCaptureAngle: Float? = null

    fun triggerPendingCaptureIfReady(context: Context, imageCapture: ImageCapture?) {
        val angle = _pendingAutoCaptureAngle
        if (angle != null && !_isCapturingImage.value && _scanStatus.value == ScanStatus.SCANNING) {
            _pendingAutoCaptureAngle = null
            captureFrame(context, imageCapture, angle)
        }
    }

    fun manualAngleStep(context: Context, imageCapture: ImageCapture?) {
        if (_scanStatus.value != ScanStatus.SCANNING) return
        val currentCount = _capturedFrames.value.size
        if (currentCount >= totalTargetFrames) return

        val targetAngle = currentCount * angleStepDegrees
        gyroscopeSensorManager?.addManualDegrees(angleStepDegrees)
        captureFrame(context, imageCapture, targetAngle)
    }

    fun captureFrame(context: Context, imageCapture: ImageCapture?, targetAngle: Float) {
        if (_isCapturingImage.value) return
        val currentCount = _capturedFrames.value.size
        if (currentCount >= totalTargetFrames) return

        val frameIndex = currentCount + 1

        val cacheDir = File(context.cacheDir, "3d_scan_frames").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
        val photoFile = File(cacheDir, "scan_frame_${String.format(Locale.US, "%02d", frameIndex)}_$timeStamp.jpg")

        if (imageCapture == null) {
            createFallbackSampleImage(photoFile)
            val newFrame = CapturedFrame(
                index = frameIndex,
                angleDegrees = targetAngle,
                fileUri = Uri.fromFile(photoFile),
                filePath = photoFile.absolutePath
            )
            onFrameSaved(context, newFrame)
            return
        }

        _isCapturingImage.value = true
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    _isCapturingImage.value = false
                    val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                    val newFrame = CapturedFrame(
                        index = frameIndex,
                        angleDegrees = targetAngle,
                        fileUri = savedUri,
                        filePath = photoFile.absolutePath
                    )
                    onFrameSaved(context, newFrame)
                }

                override fun onError(exception: ImageCaptureException) {
                    _isCapturingImage.value = false
                    Log.e("ScanViewModel", "Photo capture failed: ${exception.message}", exception)
                    createFallbackSampleImage(photoFile)
                    val newFrame = CapturedFrame(
                        index = frameIndex,
                        angleDegrees = targetAngle,
                        fileUri = Uri.fromFile(photoFile),
                        filePath = photoFile.absolutePath
                    )
                    onFrameSaved(context, newFrame)
                }
            }
        )
    }

    private fun onFrameSaved(context: Context, frame: CapturedFrame) {
        val updated = _capturedFrames.value + frame
        _capturedFrames.value = updated

        if (updated.size >= totalTargetFrames) {
            _scanStatus.value = ScanStatus.COMPLETED
            gyroscopeSensorManager?.stopListening()
            _showCompletionDialog.value = true
            
            // 20 Kare tamamlandığı an otomatik olarak sunucuya fırlatır!
            uploadFramesToServer(context)
        }
    }

    fun uploadFramesToServer(context: Context) {
        val frames = _capturedFrames.value
        if (frames.isEmpty()) {
            _uploadState.value = UploadState.ERROR("Gönderilecek kare bulunamadı.")
            return
        }

        _uploadState.value = UploadState.UPLOADING("Ana Beyin'e Aktarılıyor...")

        viewModelScope.launch {
            try {
                val responseCode = withContext(Dispatchers.IO) {
                    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)

                    builder.addFormDataPart("total_frames", frames.size.toString())
                    builder.addFormDataPart("scan_timestamp", System.currentTimeMillis().toString())

                    frames.forEachIndexed { index, frame ->
                        val file = File(frame.filePath)
                        if (file.exists() && file.length() > 0) {
                            val mediaType = "image/jpeg".toMediaTypeOrNull()
                            val requestBody = file.asRequestBody(mediaType)
                            builder.addFormDataPart("files", file.name, requestBody)
                            builder.addFormDataPart("frame_${index + 1}_angle", frame.angleDegrees.toString())
                        }
                    }

                    val requestBody = builder.build()
                    val request = Request.Builder()
                        .url(_serverUrl.value)
                        .post(requestBody)
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val code = response.code
                    response.close()
                    code
                }

                if (responseCode == 200) {
                    _uploadState.value = UploadState.SUCCESS("Veriler Başarıyla İletildi ve 3D İşlem Başladı")
                } else {
                    _uploadState.value = UploadState.ERROR("Bağlantı Hatası")
                }
            } catch (e: Exception) {
                Log.e("ScanViewModel", "Upload error: ${e.message}", e)
                _uploadState.value = UploadState.ERROR("Bağlantı Hatası")
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.IDLE
    }

    private fun createFallbackSampleImage(file: File) {
        try {
            if (!file.exists()) {
                file.createNewFile()
                file.writeBytes(ByteArray(1024))
            }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error writing sample image file", e)
        }
    }

    private fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "3d_scan_frames")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error clearing cache", e)
        }
    }

    fun dismissCompletionDialog() {
        _showCompletionDialog.value = false
    }

    fun openGalleryDialog() {
        _showGalleryDialog.value = true
    }

    fun dismissGalleryDialog() {
        _showGalleryDialog.value = false
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        gyroscopeSensorManager?.stopListening()
    }
}
