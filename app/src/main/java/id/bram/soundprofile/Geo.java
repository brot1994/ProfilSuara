package id.bram.soundprofile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Otomatis ganti profil berdasarkan lokasi (geofence Google Play Services).
 * ID geofence: "P:<id>" gereja tersimpan, "C:<osmId>" gereja OSM, "AREA" (ambil ulang data), "EXIT" (keluar 500 m).
 */
public class Geo {
    static final String ACTION = "id.bram.soundprofile.GEO";
    static final int AREA_RADIUS = 2500;     // meter: ambil ulang daftar gereja setelah keluar area ini
    static final int FETCH_RADIUS = 4000;    // meter: radius pencarian gereja OSM
    static final int MAX_CHURCH = 80;

    static boolean hasFine(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasBackground(Context c) {
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean ready(Context c) {
        return hasFine(c) && hasBackground(c);
    }

    private static PendingIntent pi(Context c) {
        Intent i = new Intent(c, GeoReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(c, 77, i, flags);
    }

    // ---------------- pendaftaran geofence ----------------

    /** Daftarkan ulang semua geofence sesuai pengaturan. Aman dipanggil berkali-kali. */
    @SuppressLint("MissingPermission")
    static void registerAll(Context c) {
        Store s = new Store(c);
        GeofencingClient gc = LocationServices.getGeofencingClient(c);
        PendingIntent pi = pi(c);
        if (!s.geoOn() || !ready(c)) {
            gc.removeGeofences(pi);
            return;
        }
        List<Geofence> list = new ArrayList<>();
        // gereja tersimpan: langsung aktif begitu masuk
        for (Place p : s.places()) {
            if (!p.enabled) continue;
            list.add(new Geofence.Builder()
                    .setRequestId("P:" + p.id)
                    .setCircularRegion(p.lat, p.lon, Math.max(50, p.radius))
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setNotificationResponsiveness(30000)
                    .build());
        }
        // semua gereja terdekat: aktif setelah berdiam N menit
        if (s.geoAll()) {
            int dwellMs = Math.max(1, s.geoDwellMin()) * 60000;
            List<double[]> ch = churches(s);
            for (int i = 0; i < ch.size() && list.size() < 95; i++) {
                double[] d = ch.get(i);
                list.add(new Geofence.Builder()
                        .setRequestId("C:" + (long) d[2])
                        .setCircularRegion(d[0], d[1], s.geoAllRadius())
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                        .setLoiteringDelay(dwellMs)
                        .setNotificationResponsiveness(30000)
                        .build());
            }
            // area pantau: keluar = ambil ulang data gereja di lokasi baru
            double[] center = churchCenter(c);
            if (center != null) {
                list.add(new Geofence.Builder()
                        .setRequestId("AREA")
                        .setCircularRegion(center[0], center[1], AREA_RADIUS)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                        .build());
            }
        }
        // zona keluar (500 m) selama sedang di gereja
        if (s.geoActive()) {
            list.add(new Geofence.Builder()
                    .setRequestId("EXIT")
                    .setCircularRegion(s.geoActiveLat(), s.geoActiveLon(), Math.max(150, s.geoExitM()))
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setNotificationResponsiveness(30000)
                    .build());
        }

        gc.removeGeofences(pi).addOnCompleteListener(t -> {
            if (list.isEmpty()) return;
            GeofencingRequest req = new GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER | GeofencingRequest.INITIAL_TRIGGER_DWELL)
                    .addGeofences(list)
                    .build();
            try {
                gc.addGeofences(req, pi)
                        .addOnSuccessListener(v -> s.setGeoError(""))
                        .addOnFailureListener(e -> s.setGeoError(errorText(e)));
            } catch (SecurityException e) {
                s.setGeoError("Izin lokasi belum lengkap");
            }
        });
    }

    static String errorText(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (m.contains("1000")) return "Lokasi HP mati atau akurasi Google dimatikan";
        if (m.contains("1001")) return "Terlalu banyak titik lokasi";
        return m;
    }

    private static double[] churchCenter(Context c) {
        Store s = new Store(c);
        if (s.churchTime() == 0) return null;
        android.content.SharedPreferences sp = c.getSharedPreferences("data", Context.MODE_PRIVATE);
        try {
            return new double[]{Double.parseDouble(sp.getString("churchLat", "0")),
                    Double.parseDouble(sp.getString("churchLon", "0"))};
        } catch (Exception e) {
            return null;
        }
    }

    /** Daftar gereja dari cache: {lat, lon, id}, urut terdekat dari titik ambil. */
    static List<double[]> churches(Store s) {
        List<double[]> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(s.churchCache());
            for (int i = 0; i < a.length(); i++) {
                JSONArray x = a.getJSONArray(i);
                l.add(new double[]{x.getDouble(0), x.getDouble(1), x.getDouble(2)});
            }
        } catch (Exception ignored) {
        }
        return l;
    }

    static String churchName(Context c, long osmId) {
        try {
            JSONArray a = new JSONArray(new Store(c).churchCache());
            for (int i = 0; i < a.length(); i++) {
                JSONArray x = a.getJSONArray(i);
                if ((long) x.getDouble(2) == osmId) return x.optString(3, "Gereja");
            }
        } catch (Exception ignored) {
        }
        return "Gereja";
    }

    // ---------------- data gereja OpenStreetMap ----------------

    /** Ambil gereja (Kristen/Katolik) di sekitar titik dari Overpass API. Jalankan di thread latar. */
    static int fetchChurches(Context c, double lat, double lon) throws Exception {
        String q = String.format(Locale.US,
                "[out:json][timeout:25];(nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"christian\"](around:%d,%.6f,%.6f););out center tags;",
                FETCH_RADIUS, lat, lon);
        String[] servers = {"https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"};
        String body = null;
        Exception last = null;
        for (String srv : servers) {
            try {
                body = post(srv, "data=" + URLEncoder.encode(q, "UTF-8"));
                break;
            } catch (Exception e) {
                last = e;
            }
        }
        if (body == null) throw last != null ? last : new Exception("Gagal mengambil data");

        JSONArray els = new JSONObject(body).optJSONArray("elements");
        List<Object[]> found = new ArrayList<>();
        if (els != null) {
            for (int i = 0; i < els.length(); i++) {
                JSONObject e = els.getJSONObject(i);
                double la, lo;
                if (e.has("lat")) {
                    la = e.getDouble("lat");
                    lo = e.getDouble("lon");
                } else if (e.has("center")) {
                    la = e.getJSONObject("center").getDouble("lat");
                    lo = e.getJSONObject("center").getDouble("lon");
                } else continue;
                JSONObject tags = e.optJSONObject("tags");
                String name = tags == null ? "" : tags.optString("name", "");
                if (name.isEmpty()) name = "Gereja";
                float[] d = new float[1];
                Location.distanceBetween(lat, lon, la, lo, d);
                found.add(new Object[]{d[0], la, lo, (double) e.getLong("id"), name});
            }
        }
        Collections.sort(found, (a, b) -> Float.compare((Float) a[0], (Float) b[0]));
        JSONArray out = new JSONArray();
        for (int i = 0; i < found.size() && i < MAX_CHURCH; i++) {
            Object[] f = found.get(i);
            JSONArray x = new JSONArray();
            x.put((double) f[1]);
            x.put((double) f[2]);
            x.put((double) f[3]);
            x.put((String) f[4]);
            out.put(x);
        }
        new Store(c).setChurchCache(out.toString(), lat, lon);
        return out.length();
    }

    private static String post(String url, String data) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        h.setRequestMethod("POST");
        h.setDoOutput(true);
        h.setRequestProperty("User-Agent", "ProfilSuara/1.6 (Android)");
        h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream os = h.getOutputStream()) {
            os.write(data.getBytes("UTF-8"));
        }
        if (h.getResponseCode() != 200) throw new Exception("Server peta: HTTP " + h.getResponseCode());
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    /** Lokasi saat ini (blocking, maks ~20 detik). Jalankan di thread latar. */
    @SuppressLint("MissingPermission")
    static Location currentLocation(Context c) throws Exception {
        if (!hasFine(c)) throw new Exception("Izin lokasi belum diberikan");
        CurrentLocationRequest req = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(60000)
                .setDurationMillis(20000)
                .build();
        Location l = com.google.android.gms.tasks.Tasks.await(
                LocationServices.getFusedLocationProviderClient(c).getCurrentLocation(req, null),
                25, java.util.concurrent.TimeUnit.SECONDS);
        if (l == null) throw new Exception("Lokasi tidak didapat. Pastikan GPS/Lokasi menyala.");
        return l;
    }

