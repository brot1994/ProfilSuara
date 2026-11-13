package id.bram.volumemelayang;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF3D7BFF;
    private SharedPreferences sp;
    private float dens;
    private Button btnOverlay, btnNotif, btnPolicy, btnStart;
    private final Handler h = new Handler(Looper.getMainLooper());

    private int dp(float v) { return Math.round(v * dens); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dens = getResources().getDisplayMetrics().density;
        sp = FloatService.prefs(this);
        getWindow().setStatusBarColor(0xFF101216);
        getWindow().setNavigationBarColor(0xFF101216);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101216);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        scroll.addView(root);

        TextView title = text("Volume Melayang", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView sub = text("Tombol volume yang melayang di atas semua aplikasi.", 14, 0xAAFFFFFF);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        // --- izin ---
        LinearLayout perm = card(root, "Izin");
        btnOverlay = button("Tampil di atas aplikasi lain", false);
        btnOverlay.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        perm.addView(btnOverlay);
        if (Build.VERSION.SDK_INT >= 33) {
            btnNotif = button("Notifikasi (untuk memunculkan tombol)", false);
            btnNotif.setOnClickListener(v -> requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
            perm.addView(btnNotif);
        }
        btnPolicy = button("Akses Jangan Ganggu (untuk mode Senyap)", false);
        btnPolicy.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        perm.addView(btnPolicy);

        // --- tombol utama ---
        btnStart = button("", true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        slp.topMargin = dp(4);
        slp.bottomMargin = dp(16);
        btnStart.setLayoutParams(slp);
        btnStart.setOnClickListener(v -> toggleService());
        root.addView(btnStart);

        // --- tampilan tombol ---
        LinearLayout look = card(root, "Tombol melayang");
        final TextView sizeLbl = text("", 14, Color.WHITE);
        look.addView(sizeLbl);
        SeekBar sizeBar = seek(80 - 36, sp.getInt("size", 52) - 36);
        sizeLbl.setText("Ukuran: " + sp.getInt("size", 52) + " dp");
        sizeBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                sizeLbl.setText("Ukuran: " + (p + 36) + " dp");
            }
            @Override public void onStopTrackingTouch(SeekBar s) {
                sp.edit().putInt("size", s.getProgress() + 36).apply();
                refreshService();
            }
        });
        look.addView(sizeBar);

        final TextView alphaLbl = text("", 14, Color.WHITE);
        alphaLbl.setPadding(0, dp(10), 0, 0);
        look.addView(alphaLbl);
        SeekBar alphaBar = seek(100 - 20, sp.getInt("alpha", 70) - 20);
        alphaLbl.setText("Kepekatan saat diam: " + sp.getInt("alpha", 70) + "%");
        alphaBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                alphaLbl.setText("Kepekatan saat diam: " + (p + 20) + "%");
            }
            @Override public void onStopTrackingTouch(SeekBar s) {
                sp.edit().putInt("alpha", s.getProgress() + 20).apply();
                refreshService();
            }
        });
        look.addView(alphaBar);

        CheckBox lock = new CheckBox(this);
        lock.setText("Kunci posisi tombol");
        lock.setTextColor(Color.WHITE);
        lock.setButtonTintList(ColorStateList.valueOf(ACCENT));
        lock.setChecked(sp.getBoolean("locked", false));
        lock.setOnCheckedChangeListener((c, on) -> sp.edit().putBoolean("locked", on).apply());
        look.addView(lock);

        // --- panel ---
        LinearLayout pan = card(root, "Panel volume");
        pan.addView(text("Slider utama saat tombol diketuk:", 14, 0xCCFFFFFF));
        RadioGroup rg = new RadioGroup(this);
        int main = sp.getInt("main", 0);
        for (int i = 0; i < FloatService.NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(FloatService.NAMES[i]);
            rb.setTextColor(Color.WHITE);
            rb.setButtonTintList(ColorStateList.valueOf(ACCENT));
            rg.addView(rb);
            if (i == main) rb.setChecked(true);
        }
        rg.setOnCheckedChangeListener((g, id) -> {
            sp.edit().putInt("main", id - 1000).apply();
            refreshService();
        });
        pan.addView(rg);

        CheckBox exp = new CheckBox(this);
        exp.setText("Langsung tampilkan semua slider");
        exp.setTextColor(Color.WHITE);
        exp.setButtonTintList(ColorStateList.valueOf(ACCENT));
        exp.setChecked(sp.getBoolean("expanded", false));
        exp.setOnCheckedChangeListener((c, on) -> sp.edit().putBoolean("expanded", on).apply());
        pan.addView(exp);

        // --- petunjuk ---
        LinearLayout tips = card(root, "Cara pakai");
        tips.addView(text(
                "• Ketuk tombol → panel slider volume (slider utama).\n"
                        + "• Tombol ⋯ di panel → tampilkan semua slider (Media, Dering, Notifikasi, Alarm, Sistem).\n"
                        + "• Ikon lonceng di panel → ganti mode Dering / Getar / Senyap.\n"
                        + "• Tekan lama tombol → menu: mode dering, kunci posisi, sembunyikan, sembunyikan 10 detik.\n"
                        + "• Geser tombol → otomatis menempel ke tepi layar.\n"
                        + "• Tombol tersembunyi dimunculkan lagi dari notifikasi, atau dari tile \"Volume Melayang\" di Quick Settings.",
                14, 0xCCFFFFFF));

        setContentView(scroll);

        if (getIntent() != null && getIntent().getBooleanExtra("autostart", false)
                && Settings.canDrawOverlays(this) && !FloatService.running) {
            startFloat();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        updateUi();
    }

    private void updateUi() {
        boolean overlay = Settings.canDrawOverlays(this);
        mark(btnOverlay, "Tampil di atas aplikasi lain", overlay);
        if (btnNotif != null) {
            mark(btnNotif, "Notifikasi (untuk memunculkan tombol)",
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        mark(btnPolicy, "Akses Jangan Ganggu (untuk mode Senyap)", nm.isNotificationPolicyAccessGranted());
        btnStart.setText(FloatService.running ? "Matikan tombol melayang" : "Tampilkan tombol melayang");
        ((GradientDrawable) btnStart.getBackground()).setColor(FloatService.running ? 0xFF3A3F4B : ACCENT);
    }

    private void mark(Button b, String label, boolean ok) {
        b.setText((ok ? "✓  " : "○  ") + label);
        b.setTextColor(ok ? 0xFF7CE3A1 : Color.WHITE);
        b.setEnabled(true);
    }

    private void toggleService() {
        if (FloatService.running) {
            startService(new Intent(this, FloatService.class).setAction(FloatService.ACTION_STOP));
        } else {
            startFloat();
        }
        h.postDelayed(this::updateUi, 400);
    }

    private void startFloat() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan dulu \"Tampil di atas aplikasi lain\"", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        startForegroundService(new Intent(this, FloatService.class));
        h.postDelayed(this::updateUi, 400);
    }

    private void refreshService() {
        if (FloatService.running) {
            startService(new Intent(this, FloatService.class).setAction(FloatService.ACTION_REFRESH));
        }
    }

    // ---------- pembantu UI ----------
    private TextView text(String s, float sz, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sz);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    private LinearLayout card(LinearLayout root, String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1B1E25);
        g.setCornerRadius(dp(18));
        c.setBackground(g);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        root.addView(c, lp);
        TextView hd = text(heading, 13, ACCENT);
        hd.setTypeface(Typeface.DEFAULT_BOLD);
        hd.setPadding(0, 0, 0, dp(8));
        c.addView(hd);
        return c;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(primary ? 17 : 14);
        b.setTextColor(Color.WHITE);
        b.setGravity(primary ? Gravity.CENTER : Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? ACCENT : 0xFF262A33);
        g.setCornerRadius(dp(primary ? 28 : 12));
        b.setBackground(g);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private SeekBar seek(int max, int progress) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(progress);
        s.setProgressTintList(ColorStateList.valueOf(ACCENT));
        s.setThumbTintList(ColorStateList.valueOf(ACCENT));
        s.setPadding(dp(8), dp(12), dp(8), dp(12));
        return s;
    }

    abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onProgressChanged(SeekBar s, int p, boolean u) { }
        @Override public void onStartTrackingTouch(SeekBar s) { }
        @Override public void onStopTrackingTouch(SeekBar s) { }
    }
}
