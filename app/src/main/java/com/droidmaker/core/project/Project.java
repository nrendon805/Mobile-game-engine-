package com.droidmaker.core.project;

import com.droidmaker.core.json.Json;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A whole game project. Resource ids are indices into the per-type lists; deleted resources leave
 * a null hole so ids stay stable (like Game Maker).
 */
public class Project {
    public static final int FORMAT_VERSION = 1;

    public String name = "Untitled";
    /** Directory holding project.json plus img/ and snd/ folders. Null for in-memory projects. */
    public transient File dir;

    // ---- global game settings
    public int gameWidth = 640, gameHeight = 360;
    public static final int ORIENT_LANDSCAPE = 0, ORIENT_PORTRAIT = 1, ORIENT_AUTO = 2;
    public int orientation = ORIENT_LANDSCAPE;
    public static final int SCALE_FIT = 0, SCALE_STRETCH = 1, SCALE_EXPAND = 2;
    public int scaleMode = SCALE_FIT;
    public boolean interpolate = false;
    public boolean showFps = false;
    public static final int PAD_NONE = 0, PAD_DPAD = 1, PAD_STICK = 2;
    public int vpad = PAD_NONE;
    public int vpadButtons = 2;
    /** Key codes produced by the virtual A, B, X, Y buttons. */
    public int[] vpadKeys = {Keys.SPACE, 'Z', 'X', Keys.ENTER};
    public double vpadOpacity = 0.45;
    public boolean keepScreenOn = true;
    public String globalCode = "";

    public final List<Sprite> sprites = new ArrayList<>();
    public final List<Sound> sounds = new ArrayList<>();
    public final List<Background> backgrounds = new ArrayList<>();
    public final List<Script> scripts = new ArrayList<>();
    public final List<GameObject> objects = new ArrayList<>();
    public final List<Room> rooms = new ArrayList<>();
    /** Room play order (room ids). The first is the start room. */
    public final List<Integer> roomOrder = new ArrayList<>();
    /** Empty folders the user created, stored as "TYPE:path". */
    public final List<String> folders = new ArrayList<>();

    // ------------------------------------------------------------------ access
    @SuppressWarnings("unchecked")
    public List<? extends Resource> list(ResType t) {
        switch (t) {
            case SPRITE: return sprites;
            case SOUND: return sounds;
            case BACKGROUND: return backgrounds;
            case SCRIPT: return scripts;
            case OBJECT: return objects;
            default: return rooms;
        }
    }

    private static <T> T at(List<T> l, int i) { return i >= 0 && i < l.size() ? l.get(i) : null; }
    public Sprite sprite(int i) { return at(sprites, i); }
    public Sound sound(int i) { return at(sounds, i); }
    public Background background(int i) { return at(backgrounds, i); }
    public Script script(int i) { return at(scripts, i); }
    public GameObject object(int i) { return at(objects, i); }
    public Room room(int i) { return at(rooms, i); }
    public Resource resource(ResType t, int i) { return at(list(t), i); }

    public Resource find(String name) {
        for (ResType t : ResType.values()) {
            Resource r = find(t, name);
            if (r != null) return r;
        }
        return null;
    }

    public Resource find(ResType t, String name) {
        for (Resource r : list(t)) if (r != null && r.name.equals(name)) return r;
        return null;
    }

    public int count(ResType t) {
        int n = 0;
        for (Resource r : list(t)) if (r != null) n++;
        return n;
    }

    /** All live resources of a type (no null holes). */
    public List<Resource> live(ResType t) {
        List<Resource> out = new ArrayList<>();
        for (Resource r : list(t)) if (r != null) out.add(r);
        return out;
    }

    /** A name not used by any resource, based on the given stem. */
    public String uniqueName(String stem) {
        if (find(stem) == null && !isReserved(stem)) return stem;
        for (int i = 1; ; i++) if (find(stem + i) == null) return stem + i;
    }

    public static boolean isReserved(String n) {
        return com.droidmaker.core.lang.Builtins.isReservedName(n);
    }

