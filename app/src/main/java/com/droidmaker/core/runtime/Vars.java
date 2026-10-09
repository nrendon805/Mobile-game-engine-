package com.droidmaker.core.runtime;

import com.droidmaker.core.lang.Ctx;
import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.lang.V;
import com.droidmaker.core.project.Ev;
import com.droidmaker.core.project.Room;
import com.droidmaker.core.project.Sprite;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Built-in variables: per-instance ones (x, speed ...) and global ones (score, room ...). */
public final class Vars {
    private Vars() {}

    // ---------------------------------------------------------------- instance variables
    public static final int X = 0, Y = 1, XPREVIOUS = 2, YPREVIOUS = 3, XSTART = 4, YSTART = 5, HSPEED = 6, VSPEED = 7,
            SPEED = 8, DIRECTION = 9, FRICTION = 10, GRAVITY = 11, GRAVITY_DIRECTION = 12, SPRITE_INDEX = 13,
            IMAGE_INDEX = 14, IMAGE_SPEED = 15, IMAGE_XSCALE = 16, IMAGE_YSCALE = 17, IMAGE_ANGLE = 18,
            IMAGE_ALPHA = 19, IMAGE_BLEND = 20, IMAGE_NUMBER = 21, SPRITE_WIDTH = 22, SPRITE_HEIGHT = 23,
            SPRITE_XOFFSET = 24, SPRITE_YOFFSET = 25, MASK_INDEX = 26, DEPTH = 27, VISIBLE = 28, SOLID = 29,
            PERSISTENT = 30, ALARM = 31, OBJECT_INDEX = 32, ID = 33, BBOX_LEFT = 34, BBOX_RIGHT = 35,
            BBOX_TOP = 36, BBOX_BOTTOM = 37, Z = 38, PITCH = 39, IMAGE_SINGLE = 40, ZSPEED = 41;

    public static final String[] NAMES = {
            "x", "y", "xprevious", "yprevious", "xstart", "ystart", "hspeed", "vspeed", "speed", "direction",
            "friction", "gravity", "gravity_direction", "sprite_index", "image_index", "image_speed", "image_xscale",
            "image_yscale", "image_angle", "image_alpha", "image_blend", "image_number", "sprite_width",
            "sprite_height", "sprite_xoffset", "sprite_yoffset", "mask_index", "depth", "visible", "solid",
            "persistent", "alarm", "object_index", "id", "bbox_left", "bbox_right", "bbox_top", "bbox_bottom",
            "z", "pitch", "image_single", "zspeed"
    };

    private static final Map<String, Integer> INST = new HashMap<>();
    static { for (int i = 0; i < NAMES.length; i++) INST.put(NAMES[i], i); }

    public static int instIndex(String n) { Integer i = INST.get(n); return i == null ? -1 : i; }

    // ---------------------------------------------------------------- global variables
    public static final int ROOM = 0, ROOM_FIRST = 1, ROOM_LAST = 2, ROOM_WIDTH = 3, ROOM_HEIGHT = 4, ROOM_SPEED = 5,
            ROOM_CAPTION = 6, ROOM_PERSISTENT = 7, SCORE = 8, LIVES = 9, HEALTH = 10, FPS = 11, CURRENT_TIME = 12,
            MOUSE_X = 13, MOUSE_Y = 14, MOUSE_BUTTON = 15, MOUSE_LASTBUTTON = 16, KEYBOARD_KEY = 17,
            KEYBOARD_LASTKEY = 18, KEYBOARD_LASTCHAR = 19, KEYBOARD_STRING = 20, BACKGROUND_COLOR = 21,
            BACKGROUND_SHOWCOLOR = 22, BACKGROUND_VISIBLE = 23, BACKGROUND_FOREGROUND = 24, BACKGROUND_INDEX = 25,
            BACKGROUND_X = 26, BACKGROUND_Y = 27, BACKGROUND_HSPEED = 28, BACKGROUND_VSPEED = 29,
            BACKGROUND_HTILED = 30, BACKGROUND_VTILED = 31, VIEW_ENABLED = 32, VIEW_CURRENT = 33, VIEW_VISIBLE = 34,
            VIEW_XVIEW = 35, VIEW_YVIEW = 36, VIEW_WVIEW = 37, VIEW_HVIEW = 38, VIEW_XPORT = 39, VIEW_YPORT = 40,
            VIEW_WPORT = 41, VIEW_HPORT = 42, VIEW_ANGLE = 43, VIEW_OBJECT = 44, VIEW_HBORDER = 45,
            VIEW_VBORDER = 46, VIEW_HSPEED = 47, VIEW_VSPEED = 48, INSTANCE_COUNT = 49, INSTANCE_ID = 50,
            DISPLAY_WIDTH = 51, DISPLAY_HEIGHT = 52, DELTA_TIME = 53, CURRENT_YEAR = 54, CURRENT_MONTH = 55,
            CURRENT_DAY = 56, CURRENT_HOUR = 57, CURRENT_MINUTE = 58, CURRENT_SECOND = 59, CURRENT_WEEKDAY = 60,
            ROOM_MODE = 61, GAME_WIDTH = 62, GAME_HEIGHT = 63, STEP_COUNT = 64, GUI_WIDTH = 65, GUI_HEIGHT = 66,
            MOUSE_GUI_X = 67, MOUSE_GUI_Y = 68, OS_TYPE = 69;

