package com.plugin.xray

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
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
class PingArgs { var value: String? = null }

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

        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, 1)
        }
    }

    @Command
    fun ping(invoke: Invoke) {
        try {
            if (xrayService == null) {
                bindAidlService()
            }

            val args = invoke.parseArgs(PingArgs::class.java)
            val json = JSONObject(args.value ?: "{}")
            val action = json.optString("action")
            val ret = JSObject()

            when (action) {
                "start" -> {
                    android.util.Log.d("XrayApp", "Plugin: UI requested START")
                    val intent = Intent(activity, XrayVpnService::class.java).apply { this.action = "START" }
                    ContextCompat.startForegroundService(activity, intent)
                    ret.put("value", JSONObject().apply { put("success", true) }.toString())
                    invoke.resolve(ret)
                }
                "stop" -> {
                    android.util.Log.d("XrayApp", "Plugin: UI requested STOP")
                    val intent = Intent(activity, XrayVpnService::class.java).apply { this.action = "STOP" }
                    ContextCompat.startForegroundService(activity, intent)
                    ret.put("value", JSONObject().apply { put("success", true) }.toString())
                    invoke.resolve(ret)
                }
                "status" -> {
                    var isRunning = false
                    try {
                        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        for (network in cm.allNetworks) {
                            val caps = cm.getNetworkCapabilities(network)
                            if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true) {
                                isRunning = true
                                break
                            }
                        }
                    } catch (e: Exception) {}
                    
                    val resp = JSONObject().apply { put("running", isRunning) }
                    ret.put("value", resp.toString())
                    invoke.resolve(ret)
                }
                else -> invoke.reject("Unknown action: $action")
            }
        } catch (e: Exception) {
            invoke.reject("Ошибка: ${e.message}")
        }
    }
}