    public static boolean isValidName(String n) {
        return n != null && n.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    @SuppressWarnings("unchecked")
    public <T extends Resource> T add(T r) {
        List<Resource> l = (List<Resource>) list(r.type());
        r.id = l.size();
        if (r.name == null || r.name.isEmpty()) r.name = uniqueName(r.type().prefix);
        l.add(r);
        if (r instanceof Room) roomOrder.add(r.id);
        return r;
    }

    public Sprite newSprite() { Sprite s = new Sprite(); s.name = uniqueName("spr_sprite"); return add(s); }
    public Sound newSound() { Sound s = new Sound(); s.name = uniqueName("snd_sound"); return add(s); }
    public Background newBackground() { Background b = new Background(); b.name = uniqueName("bg_background"); return add(b); }
    public Script newScript() { Script s = new Script(); s.name = uniqueName("scr_script"); return add(s); }
    public GameObject newObject() { GameObject o = new GameObject(); o.name = uniqueName("obj_object"); return add(o); }

    public Room newRoom() {
        Room r = new Room();
        r.name = uniqueName("rm_room");
        r.width = Math.max(gameWidth, 64);
        r.height = Math.max(gameHeight, 64);
        for (Room.View v : r.views) {
            v.wview = gameWidth; v.hview = gameHeight; v.wport = gameWidth; v.hport = gameHeight;
            v.hborder = gameWidth / 3; v.vborder = gameHeight / 3;
        }
        return add(r);
    }

    public Resource create(ResType t) {
        switch (t) {
            case SPRITE: return newSprite();
            case SOUND: return newSound();
            case BACKGROUND: return newBackground();
            case SCRIPT: return newScript();
            case OBJECT: return newObject();
            default: return newRoom();
        }
    }

    /** Deletes a resource and clears structural references to it. */
    @SuppressWarnings("unchecked")
    public void delete(Resource r) {
        List<Resource> l = (List<Resource>) list(r.type());
        if (r.id < 0 || r.id >= l.size() || l.get(r.id) != r) return;
        l.set(r.id, null);
        int id = r.id;
        switch (r.type()) {
            case SPRITE:
                for (GameObject o : objects) if (o != null) {
                    if (o.sprite == id) o.sprite = -1;
                    if (o.mask == id) o.mask = -1;
                }
                break;
            case BACKGROUND:
                for (Room rm : rooms) if (rm != null) {
                    for (Room.BgLayer b : rm.backgrounds) if (b.bg == id) { b.bg = -1; b.visible = false; }
                    rm.tiles.removeIf(t -> t.bg == id);
                    if (rm.rcFloorTex == id) rm.rcFloorTex = -1;
                    if (rm.rcCeilTex == id) rm.rcCeilTex = -1;
                    if (rm.d3FloorTex == id) rm.d3FloorTex = -1;
                }
                break;
            case OBJECT:
                for (GameObject o : objects) if (o != null) {
                    if (o.parent == id) o.parent = -1;
                    o.events.remove(Ev.key(Ev.COLLISION, id));
                }
                for (Room rm : rooms) if (rm != null) {
                    rm.instances.removeIf(i -> i.object == id);
                    for (Room.View v : rm.views) if (v.follow == id) v.follow = -1;
                }
                break;
            case ROOM:
                roomOrder.remove(Integer.valueOf(id));
                break;
            default:
                break;
        }
    }

    /** Renames a resource and rewrites references to the old name in code and action arguments. */
    public void rename(Resource r, String newName) {
        String old = r.name;
        r.name = newName;
        if (old.equals(newName) || old.isEmpty()) return;
        Pattern p = Pattern.compile("\\b" + Pattern.quote(old) + "\\b");
        String rep = Matcher.quoteReplacement(newName);
        for (Script s : scripts) if (s != null) s.code = replaceOutsideStrings(s.code, p, rep);
        for (GameObject o : objects) if (o == null) continue; else
            for (List<Action> acts : o.events.values())
                for (Action a : acts) {
                    ActionDef d = a.def();
                    for (int i = 0; i < a.args.size(); i++) {
                        ActionDef.ArgType at = d != null && i < d.args.length ? d.args[i].type : ActionDef.ArgType.EXPR;
                        if (at == ActionDef.ArgType.STRING || at == ActionDef.ArgType.TEXT) continue;
                        a.args.set(i, replaceOutsideStrings(a.args.get(i), p, rep));
                    }
                }
        for (Room rm : rooms) if (rm != null) {
            rm.creationCode = replaceOutsideStrings(rm.creationCode, p, rep);
            for (Room.Inst i : rm.instances) i.code = replaceOutsideStrings(i.code, p, rep);
        }
        globalCode = replaceOutsideStrings(globalCode, p, rep);
    }

    private static String replaceOutsideStrings(String code, Pattern p, String rep) {
        if (code == null || code.isEmpty()) return code;
        StringBuilder out = new StringBuilder();
        int i = 0, n = code.length();
        int segStart = 0;
        while (i < n) {
            char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                out.append(p.matcher(code.substring(segStart, i)).replaceAll(rep));
                int j = code.indexOf(c, i + 1);
                if (j < 0) j = n - 1;
                out.append(code, i, j + 1);
                i = j + 1;
                segStart = i;
            } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                out.append(p.matcher(code.substring(segStart, i)).replaceAll(rep));
                int j = code.indexOf('\n', i);
                if (j < 0) j = n;
                out.append(code, i, j);
                i = j;
                segStart = i;
            } else i++;
        }
        out.append(p.matcher(code.substring(segStart)).replaceAll(rep));
        return out.toString();
    }

    public int startRoom() {
        for (int id : roomOrder) if (room(id) != null) return id;
        for (Room r : rooms) if (r != null) return r.id;
        return -1;
    }

    // ------------------------------------------------------------------ json
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", (double) FORMAT_VERSION);
        m.put("name", name);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("width", (double) gameWidth);
        s.put("height", (double) gameHeight);
        s.put("orientation", (double) orientation);
        s.put("scaleMode", (double) scaleMode);
        s.put("interpolate", interpolate);
        s.put("showFps", showFps);
        s.put("vpad", (double) vpad);
        s.put("vpadButtons", (double) vpadButtons);
        s.put("vpadKeys", Sprite.listOf(vpadKeys));
        s.put("vpadOpacity", vpadOpacity);
        s.put("keepScreenOn", keepScreenOn);
        s.put("globalCode", globalCode);
        m.put("settings", s);
        m.put("sprites", listJson(sprites));
        m.put("sounds", listJson(sounds));
        m.put("backgrounds", listJson(backgrounds));
        m.put("scripts", listJson(scripts));
        m.put("objects", listJson(objects));
        m.put("rooms", listJson(rooms));
        List<Object> ro = new ArrayList<>();
        for (int i : roomOrder) ro.add((double) i);
        m.put("roomOrder", ro);
        m.put("folders", new ArrayList<Object>(folders));
        return m;
    }

    private static List<Object> listJson(List<? extends Resource> l) {
        List<Object> out = new ArrayList<>();
        for (Resource r : l) out.add(r == null ? null : r.toJson());
        return out;
    }

    public static Project fromJson(Map<String, Object> m) {
        Project p = new Project();
        p.name = Json.str(m, "name", "Untitled");
        Map<String, Object> s = Json.obj(m.get("settings"));
        p.gameWidth = Json.integer(s, "width", 640);
        p.gameHeight = Json.integer(s, "height", 360);
        p.orientation = Json.integer(s, "orientation", ORIENT_LANDSCAPE);
        p.scaleMode = Json.integer(s, "scaleMode", SCALE_FIT);
        p.interpolate = Json.bool(s, "interpolate", false);
        p.showFps = Json.bool(s, "showFps", false);
        p.vpad = Json.integer(s, "vpad", PAD_NONE);
        p.vpadButtons = Json.integer(s, "vpadButtons", 2);
        List<Object> vk = Json.arr(s.get("vpadKeys"));
        for (int i = 0; i < 4 && i < vk.size(); i++) p.vpadKeys[i] = ((Number) vk.get(i)).intValue();
        p.vpadOpacity = Json.num(s, "vpadOpacity", 0.45);
        p.keepScreenOn = Json.bool(s, "keepScreenOn", true);
        p.globalCode = Json.str(s, "globalCode", "");
        load(p.sprites, m.get("sprites"), Sprite::new);
        load(p.sounds, m.get("sounds"), Sound::new);
        load(p.backgrounds, m.get("backgrounds"), Background::new);
        load(p.scripts, m.get("scripts"), Script::new);
        load(p.objects, m.get("objects"), GameObject::new);
        load(p.rooms, m.get("rooms"), Room::new);
        for (Object o : Json.arr(m.get("roomOrder"))) {
            int id = ((Number) o).intValue();
            if (p.room(id) != null && !p.roomOrder.contains(id)) p.roomOrder.add(id);
        }
        for (Room r : p.rooms) if (r != null && !p.roomOrder.contains(r.id)) p.roomOrder.add(r.id);
        for (Object o : Json.arr(m.get("folders"))) p.folders.add(String.valueOf(o));
        return p;
    }

    private static <T extends Resource> void load(List<T> into, Object arr, java.util.function.Supplier<T> f) {
        into.clear();
        List<Object> l = Json.arr(arr);
        for (int i = 0; i < l.size(); i++) {
            Object o = l.get(i);
            if (o == null) { into.add(null); continue; }
            T r = f.get();
            r.fromJson(Json.obj(o));
            r.id = i;
            into.add(r);
        }
    }

    // ------------------------------------------------------------------ files
    public static final String PROJECT_FILE = "project.json";

    public void save() throws IOException {
        if (dir == null) throw new IOException("Project has no directory");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File tmp = new File(dir, PROJECT_FILE + ".tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(Json.write(toJson()).getBytes(StandardCharsets.UTF_8));
        }
        File f = new File(dir, PROJECT_FILE);
        if (f.exists() && !f.delete()) throw new IOException("Cannot replace " + f);
        if (!tmp.renameTo(f)) throw new IOException("Cannot write " + f);
    }

    public static Project load(File dir) throws IOException {
        File f = new File(dir, PROJECT_FILE);
        String text = readText(f);
        Project p = fromJson(Json.obj(Json.parse(text)));
        p.dir = dir;
        return p;
    }

    public static String readText(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    public File file(String rel) { return rel == null ? null : new File(dir, rel); }
}
