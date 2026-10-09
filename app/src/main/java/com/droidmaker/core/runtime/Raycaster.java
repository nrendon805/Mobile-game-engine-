package com.droidmaker.core.runtime;

import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.Room;
import com.droidmaker.core.project.Sprite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Software "2.5D" raycaster (Wolfenstein style). The room is a top-down grid: instances of objects
 * flagged as raycast walls fill cells; other instances with sprites become billboards.
 */
public final class Raycaster {
    // settings (changeable by functions/actions)
    public double fov = 66;
    public int floorColor = 0x404040, ceilColor = 0x6a8fbf;
    public int floorTex = -1, ceilTex = -1;
    public double fogDist = 0;
    public int fogColor = 0;
    public int resolution = 320;
    public int cameraId = -4;

    // grid
    private int cell = 32, gw, gh;
    private int[] grid = new int[0];        // 0 = empty, else 1 + sprite*1024 + frame
    private int[] gridInst = new int[0];    // instance id in the cell

    // output
    public int[] pixels = new int[0];
    public int width, height;
    private double[] zbuf = new double[0];

    private final Game game;
    private final Map<Integer, Tex> texCache = new HashMap<>();

    private static final class Tex {
        int[] px; int w, h, rev;
    }

    Raycaster(Game game) { this.game = game; }

    void reset(Room r) {
        fov = r.rcFov;
        floorColor = r.rcFloorColor;
        ceilColor = r.rcCeilColor;
        floorTex = r.rcFloorTex;
        ceilTex = r.rcCeilTex;
        fogDist = r.rcFogDistance;
        fogColor = r.rcFogColor;
        resolution = Math.max(80, Math.min(1280, r.rcResolution));
        cameraId = -4;
        cell = Math.max(4, r.gridX);
    }

    public int cellSize() { return cell; }

    private long gridStep = -1;

    /** Builds the wall grid at most once per step. */
    public void ensureGrid() {
        if (gridStep != game.stepCount || grid.length == 0) buildGrid();
    }

