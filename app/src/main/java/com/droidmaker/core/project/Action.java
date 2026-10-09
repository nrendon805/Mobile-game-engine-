package com.droidmaker.core.project;

import com.droidmaker.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One drag-and-drop action placed in an event. Its meaning comes from the {@link ActionLibrary}. */
public class Action {
    public static final int SELF = -1, OTHER = -2;

    public String id;
    public List<String> args = new ArrayList<>();
    /** SELF, OTHER, or an object id. */
    public int appliesTo = SELF;
    public boolean relative = false;
    public boolean not = false;

    public Action() {}

    public Action(String id) {
        this.id = id;
        ActionDef d = ActionLibrary.get(id);
        if (d != null) for (ActionDef.Arg a : d.args) args.add(a.def);
    }

    public ActionDef def() { return ActionLibrary.get(id); }

    public String arg(int i) { return i < args.size() ? args.get(i) : ""; }

    public Action copy() {
        Action a = new Action();
        a.id = id;
        a.args = new ArrayList<>(args);
        a.appliesTo = appliesTo;
        a.relative = relative;
        a.not = not;
        return a;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("args", new ArrayList<Object>(args));
        if (appliesTo != SELF) m.put("to", (double) appliesTo);
        if (relative) m.put("rel", true);
        if (not) m.put("not", true);
        return m;
    }

    public static Action fromJson(Map<String, Object> m) {
        Action a = new Action();
        a.id = Json.str(m, "id", "comment");
        for (Object o : Json.arr(m.get("args"))) a.args.add(o == null ? "" : o.toString());
        a.appliesTo = Json.integer(m, "to", SELF);
        a.relative = Json.bool(m, "rel", false);
        a.not = Json.bool(m, "not", false);
        ActionDef d = ActionLibrary.get(a.id);
        if (d != null) while (a.args.size() < d.args.length) a.args.add(d.args[a.args.size()].def);
        return a;
    }
}
