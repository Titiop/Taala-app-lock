package com.example.feature.intruder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.core.data.model.IntruderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class IntruderManager(private val context: Context) {

    private val database = TaalaDatabase.getInstance(context)
    private val dataStore = AppSettingsDataStore(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Failed attempts counter mapped by packageName
    private val failedAttempts = mutableMapOf<String, Int>()

    fun onUnlockFailed(packageName: String, appLabel: String) {
        val currentCount = (failedAttempts[packageName] ?: 0) + 1
        failedAttempts[packageName] = currentCount

        scope.launch {
            val enabled = dataStore.intruderSelfieEnabled.first()
            val threshold = dataStore.intruderThreshold.first()

            if (currentCount >= threshold) {
                // Record intruder attempt
                var photoPath: String? = null
                if (enabled && hasCameraPermission()) {
                    photoPath = captureFrontCameraSnapshot()
                }

                val record = IntruderEntity(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    packageName = packageName,
                    appLabel = appLabel,
                    photoUri = photoPath,
                    attemptCount = currentCount
                )
                database.intruderDao().insert(record)
                // Reset after recording
                failedAttempts[packageName] = 0
            }
        }
    }

    fun onUnlockSuccess(packageName: String) {
        failedAttempts.remove(packageName)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun captureFrontCameraSnapshot(): String? {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
            var frontCameraId: String? = null

            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id
                    break
                }
            }

            if (frontCameraId == null) {
                // Fallback to first camera if front facing not found
                frontCameraId = cameraManager.cameraIdList.firstOrNull() ?: return null
            }

            val intruderDir = File(context.filesDir, "intruder_captures").apply {
                if (!exists()) mkdirs()
            }
            val photoFile = File(intruderDir, "intruder_${System.currentTimeMillis()}.jpg")

            // Create image reader for capturing single frame
            val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1)
            val handler = Handler(Looper.getMainLooper())

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    FileOutputStream(photoFile).use { it.write(bytes) }
                } catch (_: Exception) {
                } finally {
                    image.close()
                }
            }, handler)

            // Setup camera device session (non-blocking capture)
            cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                        }
                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.capture(captureBuilder.build(), null, handler)
                                    } catch (_: Exception) {}
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    camera.close()
                                }
                            },
                            handler
                        )
                    } catch (_: Exception) {
                        camera.close()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, handler)

            return photoFile.absolutePath
        } catch (_: Exception) {
            return null
        }
    }
}
