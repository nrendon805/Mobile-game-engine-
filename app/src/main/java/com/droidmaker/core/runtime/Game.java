package com.droidmaker.core.runtime;

import com.droidmaker.core.lang.Code;
import com.droidmaker.core.lang.Compiler;
import com.droidmaker.core.lang.Ctx;
import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.lang.V;
import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.CodeGen;
import com.droidmaker.core.project.Ev;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Resource;
import com.droidmaker.core.project.Room;
import com.droidmaker.core.project.Script;
import com.droidmaker.core.project.Sprite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The running game: instances, rooms, the step loop and drawing. */
public final class Game {
    public final Project project;
    public final Platform platform;
    public final Compiler compiler;
    public final ObjectRt[] objects;
    public final Ctx ctx;
    public final InputState input;
    public final Effects effects = new Effects();
    public final Raycaster raycaster;
    public final D3State d3 = new D3State();
    public final List<String> compileErrors = new ArrayList<>();

    // instances
    private final ArrayList<Instance> instances = new ArrayList<>();
    private final HashMap<Integer, Instance> byId = new HashMap<>();
    private final ArrayList<Instance>[] byObj;
    private int nextId = 100001;
    private long orderCounter;
    private boolean needsCleanup;
    public boolean depthChanged = true;
    private Instance[] drawOrder = new Instance[0];
    private boolean listChanged = true;

    // globals
    private final HashMap<String, Object> globals = new HashMap<>();
    public double score = 0, lives = -1, health = 100;

    // rooms
    public RoomState rs;
    public int roomIndex = -1;
    private int pendingRoom = -1;
    private boolean restartPending, endPending;
    private final Map<Integer, RoomState> persistentRooms = new HashMap<>();
    private final Map<String, Code> extraCode = new HashMap<>();
    private Code globalCode;

    // timing
    public boolean running;
    public int fps;
    private int fpsCount;
    private long fpsTime;
    public long stepCount, frameCount;
    public long startMillis;
    public double deltaMicros;
    private long lastNanos;
    private double accum;

    // display
    public int screenW = 1280, screenH = 720;
    public double displayW = 640, displayH = 360;
    private double scaleX = 1, scaleY = 1, offX, offY;
    public int viewCurrent;

    // drawing state
    public Screen screen;
    public boolean drawing;
    public int drawPass; // 0 world, 1 gui, 2 raycast hud, 3 world in 3D
    public int drawColor = 0x000000;
    public double drawAlpha = 1;
    public double fontSize = 16;
    public boolean fontBold;
    public int halign, valign;

    public Game(Project project, Platform platform) {
        this.project = project;
        this.platform = platform;
        this.ctx = new Ctx(this);
        this.input = new InputState(this);
        this.raycaster = new Raycaster(this);
        compiler = new Compiler();
        for (ResType t : ResType.values()) {
            if (t == ResType.SCRIPT) continue;
            for (Resource r : project.list(t)) if (r != null) compiler.addResource(r.name, r.id);
        }
        for (Script s : project.scripts) if (s != null) compiler.addScript(s.name, s.id, s.code);
        for (GameObject o : project.objects) if (o != null) for (List<com.droidmaker.core.project.Action> acts : o.events.values())
            for (com.droidmaker.core.project.Action a : acts) if ("code".equals(a.id)) compiler.prescanGlobals(a.arg(0));
        compiler.prescanGlobals(project.globalCode);
        for (Room r : project.rooms) if (r != null) {
            compiler.prescanGlobals(r.creationCode);
            for (Room.Inst i : r.instances) compiler.prescanGlobals(i.code);
        }
        for (Script s : project.scripts) if (s != null) {
            try {
                compiler.script(s.name).code = compiler.compile(s.code, "Script " + s.name);
            } catch (ScriptError e) {
                compileErrors.add(e.describe());
            }
        }
        objects = new ObjectRt[project.objects.size()];
        @SuppressWarnings("unchecked")
        ArrayList<Instance>[] bo = new ArrayList[objects.length];
        byObj = bo;
        for (GameObject o : project.objects) {
            if (o == null) continue;
            ObjectRt rt = new ObjectRt(o);
            objects[o.id] = rt;
            byObj[o.id] = new ArrayList<>();
            for (Map.Entry<Integer, List<com.droidmaker.core.project.Action>> e : o.events.entrySet()) {
                String src = o.name + ": " + Ev.name(Ev.typeOf(e.getKey()), Ev.numOf(e.getKey()), project);
                try {
                    String code = CodeGen.generate(e.getValue(), project);
                    rt.own.put(e.getKey(), compiler.compile(code, src));
                } catch (ScriptError err) {
                    compileErrors.add(err.describe());
                }
            }
        }
        for (ObjectRt rt : objects) {
            if (rt == null) continue;
            int p = rt.def.parent;
            if (p >= 0 && p < objects.length && objects[p] != null && p != rt.id && !objects[p].isA(rt.id)) {
                rt.parent = objects[p];
                objects[p].children.add(rt);
            }
        }
        for (ObjectRt rt : objects) if (rt != null) { rt.resolve(); }
        for (ObjectRt rt : objects) if (rt != null) rt.buildFamily();
        try {
            if (project.globalCode != null && !project.globalCode.trim().isEmpty())
                globalCode = compiler.compile(project.globalCode, "Game start code");
        } catch (ScriptError e) {
            compileErrors.add(e.describe());
        }
        input.vbKeys = project.vpadKeys.clone();
    }

    // =================================================================== lifecycle
    public void start() {
        running = true;
        startMillis = platform.timeMillis();
        fpsTime = startMillis;
        lastNanos = 0;
        if (!compileErrors.isEmpty()) {
            StringBuilder b = new StringBuilder("Some code could not be compiled and will be skipped:\n\n");
            for (int i = 0; i < compileErrors.size() && i < 8; i++) b.append("• ").append(compileErrors.get(i)).append("\n\n");
            if (!platform.scriptError(b.toString())) { stop(); return; }
        }
        if (globalCode != null) runCode(globalCode, null, null);
        int start = project.startRoom();
        if (start < 0) {
            platform.scriptError("The project has no rooms. Add a room to run the game.");
            stop();
            return;
        }
        enterRoom(start, true);
    }

    public void stop() {
        if (!running) return;
        running = false;
        platform.soundStopAll();
        platform.gameEnded();
    }

    /** Ends the game, firing game end events. */
    public void end() {
        if (!running) return;
        fireForAll(Ev.key(Ev.OTHER, Ev.O_GAME_END));
        stop();
    }

    /**
     * Advances the game according to elapsed real time. Runs 0..4 steps at the room speed.
     * Returns true if at least one step ran.
     */
    public boolean update(long nowNanos) {
        if (!running) return false;
        if (lastNanos == 0) { lastNanos = nowNanos; step(); return true; }
        double dt = (nowNanos - lastNanos) / 1e9;
        lastNanos = nowNanos;
        if (dt > 0.25) dt = 0.25;
        accum += dt;
        double stepTime = 1.0 / Math.max(1, rs == null ? 60 : rs.speed);
        int n = 0;
        while (accum >= stepTime * 0.98 && n < 4 && running) {
            deltaMicros = stepTime * 1e6;
            step();
            accum -= stepTime;
            n++;
        }
        if (accum > stepTime * 2) accum = 0;
        return n > 0;
    }

