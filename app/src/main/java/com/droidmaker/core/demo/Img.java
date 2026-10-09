package com.droidmaker.core.demo;

/** Tiny ARGB raster used to paint the demo projects' pixel art procedurally. */
public final class Img {
    public final int w, h;
    public final int[] px;

    public Img(int w, int h) {
        this.w = w;
        this.h = h;
        px = new int[w * h];
    }

    public Img set(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < w && y < h) px[y * w + x] = c;
        return this;
    }

    public int get(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h ? px[y * w + x] : 0; }

    public Img fill(int c) { java.util.Arrays.fill(px, c); return this; }

    public Img rect(int x, int y, int rw, int rh, int c) {
        for (int j = y; j < y + rh; j++) for (int i = x; i < x + rw; i++) set(i, j, c);
        return this;
    }

    public Img frame(int x, int y, int rw, int rh, int c) {
        for (int i = x; i < x + rw; i++) { set(i, y, c); set(i, y + rh - 1, c); }
        for (int j = y; j < y + rh; j++) { set(x, j, c); set(x + rw - 1, j, c); }
        return this;
    }

    public Img circle(double cx, double cy, double r, int c) {
        for (int y = (int) (cy - r - 1); y <= cy + r + 1; y++)
            for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                if (dx * dx + dy * dy <= r * r) set(x, y, c);
            }
        return this;
    }

    public Img ellipse(double cx, double cy, double rx, double ry, int c) {
        for (int y = (int) (cy - ry - 1); y <= cy + ry + 1; y++)
            for (int x = (int) (cx - rx - 1); x <= cx + rx + 1; x++) {
                double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                if (dx * dx + dy * dy <= 1) set(x, y, c);
            }
        return this;
    }

    /** Adds deterministic per-pixel brightness noise for texture. */
    public Img noise(long seed, int amount) {
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            if ((c >>> 24) == 0) continue;
            int d = r.nextInt(amount * 2 + 1) - amount;
            int rr = clamp(((c >> 16) & 255) + d), gg = clamp(((c >> 8) & 255) + d), bb = clamp((c & 255) + d);
            px[i] = (c & 0xFF000000) | (rr << 16) | (gg << 8) | bb;
        }
        return this;
    }

    public Img outline(int c) {
        int[] copy = px.clone();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if ((copy[y * w + x] >>> 24) != 0) continue;
                boolean edge = false;
                for (int k = 0; k < 4 && !edge; k++) {
                    int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && (copy[ny * w + nx] >>> 24) != 0) edge = true;
                }
                if (edge) px[y * w + x] = c;
            }
        return this;
    }

    public Img mirrorCopy() {
        Img o = new Img(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o.px[y * w + x] = px[y * w + (w - 1 - x)];
        return o;
    }

    public Img copy() {
        Img o = new Img(w, h);
        System.arraycopy(px, 0, o.px, 0, px.length);
        return o;
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
