package com.droidmaker.core.lang;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles code for one project. Knows resource names (as constants), scripts (as callables) and
 * names declared with globalvar anywhere in the project.
 */
public final class Compiler {
    public static final class ScriptRef {
        public final String name;
        public final int index;
        public Code code;
        public String text;

        public ScriptRef(String name, int index, String text) { this.name = name; this.index = index; this.text = text; }
    }

    final Map<String, Double> resources = new HashMap<>();
    final Map<String, ScriptRef> scripts = new HashMap<>();
    final Set<String> globalvars = new HashSet<>();

    public Compiler() { Builtins.ensureLoaded(); }

    public void addResource(String name, int id) { resources.put(name, (double) id); }

    public ScriptRef addScript(String name, int index, String text) {
        ScriptRef r = new ScriptRef(name, index, text);
        scripts.put(name, r);
        resources.put(name, (double) index);
        prescanGlobals(text);
        return r;
    }

    public ScriptRef script(String name) { return scripts.get(name); }

    private static final Pattern GLOBALVAR = Pattern.compile("\\bglobalvar\\s+([A-Za-z_][A-Za-z0-9_\\s,]*)");

    /** Records names declared with globalvar so every piece of code resolves them as globals. */
    public void prescanGlobals(String code) {
        if (code == null || !code.contains("globalvar")) return;
        Matcher m = GLOBALVAR.matcher(code);
        while (m.find())
            for (String n : m.group(1).split(",")) {
                String t = n.trim();
                int sp = t.indexOf(' ');
                if (sp > 0) t = t.substring(0, sp);
                if (!t.isEmpty()) globalvars.add(t);
            }
    }

    /** Compiles all registered scripts (after every script name is known). Returns first error or null. */
    public ScriptError compileScripts() {
        ScriptError first = null;
        for (ScriptRef r : scripts.values()) {
            try {
                r.code = compile(r.text, "Script " + r.name);
            } catch (ScriptError e) {
                if (first == null) first = e;
            }
        }
        return first;
    }

    public Code compile(String src, String sourceName) {
        try {
            Parser p = new Parser(this, src == null ? "" : src);
            Ast.Stmt body = p.parseProgram();
            return new Code(body, p.localCount(), sourceName, src);
        } catch (ScriptError e) {
            throw e.in(sourceName);
        }
    }

    /** Compiles a standalone expression (used for things like room instance creation fields). */
    public Code compileExpression(String src, String sourceName) {
        return compile("return (" + src + ");", sourceName);
    }

    /** Syntax-only check that reports the first problem, or null when the code is fine. */
    public static ScriptError check(String src, Compiler withNames) {
        try {
            (withNames == null ? new Compiler() : withNames).compile(src, null);
            return null;
        } catch (ScriptError e) {
            return e;
        }
    }
}
