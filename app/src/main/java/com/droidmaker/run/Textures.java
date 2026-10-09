package com.droidmaker.run;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;

import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.Sprite;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/** Loads sprite/background images and keeps GL textures for them (2D and power-of-two 3D variants). */
final class Textures {
    static final class Tex {
        int id, w, h, rev;
        float u1 = 1, v1 = 1; // used area (always full here)
    }

    private final Project project;
    private final Map<Long, Tex> tex2d = new HashMap<>(), tex3d = new HashMap<>();
    private final Map<Long, int[]> pixelCache = new HashMap<>();
    private final Map<Long, Integer> pixelRev = new HashMap<>();
    boolean smooth;
    int white;

    Textures(Project project) {
        this.project = project;
        this.smooth = project.interpolate;
    }

    void init() {
        tex2d.clear();
        tex3d.clear();
        white = upload(new int[]{0xFFFFFFFF}, 1, 1, false, false);
    }

    private static long key(boolean bg, int res, int frame) { return ((bg ? 1L : 0L) << 40) | ((long) res << 16) | frame; }

    /** ARGB (non-premultiplied) pixels of a sprite frame or background; cached. */
    int[] pixels(boolean bg, int res, int frame) {
        long k = key(bg, res, frame);
        int rev;
        File f;
        int w, h;
        if (bg) {
            Background b = project.background(res);
            if (b == null || b.file == null) return null;
            rev = b.revision; f = project.file(b.file); w = b.width; h = b.height;
        } else {
            Sprite s = project.sprite(res);
            if (s == null || frame < 0 || frame >= s.frames.size()) return null;
            rev = s.revision; f = project.file(s.frames.get(frame)); w = s.width; h = s.height;
        }
        Integer pr = pixelRev.get(k);
        int[] px = pixelCache.get(k);
        if (px != null && pr != null && pr == rev) return px;
        px = loadPixels(f, w, h);
        pixelCache.put(k, px);
        pixelRev.put(k, rev);
        return px;
    }

    static int[] loadPixels(File f, int w, int h) {
        int[] out = new int[Math.max(1, w * h)];
        if (f == null || !f.exists() || w <= 0 || h <= 0) return out;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPremultiplied = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inScaled = false;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b == null) return out;
        if (b.getWidth() != w || b.getHeight() != h) {
            int[] src = new int[b.getWidth() * b.getHeight()];
            b.getPixels(src, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
            for (int y = 0; y < Math.min(h, b.getHeight()); y++)
                System.arraycopy(src, y * b.getWidth(), out, y * w, Math.min(w, b.getWidth()));
            b.recycle();
            return out;
        }
        b.getPixels(out, 0, w, 0, 0, w, h);
        b.recycle();
        return out;
    }

    Tex sprite(int spr, int frame) {
        Sprite s = project.sprite(spr);
        if (s == null || s.frames.isEmpty()) return null;
        frame = Math.max(0, Math.min(s.frames.size() - 1, frame));
        return get(false, spr, frame, s.revision, s.width, s.height, false);
    }

    Tex background(int bg) {
        Background b = project.background(bg);
        if (b == null || b.file == null) return null;
        return get(true, bg, 0, b.revision, b.width, b.height, false);
    }

    /** Texture for 3D use from an encoded id (see D3State). */
    Tex tex3D(int id) {
        if (id == -1 || id == 0) return null;
        if (id > 0) {
            int spr = (id - 1) / 1024, frame = (id - 1) % 1024;
            Sprite s = project.sprite(spr);
            if (s == null || s.frames.isEmpty()) return null;
            frame = Math.max(0, Math.min(s.frames.size() - 1, frame));
            return get(false, spr, frame, s.revision, s.width, s.height, true);
        }
        int bg = -id - 2;
        Background b = project.background(bg);
        if (b == null || b.file == null) return null;
        return get(true, bg, 0, b.revision, b.width, b.height, true);
    }

    private Tex get(boolean bg, int res, int frame, int rev, int w, int h, boolean repeat) {
        long k = key(bg, res, frame);
        Map<Long, Tex> m = repeat ? tex3d : tex2d;
        Tex t = m.get(k);
        if (t != null && t.rev == rev) return t;
        if (t != null) GLES20.glDeleteTextures(1, new int[]{t.id}, 0);
        int[] px = pixels(bg, res, frame);
        if (px == null) return null;
        t = new Tex();
        t.rev = rev;
        t.w = w;
        t.h = h;
        if (repeat) {
            int pw = pot(w), ph = pot(h);
            int[] sc = px;
            if (pw != w || ph != h) {
                sc = new int[pw * ph];
                for (int y = 0; y < ph; y++) {
                    int sy = y * h / ph;
                    for (int x = 0; x < pw; x++) sc[y * pw + x] = px[sy * w + x * w / pw];
                }
            }
            t.id = upload(sc, pw, ph, true, smooth);
        } else {
            t.id = upload(px, w, h, false, smooth);
        }
        m.put(k, t);
        return t;
    }

    static int pot(int v) {
        int p = 1;
        while (p < v && p < 2048) p <<= 1;
        return p;
    }

    private ByteBuffer buf;

    int upload(int[] argb, int w, int h, boolean repeat, boolean linear) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        int filter = linear ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        int wrap = repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba(argb, w * h));
        return ids[0];
    }

    void update(int id, int[] argb, int w, int h) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba(argb, w * h));
    }

    /** Converts ARGB ints to an RGBA byte buffer (reused). */
    ByteBuffer rgba(int[] argb, int n) {
        if (buf == null || buf.capacity() < n * 4) buf = ByteBuffer.allocateDirect(Math.max(n * 4, 1024)).order(ByteOrder.nativeOrder());
        buf.clear();
        for (int i = 0; i < n; i++) {
            int c = argb[i];
            buf.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
        }
        buf.flip();
        return buf;
    }
}
