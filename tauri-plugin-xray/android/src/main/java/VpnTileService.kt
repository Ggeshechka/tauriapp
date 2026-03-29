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
        val isRunning = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE).getBoolean("is_running", false)

        if (isRunning) {
            // Останавливаем
            val intent = Intent(this, XrayVpnService::class.java).apply { action = "STOP" }
            startService(intent)
        } else {
            // Проверяем разрешения перед запуском
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // Если нет разрешения - требуем открыть приложение
                val intent = Intent(this, Class.forName("com.pro100.tauriapp.MainActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(intent)
            } else {
                // Запускаем
                val intent = Intent(this, XrayVpnService::class.java).apply { action = "START" }
                startService(intent)
            }
        }
    }
}