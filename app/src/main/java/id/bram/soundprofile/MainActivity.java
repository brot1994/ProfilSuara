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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    static final int BG = 0xFF121212, BAR = 0xFF00979D, BAR_DARK = 0xFF006A6E, CARD = 0xFF1E1E1E,
            TXT = 0xFFEEEEEE, SUB = 0xFF9E9E9E, ACC = 0xFF00979D, ACC_TXT = 0xFF26B5BB, PILL = 0xFFA9A9A9,
            PILL_TXT = 0xFF111111, WARN = 0xFFEF5350, CHIP_OFF = 0xFF3A3A3A, DIVIDER = 0xFF2C2C2C,
            YELLOW = 0xFF9A8A17;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int PAGE_PROFILES = 0, PAGE_SCHEDULE = 1, PAGE_SETTINGS = 2, PAGE_EDIT = 3;
    static final String[] DAY_FULL = {"Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"};
    static final int REQ_RINGTONE = 10, REQ_NOTIF_TONE = 11;

    private LinearLayout content;
    private TextView title;
    private ImageView leftIcon, rightIcon, fab;
    private ScrollView scroll;
    private Store s;
    private int page = PAGE_PROFILES;

    // profil yang sedang diedit (halaman PAGE_EDIT)
    private Profile editing;
    private boolean editingNew;

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
        fab.setOnClickListener(v -> openEditor(null));
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
        render(false);
    }

    @Override
    public void onBackPressed() {
        if (page == PAGE_EDIT) {
            editing = null;
            go(PAGE_PROFILES);
        } else if (page != PAGE_PROFILES) {
            go(PAGE_PROFILES);
        } else {
            super.onBackPressed();
        }
    }

    private void go(int p) {
        page = p;
        render(true);
    }

    private View appBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(8), 0, dp(8), 0);

        leftIcon = icon(R.drawable.ic_menu, 0xFFFFFFFF);
        leftIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
        leftIcon.setOnClickListener(v -> {
            if (page == PAGE_EDIT) {
                onBackPressed();
                return;
            }
            PopupMenu pm = new PopupMenu(this, leftIcon);
            Menu m = pm.getMenu();
            m.add(0, PAGE_PROFILES, 0, "Profil");
            m.add(0, PAGE_SCHEDULE, 1, "Jadwal (Scheduler)");
            m.add(0, PAGE_SETTINGS, 2, "Pengaturan & izin");
            pm.setOnMenuItemClickListener(it -> {
                go(it.getItemId());
                return true;
            });
            pm.show();
        });
        bar.addView(leftIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        title = new TextView(this);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine();
        title.setPadding(dp(14), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));

        rightIcon = icon(R.drawable.ic_check, 0xFFFFFFFF);
        rightIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
        rightIcon.setOnClickListener(v -> saveEditor());
        bar.addView(rightIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return bar;
    }

    // ================= render =================
    private void render(boolean toTop) {
        content.removeAllViews();
        if (toTop) scroll.scrollTo(0, 0);
        fab.setVisibility(page == PAGE_PROFILES ? View.VISIBLE : View.GONE);
        rightIcon.setVisibility(page == PAGE_EDIT ? View.VISIBLE : View.GONE);
        leftIcon.setImageResource(page == PAGE_EDIT ? R.drawable.ic_back : R.drawable.ic_menu);
        content.setPadding(dp(14), dp(12), dp(14), dp(96));
        if (page == PAGE_EDIT && editing == null) page = PAGE_PROFILES;

        if (page == PAGE_SCHEDULE) {
            title.setText("Jadwal");
            content.setPadding(0, 0, 0, dp(40));
            renderSchedule();
        } else if (page == PAGE_SETTINGS) {
            title.setText("Pengaturan");
            renderSettings();
        } else if (page == PAGE_EDIT) {
            title.setText(editingNew ? "Profil baru" : editing.name);
            content.setPadding(0, 0, 0, dp(40));
            renderEditor();
        } else {
            title.setText("Profil Suara");
            renderProfiles();
        }
    }

    // ================= halaman profil (depan) =================
    private void renderProfiles() {
        long now = System.currentTimeMillis();
        long active = s.activeId();
        long tu = s.tempUntil();

        LinearLayout st = new LinearLayout(this);
        st.setOrientation(LinearLayout.HORIZONTAL);
        st.setGravity(Gravity.CENTER_VERTICAL);
        st.setPadding(dp(6), 0, dp(6), dp(10));
        String line = s.schedOn() ? Engine.statusLine(this, s) : "Jadwal dimatikan";
        st.addView(text(line, 13, SUB, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (tu > now) {
            TextView cancel = text("Batalkan", 13, ACC_TXT, true);
            cancel.setPadding(dp(10), dp(6), 0, dp(6));
            cancel.setOnClickListener(v -> {
                Engine.followSchedule(this);
                render(false);
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
            warn.setOnClickListener(v -> go(PAGE_SETTINGS));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.bottomMargin = dp(12);
            content.addView(warn, lp);
        }

        for (final Profile p : s.profiles()) {
            boolean on = p.id == active;
            content.addView(pill(p, on, on && tu > now ? "sampai " + Engine.fmtTime(tu) : null));
        }

        TextView hint = text("Ketuk = aktifkan · ikon jam = sementara · tekan lama = pengaturan profil", 12, SUB, false);
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
            openEditor(p);
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
        render(false);
    }

    private void durationDialog(final Profile p) {
        final String[] labels = {"15 menit", "30 menit", "45 menit", "1 jam", "1,5 jam", "2 jam", "3 jam",
                "Sampai jam…", "Lainnya (menit)…"};
        final int[] mins = {15, 30, 45, 60, 90, 120, 180};
        new AlertDialog.Builder(this).setTitle(p.name + " selama…").setItems(labels, (d, w) -> {
            if (w < mins.length) {
                activate(p, mins[w]);
            } else if (w == mins.length) {
                Calendar k = Calendar.getInstance();
                k.add(Calendar.HOUR_OF_DAY, 1);
                new TimePickerDialog(this, (tp, h, m) -> {
                    Calendar t = Calendar.getInstance();
                    t.set(Calendar.HOUR_OF_DAY, h);
                    t.set(Calendar.MINUTE, m);
                    t.set(Calendar.SECOND, 0);
                    long diff = t.getTimeInMillis() - System.currentTimeMillis();
                    if (diff <= 0) diff += 24L * 3600000L;
                    activate(p, (int) Math.max(1, Math.round(diff / 60000.0)));
                }, k.get(Calendar.HOUR_OF_DAY), 0, true).show();
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

    // ================= halaman jadwal (per hari) =================
    private void renderSchedule() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(14), dp(20), dp(14));
        top.setBackgroundColor(0xFF1A1A1A);
        Switch on = new Switch(this);
        on.setText("Jadwal aktif");
        on.setTextColor(TXT);
        on.setTextSize(17);
        on.setChecked(s.schedOn());
        on.setOnCheckedChangeListener((bt, checked) -> {
            s.setSchedOn(checked);
            if (checked) Engine.markSeen(this);
            Engine.refresh(this);
            content.post(() -> render(false));
        });
        top.addView(on);
        top.addView(text(s.schedOn() ? Engine.statusLine(this, s) : "Profil tidak akan berganti otomatis",
                13, SUB, false));
        content.addView(top);

        List<Entry> all = s.entries();
        Collections.sort(all, (a, b) -> (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
        int today = Engine.dayBit(Calendar.getInstance());

        for (int d = 0; d < 7; d++) {
            final int day = d;
            content.addView(divider());
            TextView h = text(DAY_FULL[d] + (d == today ? "  (hari ini)" : ""), 18, ACC_TXT, false);
            h.setPadding(dp(20), dp(22), dp(20), dp(8));
            content.addView(h);

            for (final Entry e : all) {
                if ((e.days & (1 << d)) == 0) continue;
                Profile p = s.profile(e.profileId);
                LinearLayout row = listRow();
                ImageView del = icon(R.drawable.ic_delete, 0xFFBDBDBD);
                del.setPadding(dp(4), dp(4), dp(4), dp(4));
                del.setOnClickListener(v -> deleteEntry(e));
                row.addView(del, new LinearLayout.LayoutParams(dp(32), dp(32)));
                TextView t = text(Engine.hm(e.hour, e.minute) + "  " + (p == null ? "?" : p.name), 20, TXT, false);
                t.setPadding(dp(28), 0, 0, 0);
                row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
                if (p != null) row.addView(icon(p.iconRes(), SUB), new LinearLayout.LayoutParams(dp(24), dp(24)));
                row.setOnClickListener(v -> entryDialog(e, day));
                content.addView(row);
            }

            LinearLayout add = listRow();
            add.addView(icon(R.drawable.ic_add, 0xFFBDBDBD), new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView at = text("Tambah waktu", 17, 0xFFBDBDBD, false);
            at.setPadding(dp(28), 0, 0, 0);
            add.addView(at);
            add.setOnClickListener(v -> entryDialog(null, day));
            content.addView(add);

            LinearLayout copy = listRow();
            copy.addView(icon(R.drawable.ic_copy, 0xFFBDBDBD), new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView ct = text("Salin ke hari berikutnya (" + DAY_FULL[(d + 1) % 7] + ")", 17, 0xFFBDBDBD, false);
            ct.setPadding(dp(30), 0, 0, 0);
            copy.addView(ct);
            copy.setOnClickListener(v -> copyDay(day, (day + 1) % 7));
            content.addView(copy);
        }
    }

    private LinearLayout listRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        row.setMinimumHeight(dp(58));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(1)));
        return v;
    }

    private void deleteEntry(Entry e) {
        List<Entry> l = s.entries();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == e.id) l.remove(i);
        s.saveEntries(l);
        Engine.refresh(this);
        render(false);
    }

    private void copyDay(final int from, final int to) {
        final List<Entry> l = s.entries();
        int count = 0, existing = 0;
        for (Entry e : l) {
            if ((e.days & (1 << from)) != 0) count++;
            if ((e.days & (1 << to)) != 0) existing++;
        }
        if (count == 0) {
            toast(DAY_FULL[from] + " belum punya jadwal");
            return;
        }
        Runnable doCopy = () -> {
            List<Entry> out = new ArrayList<>();
            List<Entry> src = new ArrayList<>();
            for (Entry e : l) {
                if ((e.days & (1 << to)) != 0) continue; // ganti jadwal hari tujuan
                out.add(e);
                if ((e.days & (1 << from)) != 0) src.add(e);
            }
            for (Entry e : src) {
                Entry n = Entry.make(s.newId(), e.hour, e.minute, 1 << to, e.profileId);
                n.enabled = e.enabled;
                out.add(n);
            }
            s.saveEntries(out);
            Engine.markSeen(this);
            Engine.refresh(this);
            toast("Disalin ke " + DAY_FULL[to]);
            render(false);
        };
        if (existing > 0) {
            new AlertDialog.Builder(this).setTitle("Salin ke " + DAY_FULL[to] + "?")
                    .setMessage("Jadwal " + DAY_FULL[to] + " yang sudah ada akan diganti dengan jadwal " + DAY_FULL[from] + ".")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Salin", (d, w) -> doCopy.run())
                    .show();
        } else {
            doCopy.run();
        }
    }

    private void entryDialog(final Entry src, final int day) {
        final List<Profile> ps = s.profiles();
        if (ps.isEmpty()) {
            toast("Buat profil dulu");
            return;
        }
        final boolean isNew = src == null;
        final int[] hm = {isNew ? 7 : src.hour, isNew ? 0 : src.minute};

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
        v.addView(label("Profil"));
        String[] names = new String[ps.size()];
        int sel = 0;
        for (int i = 0; i < ps.size(); i++) {
            names[i] = ps.get(i).name;
            if (!isNew && ps.get(i).id == src.profileId) sel = i;
        }
        final Spinner sp = spinner(names, sel);
        v.addView(sp);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle((isNew ? "Tambah waktu — " : "Ubah — ") + DAY_FULL[day])
                .setView(v)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null)
                .create();
        dlg.setOnShowListener(di -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            int pos = Math.max(0, sp.getSelectedItemPosition());
            long pid = ps.get(pos).id;
            List<Entry> l = s.entries();
            if (isNew) {
                l.add(Entry.make(s.newId(), hm[0], hm[1], 1 << day, pid));
            } else {
                for (Entry e : l) {
                    if (e.id == src.id) {
                        e.hour = hm[0];
                        e.minute = hm[1];
                        e.profileId = pid;
                    }
                }
            }
            s.saveEntries(l);
            Engine.markSeen(this);
            Engine.refresh(this);
            render(false);
            dlg.dismiss();
        }));
        dlg.show();
    }

    // ================= halaman edit profil =================
    private void openEditor(Profile p) {
        editingNew = p == null;
        editing = editingNew ? Profile.make(0, "", 0, Profile.NORMAL, 70, 70, 50, 80, 50) : p.copy();
        go(PAGE_EDIT);
    }

    private void saveEditor() {
        if (editing == null) return;
        Profile p = editing;
        if (p.name == null || p.name.trim().isEmpty()) p.name = "Profil";
        p.name = p.name.trim();
        List<Profile> l = s.profiles();
        if (editingNew) {
            p.id = s.newId();
            l.add(p);
        } else {
            for (int i = 0; i < l.size(); i++) if (l.get(i).id == p.id) l.set(i, p);
        }
        s.saveProfiles(l);
        if (p.id == s.activeId()) Engine.applyAudio(this, p);
        Engine.refresh(this);
        toast("Profil disimpan");
        editing = null;
        go(PAGE_PROFILES);
    }

    private TextView section(String t) {
        TextView tv = text(t, 18, ACC_TXT, false);
        tv.setPadding(dp(20), dp(26), dp(20), dp(10));
        return tv;
    }

    private LinearLayout body() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), 0, dp(20), dp(6));
        return l;
    }

    private void renderEditor() {
        final Profile p = editing;

        // --- nama & ikon ---
        LinearLayout head = body();
        head.setPadding(dp(20), dp(14), dp(20), dp(4));
        final EditText name = new EditText(this);
        name.setHint("Nama profil (mis. Kerja)");
        name.setText(p.name);
        name.setSingleLine();
        name.setTextColor(TXT);
        name.setHintTextColor(SUB);
        name.setTextSize(20);
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence cs, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence cs, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                p.name = e.toString();
            }
        });
        head.addView(name);
        final ImageView[] icons = new ImageView[Profile.ICONS.length];
        final Runnable paintIcons = () -> {
            for (int i = 0; i < icons.length; i++) {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(i == p.icon ? ACC : CHIP_OFF);
                icons[i].setBackground(g);
            }
        };
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout ir = new LinearLayout(this);
        ir.setOrientation(LinearLayout.HORIZONTAL);
        ir.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < icons.length; i++) {
            final int idx = i;
            ImageView iv = icon(Profile.ICONS[i], 0xFFFFFFFF);
            iv.setPadding(dp(9), dp(9), dp(9), dp(9));
            iv.setOnClickListener(x -> {
                p.icon = idx;
                paintIcons.run();
            });
            icons[i] = iv;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(0, 0, dp(8), 0);
            ir.addView(iv, lp);
        }
        paintIcons.run();
        hs.addView(ir);
        head.addView(hs);
        content.addView(head);

        // --- volume ---
        content.addView(section("Volume"));
        LinearLayout vol = body();
        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        String[] modes = {"Normal", "Getar", "Senyap"};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(modes[i]);
            rb.setTextColor(TXT);
            rg.addView(rb);
        }
        rg.check(1000 + p.mode);
        vol.addView(rg);
        final TextView note = text("Panggilan, notifikasi & sistem hanya dipakai saat mode Normal.", 12, SUB, false);
        vol.addView(note);

        String[] labels = {"Panggilan", "Notifikasi", "Multimedia", "Alarm", "Sistem"};
        final int[] vals = p.values();
        final CheckBox[] cbs = new CheckBox[5];
        final SeekBar[] bars = new SeekBar[5];
        final TextView[] tvs = new TextView[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);
            CheckBox cb = new CheckBox(this);
            cb.setText(labels[i]);
            cb.setTextColor(TXT);
            cb.setTextSize(16);
            cb.setChecked(vals[i] >= 0);
            row.addView(cb, new LinearLayout.LayoutParams(0, WRAP, 1f));
            TextView tv = text(vals[i] >= 0 ? vals[i] + "%" : "tidak diubah", 15, TXT, false);
            row.addView(tv);
            SeekBar sb = new SeekBar(this);
            sb.setMax(100);
            sb.setProgress(vals[i] >= 0 ? vals[i] : 50);
            sb.setPadding(dp(16), dp(6), dp(16), dp(6));
            cbs[i] = cb;
            bars[i] = sb;
            tvs[i] = tv;
            final Runnable store = () -> {
                int[] nv = p.values();
                nv[idx] = cbs[idx].isChecked() ? bars[idx].getProgress() : -1;
                p.setValues(nv);
            };
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    tvs[idx].setText(progress + "%");
                    store.run();
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
                store.run();
            });
            vol.addView(row);
            vol.addView(sb);
        }
        final Runnable upd = () -> {
            int m = rg.getCheckedRadioButtonId() - 1000;
            p.mode = (m < 0 || m > 2) ? Profile.NORMAL : m;
            boolean normal = p.mode == Profile.NORMAL;
            int[] ringStreams = {0, 1, 4};
            for (int i = 0; i < 5; i++) {
                boolean allowed = normal || (i != 0 && i != 1 && i != 4);
                cbs[i].setEnabled(allowed);
                bars[i].setEnabled(allowed && cbs[i].isChecked());
            }
            note.setVisibility(normal ? View.GONE : View.VISIBLE);
        };
        rg.setOnCheckedChangeListener((g, id) -> upd.run());
        upd.run();
        content.addView(vol);

        // --- getaran ---
        content.addView(divider());
        content.addView(section("Getaran"));
        LinearLayout vib = body();
        String[] tri = {"Tidak diubah", "Mati", "Nyala"};
        vib.addView(label("Getar saat ada panggilan"));
        final Spinner vr = spinner(tri, p.vibRing + 1);
        vr.setOnItemSelectedListener(onSelect(pos -> p.vibRing = pos - 1));
        vib.addView(vr);
        vib.addView(label("Getaran sentuh (touch feedback)"));
        final Spinner hp = spinner(tri, p.haptic + 1);
        hp.setOnItemSelectedListener(onSelect(pos -> p.haptic = pos - 1));
        vib.addView(hp);
        writeSettingsWarning(vib);
        content.addView(vib);

        // --- jangan ganggu ---
        content.addView(divider());
        content.addView(section("Mode Jangan Ganggu"));
        LinearLayout dnd = body();
        dnd.addView(text("Berlaku untuk seluruh HP selama profil ini aktif.", 13, SUB, false));
        final Spinner dndSp = spinner(new String[]{"Tidak diaktifkan", "Prioritas (pilih siapa yang boleh)",
                "Hanya alarm", "Senyap total"}, p.dnd);
        dnd.addView(dndSp);
        final LinearLayout prio = new LinearLayout(this);
        prio.setOrientation(LinearLayout.VERTICAL);
        prio.addView(label("Panggilan yang boleh berdering"));
        final Spinner callSp = spinner(new String[]{"Semua orang", "Kontak saja",
                "Kontak favorit (berbintang) saja", "Tidak ada"}, p.callers);
        callSp.setOnItemSelectedListener(onSelect(pos -> p.callers = pos));
        prio.addView(callSp);
        final CheckBox rep = new CheckBox(this);
        rep.setText("Izinkan penelepon berulang (menelepon 2x dalam 15 menit)");
        rep.setTextColor(TXT);
        rep.setChecked(p.repeat);
        rep.setOnCheckedChangeListener((bt, c) -> p.repeat = c);
        prio.addView(rep);
        dnd.addView(prio);
        dndSp.setOnItemSelectedListener(onSelect(pos -> {
            p.dnd = pos;
            prio.setVisibility(pos == Profile.DND_PRIORITY ? View.VISIBLE : View.GONE);
        }));
        prio.setVisibility(p.dnd == Profile.DND_PRIORITY ? View.VISIBLE : View.GONE);
        content.addView(dnd);

        // --- nada dering ---
        content.addView(divider());
        content.addView(section("Nada dering"));
        LinearLayout tones = body();
        tones.addView(toneRow("Nada panggilan", p.ringtone, RingtoneManager.TYPE_RINGTONE, REQ_RINGTONE));
        tones.addView(toneRow("Suara notifikasi", p.notifTone, RingtoneManager.TYPE_NOTIFICATION, REQ_NOTIF_TONE));
        writeSettingsWarning(tones);
        content.addView(tones);

        // --- hapus ---
        if (!editingNew) {
            content.addView(divider());
            LinearLayout del = listRow();
            del.addView(icon(R.drawable.ic_delete, WARN), new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView dt = text("Hapus profil ini", 17, WARN, false);
            dt.setPadding(dp(24), 0, 0, 0);
            del.addView(dt);
            del.setOnClickListener(v -> deleteProfile(p));
            content.addView(del);
        }
    }

    private void writeSettingsWarning(LinearLayout parent) {
        if (Settings.System.canWrite(this)) return;
        TextView w = text("Perlu izin “Ubah pengaturan sistem” agar bagian ini bisa diterapkan — ketuk di sini",
                14, 0xFFFFFFFF, false);
        w.setBackgroundColor(YELLOW);
        w.setPadding(dp(12), dp(10), dp(12), dp(10));
        w.setOnClickListener(v -> open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(12);
        parent.addView(w, lp);
    }

    private View toneRow(final String label, String value, final int type, final int req) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.addView(text(label, 16, TXT, false));
        row.addView(text(toneTitle(value), 14, SUB, false));
        row.setOnClickListener(v -> {
            String[] items = {"Tidak diubah", "Pilih nada…"};
            new AlertDialog.Builder(this).setTitle(label).setItems(items, (d, w) -> {
                if (w == 0) {
                    if (req == REQ_RINGTONE) editing.ringtone = "";
                    else editing.notifTone = "";
                    render(false);
                } else {
                    Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, label);
                    String cur = req == REQ_RINGTONE ? editing.ringtone : editing.notifTone;
                    if (cur != null && !cur.isEmpty() && !"silent".equals(cur))
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur));
                    try {
                        startActivityForResult(i, req);
                    } catch (Exception ex) {
                        toast("Pemilih nada tidak tersedia");
                    }
                }
            }).show();
        });
        return row;
    }

    private String toneTitle(String v) {
        if (v == null || v.isEmpty()) return "Tidak diubah";
        if ("silent".equals(v)) return "Tanpa suara";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(v));
            if (r != null) return r.getTitle(this);
        } catch (Exception ignored) {
        }
        return "Nada pilihan";
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (editing == null || res != RESULT_OK || data == null) return;
        if (req != REQ_RINGTONE && req != REQ_NOTIF_TONE) return;
        Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        String v = u == null ? "silent" : u.toString();
        if (req == REQ_RINGTONE) editing.ringtone = v;
        else editing.notifTone = v;
        render(false);
    }

    interface IntCb {
        void on(int v);
    }

    private AdapterView.OnItemSelectedListener onSelect(final IntCb cb) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                cb.on(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
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
                    editing = null;
                    go(PAGE_PROFILES);
                }).show();
    }

    // ================= halaman pengaturan =================
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
        permItem(c, Settings.System.canWrite(this), "Ubah pengaturan sistem (opsional)",
                "Hanya untuk fitur Getaran dan Nada dering di profil.",
                v -> open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
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
        set.addView(btn("Ikuti jadwal sekarang", v -> {
            Engine.followSchedule(this);
            toast("Profil disesuaikan dengan jadwal");
        }));
        TextView tip = text("Tips: tambahkan tombol “Profil Suara” di panel Quick Settings "
                + "(tarik panel ke bawah → ikon pensil/+), dan widget “Profil Suara” di layar utama "
                + "(tekan lama layar → Widget). 3 profil teratas dipakai di notifikasi, 4 teratas di widget. "
                + "Urutan profil bisa diubah lewat tombol di bawah.", 13, SUB, false);
        tip.setPadding(0, dp(10), 0, 0);
        set.addView(tip);
        set.addView(btn("Atur urutan profil", v -> reorderDialog()));
        content.addView(set);
    }

    private void reorderDialog() {
        final List<Profile> l = s.profiles();
        String[] names = new String[l.size()];
        for (int i = 0; i < l.size(); i++) names[i] = (i + 1) + ". " + l.get(i).name;
        new AlertDialog.Builder(this).setTitle("Ketuk profil untuk dipindah ke atas")
                .setItems(names, (d, w) -> {
                    if (w > 0) {
                        Collections.swap(l, w, w - 1);
                        s.saveProfiles(l);
                        Engine.refresh(this);
                    }
                    reorderDialog();
                })
                .setPositiveButton("Selesai", null)
                .show();
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
        render(false);
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
        tv.setTextColor(SUB);
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
