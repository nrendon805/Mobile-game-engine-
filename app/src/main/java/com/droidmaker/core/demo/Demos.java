package com.droidmaker.core.demo;

import com.droidmaker.core.project.Action;
import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.Ev;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.Keys;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.Room;
import com.droidmaker.core.project.Sound;
import com.droidmaker.core.project.Sprite;
import com.droidmaker.core.sfx.SfxSynth;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the bundled demo projects (images are painted procedurally, sounds synthesized). */
public final class Demos {
    private Demos() {}

    /** A demo project plus the files it needs written into its folder. */
    public static final class Demo {
        public final Project project = new Project();
        public final Map<String, Img> images = new LinkedHashMap<>();
        public final Map<String, byte[]> files = new LinkedHashMap<>();
    }

    // ---------------------------------------------------------------- helpers
    static Sprite sprite(Demo d, String name, int ox, int oy, Img... frames) {
        Sprite s = new Sprite();
        s.name = name;
        s.width = frames[0].w;
        s.height = frames[0].h;
        s.originX = ox;
        s.originY = oy;
        int l = s.width, t = s.height, r = -1, b = -1;
        for (int i = 0; i < frames.length; i++) {
            String path = "img/" + name + "_" + i + ".png";
            s.frames.add(path);
            d.images.put(path, frames[i]);
            Img im = frames[i];
            for (int y = 0; y < im.h; y++) for (int x = 0; x < im.w; x++) if ((im.get(x, y) >>> 24) > 8) {
                l = Math.min(l, x); r = Math.max(r, x); t = Math.min(t, y); b = Math.max(b, y);
            }
        }
        if (r < 0) { l = 0; t = 0; r = s.width - 1; b = s.height - 1; }
        s.bboxLeft = l; s.bboxTop = t; s.bboxRight = r; s.bboxBottom = b;
        return d.project.add(s);
    }

    static Background background(Demo d, String name, Img img) {
        Background b = new Background();
        b.name = name;
        b.width = img.w;
        b.height = img.h;
        b.file = "img/" + name + ".png";
        d.images.put(b.file, img);
        return d.project.add(b);
    }

    static Sound sound(Demo d, String name, int preset, long seed) {
        SfxSynth s = new SfxSynth(seed);
        s.preset(preset);
        Sound snd = new Sound();
        snd.name = name;
        snd.file = "snd/" + name + ".wav";
        snd.synth = s.toMap();
        d.files.put(snd.file, s.wav());
        return d.project.add(snd);
    }

    static GameObject object(Demo d, String name, Sprite spr) {
        GameObject o = new GameObject();
        o.name = name;
        o.sprite = spr == null ? -1 : spr.id;
        return d.project.add(o);
    }

    static Action act(String id, String... args) {
        Action a = new Action(id);
        for (int i = 0; i < args.length && i < a.args.size(); i++) a.args.set(i, args[i]);
        return a;
    }

    static Action code(String c) { return act("code", c); }

    static Action other(Action a) { a.appliesTo = Action.OTHER; return a; }

    static Action rel(Action a) { a.relative = true; return a; }

    static void inst(Room r, GameObject o, double x, double y) {
        Room.Inst i = new Room.Inst();
        i.id = r.newInstanceId();
        i.object = o.id;
        i.x = x;
        i.y = y;
        r.instances.add(i);
    }

    static Room room(Demo d, String name, int w, int h) {
        Room r = new Room();
        r.name = name;
        r.width = w;
        r.height = h;
        return d.project.add(r);
    }

    static final int T = 0, BLACK = 0xFF000000, WHITE = 0xFFFFFFFF;

