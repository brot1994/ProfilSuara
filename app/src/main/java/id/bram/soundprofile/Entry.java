package id.bram.soundprofile;

import org.json.JSONException;
import org.json.JSONObject;

/** Satu baris jadwal: jam:menit, hari (bit0=Senin .. bit6=Minggu), profil. */
public class Entry {
    public static final int WEEKDAYS = 0b0011111, WEEKEND = 0b1100000, EVERYDAY = 0b1111111;

    public long id;
    public int hour, minute;
    public int days = WEEKDAYS;
    public long profileId;
    public boolean enabled = true;

    static Entry make(long id, int h, int m, int days, long profileId) {
        Entry e = new Entry();
        e.id = id;
        e.hour = h;
        e.minute = m;
        e.days = days;
        e.profileId = profileId;
        return e;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("h", hour);
        o.put("m", minute);
        o.put("days", days);
        o.put("p", profileId);
        o.put("on", enabled);
        return o;
    }

    static Entry fromJson(JSONObject o) {
        Entry e = new Entry();
        e.id = o.optLong("id");
        e.hour = o.optInt("h");
        e.minute = o.optInt("m");
        e.days = o.optInt("days", WEEKDAYS);
        e.profileId = o.optLong("p");
        e.enabled = o.optBoolean("on", true);
        return e;
    }
}