    // =================================================================== the step
    public void step() {
        if (!running || rs == null) return;
        long now = platform.timeMillis();
        stepCount++;
        fpsCount++;
        if (now - fpsTime >= 1000) { fps = fpsCount; fpsCount = 0; fpsTime = now; }
        input.update(now);
        if (input.backPressed) {
            // back button acts as Escape for games that handle it; otherwise exits
            if (!anyHandlesKey(27)) { end(); return; }
            input.forceKey(27);
        }

        Instance[] all = snapshot();
        for (Instance i : all) { i.xprevious = i.x; i.yprevious = i.y; }
        // begin step
        for (Instance i : all) if (!i.destroyed && i.obj.hasBeginStep) fire(i, Ev.key(Ev.STEP, Ev.STEP_BEGIN), null);
        if (!running) return;
        // alarms
        for (Instance i : all) {
            if (i.destroyed) continue;
            for (int k = 0; k < 12; k++) {
                if (i.alarm[k] > 0) {
                    i.alarm[k]--;
                    if (i.alarm[k] <= 0) {
                        i.alarm[k] = -1;
                        fire(i, Ev.key(Ev.ALARM, k), null);
                        if (i.destroyed) break;
                    }
                }
            }
        }
        // keyboard
        boolean any = false;
        for (int k = 2; k < 256; k++) if (input.keyDown[k]) { any = true; break; }
        for (Instance i : all) {
            if (i.destroyed || !i.obj.hasKeyboard) continue;
            for (int key : i.obj.keys) {
                int t = Ev.typeOf(key), n = Ev.numOf(key);
                boolean hit;
                if (t == Ev.KEYBOARD) hit = n == 0 ? !any : n == 1 ? any : n < 256 && input.keyDown[n];
                else if (t == Ev.KEYPRESS) hit = n == 1 ? anyOf(input.keyPressed) : n == 0 ? false : n < 256 && input.keyPressed[n];
                else if (t == Ev.KEYRELEASE) hit = n == 1 ? anyOf(input.keyReleased) : n == 0 ? false : n < 256 && input.keyReleased[n];
                else continue;
                if (hit) { fire(i, key, null); if (i.destroyed) break; }
            }
        }
        // touch / mouse
        mouseEvents(all);
        if (!running) return;
        // step
        for (Instance i : all) if (!i.destroyed && i.obj.hasStep) fire(i, Ev.key(Ev.STEP, Ev.STEP_NORMAL), null);
        // motion
        for (Instance i : all) {
            if (i.destroyed) continue;
            if (i.friction != 0 && i.speed != 0) {
                double s = i.speed;
                if (s > 0) s = Math.max(0, s - i.friction); else s = Math.min(0, s + i.friction);
                i.setSpeed(s);
            }
            if (i.gravity != 0) i.motionAdd(i.gravityDirection, i.gravity);
            i.x += i.hspeed;
            i.y += i.vspeed;
            i.z += i.zspeed;
        }
        // outside / boundary
        for (Instance i : all) {
            if (i.destroyed) continue;
            if (i.obj.hasOutside || i.obj.hasBoundary) {
                i.updateBBox(this);
                double l = i.hasBox ? i.bl : i.x, r = i.hasBox ? i.br : i.x, t = i.hasBox ? i.bt : i.y, b = i.hasBox ? i.bb : i.y;
                boolean outside = r <= 0 || l >= rs.width || b <= 0 || t >= rs.height;
                if (i.obj.hasOutside && outside) fire(i, Ev.key(Ev.OTHER, Ev.O_OUTSIDE), null);
                if (!i.destroyed && i.obj.hasBoundary && !outside && (l < 0 || r > rs.width || t < 0 || b > rs.height))
                    fire(i, Ev.key(Ev.OTHER, Ev.O_BOUNDARY), null);
            }
            if (!i.destroyed && i.obj.hasOutsideView && rs.viewsEnabled) {
                for (int v = 0; v < 8; v++) {
                    int key = Ev.key(Ev.OTHER, Ev.O_OUTSIDE_VIEW0 + v);
                    if (!i.obj.hasEvent(key) || !rs.views[v].visible) continue;
                    RoomState.ViewRt vw = rs.views[v];
                    i.updateBBox(this);
                    double l = i.hasBox ? i.bl : i.x, r = i.hasBox ? i.br : i.x, t = i.hasBox ? i.bt : i.y, b = i.hasBox ? i.bb : i.y;
                    if (r <= vw.xview || l >= vw.xview + vw.wview || b <= vw.yview || t >= vw.yview + vw.hview) fire(i, key, null);
                }
            }
        }
        // collisions
        collisions(all);
        if (!running) return;
        // end step
        for (Instance i : all) if (!i.destroyed && i.obj.hasEndStep) fire(i, Ev.key(Ev.STEP, Ev.STEP_END), null);
        // animation
        for (Instance i : all) {
            if (i.destroyed) continue;
            Sprite s = project.sprite(i.spriteIndex);
            int n = s == null ? 0 : s.frames.size();
            if (n == 0 || i.imageSpeed == 0) continue;
            i.imageIndex += i.imageSpeed;
            boolean ended = false;
            if (i.imageIndex >= n) { i.imageIndex -= n * Math.floor(i.imageIndex / n); ended = true; }
            else if (i.imageIndex < 0) { i.imageIndex += n * Math.ceil(-i.imageIndex / n); ended = true; if (i.imageIndex >= n) i.imageIndex = 0; }
            if (ended && i.obj.hasAnimEnd) fire(i, Ev.key(Ev.OTHER, Ev.O_ANIMATION_END), null);
        }
        // backgrounds scroll
        for (Room.BgLayer b : rs.backgrounds) { b.x += b.hspeed; b.y += b.vspeed; }
        effects.step();
        updateViews(false);
        input.remap();
        cleanup();
        if (endPending) { endPending = false; end(); return; }
        if (restartPending) { restartPending = false; doRestart(); return; }
        if (pendingRoom >= 0) {
            int r = pendingRoom;
            pendingRoom = -1;
            enterRoom(r, false);
        }
    }

    private static boolean anyOf(boolean[] a) {
        for (int k = 2; k < a.length; k++) if (a[k]) return true;
        return false;
    }

    private boolean anyHandlesKey(int k) {
        for (Instance i : instances) {
            if (i.destroyed || !i.obj.hasKeyboard) continue;
            if (i.obj.hasEvent(Ev.key(Ev.KEYPRESS, k)) || i.obj.hasEvent(Ev.key(Ev.KEYBOARD, k)) || i.obj.hasEvent(Ev.key(Ev.KEYRELEASE, k)))
                return true;
        }
        // code may poll keyboard_check(vk_escape); treat any code mentioning it as handling
        return false;
    }

