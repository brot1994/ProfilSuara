package id.bram.soundprofile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Engine.ACTION_APPLY.equals(i.getAction())) {
            Engine.activate(c, i.getLongExtra("id", -1), i.getIntExtra("min", 0));
        } else {
            Engine.tick(c);
        }
    }
}
