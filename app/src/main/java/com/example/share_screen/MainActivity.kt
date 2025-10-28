package com.example.share_screen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Button

class MainActivity : Activity() {

    private val REQUEST_CODE = 1000
    private lateinit var mProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val button = findViewById<Button>(R.id.startButton)
        button.setOnClickListener {
            Log.d("MainActivity", "Requesting screen capture permission")
            val captureIntent = mProjectionManager.createScreenCaptureIntent()
            startActivityForResult(captureIntent, REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Log.d("MainActivity", "Screen capture permission granted, starting service")
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            serviceIntent.putExtra("resultCode", resultCode)
            serviceIntent.putExtra("data", data)
            startForegroundService(serviceIntent)
        } else {
            Log.d("MainActivity", "Screen capture permission denied")
        }
    }
}