    private void mouseEvents(Instance[] all) {
        InputState in = input;
        boolean anyDown = false, anyPressed = false, anyReleased = false;
        for (InputState.Touch t : in.touches) {
            if (t.down) anyDown = true;
            if (t.pressed) anyPressed = true;
            if (t.released) anyReleased = true;
        }
        for (Instance i : all) {
            if (i.destroyed || !i.obj.hasMouse) continue;
            boolean in0 = false, pressedIn = false, releasedIn = false;
            i.updateBBox(this);
            if (i.hasBox) for (InputState.Touch t : in.touches) {
                if (!t.down && !t.released) continue;
                boolean inside = t.rx >= i.bl && t.rx < i.br && t.ry >= i.bt && t.ry < i.bb;
                if (!inside) continue;
                if (t.down) in0 = true;
                if (t.pressed) pressedIn = true;
                if (t.released) releasedIn = true;
            }
            boolean wasIn = i.touchInside;
            i.touchInside = in0;
            for (int key : i.obj.keys) {
                if (Ev.typeOf(key) != Ev.MOUSE) continue;
                int n = Ev.numOf(key);
                boolean hit;
                switch (n) {
                    case Ev.M_LEFT: hit = in0; break;
                    case Ev.M_LEFT_PRESSED: hit = pressedIn; break;
                    case Ev.M_LEFT_RELEASED: hit = releasedIn; break;
                    case Ev.M_ENTER: hit = in0 && !wasIn; break;
                    case Ev.M_LEAVE: hit = !in0 && wasIn; break;
                    case Ev.M_NONE: hit = !anyDown; break;
                    case Ev.M_GLOBAL_LEFT: hit = anyDown; break;
                    case Ev.M_GLOBAL_LEFT_PRESSED: hit = anyPressed; break;
                    case Ev.M_GLOBAL_LEFT_RELEASED: hit = anyReleased; break;
                    case Ev.M_SWIPE_LEFT: hit = in.swipeLeft; break;
                    case Ev.M_SWIPE_RIGHT: hit = in.swipeRight; break;
                    case Ev.M_SWIPE_UP: hit = in.swipeUp; break;
                    case Ev.M_SWIPE_DOWN: hit = in.swipeDown; break;
                    case Ev.M_DOUBLE_TAP: hit = in.doubleTap; break;
                    case Ev.M_LONG_PRESS: hit = in.longPress; break;
                    default: hit = false;
                }
                if (hit) { fire(i, key, null); if (i.destroyed) break; }
            }
        }
    }

    private void collisions(Instance[] all) {
        List<Instance> seen = new ArrayList<>();
        for (Instance i : all) {
            if (i.destroyed || i.obj.collisionTargets.length == 0) continue;
            seen.clear();
            for (int target : i.obj.collisionTargets) {
                ObjectRt to = object(target);
                if (to == null) continue;
                int key = Ev.key(Ev.COLLISION, target);
                for (int fid : to.family) {
                    ArrayList<Instance> list = byObj[fid];
                    for (int k = 0; k < list.size(); k++) {
                        Instance j = list.get(k);
                        if (j == i || j.destroyed || i.destroyed) continue;
                        if (seen.contains(j)) continue;
                        if (!overlap(i, j)) continue;
                        seen.add(j);
                        boolean solid = i.solid || j.solid;
                        if (solid) { i.x = i.xprevious; i.y = i.yprevious; }
                        fire(i, key, j);
                        if (i.destroyed) break;
                        if (solid) {
                            double nx = i.x + i.hspeed, ny = i.y + i.vspeed;
                            if (placeMeeting(i, nx, ny, -3, true) == null && !overlapAt(i, nx, ny, j)) { i.x = nx; i.y = ny; }
                        }
                    }
                    if (i.destroyed) break;
                }
                if (i.destroyed) break;
            }
        }
    }

    // =================================================================== events
    /** Runs the (possibly inherited) event of an instance. */
    public void fire(Instance i, int key, Instance other) {
        ObjectRt owner = i.obj.ownerOf(key);
        if (owner == null) return;
        runEvent(owner, key, i, other);
    }

    void runEvent(ObjectRt owner, int key, Instance i, Instance other) {
        Code code = owner.own.get(key);
        if (code == null) return;
        int oldKey = ctx.evKey;
        ObjectRt oldObj = ctx.evObj;
        ctx.evKey = key;
        ctx.evObj = owner;
        try {
            code.run(ctx, i, other == null ? i : other);
        } catch (ScriptError e) {
            error(e);
        } catch (RuntimeException e) {
            error(new ScriptError("Internal error: " + e).in(code.source));
        } finally {
            ctx.evKey = oldKey;
            ctx.evObj = oldObj;
        }
    }

    /** event_inherited(): run the parent's version of the current event. */
    public void inherited() {
        ObjectRt cur = ctx.evObj;
        if (cur == null || cur.parent == null || ctx.self == null) return;
        ObjectRt owner = cur.parent.ownerOf(ctx.evKey);
        if (owner == null) return;
        runEvent(owner, ctx.evKey, ctx.self, ctx.other);
    }

    /** event_perform(type, num) for the current instance. */
    public void perform(Instance i, int type, int num, Instance other) { fire(i, Ev.key(type, num), other); }

    public void fireForAll(int key) {
        for (Instance i : snapshot()) if (!i.destroyed) fire(i, key, null);
    }

    public Object runCode(Code c, Instance self, Instance other) {
        try {
            return c.run(ctx, self, other == null ? self : other);
        } catch (ScriptError e) {
            error(e);
            return V.ZERO;
        }
    }

    private boolean ignoreAll;

    public void error(ScriptError e) {
        String msg = e.describe();
        platform.log("Script error: " + msg);
        if (ignoreAll) return;
        if (!platform.scriptError(msg)) stop();
    }

    public void ignoreAllErrors() { ignoreAll = true; }

    // =================================================================== instances
    public ObjectRt object(int id) { return id >= 0 && id < objects.length ? objects[id] : null; }

    public Instance instance(int id) {
        Instance i = byId.get(id);
        return i == null || i.destroyed ? null : i;
    }

    public List<Instance> activeList() { return instances; }

    public Instance[] snapshot() { return instances.toArray(new Instance[0]); }

    public int instanceCount() {
        int n = 0;
        for (Instance i : instances) if (!i.destroyed) n++;
        return n;
    }

    public Instance instanceByIndex(int k) {
        int n = 0;
        for (Instance i : instances) if (!i.destroyed) { if (n == k) return i; n++; }
        return null;
    }

    public Instance createInstance(int objId, double x, double y, boolean runCreate) {
        ObjectRt o = object(objId);
        if (o == null) throw new ScriptError("Creating instance of non-existing object " + objId);
        Instance i = new Instance(nextId++, o);
        GameObject d = o.def;
        i.spriteIndex = d.sprite;
        i.maskIndex = d.mask;
        i.visible = d.visible;
        i.solid = d.solid;
        i.persistent = d.persistent;
        i.depth = d.depth;
        i.x = i.xstart = i.xprevious = x;
        i.y = i.ystart = i.yprevious = y;
        i.order = orderCounter++;
        instances.add(i);
        byId.put(i.id, i);
        byObj[objId].add(i);
        depthChanged = true;
        listChanged = true;
        if (runCreate) fire(i, Ev.key(Ev.CREATE, 0), null);
        return i;
    }

