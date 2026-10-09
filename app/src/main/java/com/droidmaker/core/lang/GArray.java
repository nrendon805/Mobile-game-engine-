package com.droidmaker.core.lang;

import java.util.ArrayList;

/** A growable 2D array value (a[i] uses row 0, a[i, j] uses row i). Missing cells read as 0. */
public final class GArray {
    private final ArrayList<ArrayList<Object>> rows = new ArrayList<>();

    public Object get(int i, int j) {
        if (i < 0 || i >= rows.size()) return V.ZERO;
        ArrayList<Object> r = rows.get(i);
        if (j < 0 || j >= r.size()) return V.ZERO;
        Object o = r.get(j);
        return o == null ? V.ZERO : o;
    }

    public void set(int i, int j, Object v) {
        if (i < 0 || j < 0) throw new ScriptError("Negative array index");
        if (i > 1_000_000 || j > 10_000_000) throw new ScriptError("Array index too large");
        while (rows.size() <= i) rows.add(new ArrayList<>());
        ArrayList<Object> r = rows.get(i);
        while (r.size() <= j) r.add(V.ZERO);
        r.set(j, v);
    }

    public int length(int row) { return row >= 0 && row < rows.size() ? rows.get(row).size() : 0; }
    public int height() { return rows.size(); }

    @Override public String toString() {
        StringBuilder b = new StringBuilder("[");
        int n = length(0);
        for (int k = 0; k < n; k++) {
            if (k > 0) b.append(", ");
            Object o = get(0, k);
            b.append(o instanceof String ? "\"" + o + "\"" : V.str(o));
        }
        return b.append(']').toString();
    }
}
