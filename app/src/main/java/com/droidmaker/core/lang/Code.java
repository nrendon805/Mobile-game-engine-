package com.droidmaker.core.lang;

import com.droidmaker.core.runtime.Instance;

/** A compiled piece of code: an event, a script, or creation code. */
public final class Code {
    public final Ast.Stmt body;
    public final int localCount;
    public final String source;
    /** The code text (kept for error display). */
    public final String text;

    public static final int MAX_DEPTH = 400;

    public Code(Ast.Stmt body, int localCount, String source, String text) {
        this.body = body; this.localCount = localCount; this.source = source; this.text = text;
    }

    /** Runs as an event or top-level piece of code for the given instance. */
    public Object run(Ctx c, Instance self, Instance other) {
        Instance os = c.self, oo = c.other;
        Object[] ol = c.locals, oa = c.args;
        Object oret = c.ret;
        boolean top = c.depth == 0;
        c.self = self;
        c.other = other;
        c.locals = localCount == 0 ? Ctx.NO_ARGS : new Object[localCount];
        c.args = Ctx.NO_ARGS;
        c.ret = V.ZERO;
        if (top) c.budget = Ctx.LOOP_BUDGET;
        c.depth++;
        try {
            body.exec(c);
            return c.ret;
        } catch (ScriptError e) {
            throw e.in(source);
        } catch (StackOverflowError e) {
            throw new ScriptError("Too much recursion").in(source);
        } finally {
            c.depth--;
            c.self = os;
            c.other = oo;
            c.locals = ol;
            c.args = oa;
            c.ret = oret;
        }
    }

    /** Runs as a script call in the caller's instance context. */
    public Object call(Ctx c, Object[] args) {
        if (c.depth > MAX_DEPTH) throw new ScriptError("Too much recursion in " + source);
        Object[] ol = c.locals, oa = c.args;
        Object oret = c.ret;
        c.locals = localCount == 0 ? Ctx.NO_ARGS : new Object[localCount];
        c.args = args;
        c.ret = V.ZERO;
        c.depth++;
        try {
            body.exec(c);
            return c.ret;
        } catch (ScriptError e) {
            throw e.in(source);
        } finally {
            c.depth--;
            c.locals = ol;
            c.args = oa;
            c.ret = oret;
        }
    }
}
