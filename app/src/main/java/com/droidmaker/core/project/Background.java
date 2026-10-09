package com.droidmaker.core.project;

import com.droidmaker.core.json.Json;

import java.util.Map;

/** A single image used as a room background, a tileset, or a 3D/raycast texture. */
public class Background extends Resource {
    /** Image file relative to the project directory, or null when empty. */
    public String file;
    public int width = 64, height = 64;
    public boolean tileset = false;
    public int tileWidth = 16, tileHeight = 16;
    public int revision = 0;

    @Override public ResType type() { return ResType.BACKGROUND; }

    @Override public Map<String, Object> toJson() {
        Map<String, Object> m = super.toJson();
        m.put("file", file);
        m.put("width", (double) width);
        m.put("height", (double) height);
        m.put("tileset", tileset);
        m.put("tileWidth", (double) tileWidth);
        m.put("tileHeight", (double) tileHeight);
        m.put("revision", (double) revision);
        return m;
    }

    @Override public void fromJson(Map<String, Object> m) {
        super.fromJson(m);
        file = (String) m.get("file");
        width = Json.integer(m, "width", 64);
        height = Json.integer(m, "height", 64);
        tileset = Json.bool(m, "tileset", false);
        tileWidth = Math.max(1, Json.integer(m, "tileWidth", 16));
        tileHeight = Math.max(1, Json.integer(m, "tileHeight", 16));
        revision = Json.integer(m, "revision", 0);
    }
}