    // =================================================================== 2D platformer
    public static Demo platformer() {
        Demo d = new Demo();
        Project p = d.project;
        p.name = "Platformer Demo";
        p.gameWidth = 640;
        p.gameHeight = 360;
        p.vpad = Project.PAD_DPAD;
        p.vpadButtons = 1;
        p.vpadKeys[0] = Keys.SPACE;

        // --- art
        Img brick = new Img(32, 32).fill(0xFF9C5A3C);
        for (int y = 0; y < 32; y += 8) {
            brick.rect(0, y, 32, 1, 0xFF6E3B26);
            int off = (y / 8) % 2 == 0 ? 0 : 8;
            for (int x = off; x < 32; x += 16) brick.rect(x, y, 1, 8, 0xFF6E3B26);
        }
        brick.rect(0, 0, 32, 3, 0xFF4CAF50).rect(0, 3, 32, 1, 0xFF2E7D32);
        brick.noise(1, 10);
        Img dirt = new Img(32, 32).fill(0xFF8D5B3E);
        dirt.noise(2, 14);
        Sprite sBlock = sprite(d, "spr_grass", 0, 0, brick);
        Sprite sDirt = sprite(d, "spr_dirt", 0, 0, dirt);

        Img[] hero = new Img[3];
        for (int f = 0; f < 3; f++) {
            Img h = new Img(24, 32);
            h.rect(7, 2, 10, 9, 0xFFFFCC99);          // head
            h.rect(6, 1, 12, 3, 0xFFD32F2F);           // cap
            h.rect(16, 3, 4, 2, 0xFFD32F2F);           // cap brim
            h.set(14, 6, BLACK).set(14, 7, BLACK);    // eye
            h.rect(6, 11, 12, 10, 0xFF1E88E5);         // body
            h.rect(4, 12, 3, 7, 0xFFFFCC99);
            h.rect(17, 12, 3, 7, 0xFFFFCC99);          // arms
            int leg = f == 1 ? 2 : f == 2 ? -2 : 0;
            h.rect(7 + leg, 21, 4, 9, 0xFF3949AB);
            h.rect(13 - leg, 21, 4, 9, 0xFF3949AB);
            h.rect(6 + leg, 29, 6, 3, 0xFF4E342E);
            h.rect(12 - leg, 29, 6, 3, 0xFF4E342E);
            h.outline(0xFF1A1A1A);
            hero[f] = h;
        }
        Sprite sHero = sprite(d, "spr_hero", 12, 16, hero);
        sHero.bboxLeft = 6; sHero.bboxRight = 17; sHero.bboxTop = 2; sHero.bboxBottom = 31;
        sHero.maskMode = Sprite.MASK_MANUAL;

        Img[] coin = new Img[4];
        double[] widths = {6.5, 4.5, 1.5, 4.5};
        for (int f = 0; f < 4; f++) {
            Img c = new Img(16, 16);
            c.ellipse(8, 8, widths[f], 7, 0xFFFFC107);
            c.ellipse(8, 8, Math.max(0.6, widths[f] - 2), 5, 0xFFFFE082);
            c.outline(0xFF8D6E00);
            coin[f] = c;
        }
        Sprite sCoin = sprite(d, "spr_coin", 8, 8, coin);

        Img[] slime = new Img[2];
        for (int f = 0; f < 2; f++) {
            Img s = new Img(24, 20);
            int top = f == 0 ? 4 : 7;
            s.ellipse(12, 13 + (f == 0 ? 0 : 1), 11, 20 - top - 6, 0xFF7CB342);
            s.rect(1, 14, 22, 6, 0xFF7CB342);
            s.rect(7, 9 + f, 3, 4, WHITE).rect(14, 9 + f, 3, 4, WHITE);
            s.set(8, 11 + f, BLACK).set(15, 11 + f, BLACK);
            s.outline(0xFF33691E);
            slime[f] = s;
        }
        Sprite sSlime = sprite(d, "spr_slime", 12, 10, slime);

        Img flag = new Img(32, 64);
        flag.rect(4, 0, 3, 64, 0xFFBDBDBD);
        flag.rect(7, 2, 22, 14, 0xFF43A047);
        flag.rect(14, 6, 8, 6, WHITE);
        flag.outline(0xFF263238);
        Sprite sFlag = sprite(d, "spr_flag", 0, 0, flag);

        Img sky = new Img(64, 360);
        for (int y = 0; y < 360; y++) {
            double t = y / 359.0;
            int r = (int) (90 + 80 * t), g = (int) (160 + 60 * t), b = (int) (235 + 15 * t);
            sky.rect(0, y, 64, 1, 0xFF000000 | (r << 16) | (g << 8) | b);
        }
        Background bSky = background(d, "bg_sky", sky);
        Img hills = new Img(256, 360);
        for (int x = 0; x < 256; x++) {
            int top = (int) (250 + 30 * Math.sin(x / 256.0 * Math.PI * 2) + 12 * Math.sin(x / 256.0 * Math.PI * 6));
            hills.rect(x, top, 1, 360 - top, 0xFF81C784);
        }
        for (int i = 0; i < 3; i++) {
            int cx = 40 + i * 90, cy = 60 + (i % 2) * 40;
            hills.circle(cx, cy, 14, 0xF0FFFFFF).circle(cx + 14, cy - 6, 16, 0xF0FFFFFF).circle(cx + 30, cy, 13, 0xF0FFFFFF);
        }
        Background bHills = background(d, "bg_hills", hills);

        Sound sndCoin = sound(d, "snd_coin", 0, 11);
        Sound sndJump = sound(d, "snd_jump", 5, 22);
        Sound sndHurt = sound(d, "snd_hurt", 4, 33);
        Sound sndStomp = sound(d, "snd_stomp", 2, 44);

        // --- objects
        GameObject oBlock = object(d, "obj_ground", sBlock);
        oBlock.solid = true;
        GameObject oDirt = object(d, "obj_dirt", sDirt);
        oDirt.solid = true;
        oDirt.parent = oBlock.id;
        GameObject oCoin = object(d, "obj_coin", sCoin);
        oCoin.addEvent(Ev.CREATE, 0).add(act("animation_speed", "0.2"));
        GameObject oSlime = object(d, "obj_slime", sSlime);
        oSlime.addEvent(Ev.CREATE, 0).add(act("speed_h", "-1.2"));
        oSlime.addEvent(Ev.CREATE, 0).add(act("animation_speed", "0.08"));
        oSlime.addEvent(Ev.STEP, 0).add(code(
                "// turn around at walls and ledges\n"
                        + "var dir = sign(hspeed);\n"
                        + "if (place_meeting(x + dir * 4, y, obj_ground) || !place_meeting(x + dir * 14, y + 4, obj_ground)) {\n"
                        + "    hspeed = -hspeed;\n"
                        + "}\n"
                        + "image_xscale = -sign(hspeed);"));
        GameObject oFlag = object(d, "obj_flag", sFlag);
        GameObject oHero = object(d, "obj_hero", sHero);
        oHero.depth = -10;
        oHero.addEvent(Ev.CREATE, 0).add(code("lives = 3;\nscore = 0;\nimage_speed = 0;\nhurt_timer = 0;"));
        // step: sound when jumping, the ready-made platformer action, animation
        oHero.addEvent(Ev.STEP, 0).add(code(
                "if (on_ground && (keyboard_check_pressed(vk_space) || keyboard_check_pressed(vk_up))) sound_play(snd_jump);"));
        oHero.addEvent(Ev.STEP, 0).add(act("platformer", "3.2", "9", "0.45", "obj_ground"));
        oHero.addEvent(Ev.STEP, 0).add(code(
                "// walk animation\n"
                        + "if (moving && on_ground) image_speed = 0.2; else { image_speed = 0; image_index = 0; }\n"
                        + "if (hurt_timer > 0) { hurt_timer -= 1; image_alpha = 0.5 + 0.5 * (hurt_timer mod 6 < 3); } else image_alpha = 1;\n"
                        + "if (y > room_height + 64) { lives -= 1; x = xstart; y = ystart; pf_vsp = 0; sound_play(snd_hurt); }"));
        // coins: D&D only
        oHero.addEvent(Ev.COLLISION, oCoin.id).add(act("play_sound", "snd_coin", "0"));
        oHero.addEvent(Ev.COLLISION, oCoin.id).add(rel(act("set_score", "10")));
        Action eff = act("effect", "5", "0", "0", "0", String.valueOf(0xFFD54F), "1");
        oHero.addEvent(Ev.COLLISION, oCoin.id).add(other(eff));
        oHero.addEvent(Ev.COLLISION, oCoin.id).add(other(act("destroy_instance")));
        // enemies: stomp or get hurt
        oHero.addEvent(Ev.COLLISION, oSlime.id).add(code(
                "if (pf_vsp > 0 && y < other.y - 4) {\n"
                        + "    effect_create_above(ef_explosion, other.x, other.y, 0, c_lime);\n"
                        + "    with (other) instance_destroy();\n"
                        + "    pf_vsp = -6;\n"
                        + "    score += 50;\n"
                        + "    sound_play(snd_stomp);\n"
                        + "} else if (hurt_timer <= 0) {\n"
                        + "    lives -= 1;\n"
                        + "    hurt_timer = 60;\n"
                        + "    sound_play(snd_hurt);\n"
                        + "    view_shake(6, 15);\n"
                        + "    vibrate(60);\n"
                        + "    pf_vsp = -5;\n"
                        + "}"));
        oHero.addEvent(Ev.COLLISION, oFlag.id).add(act("play_sound", "snd_coin", "0"));
        oHero.addEvent(Ev.COLLISION, oFlag.id).add(act("show_message", "\"You reached the flag! Score: \" + string(score)"));
        oHero.addEvent(Ev.COLLISION, oFlag.id).add(act("set_highscore"));
        oHero.addEvent(Ev.COLLISION, oFlag.id).add(act("game_restart"));
        oHero.addEvent(Ev.OTHER, Ev.O_NO_LIVES).add(act("show_message", "Game over!"));
        oHero.addEvent(Ev.OTHER, Ev.O_NO_LIVES).add(act("set_highscore"));
        oHero.addEvent(Ev.OTHER, Ev.O_NO_LIVES).add(act("game_restart"));
        // HUD with D&D draw actions in the Draw GUI event
        oHero.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("set_font", "20", "0", "0", "1"));
        oHero.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("set_color", "0"));
        oHero.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("draw_score", "18", "14", "Score: "));
        oHero.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("draw_highscore", "18", "40", "Best: "));
        oHero.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("draw_life_images", "540", "10", "spr_hero"));

        // --- room
        Room r = room(d, "rm_level1", 2400, 360);
        r.speed = 60;
        r.backgroundColor = 0x5aa0eb;
        r.backgrounds[0].visible = true;
        r.backgrounds[0].bg = bSky.id;
        r.backgrounds[0].vtiled = false;
        r.backgrounds[1].visible = true;
        r.backgrounds[1].bg = bHills.id;
        r.backgrounds[1].vtiled = false;
        r.viewsEnabled = true;
        Room.View v = r.views[0];
        v.visible = true;
        v.wview = 640; v.hview = 360; v.wport = 640; v.hport = 360;
        v.follow = oHero.id;
        v.hborder = 260; v.vborder = 140;
        r.gridX = 32; r.gridY = 32;
        String[] map = {
                "                                                                           ",
                "                                                                           ",
                "                                           c c c                           ",
                "                     c c c                #######          c c             ",
                "                    #######                               #####         F  ",
                "          c c                    c   c                                  .  ",
                "         #####               #########          c c c                  .  ",
                "   P                    e                      #######        e        .  ",
                "################   ########################         ######################",
                "DDDDDDDDDDDDDDDD   DDDDDDDDDDDDDDDDDDDDDDDD         DDDDDDDDDDDDDDDDDDDDDD",
                "DDDDDDDDDDDDDDDD   DDDDDDDDDDDDDDDDDDDDDDDD         DDDDDDDDDDDDDDDDDDDDDD"};
        for (int row = 0; row < map.length; row++)
            for (int col = 0; col < map[row].length(); col++) {
                char ch = map[row].charAt(col);
                double x = col * 32, y = row * 32 + 8;
                switch (ch) {
                    case '#': inst(r, oBlock, x, y); break;
                    case 'D': inst(r, oDirt, x, y); break;
                    case 'c': inst(r, oCoin, x + 16, y + 16); break;
                    case 'e': inst(r, oSlime, x + 16, y + 22); break;
                    case 'P': inst(r, oHero, x + 16, y); break;
                    case 'F': inst(r, oFlag, x, 8 * 32 + 8 - 64); break;
                    default: break;
                }
            }
        return d;
    }

    // =================================================================== 2.5D raycast
    public static Demo raycast() {
        Demo d = new Demo();
        Project p = d.project;
        p.name = "Raycast Maze Demo";
        p.gameWidth = 640;
        p.gameHeight = 360;
        p.vpad = Project.PAD_STICK;
        p.vpadButtons = 1;
        p.vpadKeys[0] = Keys.SPACE;

        Img brick = new Img(64, 64).fill(0xFF8E4B3A);
        for (int y = 0; y < 64; y += 16) {
            brick.rect(0, y, 64, 2, 0xFF5D2E22);
            int off = (y / 16) % 2 == 0 ? 0 : 16;
            for (int x = off; x < 64; x += 32) brick.rect(x, y, 2, 16, 0xFF5D2E22);
        }
        brick.noise(5, 12);
        Img stone = new Img(64, 64).fill(0xFF7A7F8A);
        for (int i = 0; i < 9; i++) stone.frame((i % 3) * 21 + 1, (i / 3) * 21 + 1, 20, 20, 0xFF4F535C);
        stone.noise(6, 16);
        Img door = new Img(64, 64).fill(0xFF5D4037);
        for (int x = 4; x < 64; x += 12) door.rect(x, 0, 2, 64, 0xFF3E2723);
        door.rect(50, 30, 6, 6, 0xFFFFD54F);
        door.noise(7, 8);
        Sprite sBrick = sprite(d, "spr_brick", 0, 0, brick);
        Sprite sStone = sprite(d, "spr_stone", 0, 0, stone);

        Img[] ghost = new Img[2];
        for (int f = 0; f < 2; f++) {
            Img g = new Img(48, 48);
            g.ellipse(24, 20, 16, 16, 0xE0E1F5FE);
            g.rect(8, 20, 33, 20, 0xE0E1F5FE);
            for (int k = 0; k < 4; k++) g.circle(12 + k * 8 + (f * 4 - 2), 40, 4, 0xE0E1F5FE);
            g.ellipse(18, 18, 3, 5, 0xFF263238).ellipse(30, 18, 3, 5, 0xFF263238);
            g.ellipse(24, 30, 4, 3, 0xFF263238);
            ghost[f] = g;
        }
        Sprite sGhost = sprite(d, "spr_ghost", 24, 47, ghost);
        Img gem = new Img(32, 32);
        for (int y = 0; y < 32; y++) {
            int half = y < 12 ? y + 4 : Math.max(0, 31 - y);
            gem.rect(16 - half / 2 - 1, y, half + 2, 1, y < 12 ? 0xFF4FC3F7 : 0xFF0288D1);
        }
        gem.outline(0xFF01579B);
        Sprite sGem = sprite(d, "spr_gem", 16, 31, gem);
        Img dot = new Img(16, 16);
        dot.circle(8, 8, 7, 0xFFFFEB3B);
        Sprite sDot = sprite(d, "spr_player", 8, 8, dot);
        sprite(d, "spr_door", 0, 0, door);

        Img floor = new Img(64, 64).fill(0xFF4A4A52);
        for (int y = 0; y < 64; y += 32) for (int x = 0; x < 64; x += 32) if (((x + y) / 32) % 2 == 0) floor.rect(x, y, 32, 32, 0xFF5C5C66);
        floor.noise(8, 6);
        Background bFloor = background(d, "bg_floor", floor);

        Sound sndGem = sound(d, "snd_gem", 0, 77);
        Sound sndHurt = sound(d, "snd_hurt", 4, 88);

        Sprite sCell = sprite(d, "spr_cell", 0, 0, new Img(32, 32).fill(0xFF808080));
        GameObject oWall = object(d, "obj_wall", sBrick);
        oWall.mask = sCell.id;
        oWall.rcWall = true;
        oWall.solid = true;
        GameObject oStone = object(d, "obj_stone", sStone);
        oStone.parent = oWall.id;
        oStone.mask = sCell.id;
        oStone.rcWall = true;
        oStone.solid = true;
        GameObject oGem = object(d, "obj_gem", sGem);
        oGem.addEvent(Ev.STEP, 0).add(code("z = 4 + sin(current_time / 250 + x) * 4;"));
        GameObject oGhost = object(d, "obj_ghost", sGhost);
        oGhost.mask = sDot.id;
        oGhost.addEvent(Ev.CREATE, 0).add(code("image_speed = 0.05;\nwander = irandom(359);"));
        oGhost.addEvent(Ev.STEP, 0).add(code(
                "var p = instance_nearest(x, y, obj_player);\n"
                        + "if (p != noone && point_distance(x, y, p.x, p.y) < 220 && rc_line_clear(x, y, p.x, p.y)) {\n"
                        + "    mp_linear_step(p.x, p.y, 0.9, false);\n"
                        + "} else {\n"
                        + "    var nx = x + lengthdir_x(0.6, wander), ny = y + lengthdir_y(0.6, wander);\n"
                        + "    if (rc_wall_at(nx + lengthdir_x(10, wander), ny + lengthdir_y(10, wander))) wander = irandom(359); else { x = nx; y = ny; }\n"
                        + "}"));
        GameObject oPlayer = object(d, "obj_player", sDot);
        oPlayer.visible = false;
        oPlayer.addEvent(Ev.CREATE, 0).add(code("health = 100;\nscore = 0;\ndirection = 0;\nhurt = 0;"));
        oPlayer.addEvent(Ev.STEP, 0).add(act("rc_fps_controls", "2.2", "3"));
        oPlayer.addEvent(Ev.STEP, 0).add(code("if (hurt > 0) hurt -= 1;\nif (instance_number(obj_gem) == 0) { show_message(\"You found every gem! Score \" + string(score)); game_restart(); }"));
        oPlayer.addEvent(Ev.COLLISION, oGem.id).add(act("play_sound", "snd_gem", "0"));
        oPlayer.addEvent(Ev.COLLISION, oGem.id).add(rel(act("set_score", "25")));
        oPlayer.addEvent(Ev.COLLISION, oGem.id).add(other(act("destroy_instance")));
        oPlayer.addEvent(Ev.COLLISION, oGhost.id).add(code(
                "if (hurt <= 0) {\n    health -= 20;\n    hurt = 45;\n    sound_play(snd_hurt);\n    vibrate(80);\n}"));
        oPlayer.addEvent(Ev.OTHER, Ev.O_NO_HEALTH).add(act("show_message", "The ghosts got you!"));
        oPlayer.addEvent(Ev.OTHER, Ev.O_NO_HEALTH).add(act("game_restart"));
        oPlayer.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(code(
                "// HUD: health bar, score, crosshair and a minimap\n"
                        + "draw_set_alpha(1);\n"
                        + "draw_healthbar(16, 16, 196, 32, health, c_black, c_red, c_lime, 0, true, true);\n"
                        + "draw_set_color(c_white);\n"
                        + "draw_set_font_size(18);\n"
                        + "draw_text(16, 40, \"Gems: \" + string(instance_number(obj_gem)) + \"   Score: \" + string(score));\n"
                        + "draw_line(gui_width / 2 - 8, gui_height / 2, gui_width / 2 + 8, gui_height / 2);\n"
                        + "draw_line(gui_width / 2, gui_height / 2 - 8, gui_width / 2, gui_height / 2 + 8);\n"
                        + "if (hurt > 0) { draw_set_color(c_red); draw_set_alpha(hurt / 90); draw_rectangle(0, 0, gui_width, gui_height, false); draw_set_alpha(1); }\n"
                        + "var cs = 4, mx = gui_width - room_width / 32 * cs - 12, my = 12, i, j;\n"
                        + "draw_set_alpha(0.6);\n"
                        + "draw_set_color(c_black);\n"
                        + "draw_rectangle(mx - 2, my - 2, mx + room_width / 32 * cs + 1, my + room_height / 32 * cs + 1, false);\n"
                        + "draw_set_color(c_silver);\n"
                        + "for (i = 0; i < room_width / 32; i += 1) for (j = 0; j < room_height / 32; j += 1)\n"
                        + "    if (rc_wall_at(i * 32 + 16, j * 32 + 16)) draw_rectangle(mx + i * cs, my + j * cs, mx + i * cs + cs - 1, my + j * cs + cs - 1, false);\n"
                        + "draw_set_color(c_yellow);\n"
                        + "draw_circle(mx + x / 32 * cs, my + y / 32 * cs, 2, false);\n"
                        + "draw_set_color(c_aqua);\n"
                        + "with (obj_gem) draw_point(mx + x / 32 * cs, my + y / 32 * cs);\n"
                        + "draw_set_color(c_white);\n"
                        + "with (obj_ghost) draw_circle(mx + x / 32 * cs, my + y / 32 * cs, 1.5, false);\n"
                        + "draw_set_alpha(1);"));

        Room r = room(d, "rm_maze", 640, 640);
        r.mode = Room.MODE_RAYCAST;
        r.gridX = 32;
        r.gridY = 32;
        r.views[0].follow = oPlayer.id;
        r.rcFloorTex = bFloor.id;
        r.rcCeilColor = 0x23263a;
        r.rcFogDistance = 420;
        r.rcFogColor = 0x101018;
        r.rcResolution = 360;
        String[] map = {
                "####################",
                "#P.....#.......g...#",
                "#.##.#.#.#####.###.#",
                "#.#..#...#...#...#.#",
                "#.#.####.#.G.###.#.#",
                "#.#....#.#...#g..#.#",
                "#.####.#.##S##.###.#",
                "#....#.#.......#...#",
                "####.#.#######.#.###",
                "#g...#.......#.#...#",
                "#.#####.SSS..#.###.#",
                "#.....#.S.S....#...#",
                "#.###.#.SgS.####.#.#",
                "#...#.#.....#....#.#",
                "###.#.#####.#.####.#",
                "#...#.....#.#....#g#",
                "#.#######.#.####.#.#",
                "#.G.......#....#...#",
                "#g........#..G.....#",
                "####################"};
        for (int row = 0; row < map.length; row++)
            for (int col = 0; col < map[row].length(); col++) {
                char ch = map[row].charAt(col);
                double x = col * 32, y = row * 32;
                switch (ch) {
                    case '#': inst(r, oWall, x, y); break;
                    case 'S': inst(r, oStone, x, y); break;
                    case 'g': inst(r, oGem, x + 16, y + 16); break;
                    case 'G': inst(r, oGhost, x + 16, y + 16); break;
                    case 'P': inst(r, oPlayer, x + 16, y + 16); break;
                    default: break;
                }
            }
        return d;
    }

    // =================================================================== 3D
    public static Demo world3d() {
        Demo d = new Demo();
        Project p = d.project;
        p.name = "3D World Demo";
        p.gameWidth = 640;
        p.gameHeight = 360;
        p.vpad = Project.PAD_STICK;
        p.vpadButtons = 1;
        p.vpadKeys[0] = Keys.SPACE;

        Img crate = new Img(64, 64).fill(0xFFB07A3B);
        crate.frame(0, 0, 64, 64, 0xFF6D4C1F).frame(1, 1, 62, 62, 0xFF6D4C1F).frame(6, 6, 52, 52, 0xFF6D4C1F);
        for (int k = 0; k < 52; k++) { crate.rect(6 + k, 6 + k, 3, 1, 0xFF6D4C1F); crate.rect(57 - k, 6 + k, 3, 1, 0xFF6D4C1F); }
        crate.noise(9, 10);
        Sprite sCrate = sprite(d, "spr_crate", 0, 0, crate);
        Img wall = new Img(64, 64).fill(0xFF90A4AE);
        for (int y = 0; y < 64; y += 16) {
            wall.rect(0, y, 64, 2, 0xFF607D8B);
            for (int x = (y / 16) % 2 * 16; x < 64; x += 32) wall.rect(x, y, 2, 16, 0xFF607D8B);
        }
        wall.noise(10, 10);
        Sprite sWall = sprite(d, "spr_wall3d", 0, 0, wall);
        Img tree = new Img(48, 80);
        tree.rect(21, 48, 6, 32, 0xFF6D4C41);
        tree.circle(24, 28, 20, 0xFF2E7D32).circle(14, 38, 12, 0xFF388E3C).circle(34, 38, 12, 0xFF388E3C).circle(24, 14, 12, 0xFF43A047);
        tree.noise(11, 14);
        tree.outline(0xFF1B3D1C);
        Sprite sTree = sprite(d, "spr_tree", 24, 79, tree);
        Img orb = new Img(32, 32);
        orb.circle(16, 16, 14, 0xFFFFC107).circle(12, 12, 5, 0xFFFFF59D);
        Sprite sOrb = sprite(d, "spr_orb", 16, 31, orb);
        Img mark = new Img(16, 16).fill(0xFFFFEB3B);
        Sprite sPlayer = sprite(d, "spr_marker", 8, 8, mark);
        Img grass = new Img(64, 64).fill(0xFF558B2F);
        grass.noise(12, 18);
        for (int i = 0; i < 40; i++) grass.set((i * 37) % 64, (i * 23) % 64, 0xFF8BC34A);
        Background bGrass = background(d, "bg_grass", grass);
        Sound sndOrb = sound(d, "snd_orb", 3, 99);

        Sprite sCell3 = sprite(d, "spr_cell", 0, 0, new Img(32, 32).fill(0xFF808080));
        GameObject oWall = object(d, "obj_wall", sWall);
        oWall.mask = sCell3.id;
        oWall.solid = true;
        oWall.shape3d = GameObject.SHAPE_BLOCK;
        oWall.height3d = 48;
        GameObject oCrate = object(d, "obj_crate", sCrate);
        oCrate.mask = sCell3.id;
        oCrate.solid = true;
        oCrate.shape3d = GameObject.SHAPE_BLOCK;
        oCrate.height3d = 32;
        GameObject oTree = object(d, "obj_tree", sTree);
        oTree.shape3d = GameObject.SHAPE_BILLBOARD;
        oTree.mask = sPlayer.id;
        oTree.solid = true;
        GameObject oOrb = object(d, "obj_orb", sOrb);
        oOrb.shape3d = GameObject.SHAPE_BILLBOARD;
        oOrb.mask = sPlayer.id;
        oOrb.addEvent(Ev.STEP, 0).add(code("z = 8 + sin(current_time / 300 + x) * 6;"));
        GameObject oSpinner = object(d, "obj_spinner", null);
        oSpinner.addEvent(Ev.CREATE, 0).add(code("angle = 0;"));
        oSpinner.addEvent(Ev.STEP, 0).add(code("angle += 1.5;"));
        oSpinner.addEvent(Ev.DRAW, 0).add(code(
                "// a spinning, floating crate drawn with d3d functions\n"
                        + "d3d_transform_set_identity();\n"
                        + "d3d_transform_add_translation(-16, -16, -16);\n"
                        + "d3d_transform_add_rotation_z(angle);\n"
                        + "d3d_transform_add_rotation_x(angle * 0.5);\n"
                        + "d3d_transform_add_translation(x, y, 48 + sin(angle / 20) * 8);\n"
                        + "d3d_draw_block(0, 0, 0, 32, 32, 32, sprite_get_texture(spr_crate, 0), 1, 1);\n"
                        + "d3d_transform_set_identity();\n"
                        + "d3d_set_color(c_aqua);\n"
                        + "d3d_draw_ellipsoid(x - 10, y - 10, 100, x + 10, y + 10, 120, -1, 1, 1, 16);\n"
                        + "d3d_set_color(c_white);\n"
                        + "d3d_draw_cylinder(x - 6, y - 6, 0, x + 6, y + 6, 24, sprite_get_texture(spr_wall3d, 0), 1, 1, true, 12);"));
        GameObject oPlayer = object(d, "obj_player", sPlayer);
        oPlayer.shape3d = GameObject.SHAPE_NONE;
        oPlayer.addEvent(Ev.CREATE, 0).add(act("d3d_fps_camera", "18"));
        oPlayer.addEvent(Ev.CREATE, 0).add(code("score = 0;\ndirection = 45;\nzv = 0;"));
        oPlayer.addEvent(Ev.STEP, 0).add(act("rc_fps_controls", "2.4", "3"));
        oPlayer.addEvent(Ev.STEP, 0).add(code(
                "// jump with the A button\n"
                        + "if (z <= 0 && vbutton_check_pressed(0)) zv = 5;\n"
                        + "zv -= 0.3;\n"
                        + "z = max(0, z + zv);\n"
                        + "if (z == 0) zv = 0;\n"
                        + "if (instance_number(obj_orb) == 0) { show_message(\"All orbs collected! Score \" + string(score)); game_restart(); }"));
        oPlayer.addEvent(Ev.COLLISION, oOrb.id).add(act("play_sound", "snd_orb", "0"));
        oPlayer.addEvent(Ev.COLLISION, oOrb.id).add(rel(act("set_score", "100")));
        oPlayer.addEvent(Ev.COLLISION, oOrb.id).add(other(act("destroy_instance")));
        oPlayer.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("set_font", "18", "0", "0", "1"));
        oPlayer.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("set_color", String.valueOf(0xFFFFFF)));
        oPlayer.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("draw_score", "16", "12", "Score: "));
        oPlayer.addEvent(Ev.DRAW, Ev.DRAW_GUI).add(act("draw_text", "Joystick to walk, drag to look, A to jump", "16", "36"));

        Room r = room(d, "rm_world", 768, 768);
        r.mode = Room.MODE_3D;
        r.gridX = 32;
        r.gridY = 32;
        r.backgroundColor = 0x87ceeb;
        r.views[0].follow = oPlayer.id;
        r.d3FloorTex = bGrass.id;
        r.d3Fog = true;
        r.d3FogColor = 0x87ceeb;
        r.d3FogStart = 250;
        r.d3FogEnd = 700;
        r.d3EyeHeight = 18;
        for (int k = 0; k < 24; k++) {
            inst(r, oWall, k * 32, 0);
            inst(r, oWall, k * 32, 23 * 32);
            if (k > 0 && k < 23) { inst(r, oWall, 0, k * 32); inst(r, oWall, 23 * 32, k * 32); }
        }
        int[][] crates = {{5, 5}, {6, 5}, {5, 6}, {12, 8}, {13, 8}, {12, 9}, {17, 15}, {8, 16}, {9, 16}, {9, 17}, {15, 4}, {4, 13}};
        for (int[] c : crates) inst(r, oCrate, c[0] * 32, c[1] * 32);
        int[][] trees = {{3, 18}, {19, 19}, {20, 6}, {10, 3}, {15, 12}, {6, 10}, {18, 10}, {11, 20}};
        for (int[] t : trees) inst(r, oTree, t[0] * 32 + 16, t[1] * 32 + 16);
        int[][] orbs = {{7, 7}, {14, 9}, {18, 17}, {4, 20}, {20, 3}, {11, 13}};
        for (int[] o : orbs) inst(r, oOrb, o[0] * 32 + 16, o[1] * 32 + 16);
        inst(r, oSpinner, 11 * 32 + 16, 11 * 32 + 16);
        inst(r, oPlayer, 2 * 32 + 16, 2 * 32 + 16);
        return d;
    }
}
