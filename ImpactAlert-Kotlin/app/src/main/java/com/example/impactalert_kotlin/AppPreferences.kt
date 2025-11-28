package com.example.impactalert_kotlin

import android.content.Context

object AppPreferences {

    private const val PREFS_NAME = "ImpactAlertPrefs"
    private const val KEY_SERVER_URL = "server_url"

    // Action and extra for the BluetoothService status updates
    const val ACTION_BT_STATUS_UPDATE = "com.example.impactalert_kotlin.BT_STATUS_UPDATE"
    const val EXTRA_BT_STATUS_MESSAGE = "extra_bt_status_message"

    fun setServerUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, "") ?: ""
    }
}
