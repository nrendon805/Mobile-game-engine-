package com.droidmaker.core.project;

/** Definition of a drag-and-drop action: its arguments, how it is shown and how it becomes code. */
public final class ActionDef {
    public enum Kind { NORMAL, QUESTION, BEGIN, END, ELSE, EXIT, REPEAT, CODE, COMMENT }

    public enum ArgType { EXPR, STRING, TEXT, BOOL, MENU, SPRITE, SOUND, BACKGROUND, OBJECT, ROOM, SCRIPT, COLOR, DIRS, KEY, CODE, VARNAME }

    public static final class Arg {
        public final String label;
        public final ArgType type;
        public final String def;
        public final String[] options;

        public Arg(String label, ArgType type, String def, String... options) {
            this.label = label; this.type = type; this.def = def; this.options = options;
        }
    }

    /** Turns an action instance into code. */
    public interface Gen { String gen(CodeGen.G g); }

    public final String category, id, name, desc;
    public final Kind kind;
    public final boolean canRelative, canApply;
    public final Arg[] args;
    public final Gen gen;
    /** Short glyph shown on the action tile. */
    public final String icon;

    ActionDef(String category, String id, String name, String icon, String desc, Kind kind,
              boolean canRelative, boolean canApply, Arg[] args, Gen gen) {
        this.category = category; this.id = id; this.name = name; this.icon = icon; this.desc = desc;
        this.kind = kind; this.canRelative = canRelative; this.canApply = canApply; this.args = args; this.gen = gen;
    }

    public boolean isQuestion() { return kind == Kind.QUESTION; }
}
