package com.droidmaker.core.runtime;

import com.droidmaker.core.json.Json;
import com.droidmaker.core.lang.Ctx;
import com.droidmaker.core.lang.GArray;
import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.lang.V;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.droidmaker.core.lang.Builtins.*;

/** Data structures (ds_*), saving/loading values, ini and text files, and game save/load. */
public final class FnData {
    private FnData() {}

    // data structures are kept per game run in these tables
    static final class Store {
        final List<List<Object>> lists = new ArrayList<>();
        final List<Map<String, Object>> maps = new ArrayList<>();
        final List<Grid> grids = new ArrayList<>();
        final List<ArrayDeque<Object>> stacks = new ArrayList<>();
        final List<ArrayDeque<Object>> queues = new ArrayList<>();
        Map<String, Map<String, String>> ini;
        String iniName;
        final Map<Integer, FileH> files = new HashMap<>();
        int nextFile = 1;
    }

    static final class Grid {
        int w, h;
        Object[] cells;
        Grid(int w, int h) { this.w = w; this.h = h; cells = new Object[w * h]; java.util.Arrays.fill(cells, V.ZERO); }
    }

    static final class FileH {
        String name;
        boolean write;
        StringBuilder out;
        String[] lines;
        int line, col;
    }

    private static final Map<Game, Store> STORES = Collections.synchronizedMap(new java.util.WeakHashMap<>());

    static Store st(Ctx c) {
        Store s = STORES.get(c.game);
        if (s == null) { s = new Store(); STORES.put(c.game, s); }
        return s;
    }

    static <T> T get(List<T> l, int i, String what) {
        if (i < 0 || i >= l.size() || l.get(i) == null) throw new ScriptError(what + " " + i + " does not exist");
        return l.get(i);
    }

    static <T> int add(List<T> l, T v) {
        for (int k = 0; k < l.size(); k++) if (l.get(k) == null) { l.set(k, v); return k; }
        l.add(v);
        return l.size() - 1;
    }

    static String key(Object o) { return o instanceof String ? "s:" + o : "n:" + V.str(o); }
    static Object unkey(String k) { return k.startsWith("s:") ? k.substring(2) : V.num(V.toNum(k.substring(2))); }