    public static final String[] GNAMES = {
            "room", "room_first", "room_last", "room_width", "room_height", "room_speed", "room_caption",
            "room_persistent", "score", "lives", "health", "fps", "current_time", "mouse_x", "mouse_y",
            "mouse_button", "mouse_lastbutton", "keyboard_key", "keyboard_lastkey", "keyboard_lastchar",
            "keyboard_string", "background_color", "background_showcolor", "background_visible",
            "background_foreground", "background_index", "background_x", "background_y", "background_hspeed",
            "background_vspeed", "background_htiled", "background_vtiled", "view_enabled", "view_current",
            "view_visible", "view_xview", "view_yview", "view_wview", "view_hview", "view_xport", "view_yport",
            "view_wport", "view_hport", "view_angle", "view_object", "view_hborder", "view_vborder",
            "view_hspeed", "view_vspeed", "instance_count", "instance_id", "display_width", "display_height",
            "delta_time", "current_year", "current_month", "current_day", "current_hour", "current_minute",
            "current_second", "current_weekday", "room_mode", "game_width", "game_height", "step_count",
            "gui_width", "gui_height", "mouse_gui_x", "mouse_gui_y", "os_type"
    };

    private static final Map<String, Integer> GLOB = new HashMap<>();
    static { for (int i = 0; i < GNAMES.length; i++) GLOB.put(GNAMES[i], i); }

    public static int globalIndex(String n) { Integer i = GLOB.get(n); return i == null ? -1 : i; }

    /** Whether an instance variable is an array (alarm[n]). */
    public static boolean instArray(int idx) { return idx == ALARM; }

    /** Whether a global variable is an array (view_xview[n], background_index[n] ...). */
    public static boolean globalArray(int idx) {
        return (idx >= BACKGROUND_VISIBLE && idx <= BACKGROUND_VTILED) || (idx >= VIEW_VISIBLE && idx <= VIEW_VSPEED)
                || idx == INSTANCE_ID;
    }

    // ---------------------------------------------------------------- instance get/set
    public static Object getInst(Instance i, int idx, int k, Game g) {
        switch (idx) {
            case X: return V.num(i.x);
            case Y: return V.num(i.y);
            case XPREVIOUS: return V.num(i.xprevious);
            case YPREVIOUS: return V.num(i.yprevious);
            case XSTART: return V.num(i.xstart);
            case YSTART: return V.num(i.ystart);
            case HSPEED: return V.num(i.hspeed);
            case VSPEED: return V.num(i.vspeed);
            case SPEED: return V.num(i.speed);
            case DIRECTION: return V.num(i.direction);
            case FRICTION: return V.num(i.friction);
            case GRAVITY: return V.num(i.gravity);
            case GRAVITY_DIRECTION: return V.num(i.gravityDirection);
            case SPRITE_INDEX: return V.num(i.spriteIndex);
            case IMAGE_INDEX: return V.num(i.imageIndex);
            case IMAGE_SPEED: return V.num(i.imageSpeed);
            case IMAGE_XSCALE: return V.num(i.imageXscale);
            case IMAGE_YSCALE: return V.num(i.imageYscale);
            case IMAGE_ANGLE: return V.num(i.imageAngle);
            case IMAGE_ALPHA: return V.num(i.imageAlpha);
            case IMAGE_BLEND: return V.num(i.imageBlend);
            case IMAGE_NUMBER: {
                Sprite s = g.project.sprite(i.spriteIndex);
                return V.num(s == null ? 0 : s.frames.size());
            }
            case SPRITE_WIDTH: {
                Sprite s = g.project.sprite(i.spriteIndex);
                return V.num(s == null ? 0 : s.width * Math.abs(i.imageXscale));
            }
            case SPRITE_HEIGHT: {
                Sprite s = g.project.sprite(i.spriteIndex);
                return V.num(s == null ? 0 : s.height * Math.abs(i.imageYscale));
            }
            case SPRITE_XOFFSET: {
                Sprite s = g.project.sprite(i.spriteIndex);
                return V.num(s == null ? 0 : s.originX);
            }
            case SPRITE_YOFFSET: {
                Sprite s = g.project.sprite(i.spriteIndex);
                return V.num(s == null ? 0 : s.originY);
            }
            case MASK_INDEX: return V.num(i.maskIndex);
            case DEPTH: return V.num(i.depth);
            case VISIBLE: return V.bool(i.visible);
            case SOLID: return V.bool(i.solid);
            case PERSISTENT: return V.bool(i.persistent);
            case ALARM: return k >= 0 && k < 12 ? V.num(i.alarm[k]) : V.num(-1);
            case OBJECT_INDEX: return V.num(i.obj.id);
            case ID: return V.num(i.id);
            case BBOX_LEFT: i.updateBBox(g); return V.num(Math.floor(i.bl));
            case BBOX_RIGHT: i.updateBBox(g); return V.num(Math.ceil(i.br) - 1);
            case BBOX_TOP: i.updateBBox(g); return V.num(Math.floor(i.bt));
            case BBOX_BOTTOM: i.updateBBox(g); return V.num(Math.ceil(i.bb) - 1);
            case Z: return V.num(i.z);
            case PITCH: return V.num(i.pitch);
            case IMAGE_SINGLE: return V.num(i.imageSpeed == 0 ? i.imageIndex : -1);
            case ZSPEED: return V.num(i.zspeed);
        }
        throw new ScriptError("Unknown variable");
    }

