package com.droidmaker.core.project;

import com.droidmaker.core.project.ActionDef.Arg;
import com.droidmaker.core.project.ActionDef.ArgType;
import com.droidmaker.core.project.ActionDef.Gen;
import com.droidmaker.core.project.ActionDef.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every drag-and-drop action, grouped by category. Each compiles to script code. */
public final class ActionLibrary {
    private ActionLibrary() {}

    public static final String[] CATEGORIES = {"Move", "Main", "Game", "Control", "Variables", "Score", "Draw", "Touch", "3D", "Raycast"};

    private static final Map<String, ActionDef> BY_ID = new LinkedHashMap<>();
    private static final Map<String, List<ActionDef>> BY_CAT = new LinkedHashMap<>();

    public static ActionDef get(String id) { return id == null ? null : BY_ID.get(id); }

    public static List<ActionDef> category(String c) {
        List<ActionDef> l = BY_CAT.get(c);
        return l == null ? Collections.<ActionDef>emptyList() : l;
    }

    public static List<ActionDef> all() { return new ArrayList<>(BY_ID.values()); }

    // ------------------------------------------------------------------ DSL
    private static final int REL = 1, APPLY = 2;
    private static String cat = "Move";

    private static void def(String id, String name, String icon, String desc, Kind kind, int flags, Arg[] args, Gen gen) {
        ActionDef d = new ActionDef(cat, id, name, icon, desc, kind, (flags & REL) != 0, (flags & APPLY) != 0, args, gen);
        BY_ID.put(id, d);
        List<ActionDef> l = BY_CAT.get(cat);
        if (l == null) { l = new ArrayList<>(); BY_CAT.put(cat, l); }
        l.add(d);
    }

    private static void act(String id, String name, String icon, String desc, int flags, Arg[] args, Gen gen) {
        def(id, name, icon, desc, Kind.NORMAL, flags, args, gen);
    }

    private static void ask(String id, String name, String icon, String desc, int flags, Arg[] args, Gen gen) {
        def(id, name, icon, desc, Kind.QUESTION, flags, args, gen);
    }

    private static Arg[] A(Arg... a) { return a; }
    private static Arg e(String l, String d) { return new Arg(l, ArgType.EXPR, d); }
    private static Arg s(String l, String d) { return new Arg(l, ArgType.STRING, d); }
    private static Arg t(String l, String d) { return new Arg(l, ArgType.TEXT, d); }
    private static Arg b(String l, boolean d) { return new Arg(l, ArgType.BOOL, d ? "1" : "0"); }
    private static Arg m(String l, int d, String... o) { return new Arg(l, ArgType.MENU, String.valueOf(d), o); }
    private static Arg spr(String l) { return new Arg(l, ArgType.SPRITE, ""); }
    private static Arg snd(String l) { return new Arg(l, ArgType.SOUND, ""); }
    private static Arg bg(String l) { return new Arg(l, ArgType.BACKGROUND, ""); }
    private static Arg obj(String l) { return new Arg(l, ArgType.OBJECT, ""); }
    private static Arg room(String l) { return new Arg(l, ArgType.ROOM, ""); }
    private static Arg scr(String l) { return new Arg(l, ArgType.SCRIPT, ""); }
    private static Arg col(String l, int rgb) { return new Arg(l, ArgType.COLOR, String.valueOf(rgb)); }
    private static Arg key(String l, int k) { return new Arg(l, ArgType.KEY, String.valueOf(k)); }
    private static Arg dirs(String l) { return new Arg(l, ArgType.DIRS, "000001000"); }
    private static Arg code(String l) { return new Arg(l, ArgType.CODE, ""); }
    private static Arg var(String l, String d) { return new Arg(l, ArgType.VARNAME, d); }

    private static final String[] OPS = {"equal to", "less than", "greater than", "less or equal", "greater or equal", "not equal"};
    private static final String[] OPSYM = {"==", "<", ">", "<=", ">=", "!="};

    private static String cmp(String lhs, String rhs, int op) {
        return lhs + " " + OPSYM[Math.max(0, Math.min(5, op))] + " " + rhs;
    }

