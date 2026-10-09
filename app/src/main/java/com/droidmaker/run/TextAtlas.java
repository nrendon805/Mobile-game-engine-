package com.droidmaker.run;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.util.HashMap;
import java.util.Map;

/** Glyphs rasterized on demand into one texture. Text is drawn as scaled quads. */
final class TextAtlas {
    static final int SIZE = 1024;
    static final float BASE = 48f;

    static final class Glyph {
        float u0, v0, u1, v1;
        float w, h;        // quad size at BASE
        float advance;     // at BASE
    }

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    int texture;
    private int cx = 1, cy = 1, rowH = 0;
    final float ascent, lineHeight;
    private Bitmap scratch;
    private Canvas scratchCanvas;

    TextAtlas() {
        paint.setTextSize(BASE);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT);
        boldPaint.setTextSize(BASE);
        boldPaint.setColor(Color.WHITE);
        boldPaint.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics fm = paint.getFontMetrics();
        ascent = -fm.ascent;
        lineHeight = fm.descent - fm.ascent + fm.leading;
        scratch = Bitmap.createBitmap((int) (BASE * 2.5f), (int) (lineHeight + 4), Bitmap.Config.ARGB_8888);
        scratchCanvas = new Canvas(scratch);
    }

    void init() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        texture = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        Bitmap empty = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, empty, 0);
        empty.recycle();
        glyphs.clear();
        cx = 1; cy = 1; rowH = 0;
    }

    float advance(int ch, boolean bold) {
        Glyph g = glyph(ch, bold);
        return g == null ? 0 : g.advance;
    }

    /** Glyph for a character, rasterizing it if needed. Returns null if the atlas cannot hold it. */
    Glyph glyph(int ch, boolean bold) {
        int key = bold ? ch | 0x40000000 : ch;
        Glyph g = glyphs.get(key);
        if (g != null) return g;
        Paint p = bold ? boldPaint : paint;
        String s = new String(Character.toChars(ch));
        float adv = p.measureText(s);
        int w = (int) Math.ceil(adv) + 4, h = (int) Math.ceil(lineHeight) + 2;
        if (w > scratch.getWidth()) {
            scratch = Bitmap.createBitmap(w, scratch.getHeight(), Bitmap.Config.ARGB_8888);
            scratchCanvas = new Canvas(scratch);
        }
        if (cx + w >= SIZE) { cx = 1; cy += rowH + 1; rowH = 0; }
        if (cy + h >= SIZE) {
            // atlas full: start over (glyphs will be re-rasterized as needed)
            init();
        }
        g = new Glyph();
        g.advance = adv;
        g.w = w;
        g.h = h;
        if (!Character.isWhitespace(ch)) {
            scratch.eraseColor(0);
            scratchCanvas.drawText(s, 2, ascent + 1, p);
            Bitmap part = Bitmap.createBitmap(scratch, 0, 0, w, h);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, cx, cy, part);
            part.recycle();
        }
        g.u0 = cx / (float) SIZE;
        g.v0 = cy / (float) SIZE;
        g.u1 = (cx + w) / (float) SIZE;
        g.v1 = (cy + h) / (float) SIZE;
        cx += w + 1;
        rowH = Math.max(rowH, h);
        glyphs.put(key, g);
        return g;
    }
}