    public static void setInst(Instance i, int idx, int k, Object v, Game g) {
        double d = V.toNum(v);
        switch (idx) {
            case X: i.x = d; return;
            case Y: i.y = d; return;
            case XPREVIOUS: i.xprevious = d; return;
            case YPREVIOUS: i.yprevious = d; return;
            case XSTART: i.xstart = d; return;
            case YSTART: i.ystart = d; return;
            case HSPEED: i.setHspeed(d); return;
            case VSPEED: i.setVspeed(d); return;
            case SPEED: i.setSpeed(d); return;
            case DIRECTION: i.setDirection(d); return;
            case FRICTION: i.friction = d; return;
            case GRAVITY: i.gravity = d; return;
            case GRAVITY_DIRECTION: i.gravityDirection = d; return;
            case SPRITE_INDEX: i.spriteIndex = (int) d; return;
            case IMAGE_INDEX: i.imageIndex = d; return;
            case IMAGE_SPEED: i.imageSpeed = d; return;
            case IMAGE_XSCALE: i.imageXscale = d; return;
            case IMAGE_YSCALE: i.imageYscale = d; return;
            case IMAGE_ANGLE: i.imageAngle = d; return;
            case IMAGE_ALPHA: i.imageAlpha = d; return;
            case IMAGE_BLEND: i.imageBlend = (int) d; return;
            case MASK_INDEX: i.maskIndex = (int) d; return;
            case DEPTH: i.depth = d; g.depthChanged = true; return;
            case VISIBLE: i.visible = V.truth(v); return;
            case SOLID: i.solid = V.truth(v); return;
            case PERSISTENT: i.persistent = V.truth(v); return;
            case ALARM: if (k >= 0 && k < 12) i.alarm[k] = Math.round(d); return;
            case Z: i.z = d; return;
            case PITCH: i.pitch = Math.max(-89, Math.min(89, d)); return;
            case IMAGE_SINGLE:
                if (d < 0) i.imageSpeed = 1; else { i.imageSpeed = 0; i.imageIndex = d; }
                return;
            case ZSPEED: i.zspeed = d; return;
        }
        throw new ScriptError("Variable " + NAMES[idx] + " is read-only");
    }

