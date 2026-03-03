package com.astroproxy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

class CameraHandler(
    private val context: Context,
    private val onImageCaptured: (String, ByteArray) -> Unit,
    private val onError: (String) -> Unit
) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    private val handlerThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(handlerThread.looper)

    @SuppressLint("MissingPermission")
    fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: throw Exception("No rear camera found")

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) { cameraDevice = camera }
                override fun onDisconnected(camera: CameraDevice) { 
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    onError("Camera Hardware Error $error. Resetting...")
                    // Auto-reopen on hardware failure
                    Handler(backgroundHandler.looper).postDelayed({ openCamera() }, 1000)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Init Error: ${e.message}")
        }
    }

    fun takePhoto(params: CaptureParams?) {
        val device = cameraDevice ?: return onError("Camera Not Ready")
        
        try {
            val isRaw = params?.format == "RAW"
            val format = if (isRaw) ImageFormat.RAW_SENSOR else ImageFormat.JPEG
            val characteristics = cameraManager.getCameraCharacteristics(device.id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(format)?.maxByOrNull { it.width * it.height } ?: return onError("Format Unsupported")

            // Create a fresh reader for this shot
            val reader = ImageReader.newInstance(size.width, size.height, format, 1)
            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val plane = image.planes[0]
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                r.close() // Close reader immediately after extracting bytes
                onImageCaptured(if (isRaw) "RAW" else "JPG", bytes)
            }, backgroundHandler)

            device.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    requestBuilder.addTarget(reader.surface)

                    if (params?.iso != null && params.exposureTimeNs != null) {
                        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, params.iso)
                        requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, params.exposureTimeNs)
                    }

                    session.capture(requestBuilder.build(), null, backgroundHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError("Pipeline Config Failed")
                    reader.close()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Capture Exception: ${e.message}")
        }
    }

    fun close() {
        cameraDevice?.close()
        handlerThread.quitSafely()
    }
}
