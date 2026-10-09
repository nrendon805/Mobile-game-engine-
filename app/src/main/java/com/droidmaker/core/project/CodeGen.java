package com.droidmaker.core.project;

import java.util.List;

/**
 * Converts drag-and-drop action lists into script code, so D&D and code share one interpreter.
 * Questions apply to the next action or block, "else" follows that, and "repeat" repeats the
 * next action or block, exactly like Game Maker 8.
 */
public final class CodeGen {
    private CodeGen() {}

    /** Helper handed to each action's generator. */
    public static final class G {
        public final Action action;
        public final Project project;
        public final boolean rel;

        G(Action a, Project p) { action = a; project = p; rel = a.relative; }

        public String raw(int i) { return action.arg(i); }

        /** The argument converted to an expression. */
        public String a(int i) {
            ActionDef d = action.def();
            ActionDef.ArgType t = d != null && i < d.args.length ? d.args[i].type : ActionDef.ArgType.EXPR;
            return argCode(t, action.arg(i));
        }

        public int menu(int i) {
            try { return (int) Double.parseDouble(action.arg(i).trim()); } catch (Exception e) { return 0; }
        }

        public boolean bool(int i) { return menu(i) != 0; }

        public String set(String lhs, int i) { return lhs + (rel ? " += " : " = ") + a(i); }
        public String px(int i) { return rel ? "x + " + a(i) : a(i); }
        public String py(int i) { return rel ? "y + " + a(i) : a(i); }
    }

    public static String argCode(ActionDef.ArgType t, String v) {
        if (v == null) v = "";
        String s = v.trim();
        switch (t) {
            case EXPR:
                return s.isEmpty() ? "0" : "(" + s + ")";
            case STRING:
                return quote(v);
            case TEXT:
                if (s.startsWith("\"") || s.startsWith("'")) return "(" + s + ")";
                return quote(v);
            case BOOL:
            case MENU:
            case COLOR:
            case KEY:
                return s.isEmpty() ? "0" : s;
            case SPRITE:
            case SOUND:
            case BACKGROUND:
            case OBJECT:
            case ROOM:
            case SCRIPT:
                return s.isEmpty() || s.startsWith("<") ? "-1" : s;
            case CODE:
            case VARNAME:
            case DIRS:
            default:
                return v;
        }
    }

