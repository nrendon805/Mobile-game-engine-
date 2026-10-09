package com.droidmaker.core.project;

import com.droidmaker.core.lang.Compiler;
import com.droidmaker.core.lang.ScriptError;

/** Editor-side syntax checking with the project's names known. */
public final class Checker {
    private Checker() {}

    public static Compiler compiler(Project p) {
        Compiler c = new Compiler();
        for (ResType t : ResType.values()) {
            for (Resource r : p.list(t)) {
                if (r == null) continue;
                if (t == ResType.SCRIPT) c.addScript(r.name, r.id, ((Script) r).code);
                else c.addResource(r.name, r.id);
            }
        }
        c.prescanGlobals(p.globalCode);
        return c;
    }

    /** First error in the code, or null when it compiles. */
    public static ScriptError check(String code, Project p) {
        return Compiler.check(code, compiler(p));
    }

    /** First error in an event's actions, or null. */
    public static ScriptError checkEvent(java.util.List<Action> actions, Project p) {
        return Compiler.check(CodeGen.generate(actions, p), compiler(p));
    }
}
