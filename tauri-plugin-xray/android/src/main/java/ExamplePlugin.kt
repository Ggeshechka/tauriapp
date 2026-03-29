package com.plugin.xray

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Plugin

@TauriPlugin
class ExamplePlugin(private val activity: Activity): Plugin(activity) {
    override fun load(webView: WebView) {
        super.load(webView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }

        // Запрашиваем разрешение на VPN при входе, но НЕ запускаем сам сервис
        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, 1)
        }
    }
}