package com.droidmaker.core.project;

import com.droidmaker.core.json.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** An animated image: a list of same-size frames stored as PNG files in the project folder. */
public class Sprite extends Resource {
    public static final int MASK_AUTO = 0, MASK_FULL = 1, MASK_MANUAL = 2;

    /** Frame image file names relative to the project directory (e.g. "img/spr_3_0.png"). */
    public List<String> frames = new ArrayList<>();
    public int width = 32, height = 32;
    public int originX = 0, originY = 0;
    public int maskMode = MASK_AUTO;
    /** Collision box (inclusive pixel coords inside the frame). Kept current by the editor. */
    public int bboxLeft = 0, bboxTop = 0, bboxRight = 31, bboxBottom = 31;
    /** Use a circle instead of a rectangle for collisions. */
    public boolean circleMask = false;
    /** Animation speed used in the editor preview (frames per step). */
    public double previewSpeed = 0.25;
    /** Bumped whenever pixels change so caches can refresh. */
    public int revision = 0;

    @Override public ResType type() { return ResType.SPRITE; }

    @Override public Map<String, Object> toJson() {
        Map<String, Object> m = super.toJson();
        m.put("frames", new ArrayList<Object>(frames));
        m.put("width", (double) width);
        m.put("height", (double) height);
        m.put("originX", (double) originX);
        m.put("originY", (double) originY);
        m.put("maskMode", (double) maskMode);
        m.put("bbox", listOf(bboxLeft, bboxTop, bboxRight, bboxBottom));
        m.put("circleMask", circleMask);
        m.put("previewSpeed", previewSpeed);
        m.put("revision", (double) revision);
        return m;
    }

    @Override public void fromJson(Map<String, Object> m) {
        super.fromJson(m);
        frames.clear();
        for (Object o : Json.arr(m.get("frames"))) frames.add(String.valueOf(o));
        width = Json.integer(m, "width", 32);
        height = Json.integer(m, "height", 32);
        originX = Json.integer(m, "originX", 0);
        originY = Json.integer(m, "originY", 0);
        maskMode = Json.integer(m, "maskMode", MASK_AUTO);
        List<Object> b = Json.arr(m.get("bbox"));
        if (b.size() == 4) {
            bboxLeft = ((Number) b.get(0)).intValue();
            bboxTop = ((Number) b.get(1)).intValue();
            bboxRight = ((Number) b.get(2)).intValue();
            bboxBottom = ((Number) b.get(3)).intValue();
        } else { bboxLeft = 0; bboxTop = 0; bboxRight = width - 1; bboxBottom = height - 1; }
        circleMask = Json.bool(m, "circleMask", false);
        previewSpeed = Json.num(m, "previewSpeed", 0.25);
        revision = Json.integer(m, "revision", 0);
    }

    static List<Object> listOf(int... v) {
        List<Object> l = new ArrayList<>();
        for (int i : v) l.add((double) i);
        return l;
    }
}
