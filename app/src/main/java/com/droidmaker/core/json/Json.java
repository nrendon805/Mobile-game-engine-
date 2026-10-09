package com.droidmaker.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny dependency-free JSON reader/writer. Objects become LinkedHashMap&lt;String,Object&gt;,
 * arrays become ArrayList&lt;Object&gt;, numbers become Double, plus String/Boolean/null.
 */
public final class Json {
    private Json() {}

    // ------------------------------------------------------------------ parsing
    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < s.length()) throw new IllegalArgumentException("Trailing data at " + p.i);
        return v;
    }

    private static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        char peek() { if (i >= s.length()) throw err("Unexpected end"); return s.charAt(i); }
        IllegalArgumentException err(String m) { return new IllegalArgumentException(m + " at " + i); }
        Object value() {
            char c = peek();
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return str();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return num();
            }
        }
        void expect(String w) {
            if (!s.startsWith(w, i)) throw err("Expected " + w);
            i += w.length();
        }
        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = str();
                ws();
                if (peek() != ':') throw err("Expected :");
                i++; ws();
                m.put(k, value());
                ws();
                char c = peek(); i++;
                if (c == '}') return m;
                if (c != ',') throw err("Expected , or }");
            }
        }
        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                char c = peek(); i++;
                if (c == ']') return l;
                if (c != ',') throw err("Expected , or ]");
            }
        }
        String str() {
            if (peek() != '"') throw err("Expected string");
            i++;
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: b.append(e);
                    }
                } else b.append(c);
            }
        }
        Double num() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw err("Unexpected char '" + s.charAt(i) + "'");
            return Double.parseDouble(s.substring(st, i));
        }
    }

    // ------------------------------------------------------------------ writing
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, 0, true);
        return b.toString();
    }

    public static String writeCompact(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, 0, false);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v, int ind, boolean pretty) {
        if (v == null) b.append("null");
        else if (v instanceof String) quote(b, (String) v);
        else if (v instanceof Boolean) b.append(v.toString());
        else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else if (Double.isNaN(d) || Double.isInfinite(d)) b.append('0');
            else b.append(d);
        } else if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            if (m.isEmpty()) { b.append("{}"); return; }
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                if (pretty) nl(b, ind + 1);
                quote(b, e.getKey());
                b.append(pretty ? ": " : ":");
                write(b, e.getValue(), ind + 1, pretty);
            }
            if (pretty) nl(b, ind);
            b.append('}');
        } else if (v instanceof List) {
            List<Object> l = (List<Object>) v;
            if (l.isEmpty()) { b.append("[]"); return; }
            boolean simple = true;
            for (Object o : l) if (o instanceof Map || o instanceof List) { simple = false; break; }
            b.append('[');
            for (int k = 0; k < l.size(); k++) {
                if (k > 0) b.append(simple && pretty ? ", " : ",");
                if (pretty && !simple) nl(b, ind + 1);
                write(b, l.get(k), ind + 1, pretty);
            }
            if (pretty && !simple) nl(b, ind);
            b.append(']');
        } else quote(b, v.toString());
    }

    private static void nl(StringBuilder b, int ind) {
        b.append('\n');
        for (int k = 0; k < ind; k++) b.append("  ");
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    // ------------------------------------------------------------------ helpers
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>(); }
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return o instanceof List ? (List<Object>) o : new ArrayList<>(); }
    public static String str(Map<String, Object> m, String k, String def) { Object o = m.get(k); return o == null ? def : o.toString(); }
    public static double num(Map<String, Object> m, String k, double def) { Object o = m.get(k); return o instanceof Number ? ((Number) o).doubleValue() : def; }
    public static int integer(Map<String, Object> m, String k, int def) { Object o = m.get(k); return o instanceof Number ? (int) ((Number) o).doubleValue() : def; }
    public static boolean bool(Map<String, Object> m, String k, boolean def) { Object o = m.get(k); return o instanceof Boolean ? (Boolean) o : def; }
}
