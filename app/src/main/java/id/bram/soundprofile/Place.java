package id.bram.soundprofile;

import org.json.JSONException;
import org.json.JSONObject;

/** Lokasi tersimpan (mis. gereja tertentu) yang memicu profil saat dimasuki. */
public class Place {
    public long id;
    public String name = "Gereja";
    public double lat, lon;
    public int radius = 100;     // meter
    public boolean enabled = true;

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("lat", lat);
        o.put("lon", lon);
        o.put("radius", radius);
        o.put("on", enabled);
        return o;
    }

    static Place fromJson(JSONObject o) {
        Place p = new Place();
        p.id = o.optLong("id");
        p.name = o.optString("name", "Gereja");
        p.lat = o.optDouble("lat");
        p.lon = o.optDouble("lon");
        p.radius = o.optInt("radius", 100);
        p.enabled = o.optBoolean("on", true);
        return p;
    }

    String coords() {
        return String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon);
    }
}
