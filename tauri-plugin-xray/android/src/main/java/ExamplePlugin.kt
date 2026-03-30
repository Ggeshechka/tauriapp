package com.plugin.xray

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

@InvokeArg
class PingArgs {
    var value: String? = null
}

@TauriPlugin
class ExamplePlugin(private val activity: Activity): Plugin(activity) {

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isRunning = intent.getBooleanExtra("running", false)
            val data = JSObject().apply { put("running", isRunning) }
            trigger("vpn_state_changed", data)
        }
    }

    override fun load(webView: WebView) {
        super.load(webView)

        val filter = IntentFilter("com.plugin.xray.VPN_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(stateReceiver, filter)
        }

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
            val text = args.value ?: "{}"
            val json = JSONObject(text)
            val action = json.optString("action")
            val ret = JSObject()

            when (action) {
                "start" -> {
                    val intent = Intent(activity, XrayVpnService::class.java).apply { this.action = "START" }
                    ContextCompat.startForegroundService(activity, intent)
                    
                    val resp = JSONObject().apply { put("success", true) }
                    ret.put("value", resp.toString())
                    invoke.resolve(ret)
                }
                "stop" -> {
                    val intent = Intent(activity, XrayVpnService::class.java).apply { this.action = "STOP" }
                    ContextCompat.startForegroundService(activity, intent)
                    
                    val resp = JSONObject().apply { put("success", true) }
                    ret.put("value", resp.toString())
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
                else -> {
                    invoke.reject("Неизвестное действие: $action")
                }
            }
        } catch (e: Exception) {
            invoke.reject("Ошибка: ${e.message}")
       
        }
    }
}
