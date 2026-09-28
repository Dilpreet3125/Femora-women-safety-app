package com.example.femora;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class SOSQuickSettingsService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        
        // Trigger SOS via MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("trigger_sos", true);
        
        // Collapse the status bar and start SOS
        startActivityAndCollapse(intent);
        
        Toast.makeText(this, "SOS Triggered from Quick Settings", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        getQsTile().setState(Tile.STATE_INACTIVE);
        getQsTile().updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        getQsTile().setState(Tile.STATE_INACTIVE);
        getQsTile().updateTile();
    }
}
