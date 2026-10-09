package com.droidmaker.core.project;

import com.droidmaker.core.json.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** An object definition: properties plus events, each holding a list of D&D actions. */
public class GameObject extends Resource {
    public static final int SHAPE_AUTO = 0, SHAPE_BLOCK = 1, SHAPE_FLOOR = 2, SHAPE_NONE = 3, SHAPE_BILLBOARD = 4;
    public static final String[] SHAPE_NAMES = {"Auto (billboard)", "Block / cube", "Floor tile", "Nothing", "Billboard"};

    public int sprite = -1;
    public int mask = -1;
    public int parent = -1;
    public boolean visible = true;
    public boolean solid = false;
    public boolean persistent = false;
    public int depth = 0;

    /** How instances look in 3D rooms when the object has no Draw event. */
    public int shape3d = SHAPE_AUTO;
    /** Block height for 3D (0 = use room grid size). */
    public double height3d = 0;
    /** In raycast rooms, instances of this object become textured wall cells. */
    public boolean rcWall = false;

    /** Events keyed by {@link Ev#key(int, int)}, sorted. */
    public TreeMap<Integer, List<Action>> events = new TreeMap<>();

    @Override public ResType type() { return ResType.OBJECT; }

    public List<Action> event(int type, int num) {
        return events.get(Ev.key(type, num));
    }

    public List<Action> addEvent(int type, int num) {
        int k = Ev.key(type, num);
        List<Action> l = events.get(k);
        if (l == null) { l = new ArrayList<>(); events.put(k, l); }
        return l;
    }

    @Override public Map<String, Object> toJson() {
        Map<String, Object> m = super.toJson();
        m.put("sprite", (double) sprite);
        m.put("mask", (double) mask);
        m.put("parent", (double) parent);
        m.put("visible", visible);
        m.put("solid", solid);
        m.put("persistent", persistent);
        m.put("depth", (double) depth);
        m.put("shape3d", (double) shape3d);
        m.put("height3d", height3d);
        m.put("rcWall", rcWall);
        List<Object> ev = new ArrayList<>();
        for (Map.Entry<Integer, List<Action>> e : events.entrySet()) {
            Map<String, Object> em = new java.util.LinkedHashMap<>();
            em.put("type", (double) Ev.typeOf(e.getKey()));
            em.put("num", (double) Ev.numOf(e.getKey()));
            List<Object> acts = new ArrayList<>();
            for (Action a : e.getValue()) acts.add(a.toJson());
            em.put("actions", acts);
            ev.add(em);
        }
        m.put("events", ev);
        return m;
    }

    @Override public void fromJson(Map<String, Object> m) {
        super.fromJson(m);
        sprite = Json.integer(m, "sprite", -1);
        mask = Json.integer(m, "mask", -1);
        parent = Json.integer(m, "parent", -1);
        visible = Json.bool(m, "visible", true);
        solid = Json.bool(m, "solid", false);
        persistent = Json.bool(m, "persistent", false);
        depth = Json.integer(m, "depth", 0);
        shape3d = Json.integer(m, "shape3d", SHAPE_AUTO);
        height3d = Json.num(m, "height3d", 0);
        rcWall = Json.bool(m, "rcWall", false);
        events.clear();
        for (Object o : Json.arr(m.get("events"))) {
            Map<String, Object> em = Json.obj(o);
            List<Action> acts = new ArrayList<>();
            for (Object a : Json.arr(em.get("actions"))) acts.add(Action.fromJson(Json.obj(a)));
            events.put(Ev.key(Json.integer(em, "type", 0), Json.integer(em, "num", 0)), acts);
        }
    }
}
