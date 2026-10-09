package com.droidmaker.core.lang;

/** Error raised while compiling or running script code. */
public class ScriptError extends RuntimeException {
    public int line = -1;
    public String source;

    public ScriptError(String msg) { super(msg); }

    public ScriptError(String msg, int line) { super(msg); this.line = line; }

    public ScriptError at(int line) { if (this.line < 0) this.line = line; return this; }

    public ScriptError in(String source) { if (this.source == null) this.source = source; return this; }

    public String describe() {
        StringBuilder b = new StringBuilder();
        if (source != null) b.append(source);
        if (line >= 0) b.append(source != null ? ", line " : "Line ").append(line);
        if (b.length() > 0) b.append(": ");
        return b.append(getMessage()).toString();
    }
}
