package com.example.impactalert_kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val deviceName = "ImpactAlert_Device"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImpactAlertApp()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ImpactAlertApp() {
        var serverUrl by remember { mutableStateOf(AppPreferences.getServerUrl(this@MainActivity)) }
        var statusText by remember { mutableStateOf("Disconnected") }
        val context = LocalContext.current

        // Combined receiver for all status updates from our services
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val message = when (intent?.action) {
                        AppPreferences.ACTION_BT_STATUS_UPDATE -> intent.getStringExtra(AppPreferences.EXTRA_BT_STATUS_MESSAGE)
                        CrashAlertService.ACTION_CRASH_ALERT_RESULT -> intent.getStringExtra(CrashAlertService.EXTRA_RESULT_MESSAGE)
                        else -> null
                    }
                    if (message != null) {
                        statusText = message
                    }
                }
            }

            val intentFilter = IntentFilter().apply {
                addAction(AppPreferences.ACTION_BT_STATUS_UPDATE)
                addAction(CrashAlertService.ACTION_CRASH_ALERT_RESULT)
            }

            ContextCompat.registerReceiver(context, receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose { context.unregisterReceiver(receiver) }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            if (permissions.values.all { it }) {
                connectToEsp(context)
            } else {
                statusText = "Permissions were denied."
                Toast.makeText(context, "All permissions are required for the app to function.", Toast.LENGTH_LONG).show()
            }
        }

        Scaffold(
            topBar = { SmallTopAppBar(title = { Text("Impact Alert Rider") }) }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                TextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { AppPreferences.setServerUrl(this@MainActivity, serverUrl); Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show() }) { Text("Save") }
                    Button(onClick = { requestPermissionsAndConnect(context, permissionLauncher) }) { Text("Connect") }
                    Button(onClick = { stopBluetoothService(context); statusText = "Disconnected" }) { Text("Disconnect") }
                }

                Text("Status: $statusText")
            }
        }
    }

    private fun requestPermissionsAndConnect(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        val permissionsToRequest = mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { add(Manifest.permission.POST_NOTIFICATIONS) }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { add(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
        }

        val permissionsNeeded = permissionsToRequest.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

        if (permissionsNeeded.isEmpty()) {
            connectToEsp(context)
        } else {
            launcher.launch(permissionsNeeded.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToEsp(context: Context) {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val btAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (btAdapter == null || !btAdapter.isEnabled) {
            Toast.makeText(context, "Bluetooth is not enabled or supported.", Toast.LENGTH_SHORT).show()
            return
        }

        val device: BluetoothDevice? = btAdapter.bondedDevices.find { it.name == deviceName }
        if (device == null) {
            Toast.makeText(context, "\"$deviceName\" not paired. Please pair the device in Bluetooth settings.", Toast.LENGTH_LONG).show()
            return
        }

        Intent(context, BluetoothService::class.java).also { intent ->
            intent.putExtra(BluetoothService.EXTRA_DEVICE_ADDRESS, device.address)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun stopBluetoothService(context: Context) {
        context.stopService(Intent(context, BluetoothService::class.java))
    }
}
