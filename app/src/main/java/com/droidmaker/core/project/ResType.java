package com.droidmaker.core.project;

/** The kinds of resources a project holds, in project-tree order. */
public enum ResType {
    SPRITE("Sprites", "spr_"),
    SOUND("Sounds", "snd_"),
    BACKGROUND("Backgrounds", "bg_"),
    SCRIPT("Scripts", "scr_"),
    OBJECT("Objects", "obj_"),
    ROOM("Rooms", "rm_");

    public final String title;
    public final String prefix;

    ResType(String title, String prefix) { this.title = title; this.prefix = prefix; }
}
