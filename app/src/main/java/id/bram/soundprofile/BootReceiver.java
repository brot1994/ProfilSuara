package id.bram.soundprofile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Setelah HP dinyalakan ulang / jam diubah: jadwalkan ulang & kejar jadwal yang terlewat. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Engine.tick(c);
    }
}
