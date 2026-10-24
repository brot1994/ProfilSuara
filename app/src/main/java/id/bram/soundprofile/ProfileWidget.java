package id.bram.soundprofile;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Widget layar utama: nama profil aktif + 4 tombol profil. */
public class ProfileWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        updateAll(c);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, ProfileWidget.class));
        if (ids == null || ids.length == 0) return;

        Store s = new Store(c);
        List<Profile> ps = s.profiles();
        Profile a = s.profile(s.activeId());
        String title = a == null ? "Profil Suara" : a.name;
        long tu = s.tempUntil();
        if (tu > System.currentTimeMillis()) title += "  ·  s/d " + Engine.fmtTime(tu);

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
        rv.setTextViewText(R.id.title, title);
        Intent open = new Intent(c, MainActivity.class);
        rv.setOnClickPendingIntent(R.id.title, PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        int[] btn = {R.id.b0, R.id.b1, R.id.b2, R.id.b3};
        for (int i = 0; i < btn.length; i++) {
            if (i < ps.size()) {
                Profile p = ps.get(i);
                rv.setViewVisibility(btn[i], View.VISIBLE);
                rv.setTextViewText(btn[i], p.name);
                rv.setTextColor(btn[i], p.id == s.activeId() ? 0xFFFFB300 : 0xFFEEEEEE);
                rv.setOnClickPendingIntent(btn[i], Engine.applyIntent(c, p.id, 2000));
            } else {
                rv.setViewVisibility(btn[i], View.GONE);
            }
        }
        m.updateAppWidget(ids, rv);
    }
}
