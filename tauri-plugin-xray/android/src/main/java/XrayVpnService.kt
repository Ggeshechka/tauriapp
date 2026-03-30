package com.plugin.xray

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import androidx.core.app.NotificationCompat
import libXray.LibXray
import libXray.DialerController
import java.io.File

class XrayVpnService : VpnService(), DialerController {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val channelId = "vpn_service_channel"

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val reply = Intent("com.plugin.xray.VPN_STATUS_REPLY")
            reply.putExtra("isRunning", isRunning)
            reply.setPackage(packageName)
            sendBroadcast(reply)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.plugin.xray.REQUEST_VPN_STATUS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        isRunning = true

        try {
            android.service.quicksettings.TileService.requestListeningState(this, android.content.ComponentName(this, VpnTileService::class.java))
        } catch (e: Exception) {}

        createNotificationChannel()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, launchIntent, android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Xray VPN")
            .setContentText("Защита активирована")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        if (vpnInterface == null) {
            Thread { startCore() }.start()
        }

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
            } catch (e: Exception) {}
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

            try { android.system.Os.setenv("GOMEMLIMIT", "40MiB", true) } catch (e: Exception) {}

            LibXray.initDns(this, "8.8.8.8:53")
            copyAsset("config.json")
            copyAsset("geoip.dat")
            copyAsset("geosite.dat")

            val datDir = filesDir.absolutePath
            val configPath = File(filesDir, "config.json").absolutePath

            val runRequest = LibXray.newXrayRunRequest(datDir, "", configPath, tunFd.toLong())
            LibXray.runXray(runRequest)
        } catch (e: Exception) {
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

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    private fun stopVpn() {
        isRunning = false
        try {
            android.service.quicksettings.TileService.requestListeningState(this, android.content.ComponentName(this, VpnTileService::class.java))
        } catch (e: Exception) {}

        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        try { LibXray.stopXray() } catch (e: Exception) {}
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        
        System.exit(0)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
