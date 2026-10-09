package com.droidmaker.core.project;

import java.util.LinkedHashMap;
import java.util.Map;

/** Base class of every project resource. The id is the resource's index in its project list. */
public abstract class Resource {
    public int id;
    public String name = "";
    /** Folder path inside the project tree, e.g. "Enemies/Flying". Empty = root. */
    public String folder = "";

    public abstract ResType type();

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", (double) id);
        m.put("name", name);
        m.put("folder", folder);
        return m;
    }

    public void fromJson(Map<String, Object> m) {
        id = com.droidmaker.core.json.Json.integer(m, "id", id);
        name = com.droidmaker.core.json.Json.str(m, "name", name);
        folder = com.droidmaker.core.json.Json.str(m, "folder", "");
    }

    @Override public String toString() { return name; }
}
