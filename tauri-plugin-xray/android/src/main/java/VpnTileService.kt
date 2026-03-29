package com.plugin.xray

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class VpnTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        val isRunning = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE).getBoolean("is_running", false)
        
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Xray VPN"
        tile.updateTile()
    }

    override fun onClick() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("is_running", false)

        if (isRunning) {
            // Мгновенно выключаем кнопку визуально
            prefs.edit().putBoolean("is_running", false).apply()
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()

            // Посылаем команду на остановку
            val intent = Intent(this, XrayVpnService::class.java).apply { action = "STOP" }
            startService(intent)
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // Если система требует подтверждения - открываем главное приложение
                val intent = Intent(this, Class.forName("com.pro100.tauriapp.MainActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(intent)
            } else {
                // Мгновенно включаем кнопку визуально
                prefs.edit().putBoolean("is_running", true).apply()
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()

                // Посылаем команду на запуск
                val intent = Intent(this, XrayVpnService::class.java).apply { action = "START" }
                startService(intent)
            }
        }
    }
}