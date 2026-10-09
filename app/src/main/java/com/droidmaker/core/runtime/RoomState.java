package com.droidmaker.core.runtime;

import com.droidmaker.core.project.Room;

import java.util.ArrayList;
import java.util.List;

/** Mutable runtime copy of a room's settings (so the editor's room data is never modified). */
public final class RoomState {
    public final int id;
    public final Room def;
    public int width, height, speed, mode;
    public String caption;
    public boolean persistent, drawBackgroundColor, viewsEnabled;
    public int backgroundColor;
    public final Room.BgLayer[] backgrounds = new Room.BgLayer[8];
    public final ViewRt[] views = new ViewRt[8];
    public final List<Room.Tile> tiles = new ArrayList<>();
    /** Instances kept while a persistent room is not active. */
    public List<Instance> saved;

    public static final class ViewRt {
        public boolean visible;
        public double xview, yview, wview, hview, xport, yport, wport, hport, angle;
        public int follow;
        public double hborder, vborder, hspeed, vspeed;
        public double shake;
        public int shakeTime;
        public double shakeX, shakeY;
    }

    public RoomState(Room r) {
        def = r;
        id = r.id;
        width = r.width;
        height = r.height;
        speed = Math.max(1, r.speed);
        mode = r.mode;
        caption = r.caption;
        persistent = r.persistent;
        drawBackgroundColor = r.drawBackgroundColor;
        backgroundColor = r.backgroundColor;
        viewsEnabled = r.viewsEnabled;
        for (int i = 0; i < 8; i++) {
            Room.BgLayer s = r.backgrounds[i], b = new Room.BgLayer();
            b.visible = s.visible; b.foreground = s.foreground; b.bg = s.bg; b.x = s.x; b.y = s.y;
            b.hspeed = s.hspeed; b.vspeed = s.vspeed; b.htiled = s.htiled; b.vtiled = s.vtiled; b.stretch = s.stretch;
            backgrounds[i] = b;
            Room.View sv = r.views[i];
            ViewRt v = new ViewRt();
            v.visible = sv.visible; v.xview = sv.xview; v.yview = sv.yview; v.wview = Math.max(1, sv.wview); v.hview = Math.max(1, sv.hview);
            v.xport = sv.xport; v.yport = sv.yport; v.wport = Math.max(1, sv.wport); v.hport = Math.max(1, sv.hport);
            v.follow = sv.follow; v.hborder = sv.hborder; v.vborder = sv.vborder; v.hspeed = sv.hspeed; v.vspeed = sv.vspeed;
            views[i] = v;
        }
        for (Room.Tile t : r.tiles) tiles.add(t.copy());
        tiles.sort((a, b) -> Integer.compare(b.depth, a.depth));
    }
}
