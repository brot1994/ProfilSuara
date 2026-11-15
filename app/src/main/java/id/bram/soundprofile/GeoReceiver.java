package id.bram.soundprofile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

/** Menerima kejadian geofence: masuk gereja → profil Senyap, menjauh 500 m → profil Pelan. */
public class GeoReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        GeofencingEvent ev = GeofencingEvent.fromIntent(intent);
        if (ev == null || ev.hasError()) return;
        int tr = ev.getGeofenceTransition();
        List<Geofence> fs = ev.getTriggeringGeofences();
        if (fs == null) return;
        Store s = new Store(c);
        if (!s.geoOn()) return;
        Location loc = ev.getTriggeringLocation();

        for (Geofence f : fs) {
            String id = f.getRequestId();
            if (id.equals("EXIT") && tr == Geofence.GEOFENCE_TRANSITION_EXIT) {
                leave(c);
                return;
            }
            if (id.equals("AREA") && tr == Geofence.GEOFENCE_TRANSITION_EXIT && loc != null) {
                final PendingResult pr = goAsync();
                final double la = loc.getLatitude(), lo = loc.getLongitude();
                new Thread(() -> {
                    try {
                        Geo.fetchChurches(c, la, lo);
                    } catch (Exception e) {
                        new Store(c).setGeoError("Gagal ambil data gereja: " + e.getMessage());
                    }
                    Geo.registerAll(c);
                    pr.finish();
                }).start();
                return;
            }
            if (s.geoActive()) continue;
            if (id.startsWith("P:") && (tr == Geofence.GEOFENCE_TRANSITION_ENTER || tr == Geofence.GEOFENCE_TRANSITION_DWELL)) {
                long pid = Long.parseLong(id.substring(2));
                for (Place p : s.places()) {
                    if (p.id == pid) {
                        enter(c, p.name, p.lat, p.lon);
                        return;
                    }
                }
            }
            if (id.startsWith("C:") && tr == Geofence.GEOFENCE_TRANSITION_DWELL) {
                long oid = Long.parseLong(id.substring(2));
                for (double[] d : Geo.churches(s)) {
                    if ((long) d[2] == oid) {
                        enter(c, Geo.churchName(c, oid), d[0], d[1]);
                        return;
                    }
                }
            }
        }
    }

    static void enter(Context c, String name, double lat, double lon) {
        Store s = new Store(c);
        Profile p = s.profile(s.geoInProfile());
        if (p == null) return;
        s.clearTemp();
        s.setGeoActive(true, name, lat, lon);
        Engine.applyAudio(c, p);
        s.setActive(p.id);
        Geo.registerAll(c);   // tambahkan zona keluar 500 m
        Engine.refresh(c);
    }

    static void leave(Context c) {
        Store s = new Store(c);
        if (!s.geoActive()) return;
        s.setGeoActive(false, "", 0, 0);
        Profile p = s.profile(s.geoOutProfile());
        if (p != null) {
            Engine.applyAudio(c, p);
            s.setActive(p.id);
        }
        Engine.markSeen(c);
        Geo.registerAll(c);
        Engine.refresh(c);
    }
}
