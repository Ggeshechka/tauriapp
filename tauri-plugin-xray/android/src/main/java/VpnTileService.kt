package com.plugin.xray

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class VpnTileService : TileService() {

    override fun onStartListening() {
        val tile = qsTile ?: return
        val isRunning = XrayVpnService.isRunning
        
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Xray VPN"
        tile.updateTile()
    }

    override fun onClick() {
        val tile = qsTile ?: return
        
        if (XrayVpnService.isRunning) {
            XrayVpnService.isRunning = false
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()

            val intent = Intent(this, XrayVpnService::class.java).apply { action = "STOP" }
            ContextCompat.startForegroundService(this, intent)
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                val intent = Intent(this, Class.forName("com.pro100.tauriapp.MainActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(intent)
            } else {
                XrayVpnService.isRunning = true
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()

                val intent = Intent(this, XrayVpnService::class.java).apply { action = "START" }
                ContextCompat.startForegroundService(this, intent)
            }
        }
    }
}