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
    
    private var imageReader: ImageReader? = null
    private val handlerThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(handlerThread.looper)

    @SuppressLint("MissingPermission")
    fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: throw Exception("No rear camera found")

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) { 
                    cameraDevice = camera 
                    Log.d("AstroCam", "Camera Opened")
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { 
                    camera.close()
                    onError("Camera Error Code: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Failed to open camera: ${e.message}")
        }
    }

    fun takePhoto(params: CaptureParams?) {
        val device = cameraDevice ?: return onError("Camera not ready")
        
        try {
            val format = if (params?.format == "RAW") ImageFormat.RAW_SENSOR else ImageFormat.JPEG
            val characteristics = cameraManager.getCameraCharacteristics(device.id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(format)?.maxByOrNull { it.width * it.height } ?: return onError("Format not supported")

            imageReader = ImageReader.newInstance(size.width, size.height, format, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    onImageCaptured(if (format == ImageFormat.RAW_SENSOR) "RAW" else "JPG", bytes)
                }, backgroundHandler)
            }

            val surfaces = listOf(imageReader!!.surface)
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    requestBuilder.addTarget(imageReader!!.surface)

                    // Apply Manual Settings if provided
                    if (params?.iso != null && params.exposureTimeNs != null) {
                        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, params.iso)
                        requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, params.exposureTimeNs)
                    }

                    session.capture(requestBuilder.build(), null, backgroundHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError("Session configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Capture failed: ${e.message}")
        }
    }

    fun close() {
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        handlerThread.quitSafely()
    }
}
