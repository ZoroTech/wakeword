package com.example.wakeword

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.wakeword.audio.AudioProcessor
import com.example.wakeword.ml.ModelInterpreter
import kotlinx.coroutines.*

class WakeWordService : Service() {

    private val audioProcessor = AudioProcessor()
    private lateinit var modelInterpreter: ModelInterpreter
    private var lastDetectionTime = 0L
    private val cooldownMs = 2000L
    private val threshold = 0.6f

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "WakeWordChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WakeWordService", "Service created")
        modelInterpreter = ModelInterpreter(assets)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startWakeWordDetection()
            ACTION_STOP -> stopWakeWordDetection()
        }
        return START_STICKY
    }

    private fun startWakeWordDetection() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("WakeWordService", "Missing RECORD_AUDIO permission")
            stopSelf()
            return
        }

        val notification = createNotification("Listening for wake word...")
        startForeground(NOTIFICATION_ID, notification)

        audioProcessor.startRecording { mfcc ->
            val confidence = modelInterpreter.predict(mfcc)
            val currentTime = System.currentTimeMillis()

            if (confidence > threshold && (currentTime - lastDetectionTime) > cooldownMs) {
                lastDetectionTime = currentTime
                Log.d("WakeWordService", "Wake word detected! Confidence: $confidence")
                onWakeWordDetected(confidence)
            }
        }
    }

    private fun stopWakeWordDetection() {
        audioProcessor.stopRecording()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onWakeWordDetected(confidence: Float) {
        val notification = createNotification("Wake word detected! (${"%.0f".format(confidence * 100)}%)")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        serviceScope.launch {
            delay(2000)
            val listeningNotification = createNotification("Listening for wake word...")
            notificationManager.notify(NOTIFICATION_ID, listeningNotification)
        }

        sendBroadcast(Intent("com.example.wakeword.WAKE_WORD_DETECTED").apply {
            putExtra("confidence", confidence)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for wake word detection service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, WakeWordService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wake Word Detection")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        audioProcessor.stopRecording()
        serviceScope.cancel()
        Log.d("WakeWordService", "Service destroyed")
    }
}