    // ---------------- membaca link Google Maps ----------------

    private static final Pattern[] COORD = {
            Pattern.compile("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)"),
            Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)"),
            Pattern.compile("[?&](?:q|query|ll|destination|center)=(-?\\d+\\.\\d+)(?:,|%2C)\\s*(-?\\d+\\.\\d+)"),
            Pattern.compile("geo:(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)"),
            Pattern.compile("^\\s*(-?\\d{1,2}\\.\\d+)\\s*,\\s*(-?\\d{1,3}\\.\\d+)\\s*$"),
    };

    /** Hasil: {lat, lon} atau null. Bisa berupa teks bagikan Google Maps, link pendek, atau "lat, lon". */
    static double[] parseCoords(String text) {
        if (text == null) return null;
        for (Pattern p : COORD) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                try {
                    double la = Double.parseDouble(m.group(1)), lo = Double.parseDouble(m.group(2));
                    if (Math.abs(la) <= 90 && Math.abs(lo) <= 180) return new double[]{la, lo};
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    static String findUrl(String text) {
        Matcher m = Pattern.compile("https?://\\S+").matcher(text == null ? "" : text);
        return m.find() ? m.group() : null;
    }

    /** Ikuti redirect link pendek (maps.app.goo.gl) lalu cari koordinat. Jalankan di thread latar. */
    static double[] resolveLink(String text) throws Exception {
        double[] d = parseCoords(text);
        if (d != null) return d;
        String url = findUrl(text);
        if (url == null) return null;
        for (int i = 0; i < 6 && url != null; i++) {
            d = parseCoords(url);
            if (d != null) return d;
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(15000);
            h.setReadTimeout(15000);
            h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ProfilSuara");
            int code = h.getResponseCode();
            String loc = h.getHeaderField("Location");
            if (code >= 300 && code < 400 && loc != null) {
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            // halaman akhir: cari koordinat di isi halaman
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null && sb.length() < 400000) sb.append(line);
            } catch (Exception ignored) {
            }
            d = parseCoords(sb.toString());
            if (d == null) {
                Matcher m = Pattern.compile("\\[null,null,(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)\\]").matcher(sb);
                if (m.find()) d = new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
            }
            return d;
        }
        return null;
    }

    /** Nama tempat dari teks bagikan Google Maps (baris pertama sebelum link). */
    static String guessName(String text) {
        if (text == null) return "";
        for (String line : text.split("\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("http")) continue;
            if (parseCoords(line) != null) continue;
            return line.length() > 60 ? line.substring(0, 60) : line;
        }
        return "";
    }
}
