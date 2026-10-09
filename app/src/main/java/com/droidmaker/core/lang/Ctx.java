package com.droidmaker.core.lang;

import com.droidmaker.core.runtime.Game;
import com.droidmaker.core.runtime.Instance;

/** Execution state for running code: the current self/other instances, locals and arguments. */
public final class Ctx {
    public final Game game;
    public Instance self, other;
    /** Event being executed (for event_inherited) and the object level whose code runs. */
    public int evKey = -1;
    public com.droidmaker.core.runtime.ObjectRt evObj;
    public Object[] locals = new Object[0];
    public Object[] args = NO_ARGS;
    public Object ret = V.ZERO;
    public int depth;
    /** Loop iteration budget per top-level run, to catch infinite loops instead of freezing. */
    public long budget;

    public static final Object[] NO_ARGS = new Object[0];
    public static final long LOOP_BUDGET = 20_000_000L;

    public Ctx(Game game) { this.game = game; }

    public void tick(int line) {
        if (--budget < 0) throw new ScriptError("Loop ran too long (possible infinite loop)", line);
    }
}