    public static void register() {
        category("Lists");
        fn("ds_list_create()", "New list; returns its id", (c, a) -> V.num(add(st(c).lists, new ArrayList<>())));
        fn("ds_list_destroy(id)", (c, a) -> { st(c).lists.set(i(a, 0), null); return null; });
        fn("ds_list_exists(id)", (c, a) -> { List<List<Object>> l = st(c).lists; int k = i(a, 0); return V.bool(k >= 0 && k < l.size() && l.get(k) != null); });
        fn("ds_list_clear(id)", (c, a) -> { list(c, a).clear(); return null; });
        fn("ds_list_size(id)", (c, a) -> V.num(list(c, a).size()));
        fn("ds_list_empty(id)", (c, a) -> V.bool(list(c, a).isEmpty()));
        fn("ds_list_add(id, value, ...)", "Append values", (c, a) -> { List<Object> l = list(c, a); for (int k = 1; k < a.length; k++) l.add(a[k]); return null; });
        fn("ds_list_insert(id, pos, value)", (c, a) -> { List<Object> l = list(c, a); l.add(Math.max(0, Math.min(l.size(), i(a, 1))), a[2]); return null; });
        fn("ds_list_replace(id, pos, value)", (c, a) -> { List<Object> l = list(c, a); int p = i(a, 1); if (p >= 0 && p < l.size()) l.set(p, a[2]); return null; });
        fn("ds_list_set(id, pos, value)", (c, a) -> {
            List<Object> l = list(c, a);
            int p = i(a, 1);
            if (p < 0) return null;
            while (l.size() <= p) l.add(V.ZERO);
            l.set(p, a[2]);
            return null;
        });
        fn("ds_list_delete(id, pos)", (c, a) -> { List<Object> l = list(c, a); int p = i(a, 1); if (p >= 0 && p < l.size()) l.remove(p); return null; });
        fn("ds_list_find_index(id, value)", (c, a) -> {
            List<Object> l = list(c, a);
            for (int k = 0; k < l.size(); k++) if (V.equal(l.get(k), a[1])) return V.num(k);
            return V.num(-1);
        });
        fn("ds_list_find_value(id, pos)", (c, a) -> { List<Object> l = list(c, a); int p = i(a, 1); return p >= 0 && p < l.size() ? l.get(p) : V.ZERO; });
        fn("ds_list_sort(id, ascending)", (c, a) -> {
            List<Object> l = list(c, a);
            boolean asc = b(a, 1);
            l.sort((x, y) -> asc ? V.compare(x, y) : V.compare(y, x));
            return null;
        });
        fn("ds_list_shuffle(id)", (c, a) -> { Collections.shuffle(list(c, a), FnCore.RNG); return null; });
        fn("ds_list_copy(id, source)", (c, a) -> {
            List<Object> d = list(c, a), s = get(st(c).lists, i(a, 1), "List");
            d.clear(); d.addAll(s);
            return null;
        });

        category("Maps");
        fn("ds_map_create()", "New key/value map; returns its id", (c, a) -> V.num(add(st(c).maps, new LinkedHashMap<>())));
        fn("ds_map_destroy(id)", (c, a) -> { st(c).maps.set(i(a, 0), null); return null; });
        fn("ds_map_exists(id, key)", "Key exists in the map", (c, a) -> V.bool(map(c, a).containsKey(key(a[1]))));
        fn("ds_map_clear(id)", (c, a) -> { map(c, a).clear(); return null; });
        fn("ds_map_size(id)", (c, a) -> V.num(map(c, a).size()));
        fn("ds_map_empty(id)", (c, a) -> V.bool(map(c, a).isEmpty()));
        fn("ds_map_add(id, key, value)", (c, a) -> { Map<String, Object> m = map(c, a); if (!m.containsKey(key(a[1]))) m.put(key(a[1]), a[2]); return null; });
        fn("ds_map_set(id, key, value)", (c, a) -> { map(c, a).put(key(a[1]), a[2]); return null; });
        fn("ds_map_replace(id, key, value)", (c, a) -> { map(c, a).put(key(a[1]), a[2]); return null; });
        fn("ds_map_delete(id, key)", (c, a) -> { map(c, a).remove(key(a[1])); return null; });
        fn("ds_map_find_value(id, key)", (c, a) -> { Object o = map(c, a).get(key(a[1])); return o == null ? V.ZERO : o; });
        fn("ds_map_find_first(id)", (c, a) -> { Map<String, Object> m = map(c, a); return m.isEmpty() ? V.ZERO : unkey(m.keySet().iterator().next()); });
        fn("ds_map_find_next(id, key)", (c, a) -> {
            boolean next = false;
            for (String k : map(c, a).keySet()) {
                if (next) return unkey(k);
                if (k.equals(key(a[1]))) next = true;
            }
            return V.ZERO;
        });
        fn("ds_map_keys(id)", "All keys as an array", (c, a) -> {
            GArray arr = new GArray();
            int k = 0;
            for (String s : map(c, a).keySet()) arr.set(0, k++, unkey(s));
            return arr;
        });

        category("Grids");
        fn("ds_grid_create(w, h)", (c, a) -> V.num(add(st(c).grids, new Grid(Math.max(1, i(a, 0)), Math.max(1, i(a, 1))))));
        fn("ds_grid_destroy(id)", (c, a) -> { st(c).grids.set(i(a, 0), null); return null; });
        fn("ds_grid_width(id)", (c, a) -> V.num(grid(c, a).w));
        fn("ds_grid_height(id)", (c, a) -> V.num(grid(c, a).h));
        fn("ds_grid_clear(id, value)", (c, a) -> { java.util.Arrays.fill(grid(c, a).cells, a[1]); return null; });
        fn("ds_grid_set(id, x, y, value)", (c, a) -> {
            Grid g = grid(c, a);
            int x = i(a, 1), y = i(a, 2);
            if (x >= 0 && y >= 0 && x < g.w && y < g.h) g.cells[y * g.w + x] = a[3];
            return null;
        });
        fn("ds_grid_get(id, x, y)", (c, a) -> {
            Grid g = grid(c, a);
            int x = i(a, 1), y = i(a, 2);
            return x >= 0 && y >= 0 && x < g.w && y < g.h ? g.cells[y * g.w + x] : V.ZERO;
        });
        fn("ds_grid_add(id, x, y, value)", (c, a) -> {
            Grid g = grid(c, a);
            int x = i(a, 1), y = i(a, 2);
            if (x >= 0 && y >= 0 && x < g.w && y < g.h) g.cells[y * g.w + x] = V.add(g.cells[y * g.w + x], a[3]);
            return null;
        });
        fn("ds_grid_resize(id, w, h)", (c, a) -> {
            Grid g = grid(c, a);
            Grid n = new Grid(Math.max(1, i(a, 1)), Math.max(1, i(a, 2)));
            for (int y = 0; y < Math.min(g.h, n.h); y++) for (int x = 0; x < Math.min(g.w, n.w); x++) n.cells[y * n.w + x] = g.cells[y * g.w + x];
            g.w = n.w; g.h = n.h; g.cells = n.cells;
            return null;
        });

        category("Stacks & Queues");
        fn("ds_stack_create()", (c, a) -> V.num(add(st(c).stacks, new ArrayDeque<>())));
        fn("ds_stack_destroy(id)", (c, a) -> { st(c).stacks.set(i(a, 0), null); return null; });
        fn("ds_stack_push(id, value)", (c, a) -> { get(st(c).stacks, i(a, 0), "Stack").push(a[1]); return null; });
        fn("ds_stack_pop(id)", (c, a) -> { ArrayDeque<Object> s = get(st(c).stacks, i(a, 0), "Stack"); return s.isEmpty() ? V.ZERO : s.pop(); });
        fn("ds_stack_top(id)", (c, a) -> { ArrayDeque<Object> s = get(st(c).stacks, i(a, 0), "Stack"); return s.isEmpty() ? V.ZERO : s.peek(); });
        fn("ds_stack_size(id)", (c, a) -> V.num(get(st(c).stacks, i(a, 0), "Stack").size()));
        fn("ds_stack_empty(id)", (c, a) -> V.bool(get(st(c).stacks, i(a, 0), "Stack").isEmpty()));
        fn("ds_queue_create()", (c, a) -> V.num(add(st(c).queues, new ArrayDeque<>())));
        fn("ds_queue_destroy(id)", (c, a) -> { st(c).queues.set(i(a, 0), null); return null; });
        fn("ds_queue_enqueue(id, value)", (c, a) -> { get(st(c).queues, i(a, 0), "Queue").addLast(a[1]); return null; });
        fn("ds_queue_dequeue(id)", (c, a) -> { ArrayDeque<Object> q = get(st(c).queues, i(a, 0), "Queue"); return q.isEmpty() ? V.ZERO : q.pollFirst(); });
        fn("ds_queue_head(id)", (c, a) -> { ArrayDeque<Object> q = get(st(c).queues, i(a, 0), "Queue"); return q.isEmpty() ? V.ZERO : q.peekFirst(); });
        fn("ds_queue_size(id)", (c, a) -> V.num(get(st(c).queues, i(a, 0), "Queue").size()));
        fn("ds_queue_empty(id)", (c, a) -> V.bool(get(st(c).queues, i(a, 0), "Queue").isEmpty()));

        category("Saving");
        fn("save_set(key, value)", "Permanently store a number or string", (c, a) -> {
            c.game.platform.saveValue(s(a, 0), (a[1] instanceof String ? "s" : "n") + V.str(a[1]));
            return null;
        });
        fn("save_get(key, default)", "Read a stored value", (c, a) -> {
            String v = c.game.platform.loadValue(s(a, 0));
            if (v == null || v.isEmpty()) return a[1];
            if (v.charAt(0) == 's') return v.substring(1);
            try { return V.num(Double.parseDouble(v.substring(1))); } catch (Exception e) { return a[1]; }
        });
        fn("save_exists(key)", (c, a) -> V.bool(c.game.platform.loadValue(s(a, 0)) != null));
        fn("save_delete(key)", (c, a) -> { c.game.platform.deleteValue(s(a, 0)); return null; });
        fn("ini_open(name)", "Open an ini file for reading/writing", (c, a) -> {
            Store s = st(c);
            s.iniName = s(a, 0);
            s.ini = parseIni(c.game.platform.readFile(s.iniName));
            return null;
        });
        fn("ini_close()", (c, a) -> {
            Store s = st(c);
            if (s.ini != null) c.game.platform.writeFile(s.iniName, writeIni(s.ini));
            s.ini = null;
            return null;
        });
        fn("ini_read_string(section, key, default)", (c, a) -> {
            Map<String, String> sec = ini(c).get(s(a, 0));
            String v = sec == null ? null : sec.get(s(a, 1));
            return v == null ? s(a, 2) : v;
        });
        fn("ini_read_real(section, key, default)", (c, a) -> {
            Map<String, String> sec = ini(c).get(s(a, 0));
            String v = sec == null ? null : sec.get(s(a, 1));
            if (v == null) return a[2];
            try { return V.num(Double.parseDouble(v.trim())); } catch (Exception e) { return a[2]; }
        });
        fn("ini_write_string(section, key, value)", (c, a) -> { iniSec(c, s(a, 0)).put(s(a, 1), s(a, 2)); return null; });
        fn("ini_write_real(section, key, value)", (c, a) -> { iniSec(c, s(a, 0)).put(s(a, 1), V.str(a[2])); return null; });
        fn("ini_key_exists(section, key)", (c, a) -> { Map<String, String> sec = ini(c).get(s(a, 0)); return V.bool(sec != null && sec.containsKey(s(a, 1))); });
        fn("ini_section_exists(section)", (c, a) -> V.bool(ini(c).containsKey(s(a, 0))));
        fn("ini_key_delete(section, key)", (c, a) -> { Map<String, String> sec = ini(c).get(s(a, 0)); if (sec != null) sec.remove(s(a, 1)); return null; });
        fn("ini_section_delete(section)", (c, a) -> { ini(c).remove(s(a, 0)); return null; });

        fn("file_exists(name)", (c, a) -> V.bool(c.game.platform.fileExists(s(a, 0))));
        fn("file_delete(name)", (c, a) -> { c.game.platform.deleteFile(s(a, 0)); return null; });
        fn("file_text_open_read(name)", (c, a) -> {
            String t = c.game.platform.readFile(s(a, 0));
            if (t == null) return V.num(-1);
            FileH f = new FileH();
            f.name = s(a, 0);
            f.lines = t.split("\n", -1);
            Store s = st(c);
            s.files.put(s.nextFile, f);
            return V.num(s.nextFile++);
        });
        fn("file_text_open_write(name)", (c, a) -> openWrite(c, s(a, 0), false));
        fn("file_text_open_append(name)", (c, a) -> openWrite(c, s(a, 0), true));
        fn("file_text_close(file)", (c, a) -> {
            FileH f = st(c).files.remove(i(a, 0));
            if (f != null && f.write) c.game.platform.writeFile(f.name, f.out.toString());
            return null;
        });
        fn("file_text_write_string(file, str)", (c, a) -> { wfile(c, a).out.append(s(a, 1)); return null; });
        fn("file_text_write_real(file, value)", (c, a) -> { FileH f = wfile(c, a); f.out.append(' ').append(V.str(a[1])); return null; });
        fn("file_text_writeln(file)", (c, a) -> { wfile(c, a).out.append('\n'); return null; });
        fn("file_text_read_string(file)", (c, a) -> {
            FileH f = rfile(c, a);
            if (f.line >= f.lines.length) return "";
            String l = f.lines[f.line];
            String r = f.col < l.length() ? l.substring(f.col) : "";
            f.col = l.length();
            return r;
        });
        fn("file_text_read_real(file)", (c, a) -> {
            FileH f = rfile(c, a);
            if (f.line >= f.lines.length) return V.ZERO;
            String l = f.lines[f.line];
            int k = f.col;
            while (k < l.length() && Character.isWhitespace(l.charAt(k))) k++;
            int st = k;
            while (k < l.length() && !Character.isWhitespace(l.charAt(k))) k++;
            f.col = k;
            try { return V.num(Double.parseDouble(l.substring(st, k))); } catch (Exception e) { return V.ZERO; }
        });
        fn("file_text_readln(file)", (c, a) -> {
            FileH f = rfile(c, a);
            String r = f.line < f.lines.length ? f.lines[f.line].substring(Math.min(f.col, f.lines[f.line].length())) : "";
            f.line++;
            f.col = 0;
            return r;
        });
        fn("file_text_eof(file)", (c, a) -> { FileH f = rfile(c, a); return V.bool(f.line >= f.lines.length || (f.line == f.lines.length - 1 && f.col >= f.lines[f.line].length())); });

        fn("json_encode(map)", "Map (with nested lists/maps as values) to JSON text", (c, a) -> Json.writeCompact(toJson(c, map(c, a))));
        fn("json_decode(text)", "JSON text to a new ds_map (-1 if invalid)", (c, a) -> {
            try {
                Object o = Json.parse(s(a, 0));
                if (!(o instanceof Map)) return V.num(-1);
                return fromJson(c, o);
            } catch (Exception e) {
                return V.num(-1);
            }
        });

        fn("game_save(slot)", "Save the whole game state", (c, a) -> { saveGame(c, s(a, 0)); return null; });
        fn("game_load(slot)", "Load a saved game state", (c, a) -> V.bool(loadGame(c, s(a, 0))));
        fn("game_save_exists(slot)", (c, a) -> V.bool(c.game.platform.fileExists("save_" + s(a, 0) + ".json")));
    }

