package com.pasich.mynotes.ui.tile;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import com.pasich.mynotes.R;
import com.pasich.mynotes.ui.view.activity.noteEditor.NoteActivity;
import com.pasich.mynotes.utils.navigation.NoteExtras;

public class NoteQuickTile extends TileService {

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_ACTIVE);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_add));
        tile.setLabel(getString(R.string.tile_label));
        tile.updateTile();
    }

    @Override
    @SuppressLint("StartActivityAndCollapseDeprecated")
    public void onClick() {
        Intent intent = new Intent(this, NoteActivity.class);
        intent.putExtra(NoteExtras.EXTRA_NEW_NOTE, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // The Intent overload is the only one that exists below API 34; the call is already
        // guarded, so the deprecation warning is about the older branch by design.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pi =
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
