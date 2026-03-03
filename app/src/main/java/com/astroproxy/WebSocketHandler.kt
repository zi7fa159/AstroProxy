package com.astroproxy

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okio.ByteString

class WebSocketHandler(
    private val url: String,
    private val listener: WebSocketListenerInterface
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val gson = Gson()

    interface WebSocketListenerInterface {
        fun onCommandReceived(command: Command)
        fun onConnectionChange(connected: Boolean, error: String? = null)
    }

    fun connect() {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("AstroWS", "Connected to $url")
                listener.onConnectionChange(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("AstroWS", "Received: $text")
                try {
                    val cmd = gson.fromJson(text, Command::class.java)
                    listener.onCommandReceived(cmd)
                } catch (e: Exception) {
                    Log.e("AstroWS", "JSON Parse Error", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onConnectionChange(false, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("AstroWS", "Connection Failure", t)
                listener.onConnectionChange(false, t.message)
            }
        })
    }

    fun sendImage(format: String, data: ByteArray) {
        // Send binary data efficiently via WebSocket
        webSocket?.send(ByteString.of(*data))
    }

    fun sendStatus(status: String, details: String) {
        val response = gson.toJson(StatusUpdate(status, details))
        webSocket?.send(response)
    }

    fun disconnect() {
        webSocket?.close(1000, "Service stopped")
    }
}
