package com.plugin.xray

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Plugin
import libXray.LibXray
import libXray.DialerController
import java.io.File

@TauriPlugin
class ExamplePlugin(private val activity: Activity): Plugin(activity) {
    override fun load(webView: android.webkit.WebView?) {
        super.load(webView)

        // Запрос отключения оптимизации батареи (для стабильной работы в фоне)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }

        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, 1)
        } else {
            activity.startService(Intent(activity, XrayVpnService::class.java))
        }
    }
}

class XrayVpnService : VpnService(), DialerController {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val channelId = "vpn_service_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VPN Активен")
            .setContentText("Соединение работает в фоне")
            .setSmallIcon(android.R.drawable.ic_secure) // Замените на свою иконку
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        Thread { startCore() }.start()
        return START_STICKY
    }

    // Защита сокетов для обхода петли маршрутизации
    override fun ProtectFd(fd: Int): Boolean {
        return protect(fd)
    }

    private fun startCore() {
        try {
            vpnInterface = Builder()
                .setSession("Xray-TUN")
                .addAddress("10.8.0.1", 24)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
                .establish()

            val tunFd = vpnInterface?.fd ?: return

            System.setProperty("xray.tun.fd", tunFd.toString())
            
            // Настройка DNS с передачей controller для защиты сокетов
            LibXray.initDns(this, "8.8.8.8")

            val configFile = File(filesDir, "config.json")
            configFile.writeText(getConfigString())

            val runRequest = LibXray.newXrayRunRequest(filesDir.absolutePath, configFile.absolutePath)
            LibXray.runXray(runRequest)

        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "VPN Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
    }

    private fun getConfigString(): String {
        return """
        {
            "dns": {
                "queryStrategy": "IPIfNonMatch",
                "servers": [{"address": "https://dns.google/dns-query", "skipFallback": false}],
                "tag": "dns_out"
            },
            "inbounds": [
                {
                    "port": 0,
                    "protocol": "tun",
                    "settings": {
                        "name": "xray0",
                        "MTU": 1500
                    },
                    "sniffing": {
                        "enabled": true,
                        "destOverride": ["http", "tls", "fakedns"]
                    },
                    "tag": "tun-in"
                }
            ],
            "log": { "loglevel": "warning" },
            "outbounds": [
                {
                    "protocol": "vless",
                    "settings": {
                        "vnext": [
                            {
                                "address": "de.safelane.pro",
                                "port": 443,
                                "users": [{"encryption": "none", "flow": "xtls-rprx-vision", "id": "b329191a-6c5c-447a-800a-482cee25693b"}]
                            }
                        ]
                    },
                    "streamSettings": {
                        "network": "tcp",
                        "realitySettings": {
                            "fingerprint": "chrome",
                            "publicKey": "NP5h1YxJALizPrsdRAuk1Sc2JAWYhtg3Pe0JqGSyVhY",
                            "serverName": "de.safelane.pro",
                            "shortId": "69d0c943a62745d6",
                            "show": false
                        },
                        "security": "reality"
                    },
                    "tag": "proxy"
                },
                { "protocol": "freedom", "tag": "direct" },
                { "protocol": "blackhole", "tag": "block" }
            ],
            "policy": {
                "system": { "statsOutboundDownlink": true, "statsOutboundUplink": true }
            },
            "routing": {
                "domainStrategy": "IPIfNonMatch",
                "rules": [
                    {
                        "domain": ["habr.com", "4pda.to", "4pda.ru", "kemono.su", "jut.su", "kara.su", "theins.ru", "tvrain.ru", "echo.msk.ru", "the-village.ru", "snob.ru", "novayagazeta.ru", "moscowtimes.ru"],
                        "outboundTag": "proxy"
                    },
                    {
                        "domain": ["geosite:private", "geosite:apple", "geosite:apple-pki", "geosite:huawei", "geosite:xiaomi", "geosite:category-android-app-download", "geosite:f-droid", "geosite:twitch", "geosite:category-ru"],
                        "outboundTag": "direct"
                    },
                    {
                        "ip": ["geoip:ru", "geoip:private"],
                        "outboundTag": "direct"
                    }
                ]
            }
        }
        """.trimIndent()
    }
}