    public static String quote(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else b.append(c);
        }
        return b.append('"').toString();
    }

    /** Builds the code for a whole event. */
    public static String generate(List<Action> actions, Project p) {
        StringBuilder out = new StringBuilder();
        int[] pos = {0};
        while (pos[0] < actions.size()) {
            String s = stmt(actions, pos, p, 0);
            if (s != null) out.append(s).append('\n');
        }
        return out.toString();
    }

    private static String ind(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append("    ");
        return b.toString();
    }

    /** Generates one statement starting at pos[0], advancing it. Returns null for stray tokens. */
    private static String stmt(List<Action> acts, int[] pos, Project p, int depth) {
        if (pos[0] >= acts.size()) return ind(depth) + "{}";
        Action a = acts.get(pos[0]++);
        ActionDef d = a.def();
        if (d == null) return ind(depth) + "// unknown action " + a.id;
        G g = new G(a, p);
        switch (d.kind) {
            case BEGIN: {
                StringBuilder b = new StringBuilder(ind(depth)).append("{\n");
                while (pos[0] < acts.size()) {
                    ActionDef nd = acts.get(pos[0]).def();
                    if (nd != null && nd.kind == ActionDef.Kind.END) { pos[0]++; break; }
                    String s = stmt(acts, pos, p, depth + 1);
                    if (s != null) b.append(s).append('\n');
                }
                return b.append(ind(depth)).append('}').toString();
            }
            case END:
            case ELSE:
                return null; // unmatched; ignored
            case EXIT:
                return ind(depth) + "exit;";
            case COMMENT:
                return ind(depth) + "// " + a.arg(0).replace('\n', ' ');
            case REPEAT: {
                String body = stmt(acts, pos, p, depth + 1);
                if (body == null) body = ind(depth + 1) + "{}";
                return ind(depth) + "repeat (" + g.a(0) + ")\n" + body;
            }
            case QUESTION: {
                String cond = d.gen.gen(g);
                if (a.not) cond = "!(" + cond + ")";
                String head;
                if (a.appliesTo == Action.SELF) {
                    head = ind(depth) + "if (" + cond + ")\n";
                } else {
                    String target = a.appliesTo == Action.OTHER ? "other" : objName(p, a.appliesTo);
                    // locals declared with var stay visible inside with(), like in GML
                    head = ind(depth) + "var __q; __q = 1;\n"
                            + ind(depth) + "with (" + target + ") if !(" + cond + ") __q = 0;\n"
                            + ind(depth) + "if (__q)\n";
                }
                String then = stmt(acts, pos, p, depth + 1);
                if (then == null) then = ind(depth + 1) + "{}";
                StringBuilder b = new StringBuilder(head).append(then);
                if (pos[0] < acts.size()) {
                    ActionDef nd = acts.get(pos[0]).def();
                    if (nd != null && nd.kind == ActionDef.Kind.ELSE) {
                        pos[0]++;
                        String els = stmt(acts, pos, p, depth + 1);
                        if (els == null) els = ind(depth + 1) + "{}";
                        b.append('\n').append(ind(depth)).append("else\n").append(els);
                    }
                }
                return b.toString();
            }
            case CODE:
            case NORMAL:
            default: {
                String code = d.gen.gen(g);
                if (d.canApply && a.appliesTo != Action.SELF) {
                    String target = a.appliesTo == Action.OTHER ? "other" : objName(p, a.appliesTo);
                    return ind(depth) + "with (" + target + ") {\n" + indent(code, depth + 1) + "\n" + ind(depth) + "}";
                }
                if (d.kind == ActionDef.Kind.CODE) return ind(depth) + "{\n" + indent(code, depth + 1) + "\n" + ind(depth) + "}";
                return indent(code, depth);
            }
        }
    }

    private static String objName(Project p, int id) {
        GameObject o = p == null ? null : p.object(id);
        return o == null ? "noone" : o.name;
    }

    private static String indent(String code, int depth) {
        String pre = ind(depth);
        StringBuilder b = new StringBuilder();
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) b.append('\n');
            b.append(pre).append(lines[i]);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ display
    /** One-line description of an action for the event's action list. */
    public static String describe(Action a, Project p) {
        ActionDef d = a.def();
        if (d == null) return "Unknown action: " + a.id;
        String s = d.desc;
        for (int i = 0; i < d.args.length; i++) s = s.replace("{" + i + "}", displayArg(d.args[i], a.arg(i), p));
        if (d.kind == ActionDef.Kind.CODE) {
            String c = a.arg(0).trim();
            int nl = c.indexOf('\n');
            String first = nl < 0 ? c : c.substring(0, nl) + " …";
            if (first.length() > 60) first = first.substring(0, 60) + "…";
            s = "Code: " + (first.isEmpty() ? "(empty)" : first);
        }
        StringBuilder b = new StringBuilder();
        if (a.appliesTo == Action.OTHER) b.append("[other] ");
        else if (a.appliesTo >= 0) b.append("[").append(objName(p, a.appliesTo)).append("] ");
        if (a.not) b.append("NOT ");
        b.append(s);
        if (a.relative) b.append(" (relative)");
        return b.toString();
    }

    public static String displayArg(ActionDef.Arg def, String v, Project p) {
        if (v == null) v = "";
        switch (def.type) {
            case BOOL: return "1".equals(v.trim()) ? "true" : "false";
            case MENU: {
                int i;
                try { i = (int) Double.parseDouble(v.trim()); } catch (Exception e) { i = 0; }
                return i >= 0 && i < def.options.length ? def.options[i] : v;
            }
            case COLOR: {
                try { return String.format("#%06X", Long.parseLong(v.trim()) & 0xFFFFFF); } catch (Exception e) { return v; }
            }
            case KEY: {
                try { return Keys.name(Integer.parseInt(v.trim())); } catch (Exception e) { return v; }
            }
            case DIRS: return dirsName(v);
            case SPRITE: case SOUND: case BACKGROUND: case OBJECT: case ROOM: case SCRIPT:
                return v.isEmpty() ? "<none>" : v;
            case STRING: case TEXT:
                return v.length() > 40 ? "\"" + v.substring(0, 40) + "…\"" : (def.type == ActionDef.ArgType.STRING ? "\"" + v + "\"" : v);
            default:
                return v.length() > 40 ? v.substring(0, 40) + "…" : v;
        }
    }

    /** DIRS mask order: up-left, up, up-right, left, stop, right, down-left, down, down-right. */
    public static final int[] DIR_ANGLES = {135, 90, 45, 180, -1, 0, 225, 270, 315};
    private static final String[] DIR_SHORT = {"↖", "↑", "↗", "←", "■", "→", "↙", "↓", "↘"};

    public static String dirsName(String mask) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 9 && i < mask.length(); i++) if (mask.charAt(i) == '1') b.append(DIR_SHORT[i]);
        return b.length() == 0 ? "none" : b.toString();
    }
}
