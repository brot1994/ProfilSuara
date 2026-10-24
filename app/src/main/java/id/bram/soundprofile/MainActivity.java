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
    static final int PAGE_PROFILES = 0, PAGE_SCHEDULE = 1, PAGE_SETTINGS = 2, PAGE_EDIT = 3, PAGE_MANAGE = 4;
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
    private int editReturn = PAGE_PROFILES;
    private boolean animateIn = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new Store(this);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        rootFrame = frame;

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
    protected void onStart() {
        super.onStart();
        animateIn = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(false);
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (page == PAGE_EDIT) {
            editing = null;
            go(editReturn);
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
            openDrawer();
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

    // ================= laci menu samping =================
    private FrameLayout rootFrame;
    private View scrim;
    private ScrollView drawer;
    private boolean drawerOpen;

    private void openDrawer() {
        if (drawer != null) {
            rootFrame.removeView(drawer);
            rootFrame.removeView(scrim);
        }
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.72);
        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> closeDrawer());
        rootFrame.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));

        drawer = new ScrollView(this);
        drawer.setBackgroundColor(0xFF2B2B2B);
        drawer.setElevation(dp(12));
        drawer.addView(buildDrawer());
        rootFrame.addView(drawer, new FrameLayout.LayoutParams(w, MATCH, Gravity.START));
        drawer.setTranslationX(-w);
        drawer.animate().translationX(0).setDuration(200).start();
        scrim.animate().alpha(1f).setDuration(200).start();
        drawerOpen = true;
    }

    private void closeDrawer() {
        if (!drawerOpen || drawer == null) return;
        drawerOpen = false;
        final View d = drawer, sc = scrim;
        d.animate().translationX(-d.getWidth()).setDuration(180)
                .withEndAction(() -> {
                    rootFrame.removeView(d);
                    rootFrame.removeView(sc);
                }).start();
        sc.animate().alpha(0f).setDuration(180).start();
    }

    private View buildDrawer() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(24));

        // kepala laci
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.BOTTOM);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4FD1D6, 0xFF00979D, 0xFF006A6E});
        head.setBackground(hg);
        head.setPadding(dp(20), dp(20), dp(20), dp(16));
        head.setMinimumHeight(dp(130));
        ImageView logo = icon(R.drawable.ic_p0, 0xFFFFFFFF);
        head.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView ht = text("Profil Suara", 20, 0xFFFFFFFF, true);
        ht.setPadding(0, dp(10), 0, 0);
        head.addView(ht);
        Profile act = s.profile(s.activeId());
        head.addView(text("Aktif: " + (act == null ? "—" : act.name), 14, 0xDDFFFFFF, false));
        col.addView(head);

        col.addView(drawerItem(R.drawable.ic_list, "Profil", page == PAGE_PROFILES, null, v -> navTo(PAGE_PROFILES)));
        col.addView(divider());
        col.addView(drawerItem(R.drawable.ic_edit, "Edit profil", page == PAGE_MANAGE || page == PAGE_EDIT, null,
                v -> navTo(PAGE_MANAGE)));
        col.addView(drawerItem(R.drawable.ic_tune, "Pengaturan & izin", page == PAGE_SETTINGS, null,
                v -> navTo(PAGE_SETTINGS)));
        col.addView(divider());

        TextView adv = text("Fitur", 16, SUB, false);
        adv.setPadding(dp(20), dp(18), dp(20), dp(6));
        col.addView(adv);
        col.addView(drawerItem(R.drawable.ic_p8, "Jadwal (Scheduler)", page == PAGE_SCHEDULE, s.schedOn(), v -> navTo(PAGE_SCHEDULE)));
        col.addView(drawerItem(R.drawable.ic_stat, "Notifikasi status", false, s.showNotif(), v -> {
            s.setShowNotif(!s.showNotif());
            Engine.refresh(this);
            closeDrawer();
            toast(s.showNotif() ? "Notifikasi status dinyalakan" : "Notifikasi status dimatikan");
        }));
        col.addView(divider());

        TextView more = text("Lainnya", 16, SUB, false);
        more.setPadding(dp(20), dp(18), dp(20), dp(6));
        col.addView(more);
        col.addView(drawerItem(0, "Atur urutan profil", false, null, v -> {
            closeDrawer();
            reorderDialog();
        }));
        col.addView(drawerItem(0, "Ikuti jadwal sekarang", false, null, v -> {
            closeDrawer();
            Engine.followSchedule(this);
            toast("Profil disesuaikan dengan jadwal");
            render(false);
        }));
        col.addView(drawerItem(0, "Tentang", false, null, v -> {
            closeDrawer();
            new AlertDialog.Builder(this).setTitle("Profil Suara")
                    .setMessage("Pengatur profil suara & jadwal harian.\nVersi " + versionName())
                    .setPositiveButton("OK", null).show();
        }));
        return col;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void navTo(int p) {
        closeDrawer();
        editing = null;
        go(p);
    }

    /** Baris menu laci; toggle != null menampilkan lingkaran radio di kanan. */
    private View drawerItem(int iconRes, String label, boolean selected, Boolean toggle, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(16), 0);
        row.setMinimumHeight(dp(54));
        int fg = selected ? 0xFF26C6CC : TXT;
        if (selected) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFF1F4A4C);
            g.setCornerRadius(dp(8));
            row.setBackground(g);
        } else {
            row.setBackgroundResource(android.R.drawable.list_selector_background);
        }
        if (iconRes != 0) {
            row.addView(icon(iconRes, fg), new LinearLayout.LayoutParams(dp(26), dp(26)));
        }
        TextView t = text(label, 17, fg, selected);
        t.setPadding(iconRes != 0 ? dp(24) : 0, 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (toggle != null) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(dp(2), toggle ? 0xFF26C6CC : 0xFFBDBDBD);
            if (toggle) {
                g.setColor(0xFF26C6CC);
                g.setStroke(dp(5), 0xFF2B2B2B);
                LinearLayout ring = new LinearLayout(this);
                GradientDrawable rg = new GradientDrawable();
                rg.setShape(GradientDrawable.OVAL);
                rg.setStroke(dp(2), 0xFF26C6CC);
                ring.setBackground(rg);
                ring.setPadding(dp(2), dp(2), dp(2), dp(2));
                dot.setBackground(g);
                ring.addView(dot, new LinearLayout.LayoutParams(dp(20), dp(20)));
                row.addView(ring, new LinearLayout.LayoutParams(dp(24), dp(24)));
            } else {
                dot.setBackground(g);
                row.addView(dot, new LinearLayout.LayoutParams(dp(24), dp(24)));
            }
        }
        row.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        if (selected) lp.setMargins(dp(8), dp(6), dp(8), dp(6));
        row.setLayoutParams(lp);
        return row;
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
        } else if (page == PAGE_MANAGE) {
            title.setText("Atur profil");
            content.setPadding(0, 0, 0, dp(40));
            renderManage();
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

        int idx = 0;
        for (final Profile p : s.profiles()) {
            boolean on = p.id == active;
            View pv = pill(p, on, on && tu > now ? "sampai " + Engine.fmtTime(tu) : null);
            if (animateIn) {
                pv.setAlpha(0f);
                pv.setTranslationY(dp(18));
                pv.animate().alpha(1f).translationY(0).setStartDelay(60L + idx * 40L).setDuration(240).start();
            }
            content.addView(pv);
            idx++;
        }
        animateIn = false;

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
        if (!policyOk(p)) return;
        Engine.activate(this, p.id, minutes);
        render(false);
        showActivating(p);
    }

    /** Dialog "Mengaktifkan …" dengan lingkaran berputar, lalu kembali ke layar Home. */
    private void showActivating(Profile p) {
        final android.app.Dialog d = new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setCancelable(false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF4A4A4A);
        bg.setCornerRadius(dp(6));
        box.setBackground(bg);
        box.setPadding(dp(22), dp(20), dp(26), dp(20));

        android.widget.ProgressBar pb = new android.widget.ProgressBar(this);
        pb.setIndeterminate(true);
        pb.setIndeterminateTintList(ColorStateList.valueOf(0xFF26C6CC));
        box.addView(pb, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(22), 0, 0, 0);
        texts.addView(text("Mengaktifkan “" + p.name + "”…", 16, 0xFFFFFFFF, false));
        String info = null;
        long now = System.currentTimeMillis();
        if (s.tempUntil() > now) {
            Profile nx = s.profile(s.tempReturn());
            info = "Sampai " + Engine.fmtTime(s.tempUntil()) + (nx != null ? " - lalu " + nx.name : "");
        } else {
            Engine.Occ n = Engine.next(s.schedEntries(), now);
            if (n != null) {
                Calendar k = Calendar.getInstance();
                k.setTimeInMillis(n.time);
                Profile np = s.profile(n.entry.profileId);
                info = "Jadwal: " + DAY_FULL[Engine.dayBit(k)] + "\n" + Engine.fmtTime(n.time)
                        + " - " + (np == null ? "?" : np.name);
            }
        }
        if (info != null) texts.addView(text(info, 14, 0xFFDDDDDD, false));
        box.addView(texts);

        d.setContentView(box);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            d.getWindow().setDimAmount(0.55f);
            d.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.82), WRAP);
        }
        box.setAlpha(0f);
        box.setScaleX(0.9f);
        box.setScaleY(0.9f);
        d.show();
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();

        final boolean home = s.goHome();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            box.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                try {
                    d.dismiss();
                } catch (Exception ignored) {
                }
                if (home) moveTaskToBack(true);
            }).start();
        }, home ? 1300 : 900);
    }

    /** Jendela profil sementara ala Sound Profile: pilih jam selesai + profil berikutnya. */
    private void durationDialog(final Profile p) {
        if (!policyOk(p)) return;
        final android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        FrameLayout frame = new FrameLayout(this);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(0xFF5E5E5E);
        cg.setCornerRadius(dp(16));
        card.setBackground(cg);
        card.setPadding(dp(20), dp(56), dp(20), dp(20));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(MATCH, WRAP);
        clp.topMargin = dp(44);
        frame.addView(card, clp);

        // garis toska di atas kartu
        View strip = new View(this);
        strip.setBackgroundColor(ACC);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(MATCH, dp(8));
        slp.topMargin = dp(44);
        frame.addView(strip, slp);

        // lingkaran ikon profil
        ImageView circle = icon(p.iconRes(), 0xFFFFFFFF);
        GradientDrawable og = new GradientDrawable();
        og.setShape(GradientDrawable.OVAL);
        og.setColor(ACC);
        circle.setBackground(og);
        circle.setPadding(dp(22), dp(22), dp(22), dp(22));
        frame.addView(circle, new FrameLayout.LayoutParams(dp(90), dp(90), Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        TextView head = text("Profil:  " + p.name, 24, 0xFF26C6CC, true);
        card.addView(head);
        card.addView(text("Pilih jam selesai untuk profil ini", 16, 0xFFEEEEEE, false));

        // pemilih jam
        LinearLayout pick = new LinearLayout(this);
        pick.setOrientation(LinearLayout.HORIZONTAL);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setPadding(0, dp(10), 0, dp(10));
        final android.widget.NumberPicker hp = new android.widget.NumberPicker(this);
        hp.setMinValue(0);
        hp.setMaxValue(23);
        hp.setFormatter(v -> String.format(java.util.Locale.US, "%02d", v));
        final android.widget.NumberPicker mp = new android.widget.NumberPicker(this);
        mp.setMinValue(0);
        mp.setMaxValue(59);
        mp.setFormatter(v -> String.format(java.util.Locale.US, "%02d", v));
        Calendar k = Calendar.getInstance();
        k.add(Calendar.MINUTE, 30);
        hp.setValue(k.get(Calendar.HOUR_OF_DAY));
        mp.setValue(k.get(Calendar.MINUTE));
        pick.addView(hp, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView colon = text(":", 22, 0xFFEEEEEE, false);
        colon.setPadding(dp(6), 0, dp(6), 0);
        pick.addView(colon);
        pick.addView(mp, new LinearLayout.LayoutParams(0, WRAP, 1f));

        LinearLayout plus = new LinearLayout(this);
        plus.setOrientation(LinearLayout.VERTICAL);
        plus.setPadding(dp(10), 0, 0, 0);
        int[] adds = {10, 30};
        for (final int a : adds) {
            TextView b = text("+" + a + "'", 18, 0xFFFFFFFF, true);
            b.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ACC);
            bg.setCornerRadius(dp(26));
            b.setBackground(bg);
            b.setElevation(dp(3));
            b.setOnClickListener(v -> {
                int total = hp.getValue() * 60 + mp.getValue() + a;
                total %= 24 * 60;
                hp.setValue(total / 60);
                mp.setValue(total % 60);
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(80), dp(50));
            blp.setMargins(0, dp(6), 0, dp(6));
            plus.addView(b, blp);
        }
        pick.addView(plus);
        card.addView(pick);

        card.addView(text("Setelah jam ini tercapai, profil berikut akan otomatis aktif:", 15, 0xFFEEEEEE, false));

        // profil berikutnya (default: profil aktif sebelumnya / sesuai jadwal)
        final List<Profile> ps = s.profiles();
        long defNext = s.activeId();
        if (defNext == p.id) {
            Profile sch = Engine.scheduledAt(s, k.getTimeInMillis());
            defNext = sch != null ? sch.id : (ps.isEmpty() ? -1 : ps.get(0).id);
        }
        if (s.tempUntil() > System.currentTimeMillis() && s.profile(s.tempReturn()) != null)
            defNext = s.tempReturn();
        int sel = 0;
        for (int i = 0; i < ps.size(); i++) if (ps.get(i).id == defNext) sel = i;
        final Spinner nextSp = new Spinner(this);
        nextSp.setAdapter(new ArrayAdapter<Profile>(this, android.R.layout.simple_spinner_dropdown_item, ps) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                return deco((TextView) super.getView(pos, cv, parent), getItem(pos));
            }

            @Override
            public View getDropDownView(int pos, View cv, ViewGroup parent) {
                return deco((TextView) super.getDropDownView(pos, cv, parent), getItem(pos));
            }

            private TextView deco(TextView tv, Profile pr) {
                tv.setText(pr.name);
                tv.setTextSize(18);
                android.graphics.drawable.Drawable d = getDrawable(pr.iconRes()).mutate();
                d.setTint(0xFFDDDDDD);
                d.setBounds(0, 0, dp(22), dp(22));
                tv.setCompoundDrawablesRelative(d, null, null, null);
                tv.setCompoundDrawablePadding(dp(14));
                return tv;
            }
        });
        nextSp.setSelection(sel);
        GradientDrawable sg = new GradientDrawable();
        sg.setStroke(dp(2), 0xFF2A2A2A);
        sg.setCornerRadius(dp(6));
        nextSp.setBackground(sg);
        nextSp.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(MATCH, dp(58));
        nlp.setMargins(dp(4), dp(14), dp(4), dp(20));
        card.addView(nextSp, nlp);

        TextView go = text("AKTIFKAN", 18, 0xFFFFFFFF, true);
        go.setGravity(Gravity.CENTER);
        go.setBackgroundColor(ACC);
        go.setOnClickListener(v -> {
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, hp.getValue());
            t.set(Calendar.MINUTE, mp.getValue());
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            long until = t.getTimeInMillis();
            if (until <= System.currentTimeMillis() + 30000) until += 24L * 3600000L;
            Profile nx = ps.isEmpty() ? null : ps.get(Math.max(0, nextSp.getSelectedItemPosition()));
            Engine.activateUntil(this, p.id, until, nx == null ? -1 : nx.id);
            dlg.dismiss();
            render(false);
            showActivating(p);
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(MATCH, dp(54));
        glp.setMargins(dp(24), 0, dp(24), 0);
        card.addView(go, glp);

        ScrollView sv = new ScrollView(this);
        sv.addView(frame);
        dlg.setContentView(sv);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            dlg.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9), WRAP);
        }
        dlg.show();
    }

    private boolean policyOk(Profile p) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!p.needsPolicy() || nm.isNotificationPolicyAccessGranted()) return true;
        new AlertDialog.Builder(this)
                .setTitle("Butuh izin Jangan Ganggu")
                .setMessage("Profil “" + p.name + "” memakai mode Senyap/Jangan Ganggu. "
                        + "Berikan akses Jangan Ganggu untuk Profil Suara dulu.")
                .setNegativeButton("Nanti", null)
                .setPositiveButton("Buka pengaturan", (d, w) ->
                        open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                .show();
        return false;
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
        editReturn = page == PAGE_EDIT ? editReturn : page;
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
        go(editReturn);
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

    /** Halaman pengaturan profil ala Sound Profile: ringkasan per bagian, ketuk untuk mengubah. */
    private void renderEditor() {
        final Profile p = editing;

        // --- nama & ikon ---
        LinearLayout head = listRow();
        head.addView(icon(p.iconRes(), 0xFFBDBDBD), new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView nm = text(p.name == null || p.name.isEmpty() ? "(ketuk untuk memberi nama)" : p.name, 20, TXT, false);
        nm.setPadding(dp(30), 0, 0, 0);
        head.addView(nm, new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.setOnClickListener(v -> nameIconDialog());
        content.addView(head);

        // --- volume ---
        StringBuilder vb = new StringBuilder("Mode dering: " + p.modeLabel());
        String[] vl = {"Panggilan", "Notifikasi", "Multimedia", "Alarm", "Sistem"};
        int[] vv = p.values();
        boolean normal = p.mode == Profile.NORMAL;
        for (int i = 0; i < 5; i++) {
            vb.append('\n').append(vl[i]).append(": ");
            if (!normal && (i == 0 || i == 1 || i == 4)) vb.append("mati (").append(p.modeLabel().toLowerCase()).append(')');
            else vb.append(vv[i] >= 0 ? vv[i] + "%" : "tidak diubah");
        }
        content.addView(summarySection("Volume", vb.toString(), null, v -> volumeDialog()));

        // --- getaran ---
        String[] tri = {"Tidak diubah", "Tidak", "Ya"};
        String vib = "Getar saat ada panggilan: " + tri[p.vibRing + 1]
                + "\nGetaran sentuh (touch feedback): " + tri[p.haptic + 1];
        boolean vibWarn = (p.vibRing >= 0 || p.haptic >= 0) && !Settings.System.canWrite(this);
        content.addView(summarySection("Getaran", vib,
                vibWarn ? "Perlu izin “Ubah pengaturan sistem” — ketuk di sini" : null, v -> vibrationDialog()));

        // --- jangan ganggu ---
        String dnd;
        if (p.dnd == Profile.DND_OFF) {
            dnd = "Mode Jangan Ganggu tidak diaktifkan untuk profil ini";
        } else {
            dnd = p.dndLabel();
            if (p.dnd == Profile.DND_PRIORITY && p.repeat) dnd += "\nPenelepon berulang diizinkan";
        }
        NotificationManager nmgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean dndWarn = p.needsPolicy() && !nmgr.isNotificationPolicyAccessGranted();
        content.addView(summarySection("Mode Jangan Ganggu", dnd,
                dndWarn ? "Perlu akses Jangan Ganggu — ketuk di sini" : null, v -> dndDialog()));

        // --- nada dering ---
        String tones;
        if (p.ringtone.isEmpty() && p.notifTone.isEmpty()) {
            tones = "Profil ini tidak mengubah nada dering";
        } else {
            tones = "Nada panggilan: " + toneTitle(p.ringtone) + "\nSuara notifikasi: " + toneTitle(p.notifTone);
        }
        boolean toneWarn = (!p.ringtone.isEmpty() || !p.notifTone.isEmpty()) && !Settings.System.canWrite(this);
        content.addView(summarySection("Nada dering", tones,
                toneWarn ? "Perlu izin “Ubah pengaturan sistem” — ketuk di sini" : null, v -> toneDialog()));

        // --- hapus ---
        if (!editingNew) {
            content.addView(divider());
            LinearLayout del = listRow();
            del.addView(icon(R.drawable.ic_delete, WARN), new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView dt = text("Hapus profil ini", 17, WARN, false);
            dt.setPadding(dp(30), 0, 0, 0);
            del.addView(dt);
            del.setOnClickListener(v -> deleteProfile(p));
            content.addView(del);
        }
        TextView hint = text("Ketuk tiap bagian untuk mengubah, lalu ketuk ✓ di kanan atas untuk menyimpan.", 13, SUB, false);
        hint.setPadding(dp(20), dp(20), dp(20), 0);
        content.addView(hint);
    }

    /** Satu bagian: judul toska + ringkasan menjorok + (opsional) peringatan kuning. */
    private View summarySection(String title, String body, String warning, View.OnClickListener l) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setBackgroundResource(android.R.drawable.list_selector_background);
        sec.setPadding(dp(20), dp(22), dp(20), dp(18));
        TextView h = text(title, 18, ACC_TXT, false);
        h.setPadding(0, 0, 0, dp(14));
        sec.addView(h);
        TextView b = text(body, 17, TXT, false);
        b.setLineSpacing(dp(2), 1f);
        b.setPadding(dp(60), 0, 0, 0);
        sec.addView(b);
        if (warning != null) {
            TextView w = text(warning, 16, 0xFFFFFFFF, false);
            w.setBackgroundColor(YELLOW);
            w.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.setMargins(dp(60), dp(14), 0, 0);
            sec.addView(w, lp);
            w.setOnClickListener(v -> {
                if (title.startsWith("Mode Jangan"))
                    open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                else
                    open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
            });
        }
        sec.setOnClickListener(l);
        return sec;
    }

    private LinearLayout dialogBody() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(22), dp(8), dp(22), dp(8));
        return v;
    }

    private void showSection(String title, View body, Runnable onOk) {
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        new AlertDialog.Builder(this).setTitle(title).setView(sv)
                .setNegativeButton("Batal", null)
                .setPositiveButton("OK", (d, w) -> {
                    onOk.run();
                    render(false);
                }).show();
    }

    private void nameIconDialog() {
        final Profile p = editing;
        LinearLayout v = dialogBody();
        final EditText name = new EditText(this);
        name.setHint("Nama profil (mis. Kerja)");
        name.setText(p.name);
        name.setSingleLine();
        v.addView(name);
        v.addView(label("Ikon"));
        final int[] sel = {p.icon};
        final ImageView[] icons = new ImageView[Profile.ICONS.length];
        final Runnable paint = () -> {
            for (int i = 0; i < icons.length; i++) {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(i == sel[0] ? ACC : CHIP_OFF);
                icons[i].setBackground(g);
            }
        };
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(5);
        for (int i = 0; i < icons.length; i++) {
            final int idx = i;
            ImageView iv = icon(Profile.ICONS[i], 0xFFFFFFFF);
            iv.setPadding(dp(10), dp(10), dp(10), dp(10));
            iv.setOnClickListener(x -> {
                sel[0] = idx;
                paint.run();
            });
            icons[i] = iv;
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = dp(46);
            lp.height = dp(46);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(iv, lp);
        }
        paint.run();
        v.addView(grid);
        showSection("Nama & ikon", v, () -> {
            p.name = name.getText().toString().trim();
            p.icon = sel[0];
            title.setText(p.name.isEmpty() ? "Profil baru" : p.name);
        });
    }

    private void volumeDialog() {
        final Profile p = editing;
        LinearLayout v = dialogBody();
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
        note.setText("Panggilan, notifikasi & sistem mati saat mode Getar/Senyap.");
        note.setTextSize(12);
        v.addView(note);
        v.addView(label("Volume (hapus centang = tidak diubah)"));

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
            for (int i = 0; i < 5; i++) {
                boolean allowed = normal || (i == 2 || i == 3);
                cbs[i].setEnabled(allowed);
                bars[i].setEnabled(allowed && cbs[i].isChecked());
            }
            note.setVisibility(normal ? View.GONE : View.VISIBLE);
        };
        rg.setOnCheckedChangeListener((g, id) -> upd.run());
        upd.run();

        showSection("Volume", v, () -> {
            int m = rg.getCheckedRadioButtonId() - 1000;
            p.mode = (m < 0 || m > 2) ? Profile.NORMAL : m;
            int[] nv = new int[5];
            for (int i = 0; i < 5; i++) nv[i] = cbs[i].isChecked() ? bars[i].getProgress() : -1;
            p.setValues(nv);
        });
    }

    private void vibrationDialog() {
        final Profile p = editing;
        LinearLayout v = dialogBody();
        String[] tri = {"Tidak diubah", "Tidak", "Ya"};
        v.addView(label("Getar saat ada panggilan"));
        final Spinner vr = spinner(tri, p.vibRing + 1);
        v.addView(vr);
        v.addView(label("Getaran sentuh (touch feedback)"));
        final Spinner hp = spinner(tri, p.haptic + 1);
        v.addView(hp);
        TextView n = new TextView(this);
        n.setText("Butuh izin “Ubah pengaturan sistem”. Di beberapa HP Samsung, getar saat panggilan dikunci oleh sistem.");
        n.setTextSize(12);
        n.setPadding(0, dp(12), 0, 0);
        v.addView(n);
        showSection("Getaran", v, () -> {
            p.vibRing = Math.max(0, vr.getSelectedItemPosition()) - 1;
            p.haptic = Math.max(0, hp.getSelectedItemPosition()) - 1;
        });
    }

    private void dndDialog() {
        final Profile p = editing;
        LinearLayout v = dialogBody();
        v.addView(label("Mode Jangan Ganggu (berlaku untuk seluruh HP)"));
        final Spinner dndSp = spinner(new String[]{"Tidak diaktifkan", "Prioritas (pilih siapa yang boleh)",
                "Hanya alarm", "Senyap total"}, p.dnd);
        v.addView(dndSp);
        final LinearLayout prio = new LinearLayout(this);
        prio.setOrientation(LinearLayout.VERTICAL);
        prio.addView(label("Panggilan yang boleh berdering"));
        final Spinner callSp = spinner(new String[]{"Semua orang", "Kontak saja",
                "Kontak favorit (berbintang) saja", "Tidak ada"}, p.callers);
        prio.addView(callSp);
        final CheckBox rep = new CheckBox(this);
        rep.setText("Izinkan penelepon berulang (menelepon 2x dalam 15 menit)");
        rep.setChecked(p.repeat);
        prio.addView(rep);
        v.addView(prio);
        dndSp.setOnItemSelectedListener(onSelect(pos ->
                prio.setVisibility(pos == Profile.DND_PRIORITY ? View.VISIBLE : View.GONE)));
        prio.setVisibility(p.dnd == Profile.DND_PRIORITY ? View.VISIBLE : View.GONE);
        showSection("Mode Jangan Ganggu", v, () -> {
            p.dnd = Math.max(0, dndSp.getSelectedItemPosition());
            p.callers = Math.max(0, callSp.getSelectedItemPosition());
            p.repeat = rep.isChecked();
        });
    }

    private void toneDialog() {
        final Profile p = editing;
        String[] items = {"Nada panggilan: " + toneTitle(p.ringtone),
                "Suara notifikasi: " + toneTitle(p.notifTone)};
        new AlertDialog.Builder(this).setTitle("Nada dering").setItems(items, (d, w) -> {
            if (w == 0) chooseTone("Nada panggilan", RingtoneManager.TYPE_RINGTONE, REQ_RINGTONE);
            else chooseTone("Suara notifikasi", RingtoneManager.TYPE_NOTIFICATION, REQ_NOTIF_TONE);
        }).setNegativeButton("Tutup", null).show();
    }

    private void chooseTone(final String label, final int type, final int req) {
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
    }

    // ================= halaman atur profil =================
    private void renderManage() {
        TextView info = text("Pilih profil yang ingin diatur.", 14, SUB, false);
        info.setPadding(dp(20), dp(14), dp(20), dp(6));
        content.addView(info);
        for (final Profile p : s.profiles()) {
            content.addView(divider());
            LinearLayout row = listRow();
            row.addView(icon(p.iconRes(), 0xFFBDBDBD), new LinearLayout.LayoutParams(dp(30), dp(30)));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(dp(24), 0, 0, 0);
            mid.addView(text(p.name, 19, TXT, false));
            mid.addView(text(p.summary(), 13, SUB, false));
            row.addView(mid, new LinearLayout.LayoutParams(0, WRAP, 1f));
            row.setOnClickListener(v -> openEditor(p));
            content.addView(row);
        }
        content.addView(divider());
        LinearLayout add = listRow();
        add.addView(icon(R.drawable.ic_add, ACC_TXT), new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView at = text("Tambah profil baru", 18, ACC_TXT, false);
        at.setPadding(dp(24), 0, 0, 0);
        add.addView(at);
        add.setOnClickListener(v -> openEditor(null));
        content.addView(add);
        content.addView(divider());
        LinearLayout ord = listRow();
        ord.addView(icon(R.drawable.ic_menu, 0xFFBDBDBD), new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView ot = text("Atur urutan profil", 18, TXT, false);
        ot.setPadding(dp(24), 0, 0, 0);
        ord.addView(ot);
        ord.setOnClickListener(v -> reorderDialog());
        content.addView(ord);
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
        Switch gh = new Switch(this);
        gh.setText("Kembali ke Home setelah mengaktifkan profil");
        gh.setTextColor(TXT);
        gh.setTextSize(15);
        gh.setPadding(0, dp(12), 0, 0);
        gh.setChecked(s.goHome());
        gh.setOnCheckedChangeListener((bt, checked) -> s.setGoHome(checked));
        set.addView(gh);
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
