package id.bram.soundprofile;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    static final int BG = 0xFF121212, CARD = 0xFF1E1E1E, TXT = 0xFFEEEEEE, SUB = 0xFF9E9E9E,
            ACC = 0xFFFFB300, WARN = 0xFFEF5350, CHIP_OFF = 0xFF3A3A3A;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private LinearLayout root;
    private Store s;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new Store(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        sv.addView(root);
        setContentView(sv);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        Engine.ensureChannel(this);
        Engine.tick(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    // ================= UI utama =================
    private void render() {
        root.removeAllViews();
        long now = System.currentTimeMillis();
        Profile act = s.profile(s.activeId());

        // --- status ---
        LinearLayout st = card();
        st.addView(text("PROFIL AKTIF", 12, SUB, true));
        st.addView(text(act == null ? "—" : act.name, 32, ACC, true));
        if (act != null) st.addView(text(act.summary(), 13, SUB, false));
        TextView line = text(Engine.statusLine(this, s), 14, TXT, false);
        line.setPadding(0, dp(8), 0, dp(4));
        st.addView(line);
        LinearLayout r = hrow();
        r.addView(primaryBtn("Aktifkan sementara", v -> tempDialog(-1)), weight());
        Button follow = btn(s.tempUntil() > now ? "Batalkan sementara" : "Ikuti jadwal sekarang", v -> {
            Engine.followSchedule(this);
            toast("Profil disesuaikan dengan jadwal");
            render();
        });
        r.addView(follow, weight());
        st.addView(r);
        root.addView(st);

        // --- izin ---
        permissionCard();

        // --- profil ---
        header("Profil", v -> editProfile(null));
        for (final Profile p : s.profiles()) {
            LinearLayout c = card();
            boolean on = act != null && p.id == act.id;
            c.addView(text((on ? "● " : "") + p.name, 18, on ? ACC : TXT, true));
            c.addView(text(p.summary(), 13, SUB, false));
            c.setOnClickListener(v -> profileMenu(p));
            root.addView(c);
        }

        // --- jadwal ---
        header("Jadwal", v -> editEntry(null));
        List<Entry> es = s.entries();
        Collections.sort(es, (a, b) -> (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
        if (es.isEmpty()) root.addView(text("Belum ada jadwal. Ketuk + Tambah.", 14, SUB, false));
        for (final Entry e : es) {
            LinearLayout c = card();
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            Profile p = s.profile(e.profileId);
            TextView t = text(Engine.hm(e.hour, e.minute) + "  →  " + (p == null ? "?" : p.name), 20, TXT, true);
            left.addView(t);
            left.addView(text(Engine.daysLabel(e.days), 13, SUB, false));
            c.addView(left, weight());
            Switch sw = new Switch(this);
            sw.setChecked(e.enabled);
            sw.setOnCheckedChangeListener((bt, checked) -> {
                List<Entry> l = s.entries();
                for (Entry x : l) if (x.id == e.id) x.enabled = checked;
                s.saveEntries(l);
                Engine.markSeen(this);
                Engine.refresh(this);
                root.post(this::render);
            });
            c.addView(sw);
            c.setAlpha(e.enabled ? 1f : 0.5f);
            c.setOnClickListener(v -> editEntry(e));
            root.addView(c);
        }

        // --- pengaturan ---
        sectionTitle("Pengaturan");
        LinearLayout set = card();
        Switch ns = new Switch(this);
        ns.setText("Notifikasi status + tombol cepat");
        ns.setTextColor(TXT);
        ns.setChecked(s.showNotif());
        ns.setOnCheckedChangeListener((bt, checked) -> {
            s.setShowNotif(checked);
            Engine.refresh(this);
        });
        set.addView(ns);
        TextView tip = text("Tips: tambahkan tombol “Profil Suara” di panel Quick Settings "
                + "(tarik panel ke bawah → ikon pensil/+), dan widget “Profil Suara” di layar utama "
                + "(tekan lama layar → Widget).", 13, SUB, false);
        tip.setPadding(0, dp(10), 0, 0);
        set.addView(tip);
        root.addView(set);
    }

    private void permissionCard() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        boolean policy = nm.isNotificationPolicyAccessGranted();
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean batt = pm.isIgnoringBatteryOptimizations(getPackageName());
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (policy && notif && batt && exact) return;

        LinearLayout c = card();
        c.addView(text("Izin yang perlu diberikan", 16, WARN, true));
        if (!policy) permItem(c, "Akses Jangan Ganggu",
                "Wajib agar mode Senyap bisa diatur. Cari “Profil Suara” lalu izinkan.",
                v -> open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        if (!notif) permItem(c, "Notifikasi",
                "Untuk menampilkan profil aktif dan tombol cepat.",
                v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
        if (!batt) permItem(c, "Tanpa batasan baterai",
                "Supaya jadwal tetap jalan saat HP tidur. Di Samsung, pastikan juga aplikasi ini tidak masuk daftar “Aplikasi tidur”.",
                v -> open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))));
        if (!exact) permItem(c, "Alarm tepat waktu",
                "Supaya profil berganti tepat di menit yang dijadwalkan.",
                v -> open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()))));
        root.addView(c);
    }

    private void permItem(LinearLayout c, String title, String desc, View.OnClickListener l) {
        TextView t = text(title, 15, TXT, true);
        t.setPadding(0, dp(10), 0, 0);
        c.addView(t);
        c.addView(text(desc, 13, SUB, false));
        c.addView(primaryBtn("Buka pengaturan", l));
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        Engine.refresh(this);
        render();
    }

    // ================= profil =================
    private void profileMenu(final Profile p) {
        String[] items = {"Aktifkan sekarang", "Aktifkan sementara…", "Edit", "Hapus"};
        new AlertDialog.Builder(this).setTitle(p.name).setItems(items, (d, w) -> {
            if (w == 0) {
                Engine.activate(this, p.id, 0);
                toast(p.name + " aktif");
                render();
            } else if (w == 1) {
                durationDialog(p);
            } else if (w == 2) {
                editProfile(p);
            } else {
                deleteProfile(p);
            }
        }).show();
    }

    private void deleteProfile(final Profile p) {
        final List<Profile> ps = s.profiles();
        if (ps.size() <= 1) {
            toast("Minimal harus ada satu profil");
            return;
        }
        int used = 0;
        for (Entry e : s.entries()) if (e.profileId == p.id) used++;
        String msg = used > 0
                ? "Profil ini dipakai di " + used + " jadwal. Jadwal tersebut juga akan dihapus."
                : "Hapus profil ini?";
        new AlertDialog.Builder(this).setTitle("Hapus " + p.name).setMessage(msg)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (d, w) -> {
                    List<Profile> l = s.profiles();
                    for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == p.id) l.remove(i);
                    s.saveProfiles(l);
                    List<Entry> el = s.entries();
                    for (int i = el.size() - 1; i >= 0; i--) if (el.get(i).profileId == p.id) el.remove(i);
                    s.saveEntries(el);
                    if (s.activeId() == p.id) s.setActive(l.get(0).id);
                    Engine.refresh(this);
                    render();
                }).show();
    }

    private void editProfile(final Profile src) {
        final boolean isNew = src == null;
        final Profile p = isNew ? Profile.make(0, "", Profile.NORMAL, 70, 70, 50, 80, 50) : src;

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20), dp(8), dp(20), dp(8));

        final EditText name = new EditText(this);
        name.setHint("Nama profil (mis. Kerja)");
        name.setText(p.name);
        name.setSingleLine();
        v.addView(name);

        v.addView(label("Mode dering"));
        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        String[] modes = {"Normal", "Getar", "Senyap"};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(modes[i]);
            rg.addView(rb);
        }
        rg.check(1000 + p.mode);
        v.addView(rg);

        final TextView note = new TextView(this);
        note.setText("Dering, notifikasi & sistem hanya dipakai saat mode Normal.");
        note.setTextSize(12);
        v.addView(note);

        v.addView(label("Volume (centang = ikut diatur)"));
        String[] labels = {"Nada dering", "Notifikasi", "Media (musik/video)", "Alarm", "Sistem"};
        final int[] vals = p.values();
        final CheckBox[] cbs = new CheckBox[5];
        final SeekBar[] bars = new SeekBar[5];
        final TextView[] tvs = new TextView[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox cb = new CheckBox(this);
            cb.setText(labels[i]);
            cb.setChecked(vals[i] >= 0);
            row.addView(cb, weight());
            TextView tv = new TextView(this);
            tv.setText(vals[i] >= 0 ? vals[i] + "%" : "tidak diubah");
            row.addView(tv);
            SeekBar sb = new SeekBar(this);
            sb.setMax(100);
            sb.setProgress(vals[i] >= 0 ? vals[i] : 50);
            sb.setEnabled(vals[i] >= 0);
            sb.setPadding(dp(16), dp(4), dp(16), dp(12));
            cbs[i] = cb;
            bars[i] = sb;
            tvs[i] = tv;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    tvs[idx].setText(progress + "%");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            cb.setOnCheckedChangeListener((bt, checked) -> {
                bars[idx].setEnabled(checked && bt.isEnabled());
                tvs[idx].setText(checked ? bars[idx].getProgress() + "%" : "tidak diubah");
            });
            v.addView(row);
            v.addView(sb);
        }

        final Runnable upd = () -> {
            boolean normal = rg.getCheckedRadioButtonId() == 1000;
            int[] ringStreams = {0, 1, 4};
            for (int idx : ringStreams) {
                cbs[idx].setEnabled(normal);
                bars[idx].setEnabled(normal && cbs[idx].isChecked());
            }
            note.setVisibility(normal ? View.GONE : View.VISIBLE);
        };
        rg.setOnCheckedChangeListener((g, id) -> upd.run());
        upd.run();

        ScrollView sv = new ScrollView(this);
        sv.addView(v);

        new AlertDialog.Builder(this)
                .setTitle(isNew ? "Profil baru" : "Edit profil")
                .setView(sv)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    String n = name.getText().toString().trim();
                    p.name = n.isEmpty() ? "Profil" : n;
                    int m = rg.getCheckedRadioButtonId() - 1000;
                    p.mode = (m < 0 || m > 2) ? Profile.NORMAL : m;
                    int[] nv = new int[5];
                    for (int i = 0; i < 5; i++) nv[i] = cbs[i].isChecked() ? bars[i].getProgress() : -1;
                    p.setValues(nv);
                    List<Profile> l = s.profiles();
                    if (isNew) {
                        p.id = s.newId();
                        l.add(p);
                    } else {
                        for (int i = 0; i < l.size(); i++) if (l.get(i).id == p.id) l.set(i, p);
                    }
                    s.saveProfiles(l);
                    if (p.id == s.activeId()) Engine.applyAudio(this, p);
                    Engine.refresh(this);
                    render();
                }).show();
    }

    // ================= sementara =================
    private void tempDialog(long presetId) {
        final List<Profile> ps = s.profiles();
        if (ps.isEmpty()) return;
        String[] names = new String[ps.size()];
        for (int i = 0; i < ps.size(); i++) names[i] = ps.get(i).name;
        new AlertDialog.Builder(this).setTitle("Profil sementara")
                .setItems(names, (d, w) -> durationDialog(ps.get(w))).show();
    }

    private void durationDialog(final Profile p) {
        final String[] labels = {"15 menit", "30 menit", "45 menit", "1 jam", "1,5 jam", "2 jam", "3 jam", "Lainnya…"};
        final int[] mins = {15, 30, 45, 60, 90, 120, 180};
        new AlertDialog.Builder(this).setTitle(p.name + " selama…").setItems(labels, (d, w) -> {
            if (w < mins.length) {
                startTemp(p, mins[w]);
            } else {
                final EditText et = new EditText(this);
                et.setInputType(InputType.TYPE_CLASS_NUMBER);
                et.setHint("Menit");
                LinearLayout box = new LinearLayout(this);
                box.setPadding(dp(20), dp(8), dp(20), 0);
                box.addView(et, new LinearLayout.LayoutParams(MATCH, WRAP));
                new AlertDialog.Builder(this).setTitle("Berapa menit?").setView(box)
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("OK", (d2, w2) -> {
                            int m = 0;
                            try {
                                m = Integer.parseInt(et.getText().toString().trim());
                            } catch (Exception ignored) {
                            }
                            if (m > 0 && m <= 24 * 60) startTemp(p, m);
                            else toast("Isi 1 sampai 1440 menit");
                        }).show();
            }
        }).show();
    }

    private void startTemp(Profile p, int minutes) {
        Engine.activate(this, p.id, minutes);
        toast(p.name + " aktif sampai " + Engine.fmtTime(System.currentTimeMillis() + minutes * 60000L));
        render();
    }

    // ================= jadwal =================
    private void editEntry(final Entry src) {
        final List<Profile> ps = s.profiles();
        if (ps.isEmpty()) {
            toast("Buat profil dulu");
            return;
        }
        final boolean isNew = src == null;
        final Entry e = isNew ? Entry.make(0, 7, 0, Entry.WEEKDAYS, ps.get(0).id) : src;
        final int[] hm = {e.hour, e.minute};
        final int[] days = {e.days};

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20), dp(8), dp(20), dp(8));

        v.addView(label("Jam"));
        final Button tb = new Button(this);
        tb.setTextSize(28);
        tb.setText(Engine.hm(hm[0], hm[1]));
        tb.setOnClickListener(x -> new TimePickerDialog(this, (tp, h, m) -> {
            hm[0] = h;
            hm[1] = m;
            tb.setText(Engine.hm(h, m));
        }, hm[0], hm[1], true).show());
        v.addView(tb);

        v.addView(label("Hari"));
        final TextView[] chips = new TextView[7];
        final Runnable paint = () -> {
            for (int i = 0; i < 7; i++) {
                boolean on = (days[0] & (1 << i)) != 0;
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(10));
                g.setColor(on ? ACC : CHIP_OFF);
                chips[i].setBackground(g);
                chips[i].setTextColor(on ? 0xFF000000 : 0xFFDDDDDD);
            }
        };
        LinearLayout dr = new LinearLayout(this);
        dr.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView c = new TextView(this);
            c.setText(Engine.DAYS[i]);
            c.setGravity(Gravity.CENTER);
            c.setTextSize(13);
            c.setPadding(0, dp(10), 0, dp(10));
            c.setOnClickListener(x -> {
                days[0] ^= (1 << bit);
                paint.run();
            });
            chips[i] = c;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            dr.addView(c, lp);
        }
        v.addView(dr);
        paint.run();

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        String[] ql = {"Sen–Jum", "Sab–Min", "Tiap hari"};
        final int[] qv = {Entry.WEEKDAYS, Entry.WEEKEND, Entry.EVERYDAY};
        for (int i = 0; i < 3; i++) {
            final int qi = i;
            Button b = new Button(this);
            b.setText(ql[i]);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setOnClickListener(x -> {
                days[0] = qv[qi];
                paint.run();
            });
            quick.addView(b, weight());
        }
        v.addView(quick);

        v.addView(label("Profil"));
        List<String> names = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < ps.size(); i++) {
            names.add(ps.get(i).name);
            if (ps.get(i).id == e.profileId) sel = i;
        }
        final Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        sp.setSelection(sel);
        v.addView(sp);

        ScrollView sv = new ScrollView(this);
        sv.addView(v);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Jadwal baru" : "Edit jadwal")
                .setView(sv)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null);
        if (!isNew) {
            b.setNeutralButton("Hapus", (d, w) -> {
                List<Entry> l = s.entries();
                for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == e.id) l.remove(i);
                s.saveEntries(l);
                Engine.refresh(this);
                render();
            });
        }
        final AlertDialog dlg = b.create();
        dlg.setOnShowListener(di -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            if (days[0] == 0) {
                toast("Pilih minimal satu hari");
                return;
            }
            e.hour = hm[0];
            e.minute = hm[1];
            e.days = days[0];
            int pos = sp.getSelectedItemPosition();
            e.profileId = ps.get(pos < 0 ? 0 : pos).id;
            List<Entry> l = s.entries();
            if (isNew) {
                e.id = s.newId();
                e.enabled = true;
                l.add(e);
            } else {
                for (int i = 0; i < l.size(); i++) if (l.get(i).id == e.id) l.set(i, e);
            }
            s.saveEntries(l);
            Engine.markSeen(this);
            Engine.refresh(this);
            render();
            dlg.dismiss();
        }));
        dlg.show();
    }

    // ================= helper tampilan =================
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String t, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView label(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(14), 0, dp(4));
        return tv;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(16));
        l.setBackground(g);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(10);
        l.setLayoutParams(lp);
        return l;
    }

    private LinearLayout hrow() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(6), 0, 0);
        return l;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private Button primaryBtn(String t, View.OnClickListener l) {
        Button b = btn(t, l);
        GradientDrawable g = new GradientDrawable();
        g.setColor(ACC);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        b.setTextColor(0xFF000000);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    private void sectionTitle(String t) {
        TextView tv = text(t, 20, TXT, true);
        tv.setPadding(dp(4), dp(18), 0, dp(8));
        root.addView(tv);
    }

    private void header(String title, View.OnClickListener add) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(4), dp(18), 0, dp(8));
        h.addView(text(title, 20, TXT, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView plus = text("+ Tambah", 15, ACC, true);
        plus.setPadding(dp(12), dp(6), dp(4), dp(6));
        plus.setOnClickListener(add);
        h.addView(plus);
        root.addView(h);
    }

    private void toast(String t) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
    }
}
