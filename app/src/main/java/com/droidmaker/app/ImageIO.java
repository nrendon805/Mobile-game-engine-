package com.droidmaker.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.LruCache;

import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.Sprite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Loading, saving and caching images of sprites and backgrounds. */
public final class ImageIO {
    private ImageIO() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(48 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    /** Loads an image as a mutable ARGB bitmap of exactly w x h (blank if missing). */
    public static Bitmap loadMutable(File f, int w, int h) {
        Bitmap out = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        if (f != null && f.exists()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b != null) {
                new Canvas(out).drawBitmap(b, 0, 0, null);
                b.recycle();
            }
        }
        return out;
    }

    public static Bitmap decode(File f) {
        if (f == null || !f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inMutable = true;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    public static void savePng(Bitmap b, File f) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            if (!b.compress(Bitmap.CompressFormat.PNG, 100, o)) throw new IOException("PNG encode failed");
        }
    }

    public static String newImagePath(String prefix) {
        return "img/" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
    }

    /** Smallest box containing all non-transparent pixels of all frames, or the full frame. */
    public static int[] autoBBox(List<Bitmap> frames, int w, int h) {
        int l = w, t = h, r = -1, b = -1;
        int[] row = new int[w];
        for (Bitmap bm : frames) {
            for (int y = 0; y < h; y++) {
                bm.getPixels(row, 0, w, 0, y, w, 1);
                for (int x = 0; x < w; x++) {
                    if ((row[x] >>> 24) > 8) {
                        if (x < l) l = x;
                        if (x > r) r = x;
                        if (y < t) t = y;
                        if (y > b) b = y;
                    }
                }
            }
        }
        if (r < 0) return new int[]{0, 0, w - 1, h - 1};
        return new int[]{l, t, r, b};
    }

    // ------------------------------------------------------------------ thumbnails
    public static Bitmap spriteFrame(Project p, Sprite s, int frame) {
        if (s == null || s.frames.isEmpty()) return null;
        frame = Math.max(0, Math.min(s.frames.size() - 1, frame));
        String path = s.frames.get(frame);
        String key = p.dir + "/" + path + "#" + s.revision;
        Bitmap b = CACHE.get(key);
        if (b != null && !b.isRecycled()) return b;
        b = decode(p.file(path));
        if (b != null) CACHE.put(key, b);
        return b;
    }

    public static Bitmap background(Project p, Background bg) {
        if (bg == null || bg.file == null) return null;
        String key = p.dir + "/" + bg.file + "#" + bg.revision;
        Bitmap b = CACHE.get(key);
        if (b != null && !b.isRecycled()) return b;
        b = decode(p.file(bg.file));
        if (b != null) CACHE.put(key, b);
        return b;
    }

    public static Bitmap objectImage(Project p, GameObject o) {
        if (o == null) return null;
        return spriteFrame(p, p.sprite(o.sprite), 0);
    }

    public static void clearCache() { CACHE.evictAll(); }

    /** Deletes image files in img/ that no sprite or background uses any more. */
    public static void cleanUnused(Project p) {
        File dir = new File(p.dir, "img");
        File[] files = dir.listFiles();
        if (files == null) return;
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Sprite s : p.sprites) if (s != null) used.addAll(s.frames);
        for (Background b : p.backgrounds) if (b != null && b.file != null) used.add(b.file);
        for (File f : files) if (!used.contains("img/" + f.getName())) f.delete();
    }
}
