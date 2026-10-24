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
        if (!sp.getBoolean("init", false)) seed();
    }

    private void seed() {
        List<Profile> ps = new ArrayList<>();
        ps.add(Profile.make(1, "Normal", Profile.NORMAL, 80, 80, 60, 80, 50));
        ps.add(Profile.make(2, "Kerja", Profile.VIBRATE, 60, 60, 30, 80, -1));
        ps.add(Profile.make(3, "Rapat", Profile.SILENT, 60, 60, 0, 80, -1));
        ps.add(Profile.make(4, "Malam", Profile.SILENT, 60, 60, 20, 90, -1));
        saveProfiles(ps);

        List<Entry> es = new ArrayList<>();
        es.add(Entry.make(11, 6, 0, Entry.WEEKDAYS, 1));
        es.add(Entry.make(12, 7, 30, Entry.WEEKDAYS, 2));
        es.add(Entry.make(13, 17, 0, Entry.WEEKDAYS, 1));
        es.add(Entry.make(14, 7, 0, Entry.WEEKEND, 1));
        es.add(Entry.make(15, 22, 0, Entry.EVERYDAY, 4));
        saveEntries(es);

        sp.edit()
                .putBoolean("init", true)
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
