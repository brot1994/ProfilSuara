package id.bram.soundprofile;

import org.json.JSONException;
import org.json.JSONObject;

/** Satu profil suara. Nilai volume dalam persen 0..100, -1 = tidak diubah. */
public class Profile {
    public static final int NORMAL = 0, VIBRATE = 1, SILENT = 2;
    /** Mode Jangan Ganggu (global sistem). */
    public static final int DND_OFF = 0, DND_PRIORITY = 1, DND_ALARMS = 2, DND_TOTAL = 3;
    /** Siapa yang boleh menelepon saat DND prioritas. */
    public static final int CALL_ANY = 0, CALL_CONTACTS = 1, CALL_STARRED = 2, CALL_NONE = 3;

    static final int[] ICONS = {
            R.drawable.ic_p0, R.drawable.ic_p1, R.drawable.ic_p2, R.drawable.ic_p3,
            R.drawable.ic_p4, R.drawable.ic_p5, R.drawable.ic_p6, R.drawable.ic_p7,
            R.drawable.ic_p8, R.drawable.ic_p9, R.drawable.ic_p10};

    public long id;
    public String name = "Profil";
    public int icon = 0;
    public int mode = NORMAL;
    public int ring = 70, notif = 70, media = 50, alarm = 80, system = 50;
    public int dnd = DND_OFF;
    public int callers = CALL_ANY;
    public boolean repeat = true;

    static Profile make(long id, String name, int icon, int mode, int ring, int notif, int media, int alarm, int system) {
        Profile p = new Profile();
        p.id = id;
        p.name = name;
        p.icon = icon;
        p.mode = mode;
        p.ring = ring;
        p.notif = notif;
        p.media = media;
        p.alarm = alarm;
        p.system = system;
        return p;
    }

    Profile dnd(int dnd, int callers) {
        this.dnd = dnd;
        this.callers = callers;
        return this;
    }

    int iconRes() {
        return ICONS[(icon >= 0 && icon < ICONS.length) ? icon : 0];
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("icon", icon);
        o.put("mode", mode);
        o.put("ring", ring);
        o.put("notif", notif);
        o.put("media", media);
        o.put("alarm", alarm);
        o.put("system", system);
        o.put("dnd", dnd);
        o.put("callers", callers);
        o.put("repeat", repeat);
        return o;
    }

    static Profile fromJson(JSONObject o) {
        Profile p = new Profile();
        p.id = o.optLong("id");
        p.name = o.optString("name", "Profil");
        p.icon = o.optInt("icon", 0);
        p.mode = o.optInt("mode", NORMAL);
        p.ring = o.optInt("ring", 70);
        p.notif = o.optInt("notif", 70);
        p.media = o.optInt("media", 50);
        p.alarm = o.optInt("alarm", 80);
        p.system = o.optInt("system", 50);
        p.dnd = o.optInt("dnd", DND_OFF);
        p.callers = o.optInt("callers", CALL_ANY);
        p.repeat = o.optBoolean("repeat", true);
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

    boolean needsPolicy() {
        return dnd != DND_OFF || mode == SILENT;
    }

    String modeLabel() {
        if (mode == VIBRATE) return "Getar";
        if (mode == SILENT) return "Senyap";
        return "Normal";
    }

    String dndLabel() {
        switch (dnd) {
            case DND_PRIORITY:
                String[] c = {"semua panggilan", "panggilan kontak", "panggilan favorit", "tanpa panggilan"};
                return "Prioritas: " + c[Math.max(0, Math.min(3, callers))];
            case DND_ALARMS:
                return "Hanya alarm";
            case DND_TOTAL:
                return "Senyap total";
            default:
                return "";
        }
    }

    String summary() {
        StringBuilder b = new StringBuilder(modeLabel());
        if (dnd != DND_OFF) b.append(" · ").append(dndLabel());
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
