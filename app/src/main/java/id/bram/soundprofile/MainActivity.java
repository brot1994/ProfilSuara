package id.bram.soundprofile;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
    static final int BG = 0xFF121212, BAR = 0xFF00979D, BAR_DARK = 0xFF006A6E, CARD = 0xFF1E1E1E,
            TXT = 0xFFEEEEEE, SUB = 0xFF9E9E9E, ACC = 0xFF00979D, PILL = 0xFFA9A9A9,
            PILL_TXT = 0xFF111111, WARN = 0xFFEF5350, CHIP_OFF = 0xFF3A3A3A;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int PAGE_PROFILES = 0, PAGE_SCHEDULE = 1, PAGE_SETTINGS = 2;

    private LinearLayout content;
    private TextView title;
    private ImageView fab;
    private ScrollView scroll;
    private Store s;
    private int page = PAGE_PROFILES;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new Store(this);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(appBar(), new LinearLayout.LayoutParams(MATCH, dp(64)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(96));
        scroll.addView(content);
        col.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        frame.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        fab = new ImageView(this);
        fab.setImageResource(R.drawable.ic_add);
        fab.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        GradientDrawable fg = new GradientDrawable();
        fg.setShape(GradientDrawable.OVAL);
        fg.setColor(ACC);
        fab.setBackground(fg);
        fab.setPadding(dp(16), dp(16), dp(16), dp(16));
        fab.setElevation(dp(6));
        fab.setOnClickListener(v -> {
            if (page == PAGE_SCHEDULE) editEntry(null);
            else editProfile(null);
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(22), dp(26));
        frame.addView(fab, flp);

        setContentView(frame);
        getWindow().setStatusBarColor(BAR_DARK);
        getWindow().setNavigationBarColor(BG);
        Engine.ensureChannel(this);
        Engine.tick(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onBackPressed() {
        if (page != PAGE_PROFILES) {
            page = PAGE_PROFILES;
            render();
        } else {
            super.onBackPressed();
        }
    }

    private View appBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(8), 0, dp(8), 0);

        final ImageView menu = icon(R.drawable.ic_menu, 0xFFFFFFFF);
        menu.setPadding(dp(12), dp(12), dp(12), dp(12));
        menu.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, menu);
            Menu m = pm.getMenu();
            m.add(0, PAGE_PROFILES, 0, "Profil");
            m.add(0, PAGE_SCHEDULE, 1, "Jadwal");
            m.add(0, PAGE_SETTINGS, 2, "Pengaturan & izin");
            pm.setOnMenuItemClickListener(it -> {
                page = it.getItemId();
                render();
                return true;
            });
            pm.show();
        });
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));

        title = new TextView(this);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(12), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return bar;
    }

    // ================= render =================
    private void render() {
        content.removeAllViews();
        scroll.scrollTo(0, 0);
        fab.setVisibility(page == PAGE_SETTINGS ? View.GONE : View.VISIBLE);
        if (page == PAGE_SCHEDULE) {
            title.setText("Jadwal");
            renderSchedule();
        } else if (page == PAGE_SETTINGS) {
            title.setText("Pengaturan");
            renderSettings();
        } else {
            title.setText("Profil Suara");
            renderProfiles();
        }
    }

    private void renderProfiles() {
        long now = System.currentTimeMillis();
        long active = s.activeId();
        long tu = s.tempUntil();

        // baris status
        LinearLayout st = new LinearLayout(this);
        st.setOrientation(LinearLayout.HORIZONTAL);
        st.setGravity(Gravity.CENTER_VERTICAL);
        st.setPadding(dp(6), 0, dp(6), dp(10));
        st.addView(text(Engine.statusLine(this, s), 13, SUB, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (tu > now) {
            TextView cancel = text("Batalkan", 13, ACC, true);
            cancel.setPadding(dp(10), dp(6), 0, dp(6));
            cancel.setOnClickListener(v -> {
                Engine.followSchedule(this);
                render();
            });
            st.addView(cancel);
        }
        content.addView(st);

        if (missingPermissions() > 0) {
            TextView warn = text("⚠  Ada izin yang belum diberikan — ketuk di sini", 14, 0xFFFFFFFF, true);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFFB71C1C);
            g.setCornerRadius(dp(14));
            warn.setBackground(g);
            warn.setPadding(dp(16), dp(12), dp(16), dp(12));
            warn.setOnClickListener(v -> {
                page = PAGE_SETTINGS;
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.bottomMargin = dp(12);
            content.addView(warn, lp);
        }

        for (final Profile p : s.profiles()) {
            boolean on = p.id == active;
            content.addView(pill(p, on, on && tu > now ? "sampai " + Engine.fmtTime(tu) : null));
        }

        TextView hint = text("Ketuk = aktifkan · ikon jam = sementara · tekan lama = edit", 12, SUB, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, 0);
        content.addView(hint);
    }

    private View pill(final Profile p, boolean on, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(on ? ACC : PILL);
        g.setCornerRadius(dp(40));
        row.setBackground(g);
        row.setPadding(dp(18), 0, dp(8), 0);
        row.setMinimumHeight(dp(70));
        int fg = on ? 0xFFFFFFFF : PILL_TXT;

        row.addView(icon(p.iconRes(), fg), new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(14), dp(8), dp(6), dp(8));
        TextView name = text(p.name, 22, fg, false);
        name.setSingleLine();
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(name);
        if (sub != null) mid.addView(text(sub, 13, 0xDDFFFFFF, false));
        row.addView(mid, new LinearLayout.LayoutParams(0, WRAP, 1f));

        ImageView clock = icon(R.drawable.ic_timer, on ? 0xFFB2EBF2 : 0xFF333333);
        clock.setPadding(dp(10), dp(10), dp(10), dp(10));
        clock.setOnClickListener(v -> durationDialog(p));
        row.addView(clock, new LinearLayout.LayoutParams(dp(54), dp(54)));

        row.setOnClickListener(v -> activate(p, 0));
        row.setOnLongClickListener(v -> {
            profileMenu(p);
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(14);
        row.setLayoutParams(lp);
        return row;
    }

    private void activate(Profile p, int minutes) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (p.needsPolicy() && !nm.isNotificationPolicyAccessGranted()) {
            new AlertDialog.Builder(this)
                    .setTitle("Butuh izin Jangan Ganggu")
                    .setMessage("Profil “" + p.name + "” memakai mode Senyap/Jangan Ganggu. "
                            + "Berikan akses Jangan Ganggu untuk Profil Suara dulu.")
                    .setNegativeButton("Nanti", null)
                    .setPositiveButton("Buka pengaturan", (d, w) ->
                            open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                    .show();
            return;
        }
        Engine.activate(this, p.id, minutes);
        if (minutes > 0)
            toast(p.name + " aktif sampai " + Engine.fmtTime(System.currentTimeMillis() + minutes * 60000L));
        render();
    }

    // ================= jadwal =================
    private void renderSchedule() {
        TextView info = text(Engine.statusLine(this, s), 13, SUB, false);
        info.setPadding(dp(6), 0, dp(6), dp(10));
        content.addView(info);

        List<Entry> es = s.entries();
        Collections.sort(es, (a, b) -> (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
        if (es.isEmpty()) content.addView(text("Belum ada jadwal. Ketuk + untuk menambah.", 14, SUB, false));
        for (final Entry e : es) {
            LinearLayout c = card();
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            Profile p = s.profile(e.profileId);
            if (p != null) c.addView(icon(p.iconRes(), TXT), new LinearLayout.LayoutParams(dp(28), dp(28)));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setPadding(dp(14), 0, 0, 0);
            left.addView(text(Engine.hm(e.hour, e.minute) + "  →  " + (p == null ? "?" : p.name), 19, TXT, true));
            left.addView(text(Engine.daysLabel(e.days), 13, SUB, false));
            c.addView(left, new LinearLayout.LayoutParams(0, WRAP, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(e.enabled);
            sw.setOnCheckedChangeListener((bt, checked) -> {
                List<Entry> l = s.entries();
                for (Entry x : l) if (x.id == e.id) x.enabled = checked;
                s.saveEntries(l);
                Engine.markSeen(this);
                Engine.refresh(this);
                content.post(this::render);
            });
            c.addView(sw);
            c.setAlpha(e.enabled ? 1f : 0.5f);
            c.setOnClickListener(v -> editEntry(e));
            content.addView(c);
        }
    }

    // ================= pengaturan =================
    private int missingPermissions() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        int n = 0;
        if (!nm.isNotificationPolicyAccessGranted()) n++;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) n++;
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) n++;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) n++;
        return n;
    }

    private void renderSettings() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);

        LinearLayout c = card();
        c.addView(text("Izin", 18, TXT, true));
        permItem(c, nm.isNotificationPolicyAccessGranted(), "Akses Jangan Ganggu",
                "Wajib untuk mode Senyap, Hanya panggilan, Hanya alarm, dll. Cari “Profil Suara” lalu izinkan.",
                v -> open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        permItem(c, Build.VERSION.SDK_INT < 33
                        || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                "Notifikasi", "Menampilkan profil aktif dan tombol cepat.",
                v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
        permItem(c, pm.isIgnoringBatteryOptimizations(getPackageName()), "Tanpa batasan baterai",
                "Supaya jadwal tetap jalan saat HP tidur. Di Samsung pastikan juga tidak masuk “Aplikasi tidur”.",
                v -> open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))));
        if (Build.VERSION.SDK_INT >= 31) permItem(c, am.canScheduleExactAlarms(), "Alarm tepat waktu",
                "Supaya profil berganti tepat di menit yang dijadwalkan.",
                v -> open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()))));
        content.addView(c);

        LinearLayout set = card();
        Switch ns = new Switch(this);
        ns.setText("Notifikasi status + tombol cepat");
        ns.setTextColor(TXT);
        ns.setTextSize(15);
        ns.setChecked(s.showNotif());
        ns.setOnCheckedChangeListener((bt, checked) -> {
            s.setShowNotif(checked);
            Engine.refresh(this);
        });
        set.addView(ns);
        Button follow = btn("Ikuti jadwal sekarang", v -> {
            Engine.followSchedule(this);
            toast("Profil disesuaikan dengan jadwal");
        });
        set.addView(follow);
        TextView tip = text("Tips: tambahkan tombol “Profil Suara” di panel Quick Settings "
                + "(tarik panel ke bawah → ikon pensil/+), dan widget “Profil Suara” di layar utama "
                + "(tekan lama layar → Widget). Urutan 3 profil teratas dipakai di notifikasi, 4 teratas di widget.",
                13, SUB, false);
        tip.setPadding(0, dp(10), 0, 0);
        set.addView(tip);
        content.addView(set);
    }

    private void permItem(LinearLayout c, boolean ok, String title, String desc, View.OnClickListener l) {
        TextView t = text((ok ? "✓  " : "✗  ") + title, 15, ok ? 0xFF81C784 : WARN, true);
        t.setPadding(0, dp(12), 0, 0);
        c.addView(t);
        if (!ok) {
            c.addView(text(desc, 13, SUB, false));
            c.addView(primaryBtn("Berikan izin", l));
        }
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

    // ================= profil: menu, hapus, edit =================
    private void profileMenu(final Profile p) {
        String[] items = {"Edit", "Pindah ke atas", "Hapus"};
        new AlertDialog.Builder(this).setTitle(p.name).setItems(items, (d, w) -> {
            if (w == 0) editProfile(p);
            else if (w == 1) moveUp(p);
            else deleteProfile(p);
        }).show();
    }

    private void moveUp(Profile p) {
        List<Profile> l = s.profiles();
        for (int i = 1; i < l.size(); i++) {
            if (l.get(i).id == p.id) {
                Collections.swap(l, i, i - 1);
                break;
            }
        }
        s.saveProfiles(l);
        Engine.refresh(this);
        render();
    }

    private void deleteProfile(final Profile p) {
        if (s.profiles().size() <= 1) {
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
        final Profile p = isNew ? Profile.make(0, "", 0, Profile.NORMAL, 70, 70, 50, 80, 50) : src;

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20), dp(8), dp(20), dp(8));

        final EditText name = new EditText(this);
        name.setHint("Nama profil (mis. Kerja)");
        name.setText(p.name);
        name.setSingleLine();
        v.addView(name);

        // ikon
        v.addView(label("Ikon"));
        final int[] iconSel = {p.icon};
        final ImageView[] icons = new ImageView[Profile.ICONS.length];
        final Runnable paintIcons = () -> {
            for (int i = 0; i < icons.length; i++) {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(i == iconSel[0] ? ACC : CHIP_OFF);
                icons[i].setBackground(g);
            }
        };
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout ir = new LinearLayout(this);
        ir.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < icons.length; i++) {
            final int idx = i;
            ImageView iv = icon(Profile.ICONS[i], 0xFFFFFFFF);
            iv.setPadding(dp(9), dp(9), dp(9), dp(9));
            iv.setOnClickListener(x -> {
                iconSel[0] = idx;
                paintIcons.run();
            });
            icons[i] = iv;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(0, 0, dp(8), 0);
            ir.addView(iv, lp);
        }
        paintIcons.run();
        hs.addView(ir);
        v.addView(hs);

        // mode dering
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

        // jangan ganggu
        v.addView(label("Jangan Ganggu (berlaku untuk seluruh HP)"));
        final Spinner dndSp = spinner(new String[]{"Mati", "Prioritas (pilih siapa yang boleh)",
                "Hanya alarm", "Senyap total"}, p.dnd);
        v.addView(dndSp);
        final LinearLayout prioBox = new LinearLayout(this);
        prioBox.setOrientation(LinearLayout.VERTICAL);
        prioBox.addView(label("Panggilan yang boleh berdering"));
        final Spinner callSp = spinner(new String[]{"Semua orang", "Kontak saja",
                "Kontak favorit saja", "Tidak ada"}, p.callers);
        prioBox.addView(callSp);
        final CheckBox rep = new CheckBox(this);
        rep.setText("Izinkan penelepon berulang (menelepon 2x dalam 15 menit)");
        rep.setChecked(p.repeat);
        prioBox.addView(rep);
        v.addView(prioBox);

        // volume
        final TextView note = new TextView(this);
        note.setText("Dering, notifikasi & sistem hanya dipakai saat mode Normal.");
        note.setTextSize(12);
        v.addView(label("Volume (centang = ikut diatur)"));
        v.addView(note);
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
            row.addView(cb, new LinearLayout.LayoutParams(0, WRAP, 1f));
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
            prioBox.setVisibility(dndSp.getSelectedItemPosition() == Profile.DND_PRIORITY ? View.VISIBLE : View.GONE);
        };
        rg.setOnCheckedChangeListener((g, id) -> upd.run());
        dndSp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                upd.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
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
                    p.icon = iconSel[0];
                    int m = rg.getCheckedRadioButtonId() - 1000;
                    p.mode = (m < 0 || m > 2) ? Profile.NORMAL : m;
                    p.dnd = Math.max(0, dndSp.getSelectedItemPosition());
                    p.callers = Math.max(0, callSp.getSelectedItemPosition());
                    p.repeat = rep.isChecked();
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
    private void durationDialog(final Profile p) {
        final String[] labels = {"15 menit", "30 menit", "45 menit", "1 jam", "1,5 jam", "2 jam", "3 jam",
                "Sampai jam…", "Lainnya (menit)…"};
        final int[] mins = {15, 30, 45, 60, 90, 120, 180};
        new AlertDialog.Builder(this).setTitle(p.name + " selama…").setItems(labels, (d, w) -> {
            if (w < mins.length) {
                activate(p, mins[w]);
            } else if (w == mins.length) {
                java.util.Calendar k = java.util.Calendar.getInstance();
                k.add(java.util.Calendar.HOUR_OF_DAY, 1);
                new TimePickerDialog(this, (tp, h, m) -> {
                    java.util.Calendar t = java.util.Calendar.getInstance();
                    t.set(java.util.Calendar.HOUR_OF_DAY, h);
                    t.set(java.util.Calendar.MINUTE, m);
                    t.set(java.util.Calendar.SECOND, 0);
                    long diff = t.getTimeInMillis() - System.currentTimeMillis();
                    if (diff <= 0) diff += 24L * 3600000L;
                    int minutes = (int) Math.max(1, Math.round(diff / 60000.0));
                    activate(p, minutes);
                }, k.get(java.util.Calendar.HOUR_OF_DAY), 0, true).show();
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
                            if (m > 0 && m <= 24 * 60) activate(p, m);
                            else toast("Isi 1 sampai 1440 menit");
                        }).show();
            }
        }).show();
    }

    // ================= jadwal: edit =================
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
                chips[i].setTextColor(on ? 0xFFFFFFFF : 0xFFDDDDDD);
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
            quick.addView(b, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        v.addView(quick);

        v.addView(label("Profil"));
        String[] names = new String[ps.size()];
        int sel = 0;
        for (int i = 0; i < ps.size(); i++) {
            names[i] = ps.get(i).name;
            if (ps.get(i).id == e.profileId) sel = i;
        }
        final Spinner sp = spinner(names, sel);
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

    private ImageView icon(int res, int tint) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(tint));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    private Spinner spinner(String[] items, int sel) {
        Spinner sp = new Spinner(this);
        List<String> l = new ArrayList<>();
        Collections.addAll(l, items);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, l));
        if (sel >= 0 && sel < items.length) sp.setSelection(sel);
        return sp;
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
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    private void toast(String t) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
    }
}
