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
            } ?: throw Exception("Hardware Error: No rear camera detected.")

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) { 
                    cameraDevice = camera 
                }
                override fun onDisconnected(camera: CameraDevice) { 
                    camera.close()
                    onError("System: Camera disconnected by OS.")
                }
                override fun onError(camera: CameraDevice, error: Int) { 
                    camera.close()
                    val msg = when(error) {
                        ERROR_CAMERA_IN_USE -> "Hardware Error: Camera already in use by another app."
                        ERROR_MAX_CAMERAS_IN_USE -> "System Error: Too many camera sessions open."
                        ERROR_CAMERA_DISABLED -> "Security Error: Camera disabled by device policy."
                        else -> "Internal Error: Camera failed with code $error"
                    }
                    onError(msg)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Initialization Failed: ${e.message}")
        }
    }

    fun takePhoto(params: CaptureParams?) {
        val device = cameraDevice ?: return onError("State Error: Camera not initialized yet.")
        
        try {
            val format = if (params?.format == "RAW") ImageFormat.RAW_SENSOR else ImageFormat.JPEG
            val characteristics = cameraManager.getCameraCharacteristics(device.id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            
            val size = map?.getOutputSizes(format)?.maxByOrNull { it.width * it.height } 
                ?: return onError("Format Error: Device does not support ${params?.format ?: "JPEG"}")

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

                    if (params?.iso != null && params.exposureTimeNs != null) {
                        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, params.iso)
                        requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, params.exposureTimeNs)
                    }

                    session.capture(requestBuilder.build(), null, backgroundHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError("Session Error: Could not configure capture pipeline.")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Capture Exception: ${e.message}")
        }
    }

    fun close() {
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        handlerThread.quitSafely()
    }
}
