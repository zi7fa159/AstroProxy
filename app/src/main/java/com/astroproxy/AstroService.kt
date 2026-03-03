package com.astroproxy

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AstroService : Service(), WebSocketHandler.WebSocketListenerInterface {
    private lateinit var cameraHandler: CameraHandler
    private var webSocketHandler: WebSocketHandler? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        
        cameraHandler = CameraHandler(this, 
            onImageCaptured = { format, data ->
                webSocketHandler?.sendImage(format, data)
                webSocketHandler?.sendStatus("SUCCESS", "Image sent: ${data.size} bytes")
            },
            onError = { error ->
                webSocketHandler?.sendStatus("ERROR", error)
            }
        )
        cameraHandler.openCamera()
    }

    private fun startForegroundService() {
        val channelId = "astro_proxy_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AstroProxy", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AstroProxy Active")
            .setContentText("Awaiting remote commands...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("ws_url") ?: "ws://localhost:8080"
        webSocketHandler?.disconnect()
        webSocketHandler = WebSocketHandler(url, this)
        webSocketHandler?.connect()
        return START_STICKY
    }

    override fun onCommandReceived(command: Command) {
        when (command.type) {
            "CAPTURE" -> {
                webSocketHandler?.sendStatus("STATUS", "Capture initiated")
                cameraHandler.takePhoto(command.params)
            }
        }
    }

    override fun onConnectionChange(connected: Boolean, error: String?) {
        // Connection lost logic can be added here (e.g., auto-reconnect)
    }

    override fun onDestroy() {
        cameraHandler.close()
        webSocketHandler?.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