    /** Rebuilds the wall grid from wall instances. */
    public void buildGrid() {
        gridStep = game.stepCount;
        RoomState rs = game.rs;
        if (rs == null) return;
        gw = Math.max(1, (rs.width + cell - 1) / cell);
        gh = Math.max(1, (rs.height + cell - 1) / cell);
        if (grid.length != gw * gh) { grid = new int[gw * gh]; gridInst = new int[gw * gh]; }
        else { java.util.Arrays.fill(grid, 0); java.util.Arrays.fill(gridInst, 0); }
        for (Instance i : game.activeList()) {
            if (i.destroyed || !i.obj.def.rcWall) continue;
            double cx, cy;
            i.updateBBox(game);
            if (i.hasBox) { cx = (i.bl + i.br) / 2; cy = (i.bt + i.bb) / 2; } else { cx = i.x + cell / 2.0; cy = i.y + cell / 2.0; }
            int gx = (int) Math.floor(cx / cell), gy = (int) Math.floor(cy / cell);
            if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) continue;
            Sprite s = game.project.sprite(i.spriteIndex);
            int frame = s == null || s.frames.isEmpty() ? 0 : ((int) Math.floor(i.imageIndex) % s.frames.size() + s.frames.size()) % s.frames.size();
            grid[gy * gw + gx] = s == null ? 1 : 1 + i.spriteIndex * 1024 + frame + 1;
            gridInst[gy * gw + gx] = i.id;
        }
    }

    public boolean wallAt(double x, double y) {
        int gx = (int) Math.floor(x / cell), gy = (int) Math.floor(y / cell);
        if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) return true;
        return grid[gy * gw + gx] != 0;
    }

    public Instance camera() {
        Instance c = game.instance(cameraId);
        if (c != null && !c.destroyed) return c;
        RoomState rs = game.rs;
        if (rs != null) {
            int f = rs.views[0].follow;
            if (f >= 0) {
                Instance i = game.firstOf(f);
                if (i != null) return i;
            }
        }
        return null;
    }

    /** Distance (room units) from (x,y) along direction until a wall, up to max. */
    public double cast(double x, double y, double dirDeg, double max) {
        double a = Math.toRadians(dirDeg);
        double dx = Math.cos(a), dy = -Math.sin(a);
        double px = x / cell, py = y / cell;
        double d = dda(px, py, dx, dy, max / cell, null);
        return Math.min(max, d * cell);
    }

    private final int[] hitInfo = new int[3];

    /** DDA through the grid; returns perpendicular-ish distance in cells (along ray of unit dir). */
    private double dda(double px, double py, double dx, double dy, double maxCells, int[] info) {
        int mx = (int) Math.floor(px), my = (int) Math.floor(py);
        double ddx = dx == 0 ? 1e30 : Math.abs(1 / dx), ddy = dy == 0 ? 1e30 : Math.abs(1 / dy);
        int sx, sy;
        double sdx, sdy;
        if (dx < 0) { sx = -1; sdx = (px - mx) * ddx; } else { sx = 1; sdx = (mx + 1.0 - px) * ddx; }
        if (dy < 0) { sy = -1; sdy = (py - my) * ddy; } else { sy = 1; sdy = (my + 1.0 - py) * ddy; }
        int side = 0;
        double dist = 0;
        for (int steps = 0; steps < 4096; steps++) {
            if (sdx < sdy) { dist = sdx; sdx += ddx; mx += sx; side = 0; }
            else { dist = sdy; sdy += ddy; my += sy; side = 1; }
            if (dist > maxCells) return maxCells;
            if (mx < 0 || my < 0 || mx >= gw || my >= gh) {
                if (info != null) { info[0] = 0; info[1] = side; info[2] = -1; }
                return dist;
            }
            int g = grid[my * gw + mx];
            if (g != 0) {
                if (info != null) { info[0] = g; info[1] = side; info[2] = my * gw + mx; }
                return dist;
            }
        }
        return maxCells;
    }

    /** True if nothing blocks the straight line between two points. */
    public boolean lineClear(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double l = Math.hypot(dx, dy);
        if (l < 1e-6) return true;
        double d = dda(x1 / cell, y1 / cell, dx / l, dy / l, l / cell, null);
        return d >= l / cell - 1e-6;
    }

    // ------------------------------------------------------------------ rendering
    public void render(double portW, double portH) {
        int w = resolution;
        int h = Math.max(40, (int) Math.round(w * portH / Math.max(1, portW)));
        if (pixels.length != w * h) { pixels = new int[w * h]; }
        if (zbuf.length != w) zbuf = new double[w];
        width = w;
        height = h;
        buildGrid();
        Instance cam = camera();
        double camX, camY, dir, pitch = 0, camZ = 0;
        if (cam != null) { camX = cam.x; camY = cam.y; dir = cam.direction; pitch = cam.pitch; camZ = cam.z; }
        else { camX = cell * 1.5; camY = cell * 1.5; dir = 0; }
        double a = Math.toRadians(dir);
        double dirX = Math.cos(a), dirY = -Math.sin(a);
        double rightX = -dirY, rightY = dirX;
        double halfTan = Math.tan(Math.toRadians(Math.max(10, Math.min(170, fov))) / 2);
        double proj = (w / 2.0) / halfTan;
        double horizon = h / 2.0 + Math.tan(Math.toRadians(Math.max(-60, Math.min(60, pitch)))) * proj;
        double eye = 0.5 + camZ / cell;
        double px = camX / cell, py = camY / cell;
        double fogCells = fogDist > 0 ? fogDist / cell : 0;

        // ---- floor & ceiling
        Tex ft = floorTex >= 0 ? bgTex(floorTex) : null;
        Tex ct = ceilTex >= 0 ? bgTex(ceilTex) : null;
        double lx = dirX - rightX * halfTan, ly = dirY - rightY * halfTan; // left-most ray
        double rx = dirX + rightX * halfTan, ry = dirY + rightY * halfTan;
        for (int y = 0; y < h; y++) {
            boolean isFloor = y > horizon;
            double dy = isFloor ? (y + 0.5 - horizon) : (horizon - y - 0.5);
            int row = y * w;
            if (dy <= 0.0001) {
                int c = 0xFF000000 | (isFloor ? floorColor : ceilColor);
                for (int x = 0; x < w; x++) pixels[row + x] = c;
                continue;
            }
            double camH = isFloor ? eye : (1 - eye);
            double rowDist = camH * proj / dy; // distance in cells along the view direction
            Tex t = isFloor ? ft : ct;
            int base = isFloor ? floorColor : ceilColor;
            double fogF = fogCells > 0 ? Math.min(1, rowDist / fogCells) : shadeFactor(rowDist);
            if (t == null) {
                int c = fogCells > 0 ? mix(base, fogColor, fogF) : scale(base, 1 - fogF * 0.6);
                c |= 0xFF000000;
                for (int x = 0; x < w; x++) pixels[row + x] = c;
            } else {
                double stepX = rowDist * (rx - lx) / w, stepY = rowDist * (ry - ly) / w;
                double fx = px + rowDist * lx, fy = py + rowDist * ly;
                for (int x = 0; x < w; x++) {
                    int tx = (int) ((fx - Math.floor(fx)) * t.w) & 0x7fffffff;
                    int ty = (int) ((fy - Math.floor(fy)) * t.h) & 0x7fffffff;
                    int c = t.px[(ty % t.h) * t.w + (tx % t.w)];
                    c = fogCells > 0 ? mix(c, fogColor, fogF) : scale(c, 1 - fogF * 0.6);
                    pixels[row + x] = 0xFF000000 | c;
                    fx += stepX;
                    fy += stepY;
                }
            }
        }

        // ---- walls
        int[] info = hitInfo;
        for (int x = 0; x < w; x++) {
            double camXr = 2.0 * (x + 0.5) / w - 1;
            double rdx = dirX + rightX * halfTan * camXr, rdy = dirY + rightY * halfTan * camXr;
            info[0] = 0; info[2] = -1;
            double perp = dda(px, py, rdx, rdy, 512, info);
            if (perp < 1e-4) perp = 1e-4;
            zbuf[x] = perp;
            if (info[0] == 0 && info[2] < 0 && perp >= 512) continue;
            double top = horizon - (1 - eye) * proj / perp;
            double bot = horizon + eye * proj / perp;
            int y0 = Math.max(0, (int) Math.ceil(top - 0.5)), y1 = Math.min(h - 1, (int) Math.floor(bot - 0.5));
            if (y1 < y0) continue;
            double wallX = info[1] == 0 ? py + perp * rdy : px + perp * rdx;
            wallX -= Math.floor(wallX);
            Tex t = info[0] > 1 ? spriteTexById(info[0] - 2) : null;
            double shade = info[1] == 1 ? 0.78 : 1.0;
            double fogF = fogCells > 0 ? Math.min(1, perp / fogCells) : 0;
            double lightF = fogCells > 0 ? shade : shade * (1 - shadeFactor(perp) * 0.6);
            if (t == null) {
                int c = 0x9a8a7a;
                c = fogCells > 0 ? mix(scale(c, lightF), fogColor, fogF) : scale(c, lightF);
                for (int y = y0; y <= y1; y++) pixels[y * w + x] = 0xFF000000 | c;
            } else {
                int tx = (int) (wallX * t.w);
                if ((info[1] == 0 && rdx < 0) || (info[1] == 1 && rdy > 0)) tx = t.w - 1 - tx;
                tx = Math.max(0, Math.min(t.w - 1, tx));
                double span = bot - top;
                for (int y = y0; y <= y1; y++) {
                    int ty = (int) ((y + 0.5 - top) / span * t.h);
                    ty = Math.max(0, Math.min(t.h - 1, ty));
                    int c = t.px[ty * t.w + tx];
                    if ((c >>> 24) < 128) continue;
                    c = fogCells > 0 ? mix(scale(c, lightF), fogColor, fogF) : scale(c, lightF);
                    pixels[y * w + x] = 0xFF000000 | c;
                }
            }
        }

        // ---- billboards
        List<Instance> sprites = new ArrayList<>();
        List<Double> depths = new ArrayList<>();
        long frame = game.frameCount;
        for (Instance i : game.activeList()) {
            if (i.destroyed || !i.visible || i == cam || i.obj.def.rcWall || i.spriteIndex < 0) continue;
            if (i.obj.hasDraw && i.rcDrawFrame < frame - 1) continue;
            double rxp = i.x / cell - px, ryp = i.y / cell - py;
            double depth = rxp * dirX + ryp * dirY;
            if (depth <= 0.05) continue;
            sprites.add(i);
            depths.add(depth);
        }
        Integer[] order = new Integer[sprites.size()];
        for (int k = 0; k < order.length; k++) order[k] = k;
        java.util.Arrays.sort(order, (p, q) -> Double.compare(depths.get(q), depths.get(p)));
        for (int k : order) {
            Instance i = sprites.get(k);
            double depth = depths.get(k);
            Sprite s = game.project.sprite(i.spriteIndex);
            if (s == null || s.frames.isEmpty()) continue;
            int frameIdx = ((int) Math.floor(i.imageIndex) % s.frames.size() + s.frames.size()) % s.frames.size();
            Tex t = spriteTex(i.spriteIndex, frameIdx);
            if (t == null) continue;
            double rxp = i.x / cell - px, ryp = i.y / cell - py;
            double lat = rxp * rightX + ryp * rightY;
            double sxCenter = w / 2.0 + lat / depth * proj;
            double sw = s.width * Math.abs(i.imageXscale) / cell, sh = s.height * Math.abs(i.imageYscale) / cell;
            double pxW = sw * proj / depth, pxH = sh * proj / depth;
            double bottom = horizon + (eye - i.z / cell) * proj / depth;
            double topY = bottom - pxH;
            double originFrac = s.width > 0 ? (double) s.originX / s.width : 0.5;
            double left = sxCenter - pxW * originFrac;
            int x0 = Math.max(0, (int) Math.floor(left)), x1 = Math.min(w - 1, (int) Math.ceil(left + pxW));
            int y0 = Math.max(0, (int) Math.floor(topY)), y1 = Math.min(h - 1, (int) Math.ceil(bottom));
            double fogF = fogCells > 0 ? Math.min(1, depth / fogCells) : 0;
            double lightF = fogCells > 0 ? 1 : 1 - shadeFactor(depth) * 0.6;
            boolean flip = i.imageXscale < 0;
            int blend = i.imageBlend;
            for (int x = x0; x <= x1; x++) {
                if (depth >= zbuf[x]) continue;
                int tx = (int) ((x + 0.5 - left) / pxW * t.w);
                if (tx < 0 || tx >= t.w) continue;
                if (flip) tx = t.w - 1 - tx;
                for (int y = y0; y <= y1; y++) {
                    int ty = (int) ((y + 0.5 - topY) / pxH * t.h);
                    if (ty < 0 || ty >= t.h) continue;
                    int c = t.px[ty * t.w + tx];
                    if ((c >>> 24) < 128) continue;
                    if (blend != 0xFFFFFF) c = multiply(c, blend);
                    c = fogCells > 0 ? mix(scale(c, lightF), fogColor, fogF) : scale(c, lightF);
                    pixels[y * w + x] = 0xFF000000 | c;
                }
            }
        }
    }

    private static double shadeFactor(double distCells) {
        return Math.min(1, distCells / 24.0);
    }

    static int scale(int c, double f) {
        if (f >= 0.999) return c & 0xFFFFFF;
        if (f < 0) f = 0;
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int c, int to, double f) {
        if (f <= 0) return c & 0xFFFFFF;
        if (f > 1) f = 1;
        int r = (int) (((c >> 16) & 255) * (1 - f) + ((to >> 16) & 255) * f);
        int g = (int) (((c >> 8) & 255) * (1 - f) + ((to >> 8) & 255) * f);
        int b = (int) ((c & 255) * (1 - f) + (to & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static int multiply(int c, int m) {
        int r = ((c >> 16) & 255) * ((m >> 16) & 255) / 255;
        int g = ((c >> 8) & 255) * ((m >> 8) & 255) / 255;
        int b = (c & 255) * (m & 255) / 255;
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ textures
    private Tex spriteTexById(int code) {
        int spr = code / 1024, frame = code % 1024;
        return spriteTex(spr, frame);
    }

    private Tex spriteTex(int spr, int frame) {
        Sprite s = game.project.sprite(spr);
        if (s == null) return null;
        int key = spr * 1024 + frame;
        Tex t = texCache.get(key);
        if (t != null && t.rev == s.revision) return t;
        int[] px = game.platform.spritePixels(spr, frame);
        if (px == null || px.length < s.width * s.height || s.width <= 0) return null;
        t = new Tex();
        t.px = px; t.w = s.width; t.h = s.height; t.rev = s.revision;
        texCache.put(key, t);
        return t;
    }

    private Tex bgTex(int bg) {
        Background b = game.project.background(bg);
        if (b == null) return null;
        int key = -(bg + 2);
        Tex t = texCache.get(key);
        if (t != null && t.rev == b.revision) return t;
        int[] px = game.platform.backgroundPixels(bg);
        if (px == null || px.length < b.width * b.height || b.width <= 0) return null;
        t = new Tex();
        t.px = px; t.w = b.width; t.h = b.height; t.rev = b.revision;
        texCache.put(key, t);
        return t;
    }
}
