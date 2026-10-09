package com.droidmaker.core.project;

import com.droidmaker.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A level: size, settings, placed instances, tiles, background layers, views and 2.5D/3D settings. */
public class Room extends Resource {
    public static final int MODE_2D = 0, MODE_RAYCAST = 1, MODE_3D = 2;
    public static final String[] MODE_NAMES = {"2D", "2.5D Raycast", "3D"};

    public int width = 640, height = 360;
    public int speed = 60;
    public String caption = "";
    public boolean persistent = false;
    public int backgroundColor = 0x202030;
    public boolean drawBackgroundColor = true;
    public String creationCode = "";
    public int mode = MODE_2D;
    public int gridX = 32, gridY = 32;

    public List<Inst> instances = new ArrayList<>();
    public List<Tile> tiles = new ArrayList<>();
    public BgLayer[] backgrounds = new BgLayer[8];
    public boolean viewsEnabled = false;
    public View[] views = new View[8];

    // ---- 2.5D raycast settings
    public double rcFov = 66;
    public int rcFloorColor = 0x404040, rcCeilColor = 0x6a8fbf;
    public int rcFloorTex = -1, rcCeilTex = -1;
    public double rcFogDistance = 0; // 0 = off, else world units
    public int rcFogColor = 0x000000;
    public int rcResolution = 320;   // render width in pixels

    // ---- 3D settings
    public double d3Fov = 60;
    public double d3EyeHeight = 16;
    public boolean d3Fog = false;
    public int d3FogColor = 0x8090a0;
    public double d3FogStart = 200, d3FogEnd = 800;
    public boolean d3Lighting = true;
    public int d3Ambient = 0x505050;
    public boolean d3DrawFloor = true;
    public int d3FloorColor = 0x3c5a3c;
    public int d3FloorTex = -1;

    private int nextInstId = 100001;

    public Room() {
        for (int i = 0; i < 8; i++) { backgrounds[i] = new BgLayer(); views[i] = new View(); }
        views[0].visible = true;
    }

    @Override public ResType type() { return ResType.ROOM; }

    public int newInstanceId() {
        for (Inst i : instances) if (i.id >= nextInstId) nextInstId = i.id + 1;
        return nextInstId++;
    }

    /** An object instance placed in the room editor. */
    public static class Inst {
        public int id, object;
        public double x, y, xscale = 1, yscale = 1, angle = 0;
        public String code = "";

        public Inst copy() {
            Inst i = new Inst();
            i.id = id; i.object = object; i.x = x; i.y = y;
            i.xscale = xscale; i.yscale = yscale; i.angle = angle; i.code = code;
            return i;
        }
    }

    /** A rectangle taken from a background (tileset) and placed in the room. */
    public static class Tile {
        public int bg, sx, sy, w, h;
        public double x, y;
        public int depth = 1000000;

        public Tile copy() {
            Tile t = new Tile();
            t.bg = bg; t.sx = sx; t.sy = sy; t.w = w; t.h = h; t.x = x; t.y = y; t.depth = depth;
            return t;
        }
    }

    public static class BgLayer {
        public boolean visible = false, foreground = false;
        public int bg = -1;
        public double x, y, hspeed, vspeed;
        public boolean htiled = true, vtiled = true, stretch = false;
    }

    public static class View {
        public boolean visible = false;
        public int xview = 0, yview = 0, wview = 640, hview = 360;
        public int xport = 0, yport = 0, wport = 640, hport = 360;
        public int follow = -1;
        public int hborder = 200, vborder = 150;
        public int hspeed = -1, vspeed = -1;
    }

    // ------------------------------------------------------------------ json
    @Override public Map<String, Object> toJson() {
        Map<String, Object> m = super.toJson();
        m.put("width", (double) width);
        m.put("height", (double) height);
        m.put("speed", (double) speed);
        m.put("caption", caption);
        m.put("persistent", persistent);
        m.put("backgroundColor", (double) backgroundColor);
        m.put("drawBackgroundColor", drawBackgroundColor);
        m.put("creationCode", creationCode);
        m.put("mode", (double) mode);
        m.put("gridX", (double) gridX);
        m.put("gridY", (double) gridY);

        List<Object> il = new ArrayList<>();
        for (Inst i : instances) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", (double) i.id);
            im.put("obj", (double) i.object);
            im.put("x", i.x);
            im.put("y", i.y);
            if (i.xscale != 1) im.put("xs", i.xscale);
            if (i.yscale != 1) im.put("ys", i.yscale);
            if (i.angle != 0) im.put("a", i.angle);
            if (i.code != null && !i.code.isEmpty()) im.put("code", i.code);
            il.add(im);
        }
        m.put("instances", il);

