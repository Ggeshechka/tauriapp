package com.plugin.xray

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.IBinder
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
class PingArgs { var value: String? = null }

@TauriPlugin
class ExamplePlugin(private val activity: Activity): Plugin(activity) {

    private var xrayService: IXrayStatus? = null

    private fun notifyFrontend(isRunning: Boolean) {
        activity.runOnUiThread {
            val data = JSObject().apply { put("running", isRunning) }
            trigger("vpn_state_changed", data)
        }
    }

    private val xrayCallback = object : IXrayCallback.Stub() {
        override fun onStateChanged(isRunning: Boolean) {
            notifyFrontend(isRunning)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            xrayService = IXrayStatus.Stub.asInterface(service)
            try {
                xrayService?.registerCallback(xrayCallback)
                notifyFrontend(xrayService?.isRunning ?: false)
            } catch (e: Exception) {}
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            xrayService = null
            notifyFrontend(false)
        }
    }

    private val wakeUpReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isRunning = intent.getBooleanExtra("running", false)
            notifyFrontend(isRunning)
            if (isRunning && xrayService == null) {
                bindAidlService()
            }
        }
    }

    private fun bindAidlService() {
        val bindIntent = Intent(activity, XrayVpnService::class.java).apply { action = "BIND_STATUS" }
        activity.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun load(webView: WebView) {
        super.load(webView)

        bindAidlService()

        webView.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                val isRunning = try { xrayService?.isRunning ?: false } catch (e: Exception) { false }
                notifyFrontend(isRunning)
            }
        }

        val filter = android.content.IntentFilter("com.plugin.xray.ACTION_VPN_STATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(wakeUpReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            activity.registerReceiver(wakeUpReceiver, filter)
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
            if (xrayService == null) bindAidlService()

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
                    var isRunning = false
                    if (xrayService != null) {
                        isRunning = try { xrayService?.isRunning ?: false } catch (e: Exception) { false }
                    } else {
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
                    }
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