    static List<Object> list(Ctx c, Object[] a) { return get(st(c).lists, i(a, 0), "List"); }
    static Map<String, Object> map(Ctx c, Object[] a) { return get(st(c).maps, i(a, 0), "Map"); }
    static Grid grid(Ctx c, Object[] a) { return get(st(c).grids, i(a, 0), "Grid"); }

    static Map<String, Map<String, String>> ini(Ctx c) {
        Store s = st(c);
        if (s.ini == null) throw new ScriptError("No ini file is open (use ini_open first)");
        return s.ini;
    }

    static Map<String, String> iniSec(Ctx c, String sec) {
        Map<String, Map<String, String>> m = ini(c);
        Map<String, String> s = m.get(sec);
        if (s == null) { s = new LinkedHashMap<>(); m.put(sec, s); }
        return s;
    }

    static Map<String, Map<String, String>> parseIni(String t) {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        if (t == null) return m;
        Map<String, String> cur = null;
        for (String line : t.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                cur = new LinkedHashMap<>();
                m.put(line.substring(1, line.length() - 1), cur);
            } else if (cur != null && line.contains("=")) {
                int k = line.indexOf('=');
                cur.put(line.substring(0, k).trim(), line.substring(k + 1).trim());
            }
        }
        return m;
    }

    static String writeIni(Map<String, Map<String, String>> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> e : m.entrySet()) {
            b.append('[').append(e.getKey()).append("]\n");
            for (Map.Entry<String, String> kv : e.getValue().entrySet()) b.append(kv.getKey()).append('=').append(kv.getValue()).append('\n');
        }
        return b.toString();
    }

    static Object openWrite(Ctx c, String name, boolean append) {
        FileH f = new FileH();
        f.name = name;
        f.write = true;
        f.out = new StringBuilder();
        if (append) { String t = c.game.platform.readFile(name); if (t != null) f.out.append(t); }
        Store s = st(c);
        s.files.put(s.nextFile, f);
        return V.num(s.nextFile++);
    }

    static FileH wfile(Ctx c, Object[] a) {
        FileH f = st(c).files.get(i(a, 0));
        if (f == null || !f.write) throw new ScriptError("File is not open for writing");
        return f;
    }

    static FileH rfile(Ctx c, Object[] a) {
        FileH f = st(c).files.get(i(a, 0));
        if (f == null || f.write) throw new ScriptError("File is not open for reading");
        return f;
    }

    static Object toJson(Ctx c, Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) out.put(V.str(unkey(e.getKey())), valueToJson(e.getValue()));
        return out;
    }

    static Object valueToJson(Object v) {
        if (v instanceof GArray) {
            List<Object> l = new ArrayList<>();
            GArray g = (GArray) v;
            for (int k = 0; k < g.length(0); k++) l.add(valueToJson(g.get(0, k)));
            return l;
        }
        return v;
    }

    static Object fromJson(Ctx c, Object o) {
        if (o instanceof Map) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : Json.obj(o).entrySet()) m.put("s:" + e.getKey(), fromJson(c, e.getValue()));
            return V.num(add(st(c).maps, m));
        }
        if (o instanceof List) {
            List<Object> l = new ArrayList<>();
            for (Object x : Json.arr(o)) l.add(fromJson(c, x));
            return V.num(add(st(c).lists, l));
        }
        if (o instanceof Boolean) return V.bool((Boolean) o);
        if (o == null) return V.ZERO;
        if (o instanceof Double) return V.num((Double) o);
        return o.toString();
    }

    // ------------------------------------------------------------------ game save/load
    static Object enc(Object v) {
        if (v instanceof String) return "s:" + v;
        if (v instanceof GArray) {
            GArray g = (GArray) v;
            List<Object> rows = new ArrayList<>();
            for (int r = 0; r < g.height(); r++) {
                List<Object> row = new ArrayList<>();
                for (int k = 0; k < g.length(r); k++) row.add(enc(g.get(r, k)));
                rows.add(row);
            }
            return rows;
        }
        return V.toNum(v);
    }

    static Object dec(Object o) {
        if (o instanceof String) { String s = (String) o; return s.startsWith("s:") ? s.substring(2) : s; }
        if (o instanceof List) {
            GArray g = new GArray();
            List<Object> rows = Json.arr(o);
            for (int r = 0; r < rows.size(); r++) {
                List<Object> row = Json.arr(rows.get(r));
                for (int k = 0; k < row.size(); k++) g.set(r, k, dec(row.get(k)));
            }
            return g;
        }
        return V.num(o instanceof Number ? ((Number) o).doubleValue() : 0);
    }

    static void saveGame(Ctx c, String slot) {
        Game g = c.game;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("room", (double) g.roomIndex);
        root.put("score", g.score);
        root.put("lives", g.lives);
        root.put("health", g.health);
        Map<String, Object> gl = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : g.globalsMap().entrySet()) gl.put(e.getKey(), enc(e.getValue()));
        root.put("globals", gl);
        List<Object> insts = new ArrayList<>();
        for (Instance i : g.activeList()) {
            if (i.destroyed) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("obj", (double) i.obj.id);
            double[] f = {i.x, i.y, i.xstart, i.ystart, i.hspeed, i.vspeed, i.speed, i.direction, i.friction, i.gravity,
                    i.gravityDirection, i.spriteIndex, i.imageIndex, i.imageSpeed, i.imageXscale, i.imageYscale, i.imageAngle,
                    i.imageAlpha, i.imageBlend, i.depth, i.visible ? 1 : 0, i.solid ? 1 : 0, i.persistent ? 1 : 0, i.z, i.pitch, i.maskIndex};
            List<Object> fl = new ArrayList<>();
            for (double d : f) fl.add(d);
            m.put("f", fl);
            List<Object> al = new ArrayList<>();
            for (double d : i.alarm) al.add(d);
            m.put("alarm", al);
            Map<String, Object> vars = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : i.varsMap().entrySet()) vars.put(e.getKey(), enc(e.getValue()));
            m.put("vars", vars);
            insts.add(m);
        }
        root.put("instances", insts);
        c.game.platform.writeFile("save_" + slot + ".json", Json.writeCompact(root));
    }

    static boolean loadGame(Ctx c, String slot) {
        Game g = c.game;
        String t = g.platform.readFile("save_" + slot + ".json");
        if (t == null) return false;
        Map<String, Object> root = Json.obj(Json.parse(t));
        int room = Json.integer(root, "room", g.roomIndex);
        if (g.project.room(room) == null) return false;
        // switch room immediately without running create events of its placed instances
        for (Instance i : g.snapshot()) g.destroyInstance(i, false);
        g.score = Json.num(root, "score", 0);
        g.lives = Json.num(root, "lives", -1);
        g.health = Json.num(root, "health", 100);
        g.globalsMap().clear();
        for (Map.Entry<String, Object> e : Json.obj(root.get("globals")).entrySet()) g.globalsMap().put(e.getKey(), dec(e.getValue()));
        g.loadRoomBare(room);
        for (Object o : Json.arr(root.get("instances"))) {
            Map<String, Object> m = Json.obj(o);
            int obj = Json.integer(m, "obj", -1);
            if (g.object(obj) == null) continue;
            Instance i = g.createInstance(obj, 0, 0, false);
            List<Object> f = Json.arr(m.get("f"));
            double[] v = new double[26];
            for (int k = 0; k < v.length && k < f.size(); k++) v[k] = ((Number) f.get(k)).doubleValue();
            i.x = v[0]; i.y = v[1]; i.xstart = v[2]; i.ystart = v[3]; i.hspeed = v[4]; i.vspeed = v[5]; i.speed = v[6];
            i.direction = v[7]; i.friction = v[8]; i.gravity = v[9]; i.gravityDirection = v[10]; i.spriteIndex = (int) v[11];
            i.imageIndex = v[12]; i.imageSpeed = v[13]; i.imageXscale = v[14]; i.imageYscale = v[15]; i.imageAngle = v[16];
            i.imageAlpha = v[17]; i.imageBlend = (int) v[18]; i.depth = v[19]; i.visible = v[20] != 0; i.solid = v[21] != 0;
            i.persistent = v[22] != 0; i.z = v[23]; i.pitch = v[24]; i.maskIndex = (int) v[25];
            i.xprevious = i.x; i.yprevious = i.y;
            List<Object> al = Json.arr(m.get("alarm"));
            for (int k = 0; k < 12 && k < al.size(); k++) i.alarm[k] = ((Number) al.get(k)).doubleValue();
            for (Map.Entry<String, Object> e : Json.obj(m.get("vars")).entrySet()) i.setVar(e.getKey(), dec(e.getValue()));
        }
        g.depthChanged = true;
        return true;
    }
}
