package id.bram.soundprofile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Penyimpanan sederhana berbasis SharedPreferences + JSON. */
public class Store {
    private final SharedPreferences sp;

    public Store(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("data", Context.MODE_PRIVATE);
        if (sp.getInt("schema", 0) < 2) seed();
        splitEntries();
        seedPlace();
    }

    /** Gereja bawaan (sekali saja, tidak ditambahkan lagi bila sudah dihapus). */
    private void seedPlace() {
        if (!sp.getBoolean("placeRenamed1", false)) {
            List<Place> l = places();
            for (Place x : l) if ("Gereja (default)".equals(x.name)) x.name = "Gereja St. Stanislaus Kostka";
            savePlaces(l);
            sp.edit().putBoolean("placeRenamed1", true).commit();
        }
        if (sp.getBoolean("placeSeeded", false)) return;
        List<Place> l = places();
        boolean exists = false;
        for (Place x : l) {
            if (Math.abs(x.lat - (-6.362605316800563)) < 0.0005 && Math.abs(x.lon - 106.92794703044946) < 0.0005) exists = true;
        }
        if (!exists) {
            Place p = new Place();
            p.id = newId();
            p.name = "Gereja St. Stanislaus Kostka";
            p.lat = -6.362605316800563;
            p.lon = 106.92794703044946;
            p.radius = 100;
            p.enabled = true;
            l.add(0, p);
            savePlaces(l);
        }
        sp.edit().putBoolean("placeSeeded", true).putBoolean("geoOn", true).commit();
    }

    /** Pecah jadwal multi-hari menjadi satu baris per hari (tampilan per hari). */
    private void splitEntries() {
        List<Entry> l = entries();
        boolean changed = false;
        List<Entry> out = new ArrayList<>();
        for (Entry e : l) {
            if (Integer.bitCount(e.days) <= 1) {
                out.add(e);
                continue;
            }
            changed = true;
            for (int d = 0; d < 7; d++) {
                if ((e.days & (1 << d)) == 0) continue;
                Entry n = Entry.make(newId(), e.hour, e.minute, 1 << d, e.profileId);
                n.enabled = e.enabled;
                out.add(n);
            }
        }
        if (changed) saveEntries(out);
    }

