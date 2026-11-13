package id.bram.volumemelayang;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Tile Quick Settings: nyalakan / sembunyikan / munculkan tombol melayang. */
public class FloatTile extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        update();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (FloatService.running) {
            try {
                startService(new Intent(this, FloatService.class).setAction(FloatService.ACTION_TOGGLE));
            } catch (Exception e) {
                openApp();
            }
        } else if (Settings.canDrawOverlays(this)) {
            try {
                startForegroundService(new Intent(this, FloatService.class));
            } catch (Exception e) {
                openApp();
            }
        } else {
            openApp();
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::update, 400);
    }

    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("autostart", true);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 9, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = FloatService.running && !FloatService.hidden;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(!FloatService.running ? "Mati" : FloatService.hidden ? "Tersembunyi" : "Tampil");
        }
        t.updateTile();
    }
}
