package com.plugin.xray

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Plugin
import libXray.LibXray
import libXray.DialerController
import org.json.JSONObject

class MyVpnService : VpnService(), DialerController {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val CHANNEL_ID = "vpn_service_channel"
    private val NOTIFICATION_ID = 1

    override fun protectFd(fd: Long): Boolean {
        val success = protect(fd.toInt())
        Log.d("XRAY_DEBUG", "Protect FD $fd: $success")
        return success
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("VPN")
            .setContentText("Работает")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Thread { startVpn() }.start()
        return START_STICKY
    }

    private fun startVpn() {
        try {
            LibXray.registerDialerController(this)
            LibXray.initDns(this, "1.1.1.1")

            val builder = Builder()
                .setSession("HappXray")
                .setMtu(1400)
                .addAddress("172.19.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDisallowedApplication(packageName)

            vpnInterface = builder.establish()
            val fd = vpnInterface?.fd ?: return
            
            Log.d("XRAY_DEBUG", "TUN FD: $fd")

            // ВАЖНО: передаем дескриптор в ядро через переменные окружения
            try {
                android.system.Os.setenv("vpn_tun_fd", fd.toString(), true)
                android.system.Os.setenv("VPN_TUN_FD", fd.toString(), true)
                android.system.Os.setenv("tun_fd", fd.toString(), true)
                android.system.Os.setenv("TUN_FD", fd.toString(), true)
            } catch (e: Exception) {
                Log.e("XRAY_DEBUG", "Setenv error: ${e.message}")
            }

            val configJson = """
            {
              "log": {"loglevel": "debug"},
              "dns": { "servers": ["1.1.1.1", "8.8.8.8"] },
              "inbounds": [
                {
                  "tag": "tun-in",
                  "port": 0,
                  "protocol": "tun",
                  "settings": {
                    "mtu": 1400,
                    "sniffing": { "enabled": true, "destOverride": ["http", "tls", "fakedns"] }
                  }
                }
              ],
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "vless",
                  "settings": {
                    "vnext": [{
                      "address": "de.safelane.pro",
                      "port": 443,
                      "users": [{ 
                        "id": "b329191a-6c5c-447a-800a-482cee25693b", 
                        "encryption": "none", 
                        "flow": "xtls-rprx-vision" 
                      }]
                    }]
                  },
                  "streamSettings": {
                    "network": "tcp",
                    "security": "reality",
                    "realitySettings": {
                      "serverName": "de.safelane.pro",
                      "publicKey": "NP5h1YxJALizPrsdRAuk1Sc2JAWYhtg3Pe0JqGSyVhY",
                      "shortId": "69d0c943a62745d6",
                      "fingerprint": "chrome"
                    }
                  }
                },
                { "tag": "dns-out", "protocol": "dns" },
                { "tag": "direct", "protocol": "freedom" }
              ],
              "routing": {
                "domainStrategy": "IPIfNonMatch",
                "rules": [
                  { "port": 53, "outboundTag": "dns-out" },
                  { "network": "udp,tcp", "outboundTag": "proxy" }
                ]
              }
            }
            """.trimIndent()

            val reqJson = JSONObject()
            reqJson.put("datDir", filesDir.absolutePath)
            reqJson.put("configJSON", configJson)
            
            val base64Req = Base64.encodeToString(reqJson.toString().toByteArray(), Base64.NO_WRAP)
            
            val result = LibXray.runXrayFromJSON(fd.toLong(), base64Req)
            
            Log.d("XRAY_DEBUG", "Core stopped: ${String(Base64.decode(result, Base64.DEFAULT))}")

        } catch (e: Exception) {
            Log.e("XRAY_DEBUG", "VPN Error: ${e.message}")
        }
    }

    override fun onDestroy() {
        vpnInterface?.close()
        super.onDestroy()
    }
}

@TauriPlugin
class ExamplePlugin(private val activity: Activity): Plugin(activity) {
    override fun load(webView: android.webkit.WebView) {
        super.load(webView)
        val vpnIntent = VpnService.prepare(activity)
        if (vpnIntent == null) {
            activity.startService(Intent(activity, MyVpnService::class.java))
        } else {
            activity.startActivityForResult(vpnIntent, 1)
        }
    }
}