    public void destroyInstance(Instance i, boolean runEvent) {
        if (i == null || i.destroyed || i.dying) return;
        i.dying = true;
        if (runEvent) fire(i, Ev.key(Ev.DESTROY, 0), null);
        i.destroyed = true;
        byId.remove(i.id);
        needsCleanup = true;
    }

    public void changeInstance(Instance i, int objId, boolean perform) {
        ObjectRt o = object(objId);
        if (o == null || i.destroyed) return;
        if (perform) fire(i, Ev.key(Ev.DESTROY, 0), null);
        byObj[i.obj.id].remove(i);
        i.obj = o;
        byObj[objId].add(i);
        GameObject d = o.def;
        if (d.sprite >= 0) i.spriteIndex = d.sprite;
        i.maskIndex = d.mask;
        i.visible = d.visible;
        i.solid = d.solid;
        i.persistent = d.persistent;
        i.depth = d.depth;
        depthChanged = true;
        listChanged = true;
        if (perform) fire(i, Ev.key(Ev.CREATE, 0), null);
    }

    private void cleanup() {
        if (!needsCleanup) return;
        needsCleanup = false;
        instances.removeIf(i -> i.destroyed);
        for (ArrayList<Instance> l : byObj) if (l != null) l.removeIf(i -> i.destroyed);
        listChanged = true;
    }

    /** Resolves a with()/field target value to instances. */
    public List<Instance> targets(Object t, Ctx c) {
        int id = (int) Math.round(V.toNum(t));
        List<Instance> out = new ArrayList<>();
        switch (id) {
            case -1: if (c.self != null) out.add(c.self); return out;
            case -2: if (c.other != null) out.add(c.other); return out;
            case -3: for (Instance i : instances) if (!i.destroyed) out.add(i); return out;
            case -4: return out;
        }
        if (id >= 100000) {
            Instance i = instance(id);
            if (i != null) out.add(i);
            return out;
        }
        ObjectRt o = object(id);
        if (o == null) return out;
        if (o.family.length == 1) {
            for (Instance i : byObj[id]) if (!i.destroyed) out.add(i);
        } else {
            for (Instance i : instances) if (!i.destroyed && i.obj.isA(id)) out.add(i);
        }
        return out;
    }

    public Instance firstTarget(Object t, Ctx c) {
        int id = (int) Math.round(V.toNum(t));
        switch (id) {
            case -1: return c.self;
            case -2: return c.other;
            case -3: for (Instance i : instances) if (!i.destroyed) return i; return null;
            case -4: return null;
        }
        if (id >= 100000) return instance(id);
        return firstOf(id);
    }

    public Instance firstOf(int objId) {
        if (objId >= 100000) return instance(objId);
        ObjectRt o = object(objId);
        if (o == null) return null;
        for (Instance i : instances) if (!i.destroyed && i.obj.isA(objId)) return i;
        return null;
    }

    public int countOf(int objId) {
        if (objId == -3) return instanceCount();
        ObjectRt o = object(objId);
        if (o == null) return 0;
        int n = 0;
        for (int f : o.family) for (Instance i : byObj[f]) if (!i.destroyed) n++;
        return n;
    }

    /** Candidates for collision checks against a target (object id, instance id or all). */
    public Iterable<Instance> candidates(int target) {
        if (target == -3) return instances;
        if (target >= 100000) {
            Instance i = instance(target);
            return i == null ? Collections.<Instance>emptyList() : Collections.singletonList(i);
        }
        ObjectRt o = object(target);
        if (o == null) return Collections.emptyList();
        if (o.family.length == 1) return byObj[target];
        List<Instance> l = new ArrayList<>();
        for (int f : o.family) l.addAll(byObj[f]);
        return l;
    }

    // =================================================================== globals
    public Object globalVar(String name) {
        Object o = globals.get(name);
        return o == null ? V.ZERO : o;
    }

    public void setGlobalVar(String name, Object v) { globals.put(name, v); }

    public boolean hasGlobalVar(String name) { return globals.containsKey(name); }

    public Map<String, Object> globalsMap() { return globals; }

    // =================================================================== collisions
    public boolean overlap(Instance a, Instance b) {
        a.updateBBox(this);
        b.updateBBox(this);
        if (!a.hasBox || !b.hasBox) return false;
        if (a.br <= b.bl || b.br <= a.bl || a.bb <= b.bt || b.bb <= a.bt) return false;
        boolean ca = a.circleMask(this), cb = b.circleMask(this);
        if (!ca && !cb) return true;
        if (ca && cb) {
            double ra = Math.min(a.br - a.bl, a.bb - a.bt) / 2, rb = Math.min(b.br - b.bl, b.bb - b.bt) / 2;
            double dx = (a.bl + a.br) / 2 - (b.bl + b.br) / 2, dy = (a.bt + a.bb) / 2 - (b.bt + b.bb) / 2;
            return dx * dx + dy * dy < (ra + rb) * (ra + rb);
        }
        Instance c = ca ? a : b, r = ca ? b : a;
        return circleRect((c.bl + c.br) / 2, (c.bt + c.bb) / 2, Math.min(c.br - c.bl, c.bb - c.bt) / 2, r.bl, r.bt, r.br, r.bb);
    }

    static boolean circleRect(double cx, double cy, double rad, double l, double t, double r, double b) {
        double nx = Math.max(l, Math.min(cx, r)), ny = Math.max(t, Math.min(cy, b));
        double dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy < rad * rad;
    }

    public boolean overlapAt(Instance a, double x, double y, Instance b) {
        double ox = a.x, oy = a.y;
        a.x = x; a.y = y;
        boolean r = overlap(a, b);
        a.x = ox; a.y = oy;
        return r;
    }

    /** First instance of target colliding with self placed at (x, y), or null. */
    public Instance placeMeeting(Instance self, double x, double y, int target, boolean solidOnly) {
        double ox = self.x, oy = self.y;
        self.x = x;
        self.y = y;
        Instance hit = null;
        try {
            self.updateBBox(this);
            if (!self.hasBox) return null;
            for (Instance j : candidates(target)) {
                if (j == self || j.destroyed) continue;
                if (solidOnly && !j.solid) continue;
                if (overlap(self, j)) { hit = j; break; }
            }
        } finally {
            self.x = ox;
            self.y = oy;
        }
        return hit;
    }

    public boolean placeFree(Instance self, double x, double y) { return placeMeeting(self, x, y, -3, true) == null; }

    public Instance pointHit(double x, double y, int target, Instance notme) {
        for (Instance j : candidates(target)) {
            if (j == notme || j.destroyed) continue;
            j.updateBBox(this);
            if (!j.hasBox) continue;
            if (x >= j.bl && x < j.br && y >= j.bt && y < j.bb) {
                if (j.circleMask(this)) {
                    double r = Math.min(j.br - j.bl, j.bb - j.bt) / 2;
                    double dx = x - (j.bl + j.br) / 2, dy = y - (j.bt + j.bb) / 2;
                    if (dx * dx + dy * dy >= r * r) continue;
                }
                return j;
            }
        }
        return null;
    }

