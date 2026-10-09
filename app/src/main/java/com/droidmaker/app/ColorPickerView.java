package com.droidmaker.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** HSV color picker: saturation/value square, hue bar and optional alpha bar. */
@SuppressLint("ViewConstructor")
public final class ColorPickerView extends View {
    public interface Listener { void changed(int argb); }

    private final float[] hsv = new float[3];
    private int alpha;
    private final boolean withAlpha;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF sv = new RectF(), hue = new RectF(), al = new RectF();
    private int dragging = -1;
    private Listener listener;

    public ColorPickerView(Context c, int argb, boolean withAlpha) {
        super(c);
        this.withAlpha = withAlpha;
        setColor(argb);
    }

    public void setListener(Listener l) { listener = l; }

    public void setColor(int argb) {
        Color.colorToHSV(argb | 0xFF000000, hsv);
        alpha = withAlpha ? (argb >>> 24) : 255;
        invalidate();
    }

    public int color() { return (alpha << 24) | (Color.HSVToColor(hsv) & 0xFFFFFF); }

    @Override
    protected void onMeasure(int wm, int hm) {
        int w = MeasureSpec.getSize(wm);
        int bar = Ui.dp(getContext(), 30);
        int h = (int) (w * 0.62f) + bar * (withAlpha ? 2 : 1) + Ui.dp(getContext(), 16) * (withAlpha ? 2 : 1);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        int bar = Ui.dp(getContext(), 30), gap = Ui.dp(getContext(), 16);
        float svH = w * 0.62f;
        sv.set(0, 0, w, svH);
        hue.set(0, svH + gap, w, svH + gap + bar);
        al.set(0, hue.bottom + gap, w, hue.bottom + gap + bar);
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = Ui.dp(getContext(), 8);
        // SV square
        int pure = Color.HSVToColor(new float[]{hsv[0], 1, 1});
        p.setShader(new LinearGradient(sv.left, 0, sv.right, 0, Color.WHITE, pure, Shader.TileMode.CLAMP));
        c.drawRoundRect(sv, r, r, p);
        p.setShader(new LinearGradient(0, sv.top, 0, sv.bottom, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRoundRect(sv, r, r, p);
        // hue bar
        int[] hues = new int[7];
        for (int i = 0; i < 7; i++) hues[i] = Color.HSVToColor(new float[]{i * 60f, 1, 1});
        p.setShader(new LinearGradient(hue.left, 0, hue.right, 0, hues, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(hue, r, r, p);
        // alpha bar over checkerboard
        if (withAlpha) {
            p.setShader(null);
            float cs = al.height() / 2;
            c.save();
            c.clipRect(al);
            for (float x = al.left; x < al.right; x += cs)
                for (float y = al.top; y < al.bottom; y += cs) {
                    boolean dark = (((int) ((x - al.left) / cs) + (int) ((y - al.top) / cs)) & 1) == 0;
                    p.setColor(dark ? 0xFF808080 : 0xFFC0C0C0);
                    c.drawRect(x, y, x + cs, y + cs, p);
                }
            c.restore();
            int rgb = Color.HSVToColor(hsv) & 0xFFFFFF;
            p.setShader(new LinearGradient(al.left, 0, al.right, 0, rgb, rgb | 0xFF000000, Shader.TileMode.CLAMP));
            c.drawRect(al, p);
        }
        p.setShader(null);
        // markers
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dp(getContext(), 3));
        p.setColor(Color.WHITE);
        float mx = sv.left + hsv[1] * sv.width(), my = sv.top + (1 - hsv[2]) * sv.height();
        c.drawCircle(mx, my, Ui.dp(getContext(), 9), p);
        float hx = hue.left + hsv[0] / 360f * hue.width();
        c.drawRect(hx - 4, hue.top - 3, hx + 4, hue.bottom + 3, p);
        if (withAlpha) {
            float ax = al.left + alpha / 255f * al.width();
            c.drawRect(ax - 4, al.top - 3, ax + 4, al.bottom + 3, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (sv.contains(x, y)) dragging = 0;
            else if (y >= hue.top - 10 && y <= hue.bottom + 10) dragging = 1;
            else if (withAlpha && y >= al.top - 10 && y <= al.bottom + 10) dragging = 2;
            else dragging = -1;
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (dragging < 0) return true;
        float fx = Math.max(0, Math.min(1, (x - sv.left) / sv.width()));
        switch (dragging) {
            case 0:
                hsv[1] = fx;
                hsv[2] = 1 - Math.max(0, Math.min(1, (y - sv.top) / sv.height()));
                break;
            case 1: hsv[0] = fx * 359.9f; break;
            case 2: alpha = Math.round(fx * 255); break;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) dragging = -1;
        invalidate();
        if (listener != null) listener.changed(color());
        return true;
    }
}
