package com.example.share_screen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var socket: Socket? = null
    private var outStream: OutputStream? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ScreenCaptureService", "Service started")
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        startForegroundServiceNotification()

        handlerThread = HandlerThread("ScreenCapture")
        handlerThread?.start()
        handler = Handler(handlerThread!!.looper)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) {
            Log.e("ScreenCaptureService", "MediaProjection is null, stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        Thread {
            try {
                Log.d("ScreenCaptureService", "Attempting to connect to socket")
                socket = Socket("127.0.0.1", 54322)
                outStream = socket!!.getOutputStream()
                Log.d("ScreenCaptureService", "Socket connected, starting capture.")
                startCapture()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("ScreenCaptureService", "Socket connection failed: ", e)
            }
        }.start()

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "screen_capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Screen Capture", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Capture")
            .setContentText("Capturing screen...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1, notification)
    }

    private fun startCapture() {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi



        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener(ImageAvailableListener(), handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        Log.d("ScreenCaptureService", "Virtual display created")
    }

    private inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            val image = reader.acquireLatestImage() ?: return

            Log.d("ScreenCaptureService", "Image acquired")
            val buffer: ByteBuffer = image.planes[0].buffer
            val pixelStride = image.planes[0].pixelStride
            val rowStride = image.planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bmp = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
            image.close()

            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val bytes = stream.toByteArray()
            bmp.recycle()

            try {
                Log.d("ScreenCaptureService", "Sending ${bytes.size} bytes")
                outStream?.apply {
                    write((bytes.size shr 24) and 0xFF)
                    write((bytes.size shr 16) and 0xFF)
                    write((bytes.size shr 8) and 0xFF)
                    write(bytes.size and 0xFF)
                    write(bytes)
                    flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("ScreenCaptureService", "Failed to send data", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ScreenCaptureService", "Service destroyed")
        handlerThread?.quitSafely()
        virtualDisplay?.release()
        mediaProjection?.stop()
        socket?.close()
    }
}