    public Instance rectHit(double x1, double y1, double x2, double y2, int target, Instance notme, boolean ellipse) {
        double l = Math.min(x1, x2), r = Math.max(x1, x2), t = Math.min(y1, y2), b = Math.max(y1, y2);
        for (Instance j : candidates(target)) {
            if (j == notme || j.destroyed) continue;
            j.updateBBox(this);
            if (!j.hasBox) continue;
            if (j.br <= l || r <= j.bl || j.bb <= t || b <= j.bt) continue;
            if (ellipse) {
                double cx = (l + r) / 2, cy = (t + b) / 2, rx = Math.max(0.5, (r - l) / 2), ry = Math.max(0.5, (b - t) / 2);
                double nx = Math.max(j.bl, Math.min(cx, j.br)), ny = Math.max(j.bt, Math.min(cy, j.bb));
                double dx = (nx - cx) / rx, dy = (ny - cy) / ry;
                if (dx * dx + dy * dy >= 1) continue;
            }
            return j;
        }
        return null;
    }

    public Instance lineHit(double x1, double y1, double x2, double y2, int target, Instance notme) {
        Instance best = null;
        double bestT = Double.MAX_VALUE;
        for (Instance j : candidates(target)) {
            if (j == notme || j.destroyed) continue;
            j.updateBBox(this);
            if (!j.hasBox) continue;
            double tt = segmentBox(x1, y1, x2, y2, j.bl, j.bt, j.br, j.bb);
            if (tt >= 0 && tt < bestT) { bestT = tt; best = j; }
        }
        return best;
    }

