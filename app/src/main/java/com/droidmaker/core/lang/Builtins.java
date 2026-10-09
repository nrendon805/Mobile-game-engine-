package com.droidmaker.core.lang;

import com.droidmaker.core.runtime.Vars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry of built-in functions and constants. Function bodies live in the runtime Fn* classes. */
public final class Builtins {
    private Builtins() {}

    public interface Fn { Object call(Ctx c, Object[] a); }

    public static final class Info {
        public final String name, signature, help;
        public final int min, max;
        public final Fn fn;
        public final String category;

        Info(String name, String signature, String help, int min, int max, Fn fn, String category) {
            this.name = name; this.signature = signature; this.help = help; this.min = min; this.max = max; this.fn = fn; this.category = category;
        }
    }

    private static final Map<String, Info> FUNCS = new HashMap<>();
    private static final Map<String, Object> CONSTS = new HashMap<>();
    private static final List<String> NAMES_SORTED = new ArrayList<>();
    private static String category = "General";
    private static boolean loaded;

    public static final Set<String> KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "var", "globalvar", "if", "then", "else", "while", "do", "until", "for", "repeat", "with", "switch",
            "case", "default", "break", "continue", "exit", "return", "and", "or", "xor", "not", "div", "mod",
            "begin", "end", "self", "other", "all", "noone", "global", "true", "false", "argument", "argument_count"));

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        com.droidmaker.core.runtime.FnCore.register();
        com.droidmaker.core.runtime.FnGame.register();
        com.droidmaker.core.runtime.FnDraw.register();
        com.droidmaker.core.runtime.Fn3D.register();
        com.droidmaker.core.runtime.FnData.register();
        NAMES_SORTED.addAll(FUNCS.keySet());
        Collections.sort(NAMES_SORTED);
    }

    public static void category(String c) { category = c; }

    /**
     * Registers a function. Signature like "draw_sprite(sprite, subimg, x, y)"; optional args in
     * square brackets "[alpha]"; "..." means any number of extra args.
     */
    public static void fn(String signature, String help, Fn f) {
        int p = signature.indexOf('(');
        String name = signature.substring(0, p).trim();
        String inner = signature.substring(p + 1, signature.lastIndexOf(')')).trim();
        int min = 0, max = 0;
        if (!inner.isEmpty()) {
            for (String a : inner.split(",")) {
                a = a.trim();
                if (a.equals("...")) { max = 99; continue; }
                if (a.startsWith("[")) max++;
                else { min++; max++; }
            }
        }
        FUNCS.put(name, new Info(name, signature, help, min, max, f, category));
    }

    public static void fn(String signature, Fn f) { fn(signature, "", f); }

    public static void constant(String name, Object v) { CONSTS.put(name, v instanceof Integer ? V.num((Integer) v) : v); }

    public static void constant(String name, double v) { CONSTS.put(name, V.num(v)); }

    public static Info function(String name) { ensureLoaded(); return FUNCS.get(name); }

    public static Object constantValue(String name) { ensureLoaded(); return CONSTS.get(name); }

    public static List<String> functionNames() { ensureLoaded(); return NAMES_SORTED; }

    public static List<String> constantNames() {
        ensureLoaded();
        List<String> l = new ArrayList<>(CONSTS.keySet());
        Collections.sort(l);
        return l;
    }

    public static boolean isReservedName(String n) {
        ensureLoaded();
        return KEYWORDS.contains(n) || FUNCS.containsKey(n) || CONSTS.containsKey(n)
                || Vars.instIndex(n) >= 0 || Vars.globalIndex(n) >= 0 || n.matches("argument\\d+");
    }

    // -------------------------------------------------- argument helpers for function bodies
    public static double n(Object[] a, int i) { return i < a.length ? V.toNum(a[i]) : 0; }
    public static double n(Object[] a, int i, double def) { return i < a.length ? V.toNum(a[i]) : def; }
    public static int i(Object[] a, int i) { return i < a.length ? (int) Math.floor(V.toNum(a[i]) + 0.0000001) : 0; }
    public static int i(Object[] a, int i, int def) { return i < a.length ? (int) Math.floor(V.toNum(a[i]) + 0.0000001) : def; }
    public static String s(Object[] a, int i) { return i < a.length ? V.str(a[i]) : ""; }
    public static boolean b(Object[] a, int i) { return i < a.length && V.truth(a[i]); }
    public static boolean b(Object[] a, int i, boolean def) { return i < a.length ? V.truth(a[i]) : def; }
}
