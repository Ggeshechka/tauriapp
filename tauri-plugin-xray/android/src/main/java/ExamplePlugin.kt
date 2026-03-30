package com.plugin.xray

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.FileObserver
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
import java.io.File

@InvokeArg
class PingArgs {
    var value: String? = null
}

@TauriPlugin
class ExamplePlugin(private val activity: Activity): Plugin(activity) {

    private var fileObserver: FileObserver? = null

    private fun notifyFrontend() {
        try {
            val statusFile = File(activity.filesDir, "xray_status.txt")
            val isRunning = statusFile.exists() && statusFile.readText().trim() == "1"
            val data = JSObject().apply { put("running", isRunning) }
            trigger("vpn_state_changed", data)
        } catch (e: Exception) {}
    }

    override fun load(webView: WebView) {
        super.load(webView)

        val statusFile = File(activity.filesDir, "xray_status.txt")
        if (!statusFile.exists()) {
            try { statusFile.writeText("0") } catch (e: Exception) {}
        }

        fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(statusFile, FileObserver.CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) {
                    notifyFrontend()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(statusFile.absolutePath, FileObserver.CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) {
                    notifyFrontend()
                }
            }
        }
        fileObserver?.startWatching()

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

        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, 1)
        }
    }

    @Command
    fun ping(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(PingArgs::class.java)
            val json = JSONObject(args.value ?: "{}")
            val action = json.optString("action")
            val ret = JSObject()

            when (action) {
                "start" -> {
                    val intent = Intent(activity, XrayVpnService::class.java).apply { this.action = "START" }
                    ContextCompat.startForegroundService(activity, intent)
                    ret.put("value", JSONObject().apply { put("success", true) }.toString())
                    invoke.resolve(ret)
                }
                "stop" -> {
                    val intent = Intent(activity, XrayVpnService::class.java).apply { this.action = "STOP" }
                    ContextCompat.startForegroundService(activity, intent)
                    ret.put("value", JSONObject().apply { put("success", true) }.toString())
                    invoke.resolve(ret)
                }
                "status" -> {
                    val statusFile = File(activity.filesDir, "xray_status.txt")
                    val isRunning = statusFile.exists() && statusFile.readText().trim() == "1"
                    ret.put("value", JSONObject().apply { put("running", isRunning) }.toString())
                    invoke.resolve(ret)
                }
                else -> invoke.reject("Unknown action: $action")
            }
        } catch (e: Exception) {
            invoke.reject("Error: ${e.message}")
     
        }
    }
}
