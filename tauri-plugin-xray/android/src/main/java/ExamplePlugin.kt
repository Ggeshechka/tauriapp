package com.plugin.xray

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import org.json.JSONObject

// Класс для автоматического парсинга аргументов из Tauri (вместо getString)
@InvokeArg
class PingArgs {
    var value: String? = null
}

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

    @Command
    fun ping(invoke: Invoke) {
        try {
            // Парсим аргументы новым методом Tauri v2
            val args = invoke.parseArgs(PingArgs::class.java)
            val text = args.value ?: "{}"
            val json = JSONObject(text)
            val action = json.optString("action")
            val ret = JSObject()

            when (action) {
                "start" -> {
                    val intent = Intent(activity, XrayVpnService::class.java).apply { this.action = "START" }
                    ContextCompat.startForegroundService(activity, intent)
                    activity.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_running", true).apply()
                    ret.put("success", true)
                }
                "stop" -> {
                    val intent = Intent(activity, XrayVpnService::class.java).apply { this.action = "STOP" }
                    ContextCompat.startForegroundService(activity, intent)
                    activity.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_running", false).apply()
                    ret.put("success", true)
                }
                "status" -> {
                    val isRunning = activity.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE).getBoolean("is_running", false)
                    ret.put("running", isRunning)
                }
                else -> {
                    invoke.reject("Неизвестное действие: $action")
                    return
                }
            }
            invoke.resolve(ret)
            
        } catch (e: Exception) {
            invoke.reject("Ошибка: ${e.message}")
        }
    }
}