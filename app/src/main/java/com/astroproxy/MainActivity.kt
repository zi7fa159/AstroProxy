package com.astroproxy

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var urlInput: EditText
    private lateinit var prefs: SharedPreferences

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("log_msg")?.let { appendLog(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
        prefs = getSharedPreferences("AstroConfig", Context.MODE_PRIVATE)

        urlInput = findViewById(R.id.urlInput)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        logView = findViewById(R.id.logView)
        scrollView = logView.parent as ScrollView

        urlInput.setText(prefs.getString("saved_url", "ws://192.168.1.50:8080"))

        val filter = IntentFilter("com.astroproxy.LOG_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }

        btnStart.setOnClickListener {
            val url = urlInput.text.toString()
            if (!url.startsWith("ws")) {
                appendLog("❌ Error: Invalid URL. Must start with ws:// or wss://")
                return@setOnClickListener
            }
            prefs.edit().putString("saved_url", url).apply()
            
            val intent = Intent(this, AstroService::class.java).apply {
                putExtra("ws_url", url)
                action = "START"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) 
            else startService(intent)
            
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        }

        btnStop.setOnClickListener {
            startService(Intent(this, AstroService::class.java).apply { action = "STOP" })
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logView.append("[$time] $msg\n")
        
        // Auto-scroll to bottom
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(logReceiver)
        super.onDestroy()
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }
}
