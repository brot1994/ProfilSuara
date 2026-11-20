package id.bram.volumemelayang;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

/** Slider volume vertikal bergaya kapsul (mirip panel volume One UI). */
public class VSlider extends View {

    public interface Listener {
        void onChange(VSlider s, int value);
    }

    public final int stream;
    private int min = 0, max = 15, value = 0;
    private final Drawable icon;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float density;
    private Listener listener;
    private boolean dragging = false;
    private float dragFrac = 0f;      // posisi isian yang halus (mengikuti jari)
    private static final float RANGE_FACTOR = 1.6f; // 0→maks butuh geser 1,6x tinggi slider

    public VSlider(Context c, int stream, int iconRes, int accent) {
        super(c);
        this.stream = stream;
        density = c.getResources().getDisplayMetrics().density;
        icon = c.getDrawable(iconRes).mutate();
        trackPaint.setColor(0x33FFFFFF);
        fillPaint.setColor(accent);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(13 * density);
        textPaint.setFakeBoldText(true);
    }

    public void setListener(Listener l) { listener = l; }

    public void setRange(int min, int max) {
        this.min = min;
        this.max = Math.max(min + 1, max);
        invalidate();
    }

    public void setValue(int v) {
        v = Math.max(min, Math.min(max, v));
        if (v != value && !dragging) {
            value = v;
            invalidate();
        }
    }

    public int getValue() { return value; }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float r = w / 2f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect, trackPaint);
        float frac = dragging ? dragFrac : (value - min) / (float) (max - min);
        float top = h - frac * h;
        canvas.drawRect(0, top, w, h, fillPaint);
        canvas.restore();

        // ikon di bawah
        int is = (int) (w * 0.46f);
        int il = (int) ((w - is) / 2f);
        int ib = (int) (h - (w - is) / 2f);
        icon.setBounds(il, ib - is, il + is, ib);
        icon.setTint(frac > 0.12f ? Color.WHITE : 0xAAFFFFFF);
        icon.draw(canvas);

        // angka tingkat volume (sama seperti panel Samsung: 0..maks)
        canvas.drawText(String.valueOf(value), w / 2f, 22 * density, textPaint);
    }

    private float downY, downFrac;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // geser relatif: sentuhan pertama TIDAK mengubah volume
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                downY = e.getY();
                downFrac = (value - min) / (float) (max - min);
                dragFrac = downFrac;
                dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float h = Math.max(1f, getHeight());
                float frac = downFrac + (downY - e.getY()) / (h * RANGE_FACTOR);
                if (frac < 0f || frac > 1f) {
                    // mentok di ujung: geser balik langsung terasa
                    frac = Math.max(0f, Math.min(1f, frac));
                    downFrac = frac;
                    downY = e.getY();
                }
                dragFrac = frac;
                int v = Math.round(min + frac * (max - min));
                if (v != value) {
                    value = v;
                    if (listener != null) listener.onChange(this, v);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }
}
