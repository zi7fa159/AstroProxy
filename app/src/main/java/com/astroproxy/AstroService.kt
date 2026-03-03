package com.astroproxy

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.Context
import androidx.core.app.NotificationCompat
import android.util.Log

class AstroService : Service(), WebSocketHandler.WebSocketListenerInterface {
    private lateinit var cameraHandler: CameraHandler
    private var webSocketHandler: WebSocketHandler? = null
    private val channelId = "astro_proxy_channel"

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification("Initializing..."))
        
        cameraHandler = CameraHandler(this, 
            onImageCaptured = { format, data ->
                webSocketHandler?.sendImage(format, data)
                val msg = "📸 Sent: $format (${data.size / 1024} KB)"
                webSocketHandler?.sendStatus("SUCCESS", msg)
                broadcastLog(msg)
                updateNotification("Last Capture: $msg")
            },
            onError = { error ->
                broadcastLog("❌ $error")
                webSocketHandler?.sendStatus("ERROR", error)
                updateNotification("Error: $error")
            }
        )
        cameraHandler.openCamera()
    }

    private fun createNotification(content: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AstroProxy Active", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, 
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AstroProxy Running")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createNotification(content))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra("ws_url") ?: return START_STICKY
        
        if (webSocketHandler == null) {
            webSocketHandler = WebSocketHandler(url, this)
            webSocketHandler?.connect()
            updateNotification("Connecting to $url")
        }
        
        return START_STICKY
    }

    override fun onCommandReceived(command: Command) {
        broadcastLog("📩 Command: ${command.type}")
        if (command.type == "CAPTURE") {
            updateNotification("Capturing Image...")
            cameraHandler.takePhoto(command.params)
        }
    }

    override fun onConnectionChange(connected: Boolean, error: String?) {
        if (connected) {
            broadcastLog("✅ Connected to Server")
            updateNotification("Connected & Ready")
        } else {
            broadcastLog("⚠️ Offline: $error")
            updateNotification("Offline: Reconnecting...")
        }
    }

    private fun broadcastLog(message: String) {
        val intent = Intent("com.astroproxy.LOG_UPDATE")
        intent.putExtra("log_msg", message)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        cameraHandler.close()
        webSocketHandler?.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
