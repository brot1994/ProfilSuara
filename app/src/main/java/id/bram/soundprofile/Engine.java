package id.bram.soundprofile;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.service.quicksettings.TileService;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Inti aplikasi: menerapkan profil, menghitung jadwal, alarm, notifikasi. */
public class Engine {
    static final String ACTION_TICK = "id.bram.soundprofile.TICK";
    static final String ACTION_APPLY = "id.bram.soundprofile.APPLY";
    static final String CHANNEL = "status";
    static final int NOTIF_ID = 1;
    static final String[] DAYS = {"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};

    static class Occ {
        long time;
        Entry entry;
    }

    // ---------- perhitungan jadwal ----------
    static int dayBit(Calendar k) {
        return (k.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Senin=0 .. Minggu=6
    }

    private static Calendar at(long base, int dayOffset, Entry e) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(base);
        k.add(Calendar.DAY_OF_YEAR, dayOffset);
        k.set(Calendar.HOUR_OF_DAY, e.hour);
        k.set(Calendar.MINUTE, e.minute);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        return k;
    }

    /** Kejadian jadwal terakhir yang waktunya <= now. */
    static Occ mostRecent(List<Entry> es, long now) {
        for (int d = 0; d <= 7; d++) {
            Occ best = null;
            for (Entry e : es) {
                if (!e.enabled || e.days == 0) continue;
                Calendar k = at(now, -d, e);
                if ((e.days & (1 << dayBit(k))) == 0) continue;
                long t = k.getTimeInMillis();
                if (t > now) continue;
                if (best == null || t >= best.time) {
                    best = new Occ();
                    best.time = t;
                    best.entry = e;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Kejadian jadwal berikutnya yang waktunya > now. */
    static Occ next(List<Entry> es, long now) {
        for (int d = 0; d <= 7; d++) {
            Occ best = null;
            for (Entry e : es) {
                if (!e.enabled || e.days == 0) continue;
                Calendar k = at(now, d, e);
                if ((e.days & (1 << dayBit(k))) == 0) continue;
                long t = k.getTimeInMillis();
                if (t <= now) continue;
                if (best == null || t < best.time) {
                    best = new Occ();
                    best.time = t;
                    best.entry = e;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    // ---------- menerapkan suara ----------
    static void applyAudio(Context c, Profile p) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean policy = nm.isNotificationPolicyAccessGranted();

        // 1) Matikan dulu Jangan Ganggu supaya volume & mode dering bisa diatur
        if (policy) {
            try {
                if (nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL)
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            } catch (Exception ignored) {
            }
        }

        // 2) Volume & mode dering, getaran, nada
        applyRingerAndVolumes(am, p);
        applySystemSettings(c, p);

        // 3) Mode Jangan Ganggu global (terakhir, agar tidak tertimpa mode dering)
        if (policy && p.dnd != Profile.DND_OFF) {
            try {
                int filter;
                if (p.dnd == Profile.DND_PRIORITY) {
                    int cat = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
                    if (Build.VERSION.SDK_INT >= 28) cat |= NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
                    int senders = NotificationManager.Policy.PRIORITY_SENDERS_ANY;
                    if (p.callers != Profile.CALL_NONE) {
                        cat |= NotificationManager.Policy.PRIORITY_CATEGORY_CALLS;
                        if (p.callers == Profile.CALL_CONTACTS)
                            senders = NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS;
                        else if (p.callers == Profile.CALL_STARRED)
                            senders = NotificationManager.Policy.PRIORITY_SENDERS_STARRED;
                    }
                    if (p.repeat) cat |= NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS;
                    nm.setNotificationPolicy(new NotificationManager.Policy(cat, senders, senders));
                    filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
                } else if (p.dnd == Profile.DND_ALARMS) {
                    filter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
                } else {
                    filter = NotificationManager.INTERRUPTION_FILTER_NONE;
                }
                nm.setInterruptionFilter(filter);
            } catch (Exception ignored) {
            }
        }
    }

    /** Getaran & nada dering — butuh izin "Ubah pengaturan sistem". */
    static void applySystemSettings(Context c, Profile p) {
        if (!p.needsWriteSettings() || !Settings.System.canWrite(c)) return;
        android.content.ContentResolver cr = c.getContentResolver();
        if (p.vibRing >= 0) {
            try {
                Settings.System.putInt(cr, Settings.System.VIBRATE_WHEN_RINGING, p.vibRing);
            } catch (Exception ignored) {
            }
        }
        if (p.haptic >= 0) {
            try {
                Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, p.haptic);
            } catch (Exception ignored) {
            }
        }
        setTone(c, RingtoneManager.TYPE_RINGTONE, p.ringtone);
        setTone(c, RingtoneManager.TYPE_NOTIFICATION, p.notifTone);
    }

    private static void setTone(Context c, int type, String v) {
        if (v == null || v.isEmpty()) return;
        try {
            RingtoneManager.setActualDefaultRingtoneUri(c, type, "silent".equals(v) ? null : Uri.parse(v));
        } catch (Exception ignored) {
        }
    }

    private static void applyRingerAndVolumes(AudioManager am, Profile p) {
        setVol(am, AudioManager.STREAM_MUSIC, p.media);
        setVol(am, AudioManager.STREAM_ALARM, p.alarm);

        if (p.mode == Profile.NORMAL) {
            setRinger(am, AudioManager.RINGER_MODE_NORMAL);
            setVol(am, AudioManager.STREAM_RING, p.ring);
            setVol(am, AudioManager.STREAM_NOTIFICATION, p.notif);
            setVol(am, AudioManager.STREAM_SYSTEM, p.system);
        } else if (p.mode == Profile.VIBRATE) {
            setRinger(am, AudioManager.RINGER_MODE_VIBRATE);
        } else {
            if (!setRinger(am, AudioManager.RINGER_MODE_SILENT)) {
                setRinger(am, AudioManager.RINGER_MODE_VIBRATE); // tanpa izin: minimal getar
            }
        }
    }

    private static boolean setRinger(AudioManager am, int mode) {
        try {
            am.setRingerMode(mode);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void setVol(AudioManager am, int stream, int pct) {
        if (pct < 0) return;
        try {
            int max = am.getStreamMaxVolume(stream);
            int min = Build.VERSION.SDK_INT >= 28 ? am.getStreamMinVolume(stream) : 0;
            int v = Math.round(pct * max / 100f);
            if (v < min) v = min;
            am.setStreamVolume(stream, v, 0);
        } catch (Exception ignored) {
        }
    }

    // ---------- aksi ----------
    /** Aktifkan profil secara manual. minutes > 0 = sementara. */
    static void activate(Context c, long id, int minutes) {
        Store s = new Store(c);
        Profile p = s.profile(id);
        if (p == null) return;
        long now = System.currentTimeMillis();
        if (minutes > 0) {
            if (s.tempUntil() > now) s.extendTemp(now + minutes * 60000L);
            else s.startTemp(now, now + minutes * 60000L, s.activeId());
        } else {
            s.clearTemp();
        }
        applyAudio(c, p);
        s.setActive(id);
        refresh(c);
    }

    /** Aktifkan profil sampai jam tertentu, lalu otomatis pindah ke profil nextId. */
    static void activateUntil(Context c, long id, long until, long nextId) {
        Store s = new Store(c);
        Profile p = s.profile(id);
        if (p == null) return;
        s.startTemp(System.currentTimeMillis(), until, nextId);
        applyAudio(c, p);
        s.setActive(id);
        refresh(c);
    }

    /** Profil yang menurut jadwal berlaku pada waktu t (null bila tidak ada jadwal). */
    static Profile scheduledAt(Store s, long t) {
        Occ o = mostRecent(s.schedEntries(), t);
        return o == null ? null : s.profile(o.entry.profileId);
    }

    /** Batalkan mode sementara dan terapkan profil sesuai jadwal saat ini. */
    static void followSchedule(Context c) {
        Store s = new Store(c);
        s.clearTemp();
        Occ o = mostRecent(s.schedEntries(), System.currentTimeMillis());
        if (o != null) {
            Profile p = s.profile(o.entry.profileId);
            if (p != null) {
                applyAudio(c, p);
                s.setActive(p.id);
            }
            s.setLastSched(o.time);
        }
        refresh(c);
    }

    /** Setelah jadwal diedit: anggap jadwal yang sudah lewat sudah "dilihat". */
    static void markSeen(Context c) {
        Store s = new Store(c);
        Occ o = mostRecent(s.schedEntries(), System.currentTimeMillis());
        if (o != null && o.time > s.lastSched()) s.setLastSched(o.time);
    }

    /** Dipanggil oleh alarm, saat boot, atau saat jam diubah. */
    static void tick(Context c) {
        Store s = new Store(c);
        long now = System.currentTimeMillis();
        Occ o = mostRecent(s.schedEntries(), now);
        long tempUntil = s.tempUntil();

        if (tempUntil > 0) {
            if (now >= tempUntil - 2000) {
                // Mode sementara selesai
                long start = s.tempStart();
                long ret = s.tempReturn();
                s.clearTemp();
                Profile target = s.profile(ret); // profil pilihan pengguna di jendela sementara
                if (target == null && o != null && o.time >= start) target = s.profile(o.entry.profileId);
                if (target == null && o != null) target = s.profile(o.entry.profileId);
                if (target != null) {
                    applyAudio(c, target);
                    s.setActive(target.id);
                }
                if (o != null && o.time > s.lastSched()) s.setLastSched(o.time);
            } else if (o != null && o.time > s.lastSched()) {
                s.setLastSched(o.time); // jadwal lewat saat mode sementara: ditunda
            }
        } else if (o != null && o.time > s.lastSched()) {
            Profile p = s.profile(o.entry.profileId);
            if (p != null) {
                applyAudio(c, p);
                s.setActive(p.id);
            }
            s.setLastSched(o.time);
        }
        refresh(c);
    }

    static void refresh(Context c) {
        Store s = new Store(c);
        scheduleNext(c, s);
        showNotification(c, s);
        ProfileWidget.updateAll(c);
        try {
            TileService.requestListeningState(c, new ComponentName(c, ProfileTile.class));
        } catch (Exception ignored) {
        }
    }

    static void scheduleNext(Context c, Store s) {
        long now = System.currentTimeMillis();
        long t = Long.MAX_VALUE;
        Occ n = next(s.schedEntries(), now);
        if (n != null) t = n.time;
        long tu = s.tempUntil();
        if (tu > 0) t = Math.min(t, Math.max(tu, now + 1000));

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (t == Long.MAX_VALUE) return;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    // ---------- teks ----------
    static String hm(int h, int m) {
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    static String fmtTime(long t) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(t);
        return hm(k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE));
    }

    static String whenLabel(long t, long now) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(now);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(t);
        int diff = (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 400
                + b.get(Calendar.DAY_OF_YEAR) - a.get(Calendar.DAY_OF_YEAR);
        String time = fmtTime(t);
        if (diff == 0) return time;
        if (diff == 1) return "besok " + time;
        return DAYS[dayBit(b)] + " " + time;
    }

    static String daysLabel(int mask) {
        if (mask == Entry.EVERYDAY) return "Setiap hari";
        if (mask == Entry.WEEKDAYS) return "Sen–Jum";
        if (mask == Entry.WEEKEND) return "Sab–Min";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (b.length() > 0) b.append(", ");
                b.append(DAYS[i]);
            }
        }
        return b.length() == 0 ? "Tidak ada hari" : b.toString();
    }

    static String statusLine(Context c, Store s) {
        long now = System.currentTimeMillis();
        long tu = s.tempUntil();
        if (tu > now) return "Sementara sampai " + fmtTime(tu) + ", lalu kembali otomatis";
        Occ n = next(s.schedEntries(), now);
        if (n == null) return "Tidak ada jadwal aktif";
        Profile p = s.profile(n.entry.profileId);
        return "Berikutnya " + whenLabel(n.time, now) + " → " + (p == null ? "?" : p.name);
    }

    // ---------- notifikasi ----------
    static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Status profil",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setDescription("Menampilkan profil aktif dan tombol cepat");
            nm.createNotificationChannel(ch);
        }
    }

    static PendingIntent applyIntent(Context c, long id, int base) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_APPLY).putExtra("id", id);
        return PendingIntent.getBroadcast(c, base + (int) id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void showNotification(Context c, Store s) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!s.showNotif()) {
            nm.cancel(NOTIF_ID);
            return;
        }
        ensureChannel(c);
        Profile act = s.profile(s.activeId());
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(act == null ? R.drawable.ic_stat : act.iconRes())
                .setContentTitle("Profil: " + (act == null ? "—" : act.name))
                .setContentText(statusLine(c, s))
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setColor(0xFF00979D)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(pOpen);

        int n = 0;
        for (Profile p : s.profiles()) {
            if (n >= 3) break;
            String label = (p.id == s.activeId() ? "● " : "") + p.name;
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(c, p.iconRes()), label,
                    applyIntent(c, p.id, 1000)).build());
            n++;
        }
        try {
            nm.notify(NOTIF_ID, b.build());
        } catch (SecurityException ignored) {
        }
    }
}
