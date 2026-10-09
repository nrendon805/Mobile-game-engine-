package com.droidmaker.core.runtime;

import com.droidmaker.core.lang.Code;
import com.droidmaker.core.project.Ev;
import com.droidmaker.core.project.GameObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runtime form of an object: compiled events with parent inheritance resolved. */
public final class ObjectRt {
    public final int id;
    public final String name;
    public final GameObject def;
    public ObjectRt parent;
    /** Events defined directly on this object. */
    public final Map<Integer, Code> own = new HashMap<>();
    /** Event key -> object (self or ancestor) that defines it. */
    private final Map<Integer, ObjectRt> owner = new HashMap<>();
    /** All event keys this object responds to (own + inherited). */
    public final List<Integer> keys = new ArrayList<>();
    /** Collision event target object ids (own + inherited). */
    public int[] collisionTargets = new int[0];
    /** Ids of this object and all descendants. */
    public int[] family = new int[0];
    public final List<ObjectRt> children = new ArrayList<>();

    public boolean hasKeyboard, hasMouse, hasDraw, hasDrawGui, hasStep, hasBeginStep, hasEndStep, hasAlarm,
            hasOutside, hasBoundary, hasOutsideView, hasAnimEnd;

    public ObjectRt(GameObject def) {
        this.id = def.id;
        this.name = def.name;
        this.def = def;
    }

    /** Called after all objects and parents are linked. */
    void resolve() {
        // walk own + ancestors; nearest definition wins
        ObjectRt o = this;
        int guard = 0;
        while (o != null && guard++ < 64) {
            for (Integer k : o.own.keySet()) if (!owner.containsKey(k)) { owner.put(k, o); keys.add(k); }
            o = o.parent;
        }
        List<Integer> coll = new ArrayList<>();
        for (int k : keys) {
            int t = Ev.typeOf(k), n = Ev.numOf(k);
            switch (t) {
                case Ev.COLLISION: coll.add(n); break;
                case Ev.KEYBOARD: case Ev.KEYPRESS: case Ev.KEYRELEASE: hasKeyboard = true; break;
                case Ev.MOUSE: hasMouse = true; break;
                case Ev.DRAW: if (n == Ev.DRAW_GUI) hasDrawGui = true; else hasDraw = true; break;
                case Ev.STEP: if (n == Ev.STEP_BEGIN) hasBeginStep = true; else if (n == Ev.STEP_END) hasEndStep = true; else hasStep = true; break;
                case Ev.ALARM: hasAlarm = true; break;
                case Ev.OTHER:
                    if (n == Ev.O_OUTSIDE) hasOutside = true;
                    else if (n == Ev.O_BOUNDARY) hasBoundary = true;
                    else if (n == Ev.O_ANIMATION_END) hasAnimEnd = true;
                    else if (n >= Ev.O_OUTSIDE_VIEW0 && n < Ev.O_OUTSIDE_VIEW0 + 8) hasOutsideView = true;
                    break;
            }
        }
        collisionTargets = new int[coll.size()];
        for (int i = 0; i < collisionTargets.length; i++) collisionTargets[i] = coll.get(i);
    }

    void buildFamily() {
        List<Integer> ids = new ArrayList<>();
        collect(this, ids, 0);
        family = new int[ids.size()];
        for (int i = 0; i < family.length; i++) family[i] = ids.get(i);
    }

    private static void collect(ObjectRt o, List<Integer> ids, int depth) {
        if (depth > 64 || ids.contains(o.id)) return;
        ids.add(o.id);
        for (ObjectRt c : o.children) collect(c, ids, depth + 1);
    }

    /** The object (self or ancestor) whose code runs for this event, or null. */
    public ObjectRt ownerOf(int key) { return owner.get(key); }

    public boolean hasEvent(int key) { return owner.containsKey(key); }

    /** True when this object is objId or inherits from it. */
    public boolean isA(int objId) {
        ObjectRt o = this;
        int guard = 0;
        while (o != null && guard++ < 64) {
            if (o.id == objId) return true;
            o = o.parent;
        }
        return false;
    }
}
