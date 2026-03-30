package com.plugin.xray

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import java.io.File

class VpnTileService : TileService() {

    private fun isVpnRunning(): Boolean {
        return try {
            val file = File(filesDir, "xray_status.txt")
            file.exists() && file.readText().trim() == "1"
        } catch (e: Exception) { false }
    }

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (isVpnRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Xray VPN"
        tile.updateTile()
    }

    override fun onClick() {
        val tile = qsTile ?: return
        
        if (isVpnRunning()) {
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
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()

                val intent = Intent(this, XrayVpnService::class.java).apply { action = "START" }
                ContextCompat.startForegroundService(this, intent)
            }
        }
 
    }
}
