package com.example.impactalert_kotlin

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket // Correct import
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission") // Permissions are checked in MainActivity
class BluetoothService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var socket: BluetoothSocket? = null // Correctly uses android.bluetooth.BluetoothSocket

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (deviceAddress == null) {
            Log.e(TAG, "Service started with no device address.")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Connecting to device..."))
        broadcastUpdate("Connecting...")

        scope.launch {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val btAdapter: BluetoothAdapter? = bluetoothManager.adapter
            val device: BluetoothDevice? = btAdapter?.getRemoteDevice(deviceAddress)

            if (device == null) {
                broadcastUpdate("Device not found")
                stopSelf()
                return@launch
            }

            try {
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket?.connect() // Blocking call

                updateNotification("Connected to ${device.name}")
                broadcastUpdate("Connected")

                val inputStream = socket?.inputStream
                val buffer = ByteArray(1024)
                while (true) {
                    val bytes = try {
                        inputStream?.read(buffer) ?: break
                    } catch (e: IOException) {
                        broadcastUpdate("Connection lost")
                        break
                    }
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes).trim()
                        if (message.isNotEmpty()) {
                            // The service now handles the CRASH signal directly.
                            if (message == "CRASH") {
                                broadcastUpdate("Crash detected! Sending alert...")
                                startCrashAlertService()
                            } else {
                                // Other messages can be broadcast if needed for debugging
                                // broadcastUpdate(message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed in service", e)
                broadcastUpdate("Connection failed")
            } finally {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startCrashAlertService() {
        val intent = Intent(this, CrashAlertService::class.java).apply {
            putExtra(CrashAlertService.EXTRA_SERVER_URL, AppPreferences.getServerUrl(this@BluetoothService))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Could not close the client socket", e)
        }
        job.cancel()
        broadcastUpdate("Disconnected")
        Log.d(TAG, "BluetoothService destroyed.")
    }

    private fun broadcastUpdate(message: String) {
        val intent = Intent(AppPreferences.ACTION_BT_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra(AppPreferences.EXTRA_BT_STATUS_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Bluetooth Connection", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Impact Alert")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .build()
    }

    companion object {
        private const val TAG = "BluetoothService"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        private const val CHANNEL_ID = "BluetoothConnectionChannel"
        private const val NOTIFICATION_ID = 2
    }
}
