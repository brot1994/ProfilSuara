package id.bram.soundprofile;

import org.json.JSONException;
import org.json.JSONObject;

/** Satu profil suara. Nilai volume dalam persen 0..100, -1 = tidak diubah. */
public class Profile {
    public static final int NORMAL = 0, VIBRATE = 1, SILENT = 2;

    public long id;
    public String name = "Profil";
    public int mode = NORMAL;
    public int ring = 70, notif = 70, media = 50, alarm = 80, system = 50;

    static Profile make(long id, String name, int mode, int ring, int notif, int media, int alarm, int system) {
        Profile p = new Profile();
        p.id = id;
        p.name = name;
        p.mode = mode;
        p.ring = ring;
        p.notif = notif;
        p.media = media;
        p.alarm = alarm;
        p.system = system;
        return p;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("mode", mode);
        o.put("ring", ring);
        o.put("notif", notif);
        o.put("media", media);
        o.put("alarm", alarm);
        o.put("system", system);
        return o;
    }

    static Profile fromJson(JSONObject o) {
        Profile p = new Profile();
        p.id = o.optLong("id");
        p.name = o.optString("name", "Profil");
        p.mode = o.optInt("mode", NORMAL);
        p.ring = o.optInt("ring", 70);
        p.notif = o.optInt("notif", 70);
        p.media = o.optInt("media", 50);
        p.alarm = o.optInt("alarm", 80);
        p.system = o.optInt("system", 50);
        return p;
    }

    int[] values() {
        return new int[]{ring, notif, media, alarm, system};
    }

    void setValues(int[] v) {
        ring = v[0];
        notif = v[1];
        media = v[2];
        alarm = v[3];
        system = v[4];
    }

    String modeLabel() {
        if (mode == VIBRATE) return "Getar";
        if (mode == SILENT) return "Senyap";
        return "Normal";
    }

    String summary() {
        StringBuilder b = new StringBuilder(modeLabel());
        if (mode == NORMAL) {
            add(b, "Dering", ring);
            add(b, "Notif", notif);
        }
        add(b, "Media", media);
        add(b, "Alarm", alarm);
        return b.toString();
    }

    private static void add(StringBuilder b, String label, int v) {
        if (v >= 0) b.append(" · ").append(label).append(' ').append(v).append('%');
    }
}