    /** Liang–Barsky: parameter of first entry into the box, or -1. */
    static double segmentBox(double x1, double y1, double x2, double y2, double l, double t, double r, double b) {
        double dx = x2 - x1, dy = y2 - y1;
        double t0 = 0, t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 - l, r - x1, y1 - t, b - y1};
        for (int k = 0; k < 4; k++) {
            if (p[k] == 0) { if (q[k] < 0) return -1; }
            else {
                double u = q[k] / p[k];
                if (p[k] < 0) { if (u > t1) return -1; if (u > t0) t0 = u; }
                else { if (u < t0) return -1; if (u < t1) t1 = u; }
            }
        }
        return t0;
    }

    // =================================================================== rooms
    public void gotoRoom(int id) {
        if (project.room(id) == null) throw new ScriptError("Room " + id + " does not exist");
        pendingRoom = id;
    }

    public void restart() { restartPending = true; }

    public void requestEnd() { endPending = true; }

    public int roomNext(int r) {
        List<Integer> order = project.roomOrder;
        int k = order.indexOf(r);
        for (int j = k + 1; j < order.size(); j++) if (project.room(order.get(j)) != null) return order.get(j);
        return -1;
    }

    public int roomPrevious(int r) {
        List<Integer> order = project.roomOrder;
        int k = order.indexOf(r);
        for (int j = k - 1; j >= 0; j--) if (project.room(order.get(j)) != null) return order.get(j);
        return -1;
    }

    private void doRestart() {
        platform.soundStopAll();
        for (Instance i : instances) { i.destroyed = true; }
        instances.clear();
        byId.clear();
        for (ArrayList<Instance> l : byObj) if (l != null) l.clear();
        persistentRooms.clear();
        globals.clear();
        score = 0; lives = -1; health = 100;
        rs = null;
        roomIndex = -1;
        effects.clear();
        if (globalCode != null) runCode(globalCode, null, null);
        enterRoom(project.startRoom(), true);
    }

    public void enterRoom(int id, boolean gameStart) {
        Room def = project.room(id);
        if (def == null) { error(new ScriptError("Room " + id + " does not exist")); return; }
        if (rs != null) {
            fireForAll(Ev.key(Ev.OTHER, Ev.O_ROOM_END));
            if (rs.persistent) {
                rs.saved = new ArrayList<>();
                for (Instance i : instances) if (!i.destroyed && !i.persistent) rs.saved.add(i);
                persistentRooms.put(rs.id, rs);
            }
            List<Instance> keep = new ArrayList<>();
            for (Instance i : instances) if (!i.destroyed && i.persistent) keep.add(i);
            for (Instance i : instances) if (!keep.contains(i)) byId.remove(i.id);
            instances.clear();
            for (ArrayList<Instance> l : byObj) if (l != null) l.clear();
            for (Instance i : keep) { instances.add(i); byObj[i.obj.id].add(i); }
            listChanged = true;
        }
        roomIndex = id;
        RoomState saved = persistentRooms.get(id);
        boolean restored = saved != null && saved.saved != null;
        effects.clear();
        if (restored) {
            rs = saved;
            for (Instance i : rs.saved) {
                instances.add(i);
                byId.put(i.id, i);
                byObj[i.obj.id].add(i);
            }
            rs.saved = null;
        } else {
            rs = new RoomState(def);
            raycaster.reset(def);
            d3Reset(def);
        }
        updateDisplaySize();
        depthChanged = true;
        listChanged = true;
        if (!restored) {
            List<Instance> created = new ArrayList<>();
            List<Room.Inst> defs = new ArrayList<>();
            for (Room.Inst ri : def.instances) {
                if (object(ri.object) == null) continue;
                Instance i = createInstance(ri.object, ri.x, ri.y, false);
                i.imageXscale = ri.xscale;
                i.imageYscale = ri.yscale;
                i.imageAngle = ri.angle;
                created.add(i);
                defs.add(ri);
            }
            for (int k = 0; k < created.size(); k++) {
                Instance i = created.get(k);
                if (i.destroyed) continue;
                fire(i, Ev.key(Ev.CREATE, 0), null);
                String code = defs.get(k).code;
                if (code != null && !code.trim().isEmpty() && !i.destroyed) {
                    Code c = extra("inst" + def.id + "_" + defs.get(k).id, code, def.name + " instance creation code");
                    if (c != null) runCode(c, i, i);
                }
                if (!running) return;
            }
            if (def.creationCode != null && !def.creationCode.trim().isEmpty()) {
                Code c = extra("room" + def.id, def.creationCode, def.name + " creation code");
                if (c != null) runCode(c, null, null);
            }
        }
        if (gameStart) fireForAll(Ev.key(Ev.OTHER, Ev.O_GAME_START));
        fireForAll(Ev.key(Ev.OTHER, Ev.O_ROOM_START));
        updateViews(true);
        input.remap();
    }

    /** Switches to a room without creating its instances or firing events (used by game_load). */
    public void loadRoomBare(int id) {
        Room def = project.room(id);
        if (def == null) return;
        cleanup();
        roomIndex = id;
        rs = new RoomState(def);
        raycaster.reset(def);
        d3Reset(def);
        effects.clear();
        pendingRoom = -1;
        updateDisplaySize();
        depthChanged = true;
        listChanged = true;
    }

    private Code extra(String key, String code, String name) {
        Code c = extraCode.get(key);
        if (c != null && c.text.equals(code)) return c;
        try {
            c = compiler.compile(code, name);
            extraCode.put(key, c);
            return c;
        } catch (ScriptError e) {
            error(e);
            return null;
        }
    }

    /** Compiles and caches arbitrary code (used by execute_string). */
    public Code compileCached(String code) {
        return extra("str:" + code, code, "execute_string");
    }

    private void d3Reset(Room r) {
        d3.camMode = D3State.CAM_AUTO;
        d3.fov = r.d3Fov;
        d3.eyeHeight = r.d3EyeHeight;
        d3.fog = r.d3Fog;
        d3.fogColor = r.d3FogColor;
        d3.fogStart = r.d3FogStart;
        d3.fogEnd = r.d3FogEnd;
        d3.lighting = r.d3Lighting;
        d3.ambient = r.d3Ambient;
        d3.world = Mat4.identity();
    }

    // =================================================================== views & display
    public void setScreenSize(int w, int h) {
        screenW = Math.max(1, w);
        screenH = Math.max(1, h);
        updateDisplaySize();
    }

    public void updateDisplaySize() {
        if (rs != null && !rs.viewsEnabled && rs.mode == Room.MODE_2D) {
            // without views the whole room is shown, like Game Maker's window = room size
            displayW = Math.max(1, rs.width);
            displayH = Math.max(1, rs.height);
        } else {
            // views, 3D and raycast rooms use the project's game resolution
            displayW = Math.max(1, project.gameWidth);
            displayH = Math.max(1, project.gameHeight);
        }
        double baseW = displayW, baseH = displayH;
        if (project.scaleMode == Project.SCALE_EXPAND && (rs == null || rs.viewsEnabled || rs.mode != Room.MODE_2D)) {
            double sa = (double) screenW / screenH, ga = displayW / displayH;
            if (sa > ga) displayW = displayH * sa; else displayH = displayW / sa;
            if (rs != null && rs.viewsEnabled) {
                double fx = displayW / baseW, fy = displayH / baseH;
                for (int k = 0; k < 8; k++) {
                    Room.View dv = rs.def.views[k];
                    RoomState.ViewRt v = rs.views[k];
                    v.xport = dv.xport * fx; v.wport = Math.max(1, dv.wport) * fx;
                    v.yport = dv.yport * fy; v.hport = Math.max(1, dv.hport) * fy;
                    double cx = v.xview + v.wview / 2, cy = v.yview + v.hview / 2;
                    v.wview = Math.max(1, dv.wview) * fx;
                    v.hview = Math.max(1, dv.hview) * fy;
                    v.xview = cx - v.wview / 2;
                    v.yview = cy - v.hview / 2;
                }
            }
        }
        if (project.scaleMode == Project.SCALE_STRETCH) {
            scaleX = screenW / displayW;
            scaleY = screenH / displayH;
            offX = offY = 0;
        } else {
            double s = Math.min(screenW / displayW, screenH / displayH);
            scaleX = scaleY = s;
            offX = (screenW - displayW * s) / 2;
            offY = (screenH - displayH * s) / 2;
        }
    }

    /** {x, y, w, h} of the display area on screen in pixels. */
    public double[] displayRect() { return new double[]{offX, offY, displayW * scaleX, displayH * scaleY}; }

    public double[] screenToDisplay(double sx, double sy) {
        return new double[]{(sx - offX) / scaleX, (sy - offY) / scaleY};
    }

    public double[] displayToRoom(double dx, double dy) {
        if (rs == null) return new double[]{dx, dy};
        if (!rs.viewsEnabled) {
            if (rs.mode != Room.MODE_2D) return new double[]{dx, dy};
            return new double[]{dx, dy};
        }
        RoomState.ViewRt use = null;
        for (int k = 0; k < 8; k++) {
            RoomState.ViewRt v = rs.views[k];
            if (!v.visible) continue;
            if (use == null) use = v;
            if (dx >= v.xport && dx < v.xport + v.wport && dy >= v.yport && dy < v.yport + v.hport) { use = v; break; }
        }
        if (use == null) return new double[]{dx, dy};
        double rx = use.xview + use.shakeX + (dx - use.xport) * use.wview / use.wport;
        double ry = use.yview + use.shakeY + (dy - use.yport) * use.hview / use.hport;
        if (use.angle != 0) {
            double cx = use.xview + use.wview / 2, cy = use.yview + use.hview / 2;
            double a = Math.toRadians(use.angle), c = Math.cos(a), s = Math.sin(a);
            double px = rx - cx, py = ry - cy;
            rx = cx + px * c - py * s;
            ry = cy + px * s + py * c;
        }
        return new double[]{rx, ry};
    }

    private void updateViews(boolean snap) {
        if (rs == null || !rs.viewsEnabled) return;
        for (RoomState.ViewRt v : rs.views) {
            if (!v.visible) continue;
            if (v.follow >= 0 || v.follow <= -100) {
                Instance i = v.follow >= 100000 ? instance(v.follow) : firstOf(v.follow);
                if (i != null) {
                    double tx = v.xview, ty = v.yview;
                    double hb = Math.min(v.hborder, v.wview / 2), vb = Math.min(v.vborder, v.hview / 2);
                    if (i.x - tx < hb) tx = i.x - hb;
                    if (tx + v.wview - i.x < hb) tx = i.x + hb - v.wview;
                    if (i.y - ty < vb) ty = i.y - vb;
                    if (ty + v.hview - i.y < vb) ty = i.y + vb - v.hview;
                    tx = Math.max(0, Math.min(tx, rs.width - v.wview));
                    ty = Math.max(0, Math.min(ty, rs.height - v.hview));
                    if (rs.width < v.wview) tx = (rs.width - v.wview) / 2;
                    if (rs.height < v.hview) ty = (rs.height - v.hview) / 2;
                    if (!snap && v.hspeed >= 0) tx = v.xview + Math.max(-v.hspeed, Math.min(v.hspeed, tx - v.xview));
                    if (!snap && v.vspeed >= 0) ty = v.yview + Math.max(-v.vspeed, Math.min(v.vspeed, ty - v.yview));
                    v.xview = tx;
                    v.yview = ty;
                }
            }
            if (v.shakeTime > 0) {
                v.shakeTime--;
                v.shakeX = (Math.random() * 2 - 1) * v.shake;
                v.shakeY = (Math.random() * 2 - 1) * v.shake;
                v.shake *= 0.92;
                if (v.shakeTime == 0) { v.shakeX = v.shakeY = 0; }
            }
        }
    }

    public void shake(double strength, int steps) {
        if (rs == null) return;
        for (RoomState.ViewRt v : rs.views) { v.shake = strength; v.shakeTime = steps; }
        globalShake = strength;
        globalShakeTime = steps;
    }

    private double globalShake;
    private int globalShakeTime;

    // =================================================================== drawing
    /** Draws a full frame. */
    public void draw(Screen s) {
        if (rs == null) return;
        frameCount++;
        screen = s;
        drawing = true;
        try {
            sortDrawOrder();
            if (rs.mode == Room.MODE_3D) draw3D(s);
            else if (rs.mode == Room.MODE_RAYCAST) drawRaycast(s);
            else draw2D(s);
            // GUI layer
            drawPass = 1;
            s.beginGui(displayW, displayH);
            resetDrawState();
            for (Instance i : drawOrder) {
                if (i.destroyed || !i.visible || !i.obj.hasDrawGui) continue;
                fire(i, Ev.key(Ev.DRAW, Ev.DRAW_GUI), null);
            }
            if (project.showFps) {
                s.text(6, 6, "FPS " + fps + "  inst " + instanceCount(), 14, true, 0, 0, 1, 1, 0, 0x000000, 0.6, -1, -1);
                s.text(5, 5, "FPS " + fps + "  inst " + instanceCount(), 14, true, 0, 0, 1, 1, 0, 0xFFFF66, 1, -1, -1);
            }
        } finally {
            drawing = false;
            cleanup();
        }
    }

    private void resetDrawState() {
        drawAlpha = 1;
        s_blend(0);
    }

    private void s_blend(int m) { if (screen != null) screen.blendMode(m); }

    private void sortDrawOrder() {
        if (!depthChanged && !listChanged && drawOrder.length == countLive()) return;
        Instance[] arr = instances.toArray(new Instance[0]);
        java.util.Arrays.sort(arr, (a, b) -> {
            int c = Double.compare(b.depth, a.depth);
            return c != 0 ? c : Long.compare(a.order, b.order);
        });
        drawOrder = arr;
        depthChanged = false;
        listChanged = false;
    }

    private int countLive() { return instances.size(); }

    private void draw2D(Screen s) {
        if (!rs.viewsEnabled) {
            viewCurrent = 0;
            double sx = globalShakeTime > 0 ? (Math.random() * 2 - 1) * globalShake : 0;
            double sy = globalShakeTime > 0 ? (Math.random() * 2 - 1) * globalShake : 0;
            if (globalShakeTime > 0) globalShakeTime--;
            s.beginView2D(sx, sy, rs.width, rs.height, 0, 0, 0, displayW, displayH);
            drawWorld(s, 0, 0, rs.width, rs.height);
        } else {
            for (int k = 0; k < 8; k++) {
                RoomState.ViewRt v = rs.views[k];
                if (!v.visible) continue;
                viewCurrent = k;
                s.beginView2D(v.xview + v.shakeX, v.yview + v.shakeY, v.wview, v.hview, v.angle, v.xport, v.yport, v.wport, v.hport);
                drawWorld(s, v.xview, v.yview, v.wview, v.hview);
            }
            viewCurrent = 0;
        }
    }

    private void drawWorld(Screen s, double vx, double vy, double vw, double vh) {
        drawPass = 0;
        if (rs.drawBackgroundColor) s.clear(rs.backgroundColor, 1);
        drawBackgrounds(s, false, vx, vy, vw, vh);
        effects.draw(s, false);
        // instances and tiles merged by depth
        List<Room.Tile> tiles = rs.tiles;
        int ti = 0;
        for (Instance i : drawOrder) {
            while (ti < tiles.size() && tiles.get(ti).depth >= i.depth) drawTile(s, tiles.get(ti++), vx, vy, vw, vh);
            if (i.destroyed || !i.visible) continue;
            drawInstance(s, i);
        }
        while (ti < tiles.size()) drawTile(s, tiles.get(ti++), vx, vy, vw, vh);
        effects.draw(s, true);
        drawBackgrounds(s, true, vx, vy, vw, vh);
    }

    private void drawInstance(Screen s, Instance i) {
        if (i.obj.hasDraw) {
            resetDrawState();
            fire(i, Ev.key(Ev.DRAW, Ev.DRAW_NORMAL), null);
        } else if (i.spriteIndex >= 0) {
            drawSelf(i);
        }
    }

    public void drawSelf(Instance i) {
        if (screen == null) return;
        if (rs != null && rs.mode == Room.MODE_RAYCAST && drawPass == 2) { i.rcDrawFrame = frameCount; return; }
        if (rs != null && rs.mode == Room.MODE_3D && drawPass == 3) { draw3DShape(i); return; }
        Sprite sp = project.sprite(i.spriteIndex);
        if (sp == null || sp.frames.isEmpty()) return;
        screen.sprite(i.spriteIndex, frameOf(sp, i.imageIndex), i.x, i.y, i.imageXscale, i.imageYscale, i.imageAngle, i.imageBlend, i.imageAlpha);
    }

    public static int frameOf(Sprite sp, double imageIndex) {
        int n = sp.frames.size();
        if (n == 0) return 0;
        int f = (int) Math.floor(imageIndex) % n;
        return f < 0 ? f + n : f;
    }

    private void drawTile(Screen s, Room.Tile t, double vx, double vy, double vw, double vh) {
        if (t.x + t.w < vx || t.y + t.h < vy || t.x > vx + vw || t.y > vy + vh) return;
        s.backgroundPart(t.bg, t.sx, t.sy, t.w, t.h, t.x, t.y, 1, 1, 0xFFFFFF, 1);
    }

    private void drawBackgrounds(Screen s, boolean fg, double vx, double vy, double vw, double vh) {
        for (Room.BgLayer b : rs.backgrounds) {
            if (!b.visible || b.foreground != fg || b.bg < 0) continue;
            Background bg = project.background(b.bg);
            if (bg == null || bg.file == null) continue;
            if (b.stretch) {
                s.background(b.bg, 0, 0, rs.width / (double) bg.width, rs.height / (double) bg.height, 0, 0xFFFFFF, 1);
                continue;
            }
            double w = bg.width, h = bg.height;
            double x0 = b.x, y0 = b.y;
            if (b.htiled) x0 = vx + ((b.x - vx) % w) - (((b.x - vx) % w) > 0 ? w : 0);
            if (b.vtiled) y0 = vy + ((b.y - vy) % h) - (((b.y - vy) % h) > 0 ? h : 0);
            double x1 = b.htiled ? vx + vw : x0 + 1, y1 = b.vtiled ? vy + vh : y0 + 1;
            int guard = 0;
            for (double yy = y0; yy < y1 && guard < 4000; yy += h)
                for (double xx = x0; xx < x1 && guard++ < 4000; xx += w)
                    s.background(b.bg, xx, yy, 1, 1, 0, 0xFFFFFF, 1);
        }
    }

    // ---------------------------------------------------------------- raycast
    private void drawRaycast(Screen s) {
        double px = 0, py = 0, pw = displayW, ph = displayH;
        if (rs.viewsEnabled && rs.views[0].visible) {
            RoomState.ViewRt v = rs.views[0];
            px = v.xport; py = v.yport; pw = v.wport; ph = v.hport;
        }
        raycaster.render(pw, ph);
        s.raycastImage(raycaster.pixels, raycaster.width, raycaster.height, px, py, pw, ph);
        // Draw events become a HUD drawn over the 3D view
        drawPass = 2;
        s.beginGui(displayW, displayH);
        for (Instance i : drawOrder) {
            if (i.destroyed || !i.visible || !i.obj.hasDraw) continue;
            resetDrawState();
            fire(i, Ev.key(Ev.DRAW, Ev.DRAW_NORMAL), null);
        }
        effects.draw(s, true);
    }

    // ---------------------------------------------------------------- 3D
    private final Mesh tmpMesh = new Mesh();
    private final Mesh floorMesh = new Mesh();

    private void draw3D(Screen s) {
        double px = 0, py = 0, pw = displayW, ph = displayH;
        if (rs.viewsEnabled && rs.views[0].visible) {
            RoomState.ViewRt v = rs.views[0];
            px = v.xport; py = v.yport; pw = v.wport; ph = v.hport;
        }
        int clear = rs.drawBackgroundColor ? rs.backgroundColor : 0;
        s.begin3D(px, py, pw, ph, clear);
        drawPass = 3;
        applyCamera(s, pw / ph);
        s.fog(d3.fog, d3.fogColor, d3.fogStart, d3.fogEnd);
        s.lighting(d3.lighting, d3.ambient, d3.lights);
        s.culling(d3.culling);
        d3.world = Mat4.identity();
        s.world2D(null);
        Room def = rs.def;
        if (def.d3DrawFloor) {
            floorMesh.clear();
            double rep = 1;
            int tex = -1;
            if (def.d3FloorTex >= 0) {
                Background bg = project.background(def.d3FloorTex);
                if (bg != null) { tex = D3State.bgTex(def.d3FloorTex); rep = Math.max(1, rs.width / (double) Math.max(1, bg.width)); }
            }
            Prims.floor(floorMesh, 0, 0, 0, rs.width, rs.height, 0, rep, rep * rs.height / Math.max(1, rs.width));
            s.mesh(floorMesh, null, tex, tex != -1 ? 0xFFFFFF : def.d3FloorColor, 1);
        }
        for (Instance i : drawOrder) {
            if (i.destroyed || !i.visible) continue;
            if (i.obj.hasDraw) {
                resetDrawState();
                d3.world = Mat4.identity();
                s.world2D(null);
                fire(i, Ev.key(Ev.DRAW, Ev.DRAW_NORMAL), null);
            } else draw3DShape(i);
        }
        d3.world = Mat4.identity();
        s.world2D(null);
        s.end3D();
    }

    /** Applies the camera for this frame (auto / first person / third person / explicit). */
    public void applyCamera(Screen s, double aspect) {
        double ex, ey, ez, tx, ty, tz, ux = 0, uy = 0, uz = 1;
        Instance c = null;
        if (d3.camMode == D3State.CAM_FPS || d3.camMode == D3State.CAM_THIRD) c = instance(d3.camInstance);
        if (c == null && d3.camMode != D3State.CAM_EXPLICIT) {
            int f = rs.views[0].follow;
            if (f >= 0) c = firstOf(f);
            if (c != null && d3.camMode != D3State.CAM_THIRD) d3.camMode = D3State.CAM_AUTO;
        }
        if (d3.camMode == D3State.CAM_EXPLICIT) {
            double[] e = d3.explicit;
            ex = e[0]; ey = e[1]; ez = e[2]; tx = e[3]; ty = e[4]; tz = e[5]; ux = e[6]; uy = e[7]; uz = e[8];
        } else if (c != null && d3.camMode == D3State.CAM_THIRD) {
            double a = Math.toRadians(c.direction);
            ex = c.x - Math.cos(a) * d3.camDist;
            ey = c.y + Math.sin(a) * d3.camDist;
            ez = c.z + d3.camHeight;
            tx = c.x; ty = c.y; tz = c.z + d3.eyeHeight;
        } else if (c != null) {
            double a = Math.toRadians(c.direction), p = Math.toRadians(c.pitch);
            ex = c.x; ey = c.y; ez = c.z + d3.eyeHeight;
            tx = ex + Math.cos(a) * Math.cos(p);
            ty = ey - Math.sin(a) * Math.cos(p);
            tz = ez + Math.sin(p);
        } else {
            ex = rs.width / 2.0; ey = rs.height + rs.height * 0.3; ez = Math.max(rs.width, rs.height) * 0.6;
            tx = rs.width / 2.0; ty = rs.height / 2.0; tz = 0;
        }
        d3.camX = ex; d3.camY = ey; d3.camZ = ez;
        float[] view = Mat4.lookAtRoom(ex, ey, ez, tx, ty, tz, ux, uy, uz);
        float[] proj = Mat4.perspective(d3.fov, aspect, d3.znear, d3.zfar);
        s.matrices(view, proj);
    }

    /** Explicit projection set from code during drawing. */
    public void setProjection(double[] e, boolean now) {
        System.arraycopy(e, 0, d3.explicit, 0, 9);
        d3.camMode = D3State.CAM_EXPLICIT;
        d3.camX = e[0]; d3.camY = e[1]; d3.camZ = e[2];
        if (now && screen != null && drawPass == 3) {
            double aspect = displayW / Math.max(1, displayH);
            if (rs.viewsEnabled && rs.views[0].visible) aspect = rs.views[0].wport / Math.max(1, rs.views[0].hport);
            float[] view = Mat4.lookAtRoom(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8]);
            screen.matrices(view, Mat4.perspective(d3.fov, aspect, d3.znear, d3.zfar));
        }
    }

    private void draw3DShape(Instance i) {
        GameObject d = i.obj.def;
        Sprite sp = project.sprite(i.spriteIndex);
        int shape = d.shape3d;
        if (shape == GameObject.SHAPE_NONE) return;
        int frame = sp == null ? 0 : frameOf(sp, i.imageIndex);
        int tex = sp == null || sp.frames.isEmpty() ? -1 : D3State.spriteTex(i.spriteIndex, frame);
        tmpMesh.clear();
        if (shape == GameObject.SHAPE_BLOCK || shape == GameObject.SHAPE_FLOOR) {
            i.updateBBox(this);
            double l, t, r, b;
            if (i.hasBox) { l = i.bl; t = i.bt; r = i.br; b = i.bb; }
            else { l = i.x; t = i.y; r = i.x + rs.def.gridX; b = i.y + rs.def.gridY; }
            if (shape == GameObject.SHAPE_BLOCK) {
                double hgt = d.height3d > 0 ? d.height3d : Math.max(r - l, b - t);
                Prims.block(tmpMesh, l, t, i.z, r, b, i.z + hgt, 1, 1);
            } else {
                Prims.floor(tmpMesh, l, t, i.z, r, b, i.z, 1, 1);
            }
        } else {
            if (sp == null) return;
            double w = sp.width * Math.abs(i.imageXscale), h = sp.height * Math.abs(i.imageYscale);
            Prims.billboard(tmpMesh, i.x, i.y, i.z, w, h, sp.originX * Math.abs(i.imageXscale), d3.camX, d3.camY);
            // sprites look best unlit (they are already shaded art)
            if (d3.lighting) screen.lighting(false, d3.ambient, d3.lights);
            screen.mesh(tmpMesh, null, tex, i.imageBlend, i.imageAlpha);
            if (d3.lighting) screen.lighting(true, d3.ambient, d3.lights);
            return;
        }
        screen.mesh(tmpMesh, null, tex, i.imageBlend, i.imageAlpha);
    }

    // =================================================================== utilities
    public double random() { return Math.random(); }

    public Sprite spriteOrError(int spr) {
        Sprite s = project.sprite(spr);
        if (s == null) throw new ScriptError("Sprite " + spr + " does not exist");
        return s;
    }
}
