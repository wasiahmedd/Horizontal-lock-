package com.wasiahmedd.horizontallock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wasiahmedd.horizontallock.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraHelper: CameraHelper
    private lateinit var horizonLockManager: HorizonLockManager

    private var isLockEnabled = true
    private var isRecording = false

    // ── Permissions ──────────────────────────────────────────────────────────

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            startCameraAndSensors()
        } else {
            Toast.makeText(
                this,
                "Camera & Microphone permissions are required",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make camera preview fill the whole screen (edge-to-edge)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        cameraHelper = CameraHelper(this, binding.previewView, this)
        horizonLockManager = HorizonLockManager(this)

        setupCallbacks()
        setupButtons()
        updateLockUI()

        if (allPermissionsGranted()) {
            startCameraAndSensors()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            horizonLockManager.start()
        }
    }

    override fun onPause() {
        super.onPause()
        horizonLockManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper.shutdown()
        horizonLockManager.stop()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupCallbacks() {
        cameraHelper.onRecordingStateChanged = { recording ->
            isRecording = recording
            runOnUiThread {
                if (recording) {
                    binding.recordButton.setImageResource(R.drawable.ic_stop)
                    binding.recordingIndicator.visibility = View.VISIBLE
                    binding.recordingTimer.visibility = View.VISIBLE
                } else {
                    binding.recordButton.setImageResource(R.drawable.ic_record)
                    binding.recordingIndicator.visibility = View.GONE
                    binding.recordingTimer.visibility = View.GONE
                }
            }
        }

        cameraHelper.onError = { msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        binding.lockToggle.setOnClickListener {
            isLockEnabled = !isLockEnabled
            updateLockUI()
        }

        binding.recordButton.setOnClickListener {
            cameraHelper.toggleRecording()
        }
    }

    private fun updateLockUI() {
        if (isLockEnabled) {
            binding.lockToggle.setImageResource(R.drawable.ic_lock)
            binding.lockStatusText.text = getString(R.string.lock_on)
            binding.lockStatusText.setTextColor(getColor(R.color.lock_active))
        } else {
            binding.lockToggle.setImageResource(R.drawable.ic_lock_open)
            binding.lockStatusText.text = getString(R.string.lock_off)
            binding.lockStatusText.setTextColor(getColor(R.color.lock_inactive))
        }
    }

    // ── Camera + Sensor ───────────────────────────────────────────────────────

    private fun startCameraAndSensors() {
        cameraHelper.startCamera()
        horizonLockManager.start()

        // Collect roll angle updates on the main thread and apply correction
        lifecycleScope.launch {
            horizonLockManager.rollAngle.collectLatest { roll ->
                cameraHelper.applyRollCorrection(roll, isLockEnabled)
                updateHUD(roll)
            }
        }
    }

    private fun updateHUD(roll: Float) {
        val degrees = abs(roll).roundToInt()
        binding.angleReadout.text = if (isLockEnabled) {
            if (degrees < 2) "Perfectly Level" else "Locked · ${degrees}°"
        } else {
            "Tilt ${degrees}°"
        }

        // The horizon indicator line tilts opposite to the phone (shows real-world horizon)
        binding.horizonLine.rotation = if (isLockEnabled) 0f else -roll
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