    static {
        // =========================================================== MOVE
        cat = "Move";
        act("move_fixed", "Move Fixed", "✣", "Start moving {0} at speed {1}", REL | APPLY,
                A(dirs("Directions"), e("Speed", "4")), g -> {
                    String mask = g.raw(0);
                    StringBuilder list = new StringBuilder();
                    for (int i = 0; i < 9 && i < mask.length(); i++)
                        if (mask.charAt(i) == '1') { if (list.length() > 0) list.append(", "); list.append(CodeGen.DIR_ANGLES[i]); }
                    if (list.length() == 0) return "// no direction selected";
                    return "action_move_fixed(choose(" + list + "), " + g.a(1) + ", " + (g.rel ? 1 : 0) + ");";
                });
        act("move_free", "Move Free", "➚", "Move in direction {0} at speed {1}", REL | APPLY,
                A(e("Direction", "0"), e("Speed", "4")),
                g -> g.rel ? "motion_add(" + g.a(0) + ", " + g.a(1) + ");" : "motion_set(" + g.a(0) + ", " + g.a(1) + ");");
        act("move_towards", "Move Towards", "◎", "Move towards ({0}, {1}) at speed {2}", REL | APPLY,
                A(e("X", "0"), e("Y", "0"), e("Speed", "4")),
                g -> "move_towards_point(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + ");");
        act("speed_h", "Horizontal Speed", "↔", "Set horizontal speed to {0}", REL | APPLY,
                A(e("Hor. speed", "0")), g -> g.set("hspeed", 0) + ";");
        act("speed_v", "Vertical Speed", "↕", "Set vertical speed to {0}", REL | APPLY,
                A(e("Vert. speed", "0")), g -> g.set("vspeed", 0) + ";");
        act("set_speed", "Set Speed", "»", "Set speed to {0}", REL | APPLY,
                A(e("Speed", "0")), g -> g.set("speed", 0) + ";");
        act("set_direction", "Set Direction", "↻", "Set direction to {0}", REL | APPLY,
                A(e("Direction", "0")), g -> g.set("direction", 0) + ";");
        act("gravity", "Set Gravity", "⤓", "Gravity {1} in direction {0}", REL | APPLY,
                A(e("Direction", "270"), e("Gravity", "0.5")),
                g -> g.set("gravity_direction", 0) + ";\n" + g.set("gravity", 1) + ";");
        act("reverse_h", "Reverse Horizontal", "⇄", "Reverse horizontal direction", APPLY, A(), g -> "hspeed = -hspeed;");
        act("reverse_v", "Reverse Vertical", "⇅", "Reverse vertical direction", APPLY, A(), g -> "vspeed = -vspeed;");
        act("friction", "Set Friction", "≈", "Set friction to {0}", REL | APPLY, A(e("Friction", "0.1")), g -> g.set("friction", 0) + ";");
        act("jump_to", "Jump to Position", "⌖", "Jump to ({0}, {1})", REL | APPLY,
                A(e("X", "0"), e("Y", "0")), g -> g.set("x", 0) + ";\n" + g.set("y", 1) + ";");
        act("jump_start", "Jump to Start", "⌂", "Jump to the start position", APPLY, A(), g -> "x = xstart;\ny = ystart;");
        act("jump_random", "Jump to Random", "⚄", "Jump to a random position (snap {0}, {1})", APPLY,
                A(e("Snap hor.", "0"), e("Snap vert.", "0")), g -> "action_jump_random(" + g.a(0) + ", " + g.a(1) + ");");
        act("snap", "Align to Grid", "#", "Align to a grid of {0} x {1}", APPLY,
                A(e("Snap hor.", "32"), e("Snap vert.", "32")), g -> "move_snap(" + g.a(0) + ", " + g.a(1) + ");");
        act("wrap", "Wrap Screen", "⟲", "Wrap {0} when outside the room", APPLY,
                A(m("Direction", 2, "horizontal", "vertical", "both")),
                g -> { int d = g.menu(0); return "move_wrap(" + (d != 1 ? 1 : 0) + ", " + (d != 0 ? 1 : 0) + ", 0);"; });
        act("move_contact", "Move to Contact", "⤞", "Move in direction {0} up to {1} until contact with {2}", APPLY,
                A(e("Direction", "270"), e("Maximum", "-1"), m("Against", 0, "solid objects", "all objects")),
                g -> (g.menu(2) == 0 ? "move_contact_solid(" : "move_contact_all(") + g.a(0) + ", " + g.a(1) + ");");
        act("bounce", "Bounce", "⤾", "Bounce against {1} ({0})", APPLY,
                A(m("Precise", 0, "not precisely", "precisely"), m("Against", 0, "solid objects", "all objects")),
                g -> (g.menu(1) == 0 ? "move_bounce_solid(" : "move_bounce_all(") + (g.menu(0) == 1 ? "true" : "false") + ");");
        act("step_towards", "Step Towards", "⇢", "Step towards ({0}, {1}) at speed {2}, stopping at {3}", REL | APPLY,
                A(e("X", "0"), e("Y", "0"), e("Speed", "2"), m("Stop at", 0, "solid only", "all instances")),
                g -> "mp_linear_step(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + ", " + g.menu(3) + ");");
        act("move_outside", "Move Outside Solid", "⇱", "Move out of solid objects in direction {0}", APPLY,
                A(e("Direction", "90"), e("Maximum", "-1")),
                g -> "move_outside_solid(" + g.a(0) + ", " + g.a(1) + ");");

        // =========================================================== MAIN
        cat = "Main";
        act("create_instance", "Create Instance", "✚", "Create {0} at ({1}, {2})", REL | APPLY,
                A(obj("Object"), e("X", "0"), e("Y", "0")),
                g -> "instance_create(" + g.px(1) + ", " + g.py(2) + ", " + g.a(0) + ");");
        act("create_moving", "Create Moving", "✈", "Create {0} at ({1}, {2}) moving {4}° at {3}", REL | APPLY,
                A(obj("Object"), e("X", "0"), e("Y", "0"), e("Speed", "8"), e("Direction", "0")),
                g -> "{\n    var __i; __i = instance_create(" + g.px(1) + ", " + g.py(2) + ", " + g.a(0) + ");\n"
                        + "    __i.speed = " + g.a(3) + "; __i.direction = " + g.a(4) + ";\n}");
        act("create_random", "Create Random", "⚂", "Create one of {0}, {1}, {2}, {3} at ({4}, {5})", REL | APPLY,
                A(obj("Object 1"), obj("Object 2"), obj("Object 3"), obj("Object 4"), e("X", "0"), e("Y", "0")),
                g -> {
                    StringBuilder l = new StringBuilder();
                    for (int i = 0; i < 4; i++) {
                        String v = g.raw(i).trim();
                        if (v.isEmpty() || v.startsWith("<")) continue;
                        if (l.length() > 0) l.append(", ");
                        l.append(v);
                    }
                    if (l.length() == 0) return "// no objects chosen";
                    return "instance_create(" + g.px(4) + ", " + g.py(5) + ", choose(" + l + "));";
                });
        act("change_instance", "Change Instance", "⇋", "Change into {0} (events: {1})", APPLY,
                A(obj("Object"), m("Perform events", 0, "no", "yes")),
                g -> "instance_change(" + g.a(0) + ", " + g.menu(1) + ");");
        act("destroy_instance", "Destroy Instance", "✖", "Destroy the instance", APPLY, A(), g -> "instance_destroy();");
        act("destroy_at", "Destroy at Position", "⌧", "Destroy instances at ({0}, {1})", REL | APPLY,
                A(e("X", "0"), e("Y", "0")), g -> "position_destroy(" + g.px(0) + ", " + g.py(1) + ");");
        act("change_sprite", "Change Sprite", "▣", "Set sprite {0}, subimage {1}, speed {2}", APPLY,
                A(spr("Sprite"), e("Subimage", "0"), e("Speed", "1")),
                g -> "sprite_index = " + g.a(0) + ";\nimage_index = " + g.a(1) + ";\nimage_speed = " + g.a(2) + ";");
        act("transform_sprite", "Transform Sprite", "⤡", "Scale {0} x {1}, angle {2}, {3}", APPLY,
                A(e("X scale", "1"), e("Y scale", "1"), e("Angle", "0"), m("Mirror", 0, "no mirroring", "mirror horizontal", "flip vertical", "mirror and flip")),
                g -> {
                    int mm = g.menu(3);
                    String xs = (mm == 1 || mm == 3) ? "-" + g.a(0) : g.a(0);
                    String ys = (mm == 2 || mm == 3) ? "-" + g.a(1) : g.a(1);
                    return "image_xscale = " + xs + ";\nimage_yscale = " + ys + ";\nimage_angle = " + g.a(2) + ";";
                });
        act("color_sprite", "Color Sprite", "◐", "Blend color {0}, alpha {1}", APPLY,
                A(col("Color", 0xFFFFFF), e("Alpha", "1")),
                g -> "image_blend = " + g.a(0) + ";\nimage_alpha = " + g.a(1) + ";");
        act("animation_speed", "Animation Speed", "▶", "Set animation speed to {0}", REL | APPLY,
                A(e("Speed", "1")), g -> g.set("image_speed", 0) + ";");
        act("play_sound", "Play Sound", "♪", "Play sound {0} (loop: {1})", 0,
                A(snd("Sound"), b("Loop", false)),
                g -> (g.bool(1) ? "sound_loop(" : "sound_play(") + g.a(0) + ");");
        act("stop_sound", "Stop Sound", "♫", "Stop sound {0}", 0, A(snd("Sound")), g -> "sound_stop(" + g.a(0) + ");");
        act("stop_all_sounds", "Stop All Sounds", "∅", "Stop all sounds", 0, A(), g -> "sound_stop_all();");
        ask("if_sound", "If Sound Playing", "♬", "If sound {0} is playing", 0, A(snd("Sound")),
                g -> "sound_isplaying(" + g.a(0) + ")");
        act("room_previous", "Previous Room", "⏮", "Go to the previous room", 0, A(), g -> "room_goto_previous();");
        act("room_next", "Next Room", "⏭", "Go to the next room", 0, A(), g -> "room_goto_next();");
        act("room_restart", "Restart Room", "↺", "Restart the current room", 0, A(), g -> "room_restart();");
        act("room_goto", "Go to Room", "⇥", "Go to room {0}", 0, A(room("Room")), g -> "room_goto(" + g.a(0) + ");");
        ask("if_room_previous", "If Previous Room", "⏴", "If a previous room exists", 0, A(), g -> "room_previous(room) >= 0");
        ask("if_room_next", "If Next Room", "⏵", "If a next room exists", 0, A(), g -> "room_next(room) >= 0");

        // =========================================================== GAME
        cat = "Game";
        act("set_alarm", "Set Alarm", "⏰", "Set alarm {1} to {0} steps", REL | APPLY,
                A(e("Steps", "60"), m("Alarm", 0, "Alarm 0", "Alarm 1", "Alarm 2", "Alarm 3", "Alarm 4", "Alarm 5",
                        "Alarm 6", "Alarm 7", "Alarm 8", "Alarm 9", "Alarm 10", "Alarm 11")),
                g -> (g.rel ? "alarm[" + g.menu(1) + "] += " : "alarm[" + g.menu(1) + "] = ") + g.a(0) + ";");
        act("show_message", "Show Message", "💬", "Show message {0}", 0, A(t("Message", "Hello!")),
                g -> "show_message(" + g.a(0) + ");");
        act("toast", "Toast", "🍞", "Show toast {0}", 0, A(t("Text", "Hi")), g -> "show_toast(" + g.a(0) + ");");
        act("game_restart", "Restart Game", "⟳", "Restart the game", 0, A(), g -> "game_restart();");
        act("game_end", "End Game", "⏻", "End the game", 0, A(), g -> "game_end();");
        act("game_save", "Save Game", "💾", "Save the game to slot {0}", 0, A(t("Slot", "save1")), g -> "game_save(" + g.a(0) + ");");
        act("game_load", "Load Game", "📂", "Load the game from slot {0}", 0, A(t("Slot", "save1")), g -> "game_load(" + g.a(0) + ");");
        act("save_value", "Save Value", "⇩", "Store {1} under key {0}", 0, A(t("Key", "best"), e("Value", "score")),
                g -> "save_set(" + g.a(0) + ", " + g.a(1) + ");");
        act("load_value", "Load Value", "⇧", "Load key {1} into {0} (default {2})", 0,
                A(var("Variable", "global.best"), t("Key", "best"), e("Default", "0")),
                g -> g.raw(0) + " = save_get(" + g.a(1) + ", " + g.a(2) + ");");
        act("call_user_event", "Call User Event", "☎", "Call user event {0}", APPLY,
                A(m("Event", 0, "User 0", "User 1", "User 2", "User 3", "User 4", "User 5", "User 6", "User 7",
                        "User 8", "User 9", "User 10", "User 11", "User 12", "User 13", "User 14", "User 15")),
                g -> "event_user(" + g.menu(0) + ");");
        act("set_room_speed", "Set Game Speed", "⏲", "Set room speed to {0} steps/sec", REL, A(e("Speed", "60")),
                g -> g.set("room_speed", 0) + ";");

        // =========================================================== CONTROL
        cat = "Control";
        ask("if_empty", "Check Empty", "□", "If position ({0}, {1}) is free of {2}", REL | APPLY,
                A(e("X", "0"), e("Y", "0"), m("Objects", 0, "solid objects", "all objects")),
                g -> (g.menu(2) == 0 ? "place_free(" : "place_empty(") + g.px(0) + ", " + g.py(1) + ")");
        ask("if_collision", "Check Collision", "▣", "If there is a collision at ({0}, {1}) with {2}", REL | APPLY,
                A(e("X", "0"), e("Y", "0"), m("Objects", 0, "solid objects", "all objects")),
                g -> (g.menu(2) == 0 ? "!place_free(" : "!place_empty(") + g.px(0) + ", " + g.py(1) + ")");
        ask("if_object", "Check Object", "◉", "If there is {0} at ({1}, {2})", REL | APPLY,
                A(obj("Object"), e("X", "0"), e("Y", "0")),
                g -> "place_meeting(" + g.px(1) + ", " + g.py(2) + ", " + g.a(0) + ")");
        ask("if_number", "Test Instance Count", "№", "If the number of {0} is {2} {1}", 0,
                A(obj("Object"), e("Number", "0"), m("Operation", 0, OPS)),
                g -> cmp("instance_number(" + g.a(0) + ")", g.a(1), g.menu(2)));
        ask("if_dice", "Test Chance", "⚀", "With a chance of 1 in {0}", 0, A(e("Sides", "2")),
                g -> "irandom(" + g.a(0) + " - 1) == 0");
        ask("if_question", "Ask Question", "?", "If the player answers yes to {0}", 0, A(t("Question", "Are you sure?")),
                g -> "show_question(" + g.a(0) + ")");
        ask("if_expression", "Test Expression", "∴", "If {0}", APPLY, A(e("Expression", "true")), g -> g.a(0));
        ask("if_aligned", "Check Grid", "⌗", "If aligned to a grid of {0} x {1}", APPLY,
                A(e("Snap hor.", "32"), e("Snap vert.", "32")), g -> "place_snapped(" + g.a(0) + ", " + g.a(1) + ")");
        ask("if_instance_exists", "Instance Exists", "∃", "If an instance of {0} exists", 0, A(obj("Object")),
                g -> "instance_exists(" + g.a(0) + ")");
        def("begin_block", "Start Block", "{", "Start of block", Kind.BEGIN, 0, A(), g -> "{");
        def("end_block", "End Block", "}", "End of block", Kind.END, 0, A(), g -> "}");
        def("else", "Else", "⋯", "Else", Kind.ELSE, 0, A(), g -> "else");
        def("repeat", "Repeat", "⟳", "Repeat the next action/block {0} times", Kind.REPEAT, 0, A(e("Times", "3")), g -> "");
        def("exit", "Exit Event", "⏏", "Exit this event", Kind.EXIT, 0, A(), g -> "exit;");
        act("call_inherited", "Call Parent Event", "⇡", "Call the inherited (parent) event", 0, A(), g -> "event_inherited();");
        def("code", "Execute Code", "{ }", "Code", Kind.CODE, APPLY, A(code("Code")), g -> g.raw(0));
        act("script", "Execute Script", "ƒ", "Run script {0}({1}, {2}, {3})", APPLY,
                A(scr("Script"), e("Argument 0", "0"), e("Argument 1", "0"), e("Argument 2", "0"), e("Argument 3", "0")),
                g -> {
                    String sn = g.raw(0).trim();
                    if (sn.isEmpty() || sn.startsWith("<")) return "// no script chosen";
                    return sn + "(" + g.a(1) + ", " + g.a(2) + ", " + g.a(3) + ", " + g.a(4) + ");";
                });
        def("comment", "Comment", "✎", "{0}", Kind.COMMENT, 0, A(s("Comment", "comment")), g -> "");

        // =========================================================== VARIABLES
        cat = "Variables";
        act("set_var", "Set Variable", "x=", "Set {0} to {1}", REL | APPLY,
                A(var("Variable", "myvar"), e("Value", "0")), g -> g.set(g.raw(0).trim(), 1) + ";");
        ask("if_var", "Test Variable", "x?", "If {0} is {2} {1}", APPLY,
                A(var("Variable", "myvar"), e("Value", "0"), m("Operation", 0, OPS)),
                g -> cmp(g.raw(0).trim(), g.a(1), g.menu(2)));
        act("set_global", "Set Global", "G=", "Set global.{0} to {1}", REL,
                A(var("Name", "counter"), e("Value", "0")), g -> g.set("global." + g.raw(0).trim(), 1) + ";");
        act("increment", "Add to Variable", "++", "Add {1} to {0}", APPLY,
                A(var("Variable", "myvar"), e("Amount", "1")), g -> g.raw(0).trim() + " += " + g.a(1) + ";");
        act("random_var", "Random Value", "⚅", "Set {0} to a random number {1}..{2}", APPLY,
                A(var("Variable", "myvar"), e("Min", "0"), e("Max", "10"), b("Whole number", true)),
                g -> g.raw(0).trim() + " = " + (g.bool(3) ? "irandom_range(" : "random_range(") + g.a(1) + ", " + g.a(2) + ");");
        act("draw_var", "Draw Variable", "x→", "Draw the value of {0} at ({1}, {2})", REL,
                A(var("Variable", "myvar"), e("X", "0"), e("Y", "0")),
                g -> "draw_text(" + g.px(1) + ", " + g.py(2) + ", string(" + g.raw(0).trim() + "));");

        // =========================================================== SCORE
        cat = "Score";
        act("set_score", "Set Score", "★", "Set the score to {0}", REL, A(e("Score", "0")), g -> g.set("score", 0) + ";");
        ask("if_score", "Test Score", "★?", "If score is {1} {0}", 0, A(e("Value", "0"), m("Operation", 0, OPS)),
                g -> cmp("score", g.a(0), g.menu(1)));
        act("draw_score", "Draw Score", "★→", "Draw the score at ({0}, {1})", REL,
                A(e("X", "8"), e("Y", "8"), t("Caption", "Score: ")),
                g -> "draw_text(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + " + string(score));");
        act("set_lives", "Set Lives", "♥", "Set lives to {0}", REL, A(e("Lives", "3")), g -> g.set("lives", 0) + ";");
        ask("if_lives", "Test Lives", "♥?", "If lives is {1} {0}", 0, A(e("Value", "0"), m("Operation", 0, OPS)),
                g -> cmp("lives", g.a(0), g.menu(1)));
        act("draw_lives", "Draw Lives", "♥→", "Draw lives at ({0}, {1})", REL,
                A(e("X", "8"), e("Y", "24"), t("Caption", "Lives: ")),
                g -> "draw_text(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + " + string(lives));");
        act("draw_life_images", "Draw Life Images", "♥♥", "Draw lives as {2} at ({0}, {1})", REL,
                A(e("X", "8"), e("Y", "8"), spr("Image")),
                g -> "{\n    var __k;\n    for (__k = 0; __k < lives; __k += 1) draw_sprite(" + g.a(2) + ", 0, " + g.px(0)
                        + " + __k * sprite_get_width(" + g.a(2) + "), " + g.py(1) + ");\n}");
        act("set_health", "Set Health", "✚", "Set health to {0}", REL, A(e("Health", "100")), g -> g.set("health", 0) + ";");
        ask("if_health", "Test Health", "✚?", "If health is {1} {0}", 0, A(e("Value", "0"), m("Operation", 0, OPS)),
                g -> cmp("health", g.a(0), g.menu(1)));
        act("draw_health", "Draw Health Bar", "▭", "Draw health bar ({0},{1})-({2},{3})", REL,
                A(e("X1", "8"), e("Y1", "8"), e("X2", "108"), e("Y2", "18"), col("Back", 0x000000), col("Bar", 0x00FF00)),
                g -> "draw_healthbar(" + g.px(0) + ", " + g.py(1) + ", " + g.px(2) + ", " + g.py(3) + ", health, "
                        + g.a(4) + ", " + g.a(5) + ");");
        act("set_highscore", "Submit Highscore", "🏆", "Remember score as highscore", 0, A(),
                g -> "if (score > save_get(\"highscore\", 0)) save_set(\"highscore\", score);");
        act("draw_highscore", "Draw Highscore", "🏆→", "Draw highscore at ({0}, {1})", REL,
                A(e("X", "8"), e("Y", "40"), t("Caption", "Best: ")),
                g -> "draw_text(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + " + string(save_get(\"highscore\", 0)));");

        // =========================================================== DRAW
        cat = "Draw";
        act("draw_self", "Draw Self", "☺", "Draw the instance", 0, A(), g -> "draw_self();");
        act("draw_sprite", "Draw Sprite", "▤", "Draw {0} frame {3} at ({1}, {2})", REL | APPLY,
                A(spr("Sprite"), e("X", "0"), e("Y", "0"), e("Subimage", "-1")),
                g -> "draw_sprite(" + g.a(0) + ", " + g.a(3) + ", " + g.px(1) + ", " + g.py(2) + ");");
        act("draw_background", "Draw Background", "▦", "Draw background {0} at ({1}, {2}) tiled {3}", REL,
                A(bg("Background"), e("X", "0"), e("Y", "0"), b("Tiled", false)),
                g -> (g.bool(3) ? "draw_background_tiled(" : "draw_background(") + g.a(0) + ", " + g.px(1) + ", " + g.py(2) + ");");
        act("draw_text", "Draw Text", "A", "Draw text {0} at ({1}, {2})", REL | APPLY,
                A(t("Text", "Hello"), e("X", "0"), e("Y", "0")),
                g -> "draw_text(" + g.px(1) + ", " + g.py(2) + ", " + g.a(0) + ");");
        act("draw_text_ext", "Draw Scaled Text", "Aa", "Draw text {0} at ({1}, {2}) scale {3}, angle {4}", REL,
                A(t("Text", "Hello"), e("X", "0"), e("Y", "0"), e("Scale", "2"), e("Angle", "0")),
                g -> "draw_text_transformed(" + g.px(1) + ", " + g.py(2) + ", " + g.a(0) + ", " + g.a(3) + ", " + g.a(3) + ", " + g.a(4) + ");");
        act("draw_rect", "Draw Rectangle", "▭", "Draw rectangle ({0},{1})-({2},{3}) {4}", REL,
                A(e("X1", "0"), e("Y1", "0"), e("X2", "32"), e("Y2", "32"), m("Fill", 0, "filled", "outline")),
                g -> "draw_rectangle(" + g.px(0) + ", " + g.py(1) + ", " + g.px(2) + ", " + g.py(3) + ", " + g.menu(4) + ");");
        act("draw_ellipse", "Draw Ellipse", "⬭", "Draw ellipse ({0},{1})-({2},{3}) {4}", REL,
                A(e("X1", "0"), e("Y1", "0"), e("X2", "32"), e("Y2", "32"), m("Fill", 0, "filled", "outline")),
                g -> "draw_ellipse(" + g.px(0) + ", " + g.py(1) + ", " + g.px(2) + ", " + g.py(3) + ", " + g.menu(4) + ");");
        act("draw_circle", "Draw Circle", "◯", "Draw circle at ({0},{1}) radius {2} {3}", REL,
                A(e("X", "0"), e("Y", "0"), e("Radius", "16"), m("Fill", 0, "filled", "outline")),
                g -> "draw_circle(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + ", " + g.menu(3) + ");");
        act("draw_line", "Draw Line", "╱", "Draw line ({0},{1})-({2},{3}) width {4}", REL,
                A(e("X1", "0"), e("Y1", "0"), e("X2", "32"), e("Y2", "32"), e("Width", "1")),
                g -> "draw_line_width(" + g.px(0) + ", " + g.py(1) + ", " + g.px(2) + ", " + g.py(3) + ", " + g.a(4) + ");");
        act("draw_arrow", "Draw Arrow", "➝", "Draw arrow ({0},{1})-({2},{3}) size {4}", REL,
                A(e("X1", "0"), e("Y1", "0"), e("X2", "32"), e("Y2", "0"), e("Tip size", "8")),
                g -> "draw_arrow(" + g.px(0) + ", " + g.py(1) + ", " + g.px(2) + ", " + g.py(3) + ", " + g.a(4) + ");");
        act("set_color", "Set Color", "🎨", "Set drawing color to {0}", 0, A(col("Color", 0x000000)),
                g -> "draw_set_color(" + g.a(0) + ");");
        act("set_alpha", "Set Alpha", "◌", "Set drawing alpha to {0}", 0, A(e("Alpha", "1")),
                g -> "draw_set_alpha(" + g.a(0) + ");");
        act("set_font", "Set Font", "F", "Font size {0}, align {1}/{2}", 0,
                A(e("Size", "16"), m("Horizontal", 0, "left", "center", "right"), m("Vertical", 0, "top", "middle", "bottom"), b("Bold", false)),
                g -> "draw_set_font_size(" + g.a(0) + ");\ndraw_set_halign(" + g.menu(1) + ");\ndraw_set_valign(" + g.menu(2) + ");\ndraw_set_font_bold(" + g.menu(3) + ");");
        act("effect", "Create Effect", "✺", "Create {0} effect at ({1}, {2}) size {3}", REL,
                A(m("Type", 0, "explosion", "ring", "ellipse", "firework", "smoke", "spark", "star", "rain", "snow"),
                        e("X", "0"), e("Y", "0"), m("Size", 1, "small", "medium", "large"), col("Color", 0xFFAA00),
                        m("Where", 1, "below objects", "above objects")),
                g -> (g.menu(5) == 1 ? "effect_create_above(" : "effect_create_below(") + g.menu(0) + ", " + g.px(1) + ", "
                        + g.py(2) + ", " + g.menu(3) + ", " + g.a(4) + ");");
        act("set_background_color", "Background Color", "▩", "Set background color to {0}", 0, A(col("Color", 0x202030)),
                g -> "background_color = " + g.a(0) + ";");

        // =========================================================== TOUCH & INPUT
        cat = "Touch";
        ask("if_touching", "Screen Touched", "☝", "If the screen is touched ({0})", 0,
                A(m("State", 0, "held", "just pressed", "just released")),
                g -> new String[]{"mouse_check_button(mb_left)", "mouse_check_button_pressed(mb_left)", "mouse_check_button_released(mb_left)"}[Math.min(2, g.menu(0))]);
        ask("if_touched_self", "Instance Touched", "👆", "If the instance is touched ({0})", APPLY,
                A(m("State", 0, "held", "just pressed")),
                g -> g.menu(0) == 0 ? "touch_on_self()" : "touch_pressed_on_self()");
        ask("if_vbutton", "Virtual Button", "Ⓐ", "If virtual button {0} is {1}", 0,
                A(m("Button", 0, "A", "B", "X", "Y"), m("State", 0, "held", "just pressed", "just released")),
                g -> new String[]{"vbutton_check(", "vbutton_check_pressed(", "vbutton_check_released("}[Math.min(2, g.menu(1))] + g.menu(0) + ")");
        ask("if_key", "Check Key", "⌨", "If key {0} is {1}", 0,
                A(key("Key", Keys.LEFT), m("State", 0, "held", "just pressed", "just released")),
                g -> new String[]{"keyboard_check(", "keyboard_check_pressed(", "keyboard_check_released("}[Math.min(2, g.menu(1))] + g.a(0) + ")");
        ask("if_joystick", "Joystick Direction", "🕹", "If the joystick points {0}", 0,
                A(m("Direction", 0, "left", "right", "up", "down", "anywhere")),
                g -> new String[]{"vjoy_x() < -0.3", "vjoy_x() > 0.3", "vjoy_y() < -0.3", "vjoy_y() > 0.3",
                        "point_distance(0, 0, vjoy_x(), vjoy_y()) > 0.3"}[Math.min(4, g.menu(0))]);
        act("platformer", "Platformer Controls", "🏃", "Platformer: run {0}, jump {1}, gravity {2}, walls {3}", APPLY,
                A(e("Run speed", "4"), e("Jump speed", "10"), e("Gravity", "0.6"), obj("Wall object")),
                g -> "action_platformer(" + g.a(0) + ", " + g.a(1) + ", " + g.a(2) + ", " + g.a(3) + ");");
        act("topdown", "Top-Down Controls", "✥", "Top-down movement at speed {0}, walls {1}", APPLY,
                A(e("Speed", "3"), obj("Wall object")),
                g -> "action_topdown(" + g.a(0) + ", " + g.a(1) + ");");
        act("move_to_touch", "Move to Touch", "⇝", "Move towards the finger at speed {0}", APPLY,
                A(e("Speed", "4")), g -> "action_move_to_touch(" + g.a(0) + ");");
        act("face_touch", "Face Touch", "⦿", "Rotate the image towards the finger", APPLY, A(),
                g -> "if (mouse_check_button(mb_left)) image_angle = point_direction(x, y, mouse_x, mouse_y);");
        act("drag_self", "Drag with Finger", "✋", "Let the player drag this instance", APPLY, A(), g -> "action_drag_self();");
        act("vibrate", "Vibrate", "📳", "Vibrate for {0} ms", 0, A(e("Milliseconds", "40")), g -> "vibrate(" + g.a(0) + ");");
        act("view_follow", "Camera Follow", "🎥", "Make view 0 follow this instance", 0, A(), g -> "view_set_follow(0, id);");
        act("view_shake", "Screen Shake", "〰", "Shake the screen strength {0} for {1} steps", 0,
                A(e("Strength", "6"), e("Steps", "15")), g -> "view_shake(" + g.a(0) + ", " + g.a(1) + ");");

        // =========================================================== 3D
        cat = "3D";
        act("d3d_fps_camera", "First-Person Camera", "👁", "First-person camera at eye height {0}", 0,
                A(e("Eye height", "16")), g -> "d3d_camera_fps(" + g.a(0) + ");");
        act("d3d_third_camera", "Third-Person Camera", "🎦", "Third-person camera, distance {0}, height {1}", 0,
                A(e("Distance", "96"), e("Height", "48")), g -> "d3d_camera_third(" + g.a(0) + ", " + g.a(1) + ");");
        act("d3d_set_camera", "Set Camera", "📷", "Camera from ({0},{1},{2}) looking at ({3},{4},{5})", 0,
                A(e("From X", "0"), e("From Y", "0"), e("From Z", "64"), e("To X", "100"), e("To Y", "100"), e("To Z", "0")),
                g -> "d3d_set_projection(" + g.a(0) + ", " + g.a(1) + ", " + g.a(2) + ", " + g.a(3) + ", " + g.a(4) + ", " + g.a(5) + ", 0, 0, 1);");
        act("d3d_block", "Draw Block", "⬛", "Draw block ({0},{1},{2})-({3},{4},{5}) texture {6}", REL,
                A(e("X1", "0"), e("Y1", "0"), e("Z1", "0"), e("X2", "32"), e("Y2", "32"), e("Z2", "32"), spr("Texture"), e("Repeat", "1")),
                g -> "d3d_draw_block(" + g.px(0) + ", " + g.py(1) + ", " + (g.rel ? "z + " : "") + g.a(2) + ", " + g.px(3) + ", " + g.py(4) + ", "
                        + (g.rel ? "z + " : "") + g.a(5) + ", sprite_get_texture(" + g.a(6) + ", 0), " + g.a(7) + ", " + g.a(7) + ");");
        act("d3d_floor", "Draw Floor", "▱", "Draw floor ({0},{1})-({3},{4}) at z {2} texture {5}", REL,
                A(e("X1", "0"), e("Y1", "0"), e("Z", "0"), e("X2", "32"), e("Y2", "32"), spr("Texture"), e("Repeat", "1")),
                g -> "d3d_draw_floor(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + ", " + g.px(3) + ", " + g.py(4) + ", " + g.a(2)
                        + ", sprite_get_texture(" + g.a(5) + ", 0), " + g.a(6) + ", " + g.a(6) + ");");
        act("d3d_wall", "Draw Wall", "▯", "Draw wall ({0},{1})-({2},{3}) height {4} texture {5}", REL,
                A(e("X1", "0"), e("Y1", "0"), e("X2", "32"), e("Y2", "0"), e("Height", "32"), spr("Texture")),
                g -> "d3d_draw_wall(" + g.px(0) + ", " + g.py(1) + ", 0, " + g.px(2) + ", " + g.py(3) + ", " + g.a(4)
                        + ", sprite_get_texture(" + g.a(5) + ", 0), 1, 1);");
        act("d3d_cylinder", "Draw Cylinder", "⌭", "Draw cylinder ({0},{1},{2})-({3},{4},{5})", REL,
                A(e("X1", "0"), e("Y1", "0"), e("Z1", "0"), e("X2", "32"), e("Y2", "32"), e("Z2", "32"), spr("Texture"), b("Closed", true)),
                g -> "d3d_draw_cylinder(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + ", " + g.px(3) + ", " + g.py(4) + ", " + g.a(5)
                        + ", sprite_get_texture(" + g.a(6) + ", 0), 1, 1, " + g.menu(7) + ", 16);");
        act("d3d_sphere", "Draw Sphere", "●", "Draw sphere ({0},{1},{2})-({3},{4},{5})", REL,
                A(e("X1", "0"), e("Y1", "0"), e("Z1", "0"), e("X2", "32"), e("Y2", "32"), e("Z2", "32"), spr("Texture")),
                g -> "d3d_draw_ellipsoid(" + g.px(0) + ", " + g.py(1) + ", " + g.a(2) + ", " + g.px(3) + ", " + g.py(4) + ", " + g.a(5)
                        + ", sprite_get_texture(" + g.a(6) + ", 0), 1, 1, 16);");
        act("d3d_set_z", "Set Z", "Z", "Set z to {0}", REL | APPLY, A(e("Z", "0")), g -> g.set("z", 0) + ";");
        act("d3d_set_pitch", "Set Look Pitch", "⦨", "Set look pitch to {0}", REL | APPLY, A(e("Pitch", "0")), g -> g.set("pitch", 0) + ";");
        act("d3d_fog", "Set Fog", "☁", "Fog {0}: color {1}, from {2} to {3}", 0,
                A(b("Enabled", true), col("Color", 0x8090A0), e("Start", "200"), e("End", "800")),
                g -> "d3d_set_fog(" + g.menu(0) + ", " + g.a(1) + ", " + g.a(2) + ", " + g.a(3) + ");");
        act("d3d_lighting", "Set Lighting", "☀", "Lighting {0}, ambient {1}", 0,
                A(b("Enabled", true), col("Ambient", 0x505050)),
                g -> "d3d_set_lighting(" + g.menu(0) + ");\nd3d_light_set_ambient(" + g.a(1) + ");");
        act("d3d_light_dir", "Directional Light", "☼", "Light {0}: direction ({1},{2},{3}) color {4}", 0,
                A(e("Index", "0"), e("DX", "0.5"), e("DY", "0.3"), e("DZ", "-1"), col("Color", 0xFFFFFF)),
                g -> "d3d_light_define_direction(" + g.a(0) + ", " + g.a(1) + ", " + g.a(2) + ", " + g.a(3) + ", " + g.a(4) + ");\nd3d_light_enable(" + g.a(0) + ", 1);");
        act("d3d_light_point", "Point Light", "💡", "Light {0}: at ({1},{2},{3}) range {4} color {5}", REL,
                A(e("Index", "1"), e("X", "0"), e("Y", "0"), e("Z", "32"), e("Range", "200"), col("Color", 0xFFDDAA)),
                g -> "d3d_light_define_point(" + g.a(0) + ", " + g.px(1) + ", " + g.py(2) + ", " + g.a(3) + ", " + g.a(4) + ", " + g.a(5) + ");\nd3d_light_enable(" + g.a(0) + ", 1);");
        act("d3d_turn", "Turn", "⟲", "Turn {0} degrees", APPLY, A(e("Degrees", "3")), g -> "direction += " + g.a(0) + ";");
        act("d3d_move_forward", "Move Forward", "⇑", "Move forward {0}, sideways {1} (walls stop: {2})", APPLY,
                A(e("Forward", "2"), e("Sideways", "0"), b("Stop at solids", true)),
                g -> "action_move_forward(" + g.a(0) + ", " + g.a(1) + ", " + g.menu(2) + ");");

        // =========================================================== RAYCAST
        cat = "Raycast";
        act("rc_colors", "Floor & Ceiling Colors", "▤", "Floor {0}, ceiling {1}", 0,
                A(col("Floor", 0x404040), col("Ceiling", 0x6A8FBF)),
                g -> "rc_set_colors(" + g.a(0) + ", " + g.a(1) + ");");
        act("rc_textures", "Floor & Ceiling Textures", "▦", "Floor texture {0}, ceiling texture {1}", 0,
                A(bg("Floor"), bg("Ceiling")), g -> "rc_set_textures(" + g.a(0) + ", " + g.a(1) + ");");
        act("rc_fov", "Field of View", "◺", "Set field of view to {0}°", 0, A(e("Degrees", "66")), g -> "rc_set_fov(" + g.a(0) + ");");
        act("rc_fog", "Distance Fog", "☁", "Fog distance {0}, color {1}", 0, A(e("Distance", "400"), col("Color", 0)),
                g -> "rc_set_fog(" + g.a(0) + ", " + g.a(1) + ");");
        act("rc_camera", "Camera on Self", "👁", "Raycast camera follows this instance", 0, A(), g -> "rc_camera_follow(id);");
        act("rc_turn", "Turn", "⟲", "Turn {0} degrees", APPLY, A(e("Degrees", "3")), g -> "direction += " + g.a(0) + ";");
        act("rc_move", "Move Forward", "⇑", "Move forward {0}, sideways {1}", APPLY,
                A(e("Forward", "2"), e("Sideways", "0")), g -> "action_move_forward(" + g.a(0) + ", " + g.a(1) + ", 1);");
        act("rc_fps_controls", "FPS Touch Controls", "🕹", "Joystick walks at {0}, turn at {1}°", APPLY,
                A(e("Walk speed", "2.5"), e("Turn speed", "3")), g -> "action_fps_controls(" + g.a(0) + ", " + g.a(1) + ");");
        ask("rc_if_wall", "Wall at Position", "▥", "If there is a wall at ({0}, {1})", REL,
                A(e("X", "0"), e("Y", "0")), g -> "rc_wall_at(" + g.px(0) + ", " + g.py(1) + ")");
        ask("rc_if_see", "Can See", "👀", "If {0} is visible from here", 0, A(obj("Object")),
                g -> "rc_can_see(" + g.a(0) + ")");
    }
}
