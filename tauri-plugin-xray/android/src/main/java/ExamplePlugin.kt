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
import android.util.Log
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Plugin
import libXray.LibXray
import libXray.DialerController
import java.io.File

@TauriPlugin
class ExamplePlugin(private val activity: Activity): Plugin(activity) {
    override fun load(webView: WebView) {
        super.load(webView)
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
            .setContentTitle("VPN")
            .setContentText("Работает")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        Thread { startCore() }.start()
        return START_STICKY
    }

    override fun protectFd(fd: Long): Boolean {
        return protect(fd.toInt())
    }

    private fun copyAsset(assetName: String) {
        val file = File(filesDir, assetName)
        if (!file.exists()) {
            try {
                assets.open(assetName).use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                Log.e("XrayVPN", "Ошибка копирования $assetName", e)
            }
        }
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

            LibXray.initDns(this, "8.8.8.8:53")

            copyAsset("config.json")
            copyAsset("geoip.dat")
            copyAsset("geosite.dat")

            val datDir = filesDir.absolutePath
            val configPath = File(filesDir, "config.json").absolutePath

            // Передаем tunFd 4-ым аргументом
            val runRequest = LibXray.newXrayRunRequest(datDir, "", configPath, tunFd.toLong())
            val result = LibXray.runXray(runRequest)
            Log.d("XrayVPN", "Run Xray Result: $result")

        } catch (e: Exception) {
            Log.e("XrayVPN", "Ошибка", e)
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
        val restartIntent = Intent(applicationContext, this.javaClass)
        restartIntent.setPackage(packageName)
        startService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
        try {
            LibXray.stopXray()
        } catch (e: Exception) {}
    }
}