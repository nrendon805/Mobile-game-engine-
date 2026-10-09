package com.droidmaker.core.lang;

/**
 * Value helpers. Script values are Double, String or {@link GArray}. Truth follows Game Maker:
 * a number is true when it is 0.5 or more.
 */
public final class V {
    private V() {}

    private static final Double[] CACHE = new Double[1280];
    static { for (int i = 0; i < CACHE.length; i++) CACHE[i] = (double) (i - 256); }

    public static final Double ZERO = CACHE[256], ONE = CACHE[257], MINUS_ONE = CACHE[255];

    public static Double num(double d) {
        if (d >= -256 && d < 1024) {
            int i = (int) d;
            if (i == d) return CACHE[i + 256];
        }
        return d;
    }

    public static Double bool(boolean b) { return b ? ONE : ZERO; }

    public static double toNum(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            // lenient: numeric strings convert, others are 0 (GM8 would raise an error)
            String s = ((String) o).trim();
            if (s.isEmpty()) return 0;
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
        }
        if (o instanceof Boolean) return (Boolean) o ? 1 : 0;
        return 0;
    }

    public static boolean truth(Object o) {
        if (o instanceof Double) return (Double) o >= 0.5;
        if (o instanceof String) return !((String) o).isEmpty();
        if (o instanceof GArray) return true;
        return o != null && toNum(o) >= 0.5;
    }

    public static String str(Object o) {
        if (o instanceof String) return (String) o;
        if (o instanceof Double) return numStr((Double) o);
        if (o instanceof GArray) return o.toString();
        if (o == null) return "undefined";
        return o.toString();
    }

    /** Number formatting like GM's string(): integers plain, otherwise up to 2 decimals. */
    public static String numStr(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        if (Double.isNaN(d)) return "NaN";
        if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
        String s = String.format(java.util.Locale.US, "%.2f", d);
        if (s.indexOf('.') >= 0) {
            int e = s.length();
            while (e > 0 && s.charAt(e - 1) == '0') e--;
            if (e > 0 && s.charAt(e - 1) == '.') e--;
            s = s.substring(0, e);
        }
        return s;
    }

    public static boolean isNum(Object o) { return o instanceof Double; }
    public static boolean isStr(Object o) { return o instanceof String; }

    public static boolean equal(Object a, Object b) {
        if (a instanceof Double && b instanceof Double) {
            double x = (Double) a, y = (Double) b;
            return x == y || Math.abs(x - y) < 1e-10;
        }
        if (a instanceof String && b instanceof String) return a.equals(b);
        if (a == b) return true;
        return false;
    }

    public static int compare(Object a, Object b) {
        if (a instanceof String && b instanceof String) return ((String) a).compareTo((String) b);
        if (a instanceof String) return 1;
        if (b instanceof String) return -1;
        return Double.compare(toNum(a), toNum(b));
    }

    public static Object add(Object a, Object b) {
        if (a instanceof Double && b instanceof Double) return num((Double) a + (Double) b);
        if (a instanceof String || b instanceof String) return str(a) + str(b);
        return num(toNum(a) + toNum(b));
    }

    public static int toInt(Object o) {
        double d = toNum(o);
        if (Double.isNaN(d)) return 0;
        return (int) Math.round(d);
    }

    /** Converts a GM color (0xRRGGBB) to its components. */
    public static int r(int c) { return (c >> 16) & 255; }
    public static int g(int c) { return (c >> 8) & 255; }
    public static int b(int c) { return c & 255; }
}