        List<Object> tl = new ArrayList<>();
        for (Tile t : tiles) {
            List<Object> a = new ArrayList<>();
            a.add((double) t.bg); a.add((double) t.sx); a.add((double) t.sy); a.add((double) t.w);
            a.add((double) t.h); a.add(t.x); a.add(t.y); a.add((double) t.depth);
            tl.add(a);
        }
        m.put("tiles", tl);

        List<Object> bl = new ArrayList<>();
        for (BgLayer b : backgrounds) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("visible", b.visible); bm.put("fg", b.foreground); bm.put("bg", (double) b.bg);
            bm.put("x", b.x); bm.put("y", b.y); bm.put("hs", b.hspeed); bm.put("vs", b.vspeed);
            bm.put("ht", b.htiled); bm.put("vt", b.vtiled); bm.put("stretch", b.stretch);
            bl.add(bm);
        }
        m.put("backgrounds", bl);

        m.put("viewsEnabled", viewsEnabled);
        List<Object> vl = new ArrayList<>();
        for (View v : views) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("visible", v.visible);
            vm.put("view", Sprite.listOf(v.xview, v.yview, v.wview, v.hview));
            vm.put("port", Sprite.listOf(v.xport, v.yport, v.wport, v.hport));
            vm.put("follow", (double) v.follow);
            vm.put("border", Sprite.listOf(v.hborder, v.vborder));
            vm.put("spd", Sprite.listOf(v.hspeed, v.vspeed));
            vl.add(vm);
        }
        m.put("views", vl);

        Map<String, Object> rc = new LinkedHashMap<>();
        rc.put("fov", rcFov); rc.put("floor", (double) rcFloorColor); rc.put("ceil", (double) rcCeilColor);
        rc.put("floorTex", (double) rcFloorTex); rc.put("ceilTex", (double) rcCeilTex);
        rc.put("fog", rcFogDistance); rc.put("fogColor", (double) rcFogColor); rc.put("res", (double) rcResolution);
        m.put("raycast", rc);

        Map<String, Object> d3 = new LinkedHashMap<>();
        d3.put("fov", d3Fov); d3.put("eye", d3EyeHeight); d3.put("fog", d3Fog);
        d3.put("fogColor", (double) d3FogColor); d3.put("fogStart", d3FogStart); d3.put("fogEnd", d3FogEnd);
        d3.put("lighting", d3Lighting); d3.put("ambient", (double) d3Ambient);
        d3.put("drawFloor", d3DrawFloor); d3.put("floorColor", (double) d3FloorColor); d3.put("floorTex", (double) d3FloorTex);
        m.put("d3d", d3);
        return m;
    }

    @Override public void fromJson(Map<String, Object> m) {
        super.fromJson(m);
        width = Json.integer(m, "width", 640);
        height = Json.integer(m, "height", 360);
        speed = Json.integer(m, "speed", 60);
        caption = Json.str(m, "caption", "");
        persistent = Json.bool(m, "persistent", false);
        backgroundColor = Json.integer(m, "backgroundColor", 0x202030);
        drawBackgroundColor = Json.bool(m, "drawBackgroundColor", true);
        creationCode = Json.str(m, "creationCode", "");
        mode = Json.integer(m, "mode", MODE_2D);
        gridX = Math.max(1, Json.integer(m, "gridX", 32));
        gridY = Math.max(1, Json.integer(m, "gridY", 32));

        instances.clear();
        for (Object o : Json.arr(m.get("instances"))) {
            Map<String, Object> im = Json.obj(o);
            Inst i = new Inst();
            i.id = Json.integer(im, "id", 0);
            i.object = Json.integer(im, "obj", 0);
            i.x = Json.num(im, "x", 0);
            i.y = Json.num(im, "y", 0);
            i.xscale = Json.num(im, "xs", 1);
            i.yscale = Json.num(im, "ys", 1);
            i.angle = Json.num(im, "a", 0);
            i.code = Json.str(im, "code", "");
            instances.add(i);
        }
        tiles.clear();
        for (Object o : Json.arr(m.get("tiles"))) {
            List<Object> a = Json.arr(o);
            if (a.size() < 8) continue;
            Tile t = new Tile();
            t.bg = n(a, 0); t.sx = n(a, 1); t.sy = n(a, 2); t.w = n(a, 3); t.h = n(a, 4);
            t.x = ((Number) a.get(5)).doubleValue(); t.y = ((Number) a.get(6)).doubleValue(); t.depth = n(a, 7);
            tiles.add(t);
        }
        List<Object> bl = Json.arr(m.get("backgrounds"));
        for (int k = 0; k < 8; k++) {
            BgLayer b = new BgLayer();
            if (k < bl.size()) {
                Map<String, Object> bm = Json.obj(bl.get(k));
                b.visible = Json.bool(bm, "visible", false); b.foreground = Json.bool(bm, "fg", false);
                b.bg = Json.integer(bm, "bg", -1); b.x = Json.num(bm, "x", 0); b.y = Json.num(bm, "y", 0);
                b.hspeed = Json.num(bm, "hs", 0); b.vspeed = Json.num(bm, "vs", 0);
                b.htiled = Json.bool(bm, "ht", true); b.vtiled = Json.bool(bm, "vt", true);
                b.stretch = Json.bool(bm, "stretch", false);
            }
            backgrounds[k] = b;
        }
        viewsEnabled = Json.bool(m, "viewsEnabled", false);
        List<Object> vl = Json.arr(m.get("views"));
        for (int k = 0; k < 8; k++) {
            View v = new View();
            if (k < vl.size()) {
                Map<String, Object> vm = Json.obj(vl.get(k));
                v.visible = Json.bool(vm, "visible", false);
                List<Object> a = Json.arr(vm.get("view"));
                if (a.size() == 4) { v.xview = n(a, 0); v.yview = n(a, 1); v.wview = n(a, 2); v.hview = n(a, 3); }
                a = Json.arr(vm.get("port"));
                if (a.size() == 4) { v.xport = n(a, 0); v.yport = n(a, 1); v.wport = n(a, 2); v.hport = n(a, 3); }
                v.follow = Json.integer(vm, "follow", -1);
                a = Json.arr(vm.get("border"));
                if (a.size() == 2) { v.hborder = n(a, 0); v.vborder = n(a, 1); }
                a = Json.arr(vm.get("spd"));
                if (a.size() == 2) { v.hspeed = n(a, 0); v.vspeed = n(a, 1); }
            } else if (k == 0) v.visible = true;
            views[k] = v;
        }
        Map<String, Object> rc = Json.obj(m.get("raycast"));
        rcFov = Json.num(rc, "fov", 66);
        rcFloorColor = Json.integer(rc, "floor", 0x404040);
        rcCeilColor = Json.integer(rc, "ceil", 0x6a8fbf);
        rcFloorTex = Json.integer(rc, "floorTex", -1);
        rcCeilTex = Json.integer(rc, "ceilTex", -1);
        rcFogDistance = Json.num(rc, "fog", 0);
        rcFogColor = Json.integer(rc, "fogColor", 0);
        rcResolution = Json.integer(rc, "res", 320);
        Map<String, Object> d3 = Json.obj(m.get("d3d"));
        d3Fov = Json.num(d3, "fov", 60);
        d3EyeHeight = Json.num(d3, "eye", 16);
        d3Fog = Json.bool(d3, "fog", false);
        d3FogColor = Json.integer(d3, "fogColor", 0x8090a0);
        d3FogStart = Json.num(d3, "fogStart", 200);
        d3FogEnd = Json.num(d3, "fogEnd", 800);
        d3Lighting = Json.bool(d3, "lighting", true);
        d3Ambient = Json.integer(d3, "ambient", 0x505050);
        d3DrawFloor = Json.bool(d3, "drawFloor", true);
        d3FloorColor = Json.integer(d3, "floorColor", 0x3c5a3c);
        d3FloorTex = Json.integer(d3, "floorTex", -1);
    }

    private static int n(List<Object> a, int i) { return ((Number) a.get(i)).intValue(); }
}
