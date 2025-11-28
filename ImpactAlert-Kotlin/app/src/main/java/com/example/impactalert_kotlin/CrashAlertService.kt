package com.example.impactalert_kotlin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CrashAlertService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: AppPreferences.getServerUrl(this)

        createNotificationChannel()
        val notification = createNotification()

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            val errorMessage = "Service failed: Notification permission likely denied."
            Log.e(TAG, errorMessage, e)
            broadcastResult(errorMessage)
            stopSelf()
            return START_NOT_STICKY
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ImpactAlert:WakeLock")
        wakeLock.acquire(10 * 60 * 1000L /* 10 minutes timeout */)

        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "CrashAlertService work started.")
            var resultMessage = "Service finished without result."
            try {
                if (ContextCompat.checkSelfPermission(this@CrashAlertService, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@CrashAlertService)
                    val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()

                    if (location != null) {
                        Log.d(TAG, "Location acquired: ${location.latitude}, ${location.longitude}")
                        val success = HttpClient.sendCrashAlert(serverUrl, location.latitude, location.longitude)
                        resultMessage = if (success) "POST sent!" else "Failed POST"
                    } else {
                        resultMessage = "Location/POST failed: Could not retrieve location."
                    }
                } else {
                    resultMessage = "Location permission not granted inside service."
                }
            } catch (e: Exception) {
                resultMessage = "Location/POST failed: ${e.message}"
                Log.e(TAG, "Exception in CrashAlertService work", e)
            } finally {
                Log.d(TAG, "Work finished. Broadcasting result: '$resultMessage'")
                broadcastResult(resultMessage)
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun broadcastResult(message: String) {
        val intent = Intent(ACTION_CRASH_ALERT_RESULT).apply {
            setPackage(packageName) // Keep the broadcast within our app for security
            putExtra(EXTRA_RESULT_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Crash Alerts", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Impact Alert")
            .setContentText("Crash detected. Sending alert...")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    companion object {
        private const val TAG = "CrashAlertService"
        private const val CHANNEL_ID = "CrashAlertChannel"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val ACTION_CRASH_ALERT_RESULT = "com.example.impactalert_kotlin.CRASH_ALERT_RESULT"
        const val EXTRA_RESULT_MESSAGE = "extra_result_message"
    }
}
