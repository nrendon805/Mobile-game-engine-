package com.droidmaker.core.project;

import com.droidmaker.core.json.Json;

import java.util.Map;

/** A named piece of code callable like a function: scr_name(arg0, arg1, ...). */
public class Script extends Resource {
    public String code = "// argument0, argument1 ... hold the arguments\n";

    @Override public ResType type() { return ResType.SCRIPT; }

    @Override public Map<String, Object> toJson() {
        Map<String, Object> m = super.toJson();
        m.put("code", code);
        return m;
    }

    @Override public void fromJson(Map<String, Object> m) {
        super.fromJson(m);
        code = Json.str(m, "code", "");
    }
}
