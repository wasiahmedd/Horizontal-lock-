package com.wasiahmedd.horizontallock

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * CameraHelper
 *
 * Wraps CameraX to provide:
 *  1. Camera preview via PreviewView
 *  2. HD video recording to the public Movies/HorizontalLock gallery folder
 *  3. Real-time horizon roll correction applied to the PreviewView transform
 *
 * Roll correction works by counter-rotating the PreviewView and scaling it
 * so there are no black corners — exactly how hardware gimbal stabilization works.
 */
class CameraHelper(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner
) {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Called with `true` when recording starts, `false` when it stops. */
    var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .setExecutor(cameraExecutor)
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                onError?.invoke("Could not open camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Toggle recording on/off. Auto-saves to Movies/HorizontalLock with a timestamp name. */
    fun toggleRecording() {
        val current = activeRecording
        if (current != null) {
            current.stop()
            activeRecording = null
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "HorizonLock_$timestamp")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HorizontalLock")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        activeRecording = videoCapture?.output
            ?.prepareRecording(context, outputOptions)
            ?.withAudioEnabled()
            ?.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onRecordingStateChanged?.invoke(true)
                    is VideoRecordEvent.Finalize -> {
                        onRecordingStateChanged?.invoke(false)
                        if (event.hasError()) {
                            Log.e(TAG, "Recording error: ${event.cause}")
                            onError?.invoke("Recording failed: ${event.cause?.message}")
                        }
                    }
                    else -> Unit
                }
            }
    }

    /**
     * Apply horizon roll correction to the PreviewView.
     *
     * When [isLockEnabled] is true, we counter-rotate the view by [-rollDegrees]
     * and scale it up to fill the frame (avoid black corners).
     *
     * Scale formula: to fill the bounding box after rotation by angle θ,
     * we need scale ≥ |cos θ| + |sin θ|. This is the tightest correct fill.
     *
     * When [isLockEnabled] is false, we smoothly animate back to normal.
     */
    fun applyRollCorrection(rollDegrees: Float, isLockEnabled: Boolean) {
        if (isLockEnabled) {
            val correctionAngle = -rollDegrees
            val radians = Math.toRadians(rollDegrees.toDouble())
            val scale = (abs(cos(radians)) + abs(sin(radians))).toFloat()
                .coerceAtLeast(1f)

            previewView.animate()
                .rotation(correctionAngle)
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(32L)   // ~2 frames at 60fps → seamless
                .start()
        } else {
            previewView.animate()
                .rotation(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300L)
                .start()
        }
    }

    fun shutdown() {
        activeRecording?.stop()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraHelper"
    }
}
