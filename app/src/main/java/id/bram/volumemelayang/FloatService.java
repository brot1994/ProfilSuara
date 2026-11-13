package id.bram.volumemelayang;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatService extends Service {

    public static final String ACTION_TOGGLE = "id.bram.volumemelayang.TOGGLE";
    public static final String ACTION_STOP = "id.bram.volumemelayang.STOP";
    public static final String ACTION_REFRESH = "id.bram.volumemelayang.REFRESH";

    public static volatile boolean running = false;
    public static volatile boolean hidden = false;

    // urutan: Media, Dering, Notifikasi, Alarm, Sistem
    public static final int[] STREAMS = {
            AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM, AudioManager.STREAM_SYSTEM};
    public static final String[] NAMES = {"Media", "Dering", "Notifikasi", "Alarm", "Sistem"};
    public static final int[] ICONS = {
            R.drawable.ic_music, R.drawable.ic_phone, R.drawable.ic_bell,
            R.drawable.ic_alarm, R.drawable.ic_tune};
    private static final int ACCENT = 0xFF3D7BFF;

    private static final String CH = "float";
    private static final int NOTIF_ID = 7;

    private WindowManager wm;
    private AudioManager am;
    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private float dens;

    // tombol
    private ImageView bubble;
    private WindowManager.LayoutParams bp;
    private boolean bubbleAdded = false;
    private int size;

    // panel volume
    private TouchFrame panel;
    private WindowManager.LayoutParams pp;
    private boolean panelShown = false;
    private boolean expanded = false;
    private VSlider[] sliders;
    private LinearLayout[] cols;
    private ImageView modeBtn, expandBtn;

    // menu tekan lama
    private TouchFrame menu;
    private boolean menuShown = false;

    private long lastOutside = 0;
    private BroadcastReceiver volRx;

    // ---------- pengaturan ----------
    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("vm", MODE_PRIVATE);
    }

    private int dp(float v) { return Math.round(v * dens); }

    // ---------- siklus hidup ----------
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        hidden = false;
        dens = getResources().getDisplayMetrics().density;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        sp = prefs(this);

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH, "Tombol volume melayang",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        Notification n = buildNotif();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        volRx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (panelShown) refreshPanel();
            }
        };
        IntentFilter f = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(volRx, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(volRx, f);
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izin tampil di atas aplikasi lain belum diberikan", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        createBubble();
        updateTile();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(a)) {
            stopSelf();
            return START_NOT_STICKY;
        } else if (ACTION_TOGGLE.equals(a)) {
            setHidden(!hidden);
        } else if (ACTION_REFRESH.equals(a)) {
            applyPrefs();
        } else if (hidden) {
            setHidden(false);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        hidden = false;
        h.removeCallbacksAndMessages(null);
        closePanel();
        closeMenu();
        if (bubbleAdded) {
            try { wm.removeView(bubble); } catch (Exception ignored) { }
            bubbleAdded = false;
        }
        try { unregisterReceiver(volRx); } catch (Exception ignored) { }
        updateTile();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        closePanel();
        closeMenu();
        if (bubbleAdded) {
            h.postDelayed(this::snapToEdge, 300);
        }
    }

    // ---------- notifikasi & tile ----------
    private Notification buildNotif() {
        int fl = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent toggle = PendingIntent.getService(this, 1,
                new Intent(this, FloatService.class).setAction(ACTION_TOGGLE), fl);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, FloatService.class).setAction(ACTION_STOP), fl);
        PendingIntent open = PendingIntent.getActivity(this, 3,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), fl);

        return new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_volume)
                .setContentTitle(hidden ? "Tombol volume disembunyikan" : "Tombol volume aktif")
                .setContentText(hidden ? "Ketuk untuk menampilkan tombol" : "Ketuk untuk menyembunyikan tombol")
                .setContentIntent(toggle)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_hide),
                        hidden ? "Tampilkan" : "Sembunyikan", toggle).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_tune), "Pengaturan", open).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_close), "Matikan", stop).build())
                .build();
    }

    private void updateNotif() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotif());
    }

    private void updateTile() {
        try {
            TileService.requestListeningState(this, new ComponentName(this, FloatTile.class));
        } catch (Exception ignored) { }
    }

    // ---------- ukuran layar ----------
    private Point screen() {
        Point p = new Point();
        if (Build.VERSION.SDK_INT >= 30) {
            Rect b = wm.getCurrentWindowMetrics().getBounds();
            p.set(b.width(), b.height());
        } else {
            wm.getDefaultDisplay().getRealSize(p);
        }
        return p;
    }

    private int overlayType() {
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    // ---------- tombol melayang ----------
    private void createBubble() {
        size = dp(sp.getInt("size", 52));
        bubble = new ImageView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xE61C1F26);
        bg.setStroke(dp(1.5f), 0x55FFFFFF);
        bubble.setBackground(bg);
        bubble.setImageResource(R.drawable.ic_volume);
        bubble.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Math.round(size * 0.24f);
        bubble.setPadding(pad, pad, pad, pad);
        bubble.setAlpha(idleAlpha());

        Point s = screen();
        bp = new WindowManager.LayoutParams(size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        bp.gravity = Gravity.TOP | Gravity.LEFT;
        bp.x = sp.getInt("x", s.x - size);
        bp.y = sp.getInt("y", s.y / 3);
        clampBubble();

        bubble.setOnTouchListener(new BubbleTouch());
        wm.addView(bubble, bp);
        bubbleAdded = true;
    }

    private float idleAlpha() {
        return sp.getInt("alpha", 70) / 100f;
    }

    private void clampBubble() {
        Point s = screen();
        bp.x = Math.max(0, Math.min(s.x - size, bp.x));
        bp.y = Math.max(0, Math.min(s.y - size, bp.y));
    }

    private final Runnable dimRunnable = () -> {
        if (bubble != null) bubble.animate().alpha(idleAlpha()).setDuration(300).start();
    };

    private void wake() {
        h.removeCallbacks(dimRunnable);
        if (bubble != null) {
            bubble.animate().cancel();
            bubble.setAlpha(1f);
        }
    }

    private void scheduleDim() {
        h.removeCallbacks(dimRunnable);
        h.postDelayed(dimRunnable, 1500);
    }

    private void snapToEdge() {
        if (!bubbleAdded) return;
        clampBubble();
        Point s = screen();
        int target = (bp.x + size / 2 < s.x / 2) ? 0 : s.x - size;
        ValueAnimator va = ValueAnimator.ofInt(bp.x, target);
        va.setDuration(220);
        va.addUpdateListener(an -> {
            if (!bubbleAdded) return;
            bp.x = (int) an.getAnimatedValue();
            try { wm.updateViewLayout(bubble, bp); } catch (Exception ignored) { }
        });
        va.start();
        sp.edit().putInt("x", target).putInt("y", bp.y).apply();
    }

    private boolean onRightSide() {
        return bp.x + size / 2 >= screen().x / 2;
    }

    private class BubbleTouch implements View.OnTouchListener {
        float downX, downY;
        int startX, startY;
        boolean moved, longFired;
        final int slop = ViewConfiguration.get(FloatService.this).getScaledTouchSlop();
        final Runnable longPress = () -> {
            longFired = true;
            bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            closePanel();
            showMenu();
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = bp.x;
                    startY = bp.y;
                    moved = false;
                    longFired = false;
                    wake();
                    h.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!moved && Math.hypot(dx, dy) > slop) {
                        moved = true;
                        h.removeCallbacks(longPress);
                        if (!longFired) closePanel();
                    }
                    if (moved && !longFired && !sp.getBoolean("locked", false)) {
                        bp.x = startX + (int) dx;
                        bp.y = startY + (int) dy;
                        clampBubble();
                        try { wm.updateViewLayout(bubble, bp); } catch (Exception ignored) { }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    h.removeCallbacks(longPress);
                    if (!longFired) {
                        if (moved) {
                            if (!sp.getBoolean("locked", false)) snapToEdge();
                        } else {
                            togglePanel();
                        }
                    }
                    if (!panelShown && !menuShown) scheduleDim();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacks(longPress);
                    scheduleDim();
                    return true;
            }
            return false;
        }
    }

    private void setHidden(boolean hide) {
        h.removeCallbacks(unhideRunnable);
        closePanel();
        closeMenu();
        hidden = hide;
        if (hide) {
            if (bubbleAdded) {
                try { wm.removeView(bubble); } catch (Exception ignored) { }
                bubbleAdded = false;
            }
        } else if (!bubbleAdded && bubble != null) {
            clampBubble();
            try {
                wm.addView(bubble, bp);
                bubbleAdded = true;
            } catch (Exception ignored) { }
            bubble.setAlpha(1f);
            scheduleDim();
        } else if (bubble == null) {
            createBubble();
        }
        updateNotif();
        updateTile();
    }

    private final Runnable unhideRunnable = () -> setHidden(false);

    private void applyPrefs() {
        boolean wasHidden = hidden;
        closePanel();
        closeMenu();
        panel = null; // dibangun ulang dengan pengaturan baru
        if (bubbleAdded) {
            try { wm.removeView(bubble); } catch (Exception ignored) { }
            bubbleAdded = false;
        }
        int oldX = bp != null ? bp.x : -1;
        boolean right = bp != null && onRightSide();
        createBubble();
        if (oldX >= 0) {
            bp.x = right ? screen().x - size : 0;
            clampBubble();
            wm.updateViewLayout(bubble, bp);
        }
        if (wasHidden) {
            hidden = false;
            setHidden(true);
        }
    }

    // ---------- panel volume ----------
    private final Runnable autoClose = this::closePanel;

    private void kickAutoClose() {
        h.removeCallbacks(autoClose);
        h.postDelayed(autoClose, 4000);
    }

    private void togglePanel() {
        if (panelShown) {
            closePanel();
            return;
        }
        if (SystemClock.uptimeMillis() - lastOutside < 700) return; // baru saja ditutup oleh ketukan ini
        showPanel();
    }

    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF2202329);
        g.setCornerRadius(dp(26));
        g.setStroke(dp(1), 0x22FFFFFF);
        return g;
    }

    private void buildPanel() {
        panel = new TouchFrame(this);
        panel.onTouchAny = this::kickAutoClose;
        panel.onOutside = () -> {
            lastOutside = SystemClock.uptimeMillis();
            closePanel();
        };

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        card.setPadding(dp(14), dp(10), dp(14), dp(14));
        card.setElevation(dp(8));

        // header: mode dering | judul | perluas
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        modeBtn = iconButton(R.drawable.ic_bell);
        modeBtn.setOnClickListener(v -> cycleRinger());
        head.addView(modeBtn);

        TextView title = new TextView(this);
        title.setText("Volume");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        expandBtn = iconButton(R.drawable.ic_more);
        expandBtn.setOnClickListener(v -> {
            expanded = !expanded;
            applyExpanded();
            kickAutoClose();
        });
        head.addView(expandBtn);
        card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        // baris slider: slider utama paling depan
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        rlp.topMargin = dp(6);
        card.addView(row, rlp);

        int main = mainIndex();
        sliders = new VSlider[STREAMS.length];
        cols = new LinearLayout[STREAMS.length];
        int[] order = new int[STREAMS.length];
        order[0] = main;
        for (int i = 0, k = 1; i < STREAMS.length; i++) if (i != main) order[k++] = i;

        for (int idx : order) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(dp(5), 0, dp(5), 0);

            VSlider s = new VSlider(this, STREAMS[idx], ICONS[idx], ACCENT);
            s.setListener(this::onSlider);
            col.addView(s, new LinearLayout.LayoutParams(dp(52), dp(210)));

            TextView lbl = new TextView(this);
            lbl.setText(NAMES[idx]);
            lbl.setTextColor(0xCCFFFFFF);
            lbl.setTextSize(11);
            lbl.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(6);
            col.addView(lbl, llp);

            row.addView(col);
            sliders[idx] = s;
            cols[idx] = col;
        }

        panel.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        pp.windowAnimations = android.R.style.Animation_Dialog;
    }

    private int mainIndex() {
        int m = sp.getInt("main", 0);
        return (m < 0 || m >= STREAMS.length) ? 0 : m;
    }

    private ImageView iconButton(int res) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Color.WHITE);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        iv.setBackground(pressedBg(dp(20)));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return iv;
    }

    private StateListDrawable pressedBg(float radius) {
        StateListDrawable sl = new StateListDrawable();
        GradientDrawable pr = new GradientDrawable();
        pr.setColor(0x33FFFFFF);
        pr.setCornerRadius(radius);
        sl.addState(new int[]{android.R.attr.state_pressed}, pr);
        sl.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return sl;
    }

    private void applyExpanded() {
        int main = mainIndex();
        for (int i = 0; i < cols.length; i++) {
            cols[i].setVisibility(expanded || i == main ? View.VISIBLE : View.GONE);
        }
        expandBtn.setColorFilter(expanded ? ACCENT : Color.WHITE);
        if (panelShown) positionPanel(true);
    }

    private void positionPanel(boolean update) {
        Point s = screen();
        int gap = dp(8);
        int estW = expanded ? dp(28 + 62 * 5 + 10) : dp(28 + 62 + 10);
        int estH = dp(10 + 40 + 6 + 210 + 6 + 18 + 14);
        if (onRightSide()) {
            pp.gravity = Gravity.TOP | Gravity.RIGHT;
            pp.x = Math.max(0, Math.min(s.x - bp.x + gap, s.x - estW));
        } else {
            pp.gravity = Gravity.TOP | Gravity.LEFT;
            pp.x = Math.max(0, Math.min(bp.x + size + gap, s.x - estW));
        }
        pp.y = Math.max(dp(24), Math.min(bp.y + size / 2 - estH / 2, s.y - estH - dp(24)));
        if (update) {
            try { wm.updateViewLayout(panel, pp); } catch (Exception ignored) { }
        }
    }

    private void showPanel() {
        closeMenu();
        if (panel == null) buildPanel();
        expanded = sp.getBoolean("expanded", false);
        applyExpanded();
        refreshPanel();
        positionPanel(false);
        try {
            wm.addView(panel, pp);
            panelShown = true;
        } catch (Exception e) {
            panelShown = false;
            return;
        }
        wake();
        kickAutoClose();
    }

    private void closePanel() {
        h.removeCallbacks(autoClose);
        if (panelShown && panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
        }
        panelShown = false;
        if (!menuShown) scheduleDim();
    }

    private void refreshPanel() {
        if (sliders == null) return;
        for (VSlider s : sliders) {
            int min = Build.VERSION.SDK_INT >= 28 ? am.getStreamMinVolume(s.stream) : 0;
            s.setRange(min, am.getStreamMaxVolume(s.stream));
            s.setValue(am.getStreamVolume(s.stream));
        }
        if (modeBtn != null) modeBtn.setImageResource(ringerIcon(am.getRingerMode()));
    }

    private void onSlider(VSlider s, int value) {
        kickAutoClose();
        try {
            am.setStreamVolume(s.stream, value, 0);
        } catch (SecurityException e) {
            askPolicyAccess();
        }
        h.removeCallbacks(refreshLater);
        h.postDelayed(refreshLater, 250);
    }

    private final Runnable refreshLater = this::refreshPanel;

    // ---------- mode dering ----------
    private static int ringerIcon(int mode) {
        if (mode == AudioManager.RINGER_MODE_VIBRATE) return R.drawable.ic_vibrate;
        if (mode == AudioManager.RINGER_MODE_SILENT) return R.drawable.ic_mute;
        return R.drawable.ic_bell;
    }

    private void cycleRinger() {
        kickAutoClose();
        int m = am.getRingerMode();
        int next = m == AudioManager.RINGER_MODE_NORMAL ? AudioManager.RINGER_MODE_VIBRATE
                : m == AudioManager.RINGER_MODE_VIBRATE ? AudioManager.RINGER_MODE_SILENT
                : AudioManager.RINGER_MODE_NORMAL;
        setRinger(next);
        h.postDelayed(refreshLater, 200);
    }

    private void setRinger(int mode) {
        try {
            am.setRingerMode(mode);
            String t = mode == AudioManager.RINGER_MODE_VIBRATE ? "Mode getar"
                    : mode == AudioManager.RINGER_MODE_SILENT ? "Mode senyap" : "Mode dering normal";
            Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            askPolicyAccess();
        }
    }

    private void askPolicyAccess() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            Toast.makeText(this, "Izinkan akses \"Jangan Ganggu\" untuk Volume Melayang", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
            closePanel();
            closeMenu();
        }
    }

    // ---------- menu tekan lama ----------
    private void showMenu() {
        closeMenu();
        menu = new TouchFrame(this);
        menu.onOutside = () -> {
            lastOutside = SystemClock.uptimeMillis();
            closeMenu();
        };

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        card.setPadding(0, dp(8), 0, dp(8));
        card.setElevation(dp(8));

        int mode = am.getRingerMode();
        card.addView(menuRow(R.drawable.ic_bell, "Dering normal", mode == AudioManager.RINGER_MODE_NORMAL,
                () -> setRinger(AudioManager.RINGER_MODE_NORMAL)));
        card.addView(menuRow(R.drawable.ic_vibrate, "Getar", mode == AudioManager.RINGER_MODE_VIBRATE,
                () -> setRinger(AudioManager.RINGER_MODE_VIBRATE)));
        card.addView(menuRow(R.drawable.ic_mute, "Senyap", mode == AudioManager.RINGER_MODE_SILENT,
                () -> setRinger(AudioManager.RINGER_MODE_SILENT)));
        card.addView(divider());
        boolean locked = sp.getBoolean("locked", false);
        card.addView(menuRow(R.drawable.ic_lock, locked ? "Buka kunci posisi" : "Kunci posisi", locked,
                () -> {
                    boolean nl = !sp.getBoolean("locked", false);
                    sp.edit().putBoolean("locked", nl).apply();
                    Toast.makeText(this, nl ? "Posisi tombol dikunci" : "Posisi tombol bisa digeser",
                            Toast.LENGTH_SHORT).show();
                }));
        card.addView(menuRow(R.drawable.ic_hide, "Sembunyikan", false, () -> {
            setHidden(true);
            Toast.makeText(this, "Munculkan lagi dari notifikasi atau tile Quick Settings",
                    Toast.LENGTH_LONG).show();
        }));
        card.addView(menuRow(R.drawable.ic_alarm, "Sembunyikan 10 detik", false, () -> {
            setHidden(true);
            h.postDelayed(unhideRunnable, 10000);
        }));
        card.addView(divider());
        card.addView(menuRow(R.drawable.ic_tune, "Pengaturan", false, () -> {
            try {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }));
        card.addView(menuRow(R.drawable.ic_close, "Matikan tombol", false, this::stopSelf));

        menu.addView(card, new FrameLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT));

        WindowManager.LayoutParams mp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        mp.windowAnimations = android.R.style.Animation_Dialog;
        Point s = screen();
        int estH = dp(16 + 9 * 48 + 2);
        if (onRightSide()) {
            mp.gravity = Gravity.TOP | Gravity.RIGHT;
            mp.x = Math.max(0, s.x - bp.x - size / 2);
        } else {
            mp.gravity = Gravity.TOP | Gravity.LEFT;
            mp.x = Math.max(0, bp.x + size / 2);
        }
        mp.x = Math.min(mp.x, s.x - dp(230));
        mp.y = bp.y + size + dp(4);
        if (mp.y + estH > s.y - dp(24)) mp.y = Math.max(dp(24), bp.y - estH - dp(4));
        try {
            wm.addView(menu, mp);
            menuShown = true;
        } catch (Exception ignored) {
            menuShown = false;
        }
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        v.setLayoutParams(lp);
        return v;
    }

    private View menuRow(int icon, String text, boolean active, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), 0, dp(18), 0);
        r.setBackground(pressedBg(0));
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(active ? ACCENT : Color.WHITE);
        r.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(active ? ACCENT : Color.WHITE);
        if (active) tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(16);
        r.addView(tv, tlp);

        r.setOnClickListener(v -> {
            closeMenu();
            action.run();
        });
        return r;
    }

    private void closeMenu() {
        if (menuShown && menu != null) {
            try { wm.removeView(menu); } catch (Exception ignored) { }
        }
        menuShown = false;
        menu = null;
        if (!panelShown) scheduleDim();
    }

    // ---------- root yang bisa mendeteksi sentuhan di luar ----------
    static class TouchFrame extends FrameLayout {
        Runnable onTouchAny, onOutside;

        TouchFrame(Context c) { super(c); }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                if (onOutside != null) onOutside.run();
                return true;
            }
            if (onTouchAny != null) onTouchAny.run();
            return super.dispatchTouchEvent(e);
        }
    }
}