    // ---------------------------------------------------------------- global get/set
    public static Object getGlobal(Game g, int idx, int k, Ctx c) {
        RoomState r = g.rs;
        switch (idx) {
            case ROOM: return V.num(g.roomIndex);
            case ROOM_FIRST: return V.num(g.project.startRoom());
            case ROOM_LAST: {
                int last = -1;
                for (int id : g.project.roomOrder) if (g.project.room(id) != null) last = id;
                return V.num(last);
            }
            case ROOM_WIDTH: return V.num(r == null ? 0 : r.width);
            case ROOM_HEIGHT: return V.num(r == null ? 0 : r.height);
            case ROOM_SPEED: return V.num(r == null ? 60 : r.speed);
            case ROOM_CAPTION: return r == null ? "" : r.caption;
            case ROOM_PERSISTENT: return V.bool(r != null && r.persistent);
            case SCORE: return V.num(g.score);
            case LIVES: return V.num(g.lives);
            case HEALTH: return V.num(g.health);
            case FPS: return V.num(g.fps);
            case CURRENT_TIME: return V.num(g.platform.timeMillis() - g.startMillis);
            case MOUSE_X: return V.num(g.input.mouseX);
            case MOUSE_Y: return V.num(g.input.mouseY);
            case MOUSE_BUTTON: return V.num(g.input.touchDown(0) ? 1 : 0);
            case MOUSE_LASTBUTTON: return V.num(g.input.lastButton);
            case KEYBOARD_KEY: return V.num(g.input.currentKey());
            case KEYBOARD_LASTKEY: return V.num(g.input.lastKey);
            case KEYBOARD_LASTCHAR: return g.input.lastChar;
            case KEYBOARD_STRING: return g.input.keyboardString.toString();
            case BACKGROUND_COLOR: return V.num(r == null ? 0 : r.backgroundColor);
            case BACKGROUND_SHOWCOLOR: return V.bool(r != null && r.drawBackgroundColor);
            case VIEW_ENABLED: return V.bool(r != null && r.viewsEnabled);
            case VIEW_CURRENT: return V.num(g.viewCurrent);
            case INSTANCE_COUNT: return V.num(g.instanceCount());
            case INSTANCE_ID: {
                Instance inst = g.instanceByIndex(k);
                return V.num(inst == null ? -4 : inst.id);
            }
            case DISPLAY_WIDTH: return V.num(g.screenW);
            case DISPLAY_HEIGHT: return V.num(g.screenH);
            case DELTA_TIME: return V.num(g.deltaMicros);
            case CURRENT_YEAR: return V.num(cal().get(Calendar.YEAR));
            case CURRENT_MONTH: return V.num(cal().get(Calendar.MONTH) + 1);
            case CURRENT_DAY: return V.num(cal().get(Calendar.DAY_OF_MONTH));
            case CURRENT_HOUR: return V.num(cal().get(Calendar.HOUR_OF_DAY));
            case CURRENT_MINUTE: return V.num(cal().get(Calendar.MINUTE));
            case CURRENT_SECOND: return V.num(cal().get(Calendar.SECOND));
            case CURRENT_WEEKDAY: return V.num(cal().get(Calendar.DAY_OF_WEEK) - 1);
            case ROOM_MODE: return V.num(r == null ? 0 : r.mode);
            case GAME_WIDTH: return V.num(g.displayW);
            case GAME_HEIGHT: return V.num(g.displayH);
            case STEP_COUNT: return V.num(g.stepCount);
            case GUI_WIDTH: return V.num(g.displayW);
            case GUI_HEIGHT: return V.num(g.displayH);
            case MOUSE_GUI_X: return V.num(g.input.guiX(0));
            case MOUSE_GUI_Y: return V.num(g.input.guiY(0));
            case OS_TYPE: return V.num(4); // os_android
        }
        if (r == null) return V.ZERO;
        if (idx >= BACKGROUND_VISIBLE && idx <= BACKGROUND_VTILED) {
            if (k < 0 || k > 7) return V.ZERO;
            Room.BgLayer b = r.backgrounds[k];
            switch (idx) {
                case BACKGROUND_VISIBLE: return V.bool(b.visible);
                case BACKGROUND_FOREGROUND: return V.bool(b.foreground);
                case BACKGROUND_INDEX: return V.num(b.bg);
                case BACKGROUND_X: return V.num(b.x);
                case BACKGROUND_Y: return V.num(b.y);
                case BACKGROUND_HSPEED: return V.num(b.hspeed);
                case BACKGROUND_VSPEED: return V.num(b.vspeed);
                case BACKGROUND_HTILED: return V.bool(b.htiled);
                case BACKGROUND_VTILED: return V.bool(b.vtiled);
            }
        }
        if (idx >= VIEW_VISIBLE && idx <= VIEW_VSPEED) {
            if (k < 0 || k > 7) return V.ZERO;
            RoomState.ViewRt v = r.views[k];
            switch (idx) {
                case VIEW_VISIBLE: return V.bool(v.visible);
                case VIEW_XVIEW: return V.num(v.xview);
                case VIEW_YVIEW: return V.num(v.yview);
                case VIEW_WVIEW: return V.num(v.wview);
                case VIEW_HVIEW: return V.num(v.hview);
                case VIEW_XPORT: return V.num(v.xport);
                case VIEW_YPORT: return V.num(v.yport);
                case VIEW_WPORT: return V.num(v.wport);
                case VIEW_HPORT: return V.num(v.hport);
                case VIEW_ANGLE: return V.num(v.angle);
                case VIEW_OBJECT: return V.num(v.follow);
                case VIEW_HBORDER: return V.num(v.hborder);
                case VIEW_VBORDER: return V.num(v.vborder);
                case VIEW_HSPEED: return V.num(v.hspeed);
                case VIEW_VSPEED: return V.num(v.vspeed);
            }
        }
        return V.ZERO;
    }