    // ---- lokasi (gereja) ----
    public List<Place> places() {
        List<Place> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("places", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Place.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return l;
    }

    public void savePlaces(List<Place> l) {
        JSONArray a = new JSONArray();
        try {
            for (Place p : l) a.put(p.toJson());
        } catch (JSONException ignored) {
        }
        sp.edit().putString("places", a.toString()).commit();
    }

    public boolean geoOn() { return sp.getBoolean("geoOn", false); }
    public void setGeoOn(boolean b) { sp.edit().putBoolean("geoOn", b).commit(); }
    /** Semua gereja terdekat (data OpenStreetMap). */
    public boolean geoAll() { return sp.getBoolean("geoAll", false); }
    public void setGeoAll(boolean b) { sp.edit().putBoolean("geoAll", b).commit(); }
    public int geoDwellMin() { return sp.getInt("geoDwell", 2); }
    public void setGeoDwellMin(int v) { sp.edit().putInt("geoDwell", v).commit(); }
    public int geoAllRadius() { return sp.getInt("geoAllRadius", 80); }
    public void setGeoAllRadius(int v) { sp.edit().putInt("geoAllRadius", v).commit(); }
    public int geoExitM() { return sp.getInt("geoExit", 500); }
    public void setGeoExitM(int v) { sp.edit().putInt("geoExit", v).commit(); }
    public long geoInProfile() { return sp.getLong("geoIn", findByName("Senyap", 3)); }
    public void setGeoInProfile(long id) { sp.edit().putLong("geoIn", id).commit(); }
    public long geoOutProfile() { return sp.getLong("geoOut", findByName("Pelan", 2)); }
    public void setGeoOutProfile(long id) { sp.edit().putLong("geoOut", id).commit(); }

    private long findByName(String name, long fallback) {
        for (Profile p : profiles()) if (p.name.equalsIgnoreCase(name)) return p.id;
        return fallback;
    }

    /** Sedang berada di area gereja (profil lokasi aktif). */
    public boolean geoActive() { return sp.getBoolean("geoActive", false); }
    public String geoActiveName() { return sp.getString("geoActiveName", ""); }
    public void setGeoActive(boolean on, String name, double lat, double lon) {
        sp.edit().putBoolean("geoActive", on).putString("geoActiveName", name)
                .putString("geoActiveLat", Double.toString(lat))
                .putString("geoActiveLon", Double.toString(lon)).commit();
    }
    public double geoActiveLat() { return Double.parseDouble(sp.getString("geoActiveLat", "0")); }
    public double geoActiveLon() { return Double.parseDouble(sp.getString("geoActiveLon", "0")); }

    /** Cache gereja terdekat dari OpenStreetMap. */
    public String churchCache() { return sp.getString("churches", "[]"); }
    public void setChurchCache(String json, double lat, double lon) {
        sp.edit().putString("churches", json).putLong("churchTime", System.currentTimeMillis())
                .putString("churchLat", Double.toString(lat)).putString("churchLon", Double.toString(lon)).commit();
    }
    public long churchTime() { return sp.getLong("churchTime", 0); }
    public String geoError() { return sp.getString("geoErr", ""); }
    public void setGeoError(String e) { sp.edit().putString("geoErr", e == null ? "" : e).commit(); }

    public boolean goHome() { return sp.getBoolean("goHome", true); }
    public void setGoHome(boolean b) { sp.edit().putBoolean("goHome", b).commit(); }

    public boolean schedOn() { return sp.getBoolean("schedOn", true); }
    public void setSchedOn(boolean b) { sp.edit().putBoolean("schedOn", b).commit(); }

    /** Jadwal yang dipakai mesin (kosong bila scheduler dimatikan). */
    public List<Entry> schedEntries() {
        return schedOn() ? entries() : new ArrayList<>();
    }

    private void seed() {
        List<Profile> ps = new ArrayList<>();
        ps.add(Profile.make(1, "Normal", 0, Profile.NORMAL, 80, 80, 60, 80, 50));
        ps.add(Profile.make(2, "Pelan", 1, Profile.NORMAL, 30, 30, 30, 70, 20));
        ps.add(Profile.make(3, "Senyap", 2, Profile.SILENT, 60, 60, -1, 80, -1));
        ps.add(Profile.make(4, "Malam", 3, Profile.SILENT, 60, 60, 20, 90, -1)
                .dnd(Profile.DND_PRIORITY, Profile.CALL_STARRED));
        ps.add(Profile.make(5, "Getar saja", 4, Profile.VIBRATE, 60, 60, -1, 80, -1));
        ps.add(Profile.make(6, "Hanya panggilan", 5, Profile.NORMAL, 80, 0, -1, 80, 0)
                .dnd(Profile.DND_PRIORITY, Profile.CALL_ANY));
        ps.add(Profile.make(7, "Kontak favorit saja", 6, Profile.NORMAL, 80, 0, -1, 80, 0)
                .dnd(Profile.DND_PRIORITY, Profile.CALL_STARRED));
        ps.add(Profile.make(8, "Kontak dikenal saja", 7, Profile.NORMAL, 80, 0, -1, 80, 0)
                .dnd(Profile.DND_PRIORITY, Profile.CALL_CONTACTS));
        ps.add(Profile.make(9, "Hanya alarm", 8, Profile.SILENT, 60, 60, -1, 80, -1)
                .dnd(Profile.DND_ALARMS, Profile.CALL_NONE));
        ps.add(Profile.make(10, "Senyap total", 9, Profile.SILENT, 60, 60, -1, -1, -1)
                .dnd(Profile.DND_TOTAL, Profile.CALL_NONE));
        saveProfiles(ps);

        List<Entry> es = new ArrayList<>();
        es.add(Entry.make(11, 6, 0, Entry.WEEKDAYS, 1));
        es.add(Entry.make(12, 7, 30, Entry.WEEKDAYS, 5));
        es.add(Entry.make(13, 17, 0, Entry.WEEKDAYS, 1));
        es.add(Entry.make(14, 7, 0, Entry.WEEKEND, 1));
        es.add(Entry.make(15, 22, 0, Entry.EVERYDAY, 4));
        saveEntries(es);

        sp.edit()
                .putInt("schema", 2)
                .putLong("tempUntil", 0)
                .putLong("nextId", 100)
                .putLong("active", 1)
                .putBoolean("notif", true)
                .putLong("lastSched", System.currentTimeMillis())
                .commit();
    }

    // ---- profil ----
    public List<Profile> profiles() {
        List<Profile> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("profiles", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Profile.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return l;
    }

    public void saveProfiles(List<Profile> l) {
        JSONArray a = new JSONArray();
        try {
            for (Profile p : l) a.put(p.toJson());
        } catch (JSONException ignored) {
        }
        sp.edit().putString("profiles", a.toString()).commit();
    }

    public Profile profile(long id) {
        for (Profile p : profiles()) if (p.id == id) return p;
        return null;
    }

    // ---- jadwal ----
    public List<Entry> entries() {
        List<Entry> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("entries", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Entry.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return l;
    }

    public void saveEntries(List<Entry> l) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : l) a.put(e.toJson());
        } catch (JSONException ignored) {
        }
        sp.edit().putString("entries", a.toString()).commit();
    }

    public long newId() {
        long id = sp.getLong("nextId", 100);
        sp.edit().putLong("nextId", id + 1).commit();
        return id;
    }

    // ---- status ----
    public long activeId() { return sp.getLong("active", -1); }
    public void setActive(long id) { sp.edit().putLong("active", id).commit(); }

    public long lastSched() { return sp.getLong("lastSched", 0); }
    public void setLastSched(long t) { sp.edit().putLong("lastSched", t).commit(); }

    public long tempUntil() { return sp.getLong("tempUntil", 0); }
    public long tempStart() { return sp.getLong("tempStart", 0); }
    public long tempReturn() { return sp.getLong("tempReturn", -1); }

    public void startTemp(long start, long until, long returnId) {
        sp.edit().putLong("tempStart", start).putLong("tempUntil", until).putLong("tempReturn", returnId).commit();
    }

    public void extendTemp(long until) { sp.edit().putLong("tempUntil", until).commit(); }
    public void clearTemp() { sp.edit().putLong("tempUntil", 0).commit(); }

    public boolean showNotif() { return sp.getBoolean("notif", true); }
    public void setShowNotif(boolean b) { sp.edit().putBoolean("notif", b).commit(); }
}