    public static void setGlobal(Game g, int idx, int k, Object v, Ctx c) {
        double d = V.toNum(v);
        RoomState r = g.rs;
        switch (idx) {
            case ROOM: g.gotoRoom((int) d); return;
            case ROOM_SPEED: if (r != null) r.speed = Math.max(1, (int) d); return;
            case ROOM_CAPTION: if (r != null) r.caption = V.str(v); return;
            case ROOM_PERSISTENT: if (r != null) r.persistent = V.truth(v); return;
            case ROOM_WIDTH: if (r != null) r.width = (int) d; return;
            case ROOM_HEIGHT: if (r != null) r.height = (int) d; return;
            case SCORE: g.score = d; return;
            case LIVES: {
                double old = g.lives;
                g.lives = d;
                if (old > 0 && d <= 0) g.fireForAll(Ev.key(Ev.OTHER, Ev.O_NO_LIVES));
                return;
            }
            case HEALTH: {
                double old = g.health;
                g.health = d;
                if (old > 0 && d <= 0) g.fireForAll(Ev.key(Ev.OTHER, Ev.O_NO_HEALTH));
                return;
            }
            case KEYBOARD_KEY: g.input.forceKey((int) d); return;
            case KEYBOARD_LASTKEY: g.input.lastKey = (int) d; return;
            case KEYBOARD_LASTCHAR: g.input.lastChar = V.str(v); return;
            case KEYBOARD_STRING: g.input.keyboardString.setLength(0); g.input.keyboardString.append(V.str(v)); return;
            case BACKGROUND_COLOR: if (r != null) r.backgroundColor = (int) d; return;
            case BACKGROUND_SHOWCOLOR: if (r != null) r.drawBackgroundColor = V.truth(v); return;
            case VIEW_ENABLED: if (r != null) { r.viewsEnabled = V.truth(v); g.updateDisplaySize(); } return;
            case MOUSE_X: case MOUSE_Y: return; // ignored
        }
        if (r != null && idx >= BACKGROUND_VISIBLE && idx <= BACKGROUND_VTILED) {
            if (k < 0 || k > 7) return;
            Room.BgLayer b = r.backgrounds[k];
            switch (idx) {
                case BACKGROUND_VISIBLE: b.visible = V.truth(v); return;
                case BACKGROUND_FOREGROUND: b.foreground = V.truth(v); return;
                case BACKGROUND_INDEX: b.bg = (int) d; return;
                case BACKGROUND_X: b.x = d; return;
                case BACKGROUND_Y: b.y = d; return;
                case BACKGROUND_HSPEED: b.hspeed = d; return;
                case BACKGROUND_VSPEED: b.vspeed = d; return;
                case BACKGROUND_HTILED: b.htiled = V.truth(v); return;
                case BACKGROUND_VTILED: b.vtiled = V.truth(v); return;
            }
        }
        if (r != null && idx >= VIEW_VISIBLE && idx <= VIEW_VSPEED) {
            if (k < 0 || k > 7) return;
            RoomState.ViewRt vw = r.views[k];
            switch (idx) {
                case VIEW_VISIBLE: vw.visible = V.truth(v); return;
                case VIEW_XVIEW: vw.xview = d; return;
                case VIEW_YVIEW: vw.yview = d; return;
                case VIEW_WVIEW: vw.wview = Math.max(1, d); return;
                case VIEW_HVIEW: vw.hview = Math.max(1, d); return;
                case VIEW_XPORT: vw.xport = d; return;
                case VIEW_YPORT: vw.yport = d; return;
                case VIEW_WPORT: vw.wport = Math.max(1, d); return;
                case VIEW_HPORT: vw.hport = Math.max(1, d); return;
                case VIEW_ANGLE: vw.angle = d; return;
                case VIEW_OBJECT: vw.follow = (int) d; return;
                case VIEW_HBORDER: vw.hborder = d; return;
                case VIEW_VBORDER: vw.vborder = d; return;
                case VIEW_HSPEED: vw.hspeed = d; return;
                case VIEW_VSPEED: vw.vspeed = d; return;
            }
        }
        throw new ScriptError("Variable " + GNAMES[idx] + " is read-only");
    }

    private static Calendar cal() { return Calendar.getInstance(); }
}
