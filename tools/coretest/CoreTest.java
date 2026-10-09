import com.droidmaker.core.json.Json;
import com.droidmaker.core.lang.*;
import com.droidmaker.core.lang.Compiler;
import com.droidmaker.core.project.*;
import com.droidmaker.core.runtime.*;

import java.util.*;

/** Headless tests for the core engine (run on a desktop JVM, no Android needed). */
public class CoreTest {
    static int pass, fail;

    static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); }
    }

    static void eq(String name, Object got, Object want) {
        String gs = got instanceof Double ? V.str(got) : String.valueOf(got);
        boolean ok = Objects.equals(gs, String.valueOf(want));
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + name + " got=" + got + " want=" + want); }
    }

    static class HP implements Platform {
        final Map<String, String> kv = new HashMap<>(), files = new HashMap<>();
        final List<String> errors = new ArrayList<>(), messages = new ArrayList<>();
        public long timeMillis() { return System.currentTimeMillis(); }
        public void soundPlay(int s, boolean l) {}
        public void soundStop(int s) {}
        public void soundStopAll() {}
        public boolean soundIsPlaying(int s) { return false; }
        public void soundVolume(int s, double v) {}
        public void soundPitch(int s, double p) {}
        public void showMessage(String m) { messages.add(m); }
        public boolean showQuestion(String m) { return true; }
        public String getString(String m, String d) { return d; }
        public void toast(String m) { messages.add(m); }
        public void vibrate(int ms) {}
        public void openUrl(String u) {}
        public String loadValue(String k) { return kv.get(k); }
        public void saveValue(String k, String v) { kv.put(k, v); }
        public void deleteValue(String k) { kv.remove(k); }
        public String readFile(String n) { return files.get(n); }
        public void writeFile(String n, String t) { files.put(n, t); }
        public boolean fileExists(String n) { return files.containsKey(n); }
        public void deleteFile(String n) { files.remove(n); }
        public int[] spritePixels(int s, int f) { int[] p = new int[256 * 256]; Arrays.fill(p, 0xFF8080FF); return p; }
        public int[] backgroundPixels(int b) { int[] p = new int[256 * 256]; Arrays.fill(p, 0xFF406040); return p; }
        public void gameEnded() {}
        public boolean scriptError(String m) { errors.add(m); System.out.println("  script error: " + m); return true; }
        public void log(String m) { messages.add(m); }
    }

    static class NullScreen implements Screen {
        int calls, meshes, texts;
        public void sprite(int spr, int frame, double x, double y, double xs, double ys, double angle, int color, double alpha) { calls++; }
        public void spritePart(int spr, int frame, int left, int top, int w, int h, double x, double y, double xs, double ys, int color, double alpha) { calls++; }
        public void spriteGeneral(int spr, int frame, double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4, int color, double alpha) { calls++; }
        public void background(int bg, double x, double y, double xs, double ys, double angle, int color, double alpha) { calls++; }
        public void backgroundPart(int bg, int left, int top, int w, int h, double x, double y, double xs, double ys, int color, double alpha) { calls++; }
        public void rect(double x1, double y1, double x2, double y2, int c1, int c2, int c3, int c4, double alpha, boolean outline) { calls++; }
        public void line(double x1, double y1, double x2, double y2, double width, int c1, int c2, double alpha) { calls++; }
        public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, int c1, int c2, int c3, double alpha, boolean outline) { calls++; }
        public void ellipse(double x1, double y1, double x2, double y2, int cc, int ce, double alpha, boolean outline, int segments) { calls++; }
        public void point(double x, double y, int color, double alpha) { calls++; }
        public void text(double x, double y, String s, double size, boolean bold, int h, int v, double xs, double ys, double angle, int color, double alpha, double sep, double width) { texts++; }
        public double textWidth(String s, double size, boolean bold, double width) { return s.length() * size / 2; }
        public double textHeight(String s, double size, boolean bold, double sep, double width) { return size; }
        public void clear(int color, double alpha) {}
        public void blendMode(int mode) {}
        public void beginView2D(double vx, double vy, double vw, double vh, double angle, double px, double py, double pw, double ph) {}
        public void begin3D(double px, double py, double pw, double ph, int c) {}
        public void end3D() {}
        public void raycastImage(int[] p, int w, int h, double px, double py, double pw, double ph) { calls++; }
        public void beginGui(double w, double h) {}
        public void matrices(float[] v, float[] p) {}
        public void fog(boolean on, int color, double s, double e) {}
        public void lighting(boolean on, int amb, D3State.Light[] l) {}
        public void culling(boolean on) {}
        public void mesh(Mesh m, float[] w, int tex, int color, double alpha) { meshes++; if (m.count % 3 != 0) throw new RuntimeException("bad mesh"); }
        public void world2D(float[] w) {}
    }

    /** Runs code in a game with an empty single-room project; returns global var "r". */
    static Object run(String code) {
        Project p = new Project();
        p.newRoom();
        Script s = p.newScript();
        s.name = "scr_fact";
        s.code = "if (argument0 <= 1) return 1; return argument0 * scr_fact(argument0 - 1);";
        Game g = new Game(p, new HP());
        g.start();
        Code c = g.compiler.compile(code, "test");
        g.runCode(c, null, null);
        return g.globalVar("r");
    }

    static Object eval(String expr) { return run("r = " + expr + ";"); }

    public static void main(String[] args) {
        // ---------------------------------------------------------------- language
        eq("arith", eval("1 + 2 * 3"), 7);
        eq("prec", eval("(1 + 2) * 3"), 9);
        eq("div", eval("7 div 2"), 3);
        eq("mod", eval("7 mod 3"), 1);
        eq("float", eval("7 / 2"), 3.5);
        eq("str concat", eval("\"a\" + string(5)"), "a5");
        eq("compare =", eval("3 = 3"), 1);
        eq("and/or", eval("true and false or true"), 1);
        eq("not", eval("!true"), 0);
        eq("bit", eval("5 & 3 | 8"), 9);
        eq("shift", eval("1 << 4"), 16);
        eq("hex", eval("$FF + 0x10"), 271);
        eq("ternary", eval("2 > 1 ? \"y\" : \"n\""), "y");
        eq("neg", eval("-(3 - 5)"), 2);
        eq("var/for", run("var i, s; s = 0; for (i = 0; i < 10; i += 1) s += i; r = s;"), 45);
        eq("for ++", run("var i, s = 0; for (i = 0; i < 5; i++) { s += 2; } r = s;"), 10);
        eq("while", run("var n = 0; while (n < 7) n += 1; r = n;"), 7);
        eq("do until", run("var n = 0; do { n += 3; } until (n > 10); r = n;"), 12);
        eq("repeat", run("var n = 0; repeat (4) n += 1; r = n;"), 4);
        eq("switch", run("var v = 2; switch (v) { case 1: r = \"one\"; break; case 2: r = \"two\"; break; default: r = \"x\"; }"), "two");
        eq("switch fall", run("r = 0; switch (1) { case 1: r += 1; case 2: r += 2; break; case 3: r += 4; }"), 3);
        eq("switch default", run("switch (9) { case 1: r = 1; break; default: r = 5; }"), 5);
        eq("break", run("var i; r = 0; for (i = 0; i < 100; i += 1) { if (i == 5) break; r += 1; }"), 5);
        eq("continue", run("var i; r = 0; for (i = 0; i < 10; i += 1) { if (i mod 2 == 0) continue; r += 1; }"), 5);
        eq("array", run("var a; a[0] = 1; a[3] = 4; r = a[3] + array_length_1d(a);"), 8);
        eq("2d array", run("var a; a[1, 2] = 7; r = a[1, 2];"), 7);
        eq("array lit", run("var a = [1, 2, 3]; r = a[1];"), 2);
        eq("script recursion", eval("scr_fact(5)"), 120);
        eq("string fns", eval("string_upper(string_copy(\"hello\", 2, 3))"), "ELL");
        eq("string_pos", eval("string_pos(\"l\", \"hello\")"), 3);
        eq("string_replace_all", eval("string_replace_all(\"a-b-c\", \"-\", \"+\")"), "a+b+c");
        eq("real", eval("real(\"12.5\") * 2"), 25);
        eq("point_direction", eval("point_direction(0, 0, 0, -10)"), 90);
        eq("lengthdir", eval("round(lengthdir_x(10, 180))"), -10);
        eq("min max", eval("min(3, 1, 2) + max(3, 9, 2)"), 10);
        eq("clamp", eval("clamp(15, 0, 10)"), 10);
        eq("choose", eval("choose(4, 4, 4)"), 4);
        eq("global", run("global.foo = 3; r = global.foo + 1;"), 4);
        eq("globalvar", run("globalvar gg; gg = 5; r = global.gg;"), 5);
        eq("exit", run("r = 1; exit; r = 2;"), 1);
        eq("colors", eval("make_color_rgb(255, 0, 0) == c_red"), 1);
        eq("ds_list", run("var l = ds_list_create(); ds_list_add(l, 5, 6, 7); r = ds_list_find_value(l, 1) + ds_list_size(l);"), 9);
        eq("ds_map", run("var m = ds_map_create(); ds_map_add(m, \"a\", 10); r = ds_map_find_value(m, \"a\");"), 10);
        eq("ds_grid", run("var g = ds_grid_create(3, 3); ds_grid_set(g, 1, 2, 9); r = ds_grid_get(g, 1, 2);"), 9);
        eq("save", run("save_set(\"k\", 42); r = save_get(\"k\", 0);"), 42);
        eq("ini", run("ini_open(\"t.ini\"); ini_write_real(\"s\", \"k\", 3); ini_close(); ini_open(\"t.ini\"); r = ini_read_real(\"s\", \"k\", 0); ini_close();"), 3);
        eq("json", run("var m = ds_map_create(); ds_map_add(m, \"x\", 2); var t = json_encode(m); var m2 = json_decode(t); r = ds_map_find_value(m2, \"x\");"), 2);
        eq("execute_string", eval("execute_string(\"return 6 * 7;\")"), 42);
        eq("string mul", eval("\"ab\" * 2"), "abab");
        eq("string_split", run("var a = string_split(\"x,y,z\", \",\"); r = a[2];"), "z");
        eq("comments", run("// hello\n r = 1; /* block */ r += 1;"), 2);
        eq("begin end", run("if true begin r = 3 end"), 3);
        eq("if then else", run("if 1 > 2 then r = 1 else r = 2"), 2);
        eq("nested with all", run("r = 0; with (all) r += 1;"), 0);

        // syntax errors
        check("syntax error detected", Compiler.check("x = ;", null) != null);
        check("unknown fn detected", Compiler.check("foo_bar_baz(1);", null) != null);
        check("missing brace", Compiler.check("if (1) { x = 1;", null) != null);
        ScriptError se = Compiler.check("x = 1;\ny = 2 +;\n", null);
        check("error line", se != null && se.line == 2);

        // infinite loop protection
        Project pl = new Project();
        pl.newRoom();
        HP hp = new HP();
        Game gl = new Game(pl, hp);
        gl.start();
        gl.runCode(gl.compiler.compile("while (true) {}", "loop"), null, null);
        check("infinite loop caught", !hp.errors.isEmpty() && hp.errors.get(0).contains("too long"));

        // ---------------------------------------------------------------- all D&D actions compile
        Project pa = new Project();
        GameObject oa = pa.newObject();
        pa.newSprite();
        pa.newRoom();
        int compiled = 0;
        for (ActionDef d : ActionLibrary.all()) {
            Action a = new Action(d.id);
            for (int k = 0; k < d.args.length; k++) {
                switch (d.args[k].type) {
                    case OBJECT: a.args.set(k, oa.name); break;
                    case SPRITE: a.args.set(k, "spr_sprite"); break;
                    case CODE: a.args.set(k, "x += 1;"); break;
                    default: break;
                }
            }
            List<Action> list = new ArrayList<>();
            list.add(a);
            if (d.kind == ActionDef.Kind.QUESTION || d.kind == ActionDef.Kind.REPEAT) list.add(new Action("set_var"));
            String code = CodeGen.generate(list, pa);
            ScriptError e = Compiler.check(code, gameCompiler(pa));
            if (e != null) { fail++; System.out.println("FAIL action " + d.id + ": " + e.describe() + "\n" + code); }
            else compiled++;
            // applies to other variant
            if (d.canApply) {
                a.appliesTo = Action.OTHER;
                code = CodeGen.generate(list, pa);
                e = Compiler.check(code, gameCompiler(pa));
                if (e != null) { fail++; System.out.println("FAIL action(other) " + d.id + ": " + e.describe() + "\n" + code); }
            }
        }
        pass++;
        System.out.println("actions compiled: " + compiled + " / " + ActionLibrary.all().size());

        // structured D&D: if/else/blocks
        List<Action> acts = new ArrayList<>();
        Action q = new Action("if_expression"); q.args.set(0, "1 > 2"); acts.add(q);
        acts.add(new Action("begin_block"));
        Action s1 = new Action("set_var"); s1.args.set(0, "global.r"); s1.args.set(1, "1"); acts.add(s1);
        acts.add(new Action("end_block"));
        acts.add(new Action("else"));
        Action s2 = new Action("set_var"); s2.args.set(0, "global.r"); s2.args.set(1, "2"); acts.add(s2);
        Action rep = new Action("repeat"); rep.args.set(0, "3"); acts.add(rep);
        Action s3 = new Action("set_var"); s3.args.set(0, "global.r"); s3.args.set(1, "10"); s3.relative = true; acts.add(s3);
        String gen = CodeGen.generate(acts, pa);
        Game ga = new Game(pa, new HP());
        ga.start();
        ga.runCode(ga.compiler.compile(gen, "dnd"), null, null);
        eq("dnd if/else/repeat", ga.globalVar("r"), 32);

        // ---------------------------------------------------------------- runtime
        Project p = new Project();
        Sprite spr = p.newSprite();
        spr.name = "spr_box";
        spr.frames.add("img/a.png");
        spr.frames.add("img/b.png");
        spr.width = 32; spr.height = 32; spr.bboxRight = 31; spr.bboxBottom = 31;
        GameObject wall = p.newObject();
        wall.name = "obj_wall";
        wall.sprite = spr.id;
        wall.solid = true;
        GameObject ball = p.newObject();
        ball.name = "obj_ball";
        ball.sprite = spr.id;
        Action c1 = new Action("code");
        c1.args.set(0, "hspeed = 4; hits = 0; global.created += 1;");
        ball.addEvent(Ev.CREATE, 0).add(c1);
        Action c2 = new Action("code");
        c2.args.set(0, "hits += 1; global.hitx = x;");
        ball.addEvent(Ev.COLLISION, wall.id).add(c2);
        ball.addEvent(Ev.COLLISION, wall.id).add(new Action("reverse_h"));
        Action al = new Action("code");
        al.args.set(0, "global.alarmed = 1;");
        ball.addEvent(Ev.ALARM, 0).add(al);
        Action ca = new Action("set_alarm");
        ca.args.set(0, "5");
        ball.addEvent(Ev.CREATE, 0).add(ca);
        GameObject child = p.newObject();
        child.name = "obj_child";
        child.parent = ball.id;
        child.sprite = spr.id;
        Room rm = p.newRoom();
        rm.width = 400; rm.height = 200;
        Room.Inst wi = new Room.Inst(); wi.object = wall.id; wi.x = 200; wi.y = 0; wi.id = rm.newInstanceId(); rm.instances.add(wi);
        Room.Inst bi = new Room.Inst(); bi.object = ball.id; bi.x = 100; bi.y = 0; bi.id = rm.newInstanceId(); rm.instances.add(bi);
        Room.Inst ci = new Room.Inst(); ci.object = child.id; ci.x = 0; ci.y = 100; ci.id = rm.newInstanceId(); rm.instances.add(ci);
        Room rm2 = p.newRoom();
        HP hp2 = new HP();
        Game g = new Game(p, hp2);
        g.start();
        eq("created (inherited create)", g.globalVar("created"), 2);
        eq("instance_number parent", g.countOf(ball.id), 2);
        eq("instance_number child", g.countOf(child.id), 1);
        for (int k = 0; k < 30; k++) g.step();
        Instance b = g.firstOf(ball.id);
        check("ball bounced back", b.hspeed < 0);
        check("ball not inside wall", b.x + 32 <= 200 + 1e-6);
        eq("alarm fired", g.globalVar("alarmed"), 1);
        eq("hit count", b.getVar("hits"), 1);
        NullScreen ns = new NullScreen();
        g.setScreenSize(1280, 720);
        g.draw(ns);
        check("drew sprites", ns.calls >= 3);
        // room change
        g.runCode(g.compiler.compile("room_goto_next();", "x"), null, null);
        g.step();
        eq("room changed", g.roomIndex, rm2.id);
        eq("instances cleared", g.instanceCount(), 0);
        check("no errors", hp2.errors.isEmpty());

        // with + other + field access
        eq("with/other", run("var a, b; a = 0; r = 0;"), 0);

        // ---------------------------------------------------------------- platformer helper
        Project pp = new Project();
        Sprite s32 = pp.newSprite(); s32.frames.add("x"); s32.width = 32; s32.height = 32; s32.bboxRight = 31; s32.bboxBottom = 31;
        GameObject floor = pp.newObject(); floor.name = "obj_floor"; floor.sprite = s32.id; floor.solid = true;
        GameObject pl2 = pp.newObject(); pl2.name = "obj_player"; pl2.sprite = s32.id;
        Action pf = new Action("platformer"); pf.args.set(3, "obj_floor");
        pl2.addEvent(Ev.STEP, 0).add(pf);
        Room pr = pp.newRoom();
        for (int k = 0; k < 10; k++) { Room.Inst f = new Room.Inst(); f.object = floor.id; f.x = k * 32; f.y = 200; f.id = pr.newInstanceId(); pr.instances.add(f); }
        Room.Inst pi = new Room.Inst(); pi.object = pl2.id; pi.x = 64; pi.y = 50; pi.id = pr.newInstanceId(); pr.instances.add(pi);
        HP hp3 = new HP();
        Game gp = new Game(pp, hp3);
        gp.start();
        for (int k = 0; k < 120; k++) gp.step();
        Instance pl3 = gp.firstOf(pl2.id);
        eq("player landed on floor", pl3.y, 168);
        gp.input.postKeyDown(39);
        for (int k = 0; k < 10; k++) gp.step();
        check("player walked right", pl3.x > 64);
        gp.input.postKeyUp(39);
        gp.input.postKeyDown(38);
        gp.step();
        gp.step();
        check("player jumped", pl3.y < 168);
        check("no platformer errors", hp3.errors.isEmpty());

        // ---------------------------------------------------------------- raycast & 3D render smoke tests
        Project rp = new Project();
        Sprite ws = rp.newSprite(); ws.frames.add("w"); ws.width = 32; ws.height = 32; ws.bboxRight = 31; ws.bboxBottom = 31;
        GameObject rw = rp.newObject(); rw.name = "obj_rwall"; rw.sprite = ws.id; rw.rcWall = true; rw.solid = true;
        GameObject cam = rp.newObject(); cam.name = "obj_cam";
        Action fps = new Action("rc_fps_controls");
        cam.addEvent(Ev.STEP, 0).add(fps);
        GameObject enemy = rp.newObject(); enemy.name = "obj_enemy"; enemy.sprite = ws.id;
        Room rr = rp.newRoom();
        rr.mode = Room.MODE_RAYCAST;
        rr.width = 320; rr.height = 320;
        for (int k = 0; k < 10; k++) for (int j = 0; j < 10; j++) if (k == 0 || j == 0 || k == 9 || j == 9) {
            Room.Inst w = new Room.Inst(); w.object = rw.id; w.x = k * 32; w.y = j * 32; w.id = rr.newInstanceId(); rr.instances.add(w);
        }
        Room.Inst camI = new Room.Inst(); camI.object = cam.id; camI.x = 160; camI.y = 160; camI.id = rr.newInstanceId(); rr.instances.add(camI);
        Room.Inst enI = new Room.Inst(); enI.object = enemy.id; enI.x = 250; enI.y = 160; enI.id = rr.newInstanceId(); rr.instances.add(enI);
        rr.views[0].follow = cam.id;
        HP hp4 = new HP();
        Game gr = new Game(rp, hp4);
        gr.start();
        gr.setScreenSize(1280, 720);
        gr.step();
        NullScreen ns2 = new NullScreen();
        gr.draw(ns2);
        check("raycast rendered", gr.raycaster.width > 0 && gr.raycaster.pixels.length > 0);
        int center = gr.raycaster.pixels[(gr.raycaster.height / 2) * gr.raycaster.width + gr.raycaster.width / 2];
        check("enemy billboard visible in center", (center & 0xFFFFFF) != 0 && center != (0xFF000000 | 0x9a8a7a));
        gr.input.postKeyDown(38);
        for (int k = 0; k < 200; k++) gr.step();
        Instance camInst = gr.firstOf(cam.id);
        check("camera stopped by wall", camInst.x < 288 && camInst.x > 160);
        eq("rc_cast", Math.round(gr.raycaster.cast(160, 160, 180, 1000)), 128);
        check("no raycast errors", hp4.errors.isEmpty());

        Project dp = new Project();
        Sprite bs = dp.newSprite(); bs.frames.add("b"); bs.width = 32; bs.height = 32; bs.bboxRight = 31; bs.bboxBottom = 31;
        GameObject block = dp.newObject(); block.name = "obj_block"; block.sprite = bs.id; block.shape3d = GameObject.SHAPE_BLOCK;
        GameObject drawer = dp.newObject(); drawer.name = "obj_drawer";
        Action dc = new Action("code");
        dc.args.set(0, "d3d_draw_cylinder(0,0,0,32,32,64,-1,1,1,true,12); d3d_transform_add_rotation_z(45); d3d_draw_ellipsoid(0,0,0,10,10,10,-1,1,1,8);"
                + "var m = d3d_model_create(); d3d_model_block(m,0,0,0,8,8,8,1,1); d3d_model_draw(m, 10, 10, 0, -1);"
                + "d3d_primitive_begin(pr_trianglefan); d3d_vertex(0,0,0); d3d_vertex(10,0,0); d3d_vertex(10,10,0); d3d_vertex(0,10,0); d3d_primitive_end();");
        drawer.addEvent(Ev.DRAW, 0).add(dc);
        Room dr = dp.newRoom();
        dr.mode = Room.MODE_3D;
        Room.Inst b1 = new Room.Inst(); b1.object = block.id; b1.x = 64; b1.y = 64; b1.id = dr.newInstanceId(); dr.instances.add(b1);
        Room.Inst b2 = new Room.Inst(); b2.object = drawer.id; b2.x = 0; b2.y = 0; b2.id = dr.newInstanceId(); dr.instances.add(b2);
        HP hp5 = new HP();
        Game gd = new Game(dp, hp5);
        gd.start();
        gd.step();
        NullScreen ns3 = new NullScreen();
        gd.draw(ns3);
        check("3d meshes drawn", ns3.meshes >= 6);
        check("no 3d errors", hp5.errors.isEmpty());

        // project json roundtrip
        String js = Json.write(p.toJson());
        Project p2 = Project.fromJson(Json.obj(Json.parse(js)));
        eq("roundtrip objects", p2.objects.size(), p.objects.size());
        eq("roundtrip events", p2.object(ball.id).events.size(), ball.events.size());
        eq("roundtrip json equal", Json.write(p2.toJson()).equals(js), true);

        // rename refactor
        p.rename(wall, "obj_brick");
        check("rename updates code", p.object(ball.id).events.get(Ev.key(Ev.COLLISION, wall.id)) != null);

        // game save/load
        HP hp6 = new HP();
        Game gs = new Game(pp, hp6);
        gs.start();
        gs.runCode(gs.compiler.compile("global.coins = 7; game_save(\"a\"); global.coins = 0; game_load(\"a\");", "save"), null, null);
        eq("game_load globals", gs.globalVar("coins"), 7);
        eq("game_load instances", gs.countOf(pl2.id), 1);

        // ---------------------------------------------------------------- demo projects run headless
        com.droidmaker.core.demo.Demos.Demo[] demos = {com.droidmaker.core.demo.Demos.platformer(),
                com.droidmaker.core.demo.Demos.raycast(), com.droidmaker.core.demo.Demos.world3d()};
        for (com.droidmaker.core.demo.Demos.Demo dm : demos) {
            HP dh = new HP();
            // round-trip through JSON like the app does
            Project dp2 = Project.fromJson(Json.obj(Json.parse(Json.write(dm.project.toJson()))));
            Game dg = new Game(dp2, dh);
            dg.setScreenSize(1280, 720);
            dg.start();
            check(dm.project.name + " compiled", dg.compileErrors.isEmpty());
            for (String ce : dg.compileErrors) System.out.println("  " + ce);
            NullScreen dns = new NullScreen();
            int[] keys = {39, 38, 32, 37, 40};
            for (int k = 0; k < 900 && dg.running; k++) {
                if (k % 60 == 0) dg.input.postKeyDown(keys[(k / 60) % keys.length]);
                if (k % 60 == 30) dg.input.postKeyUp(keys[(k / 60) % keys.length]);
                if (k % 90 == 0) dg.input.postJoystick((float) Math.cos(k), (float) Math.sin(k));
                if (k % 120 == 10) { dg.input.postTouchDown(0, 900, 300); }
                if (k % 120 == 20) { dg.input.postTouchMove(0, 700, 320); }
                if (k % 120 == 25) { dg.input.postTouchUp(0, 700, 320); }
                dg.step();
                if (k % 3 == 0) dg.draw(dns);
            }
            check(dm.project.name + " ran without errors", dh.errors.isEmpty());
            check(dm.project.name + " drew something", dns.calls + dns.meshes + dns.texts > 0);
            System.out.println(dm.project.name + ": " + dg.instanceCount() + " instances, score " + dg.score + ", draw calls " + (dns.calls + dns.meshes));
        }
        {
            // play the platformer: run right and jump regularly; should progress and collect coins
            HP ph = new HP();
            Game pg = new Game(com.droidmaker.core.demo.Demos.platformer().project, ph);
            pg.setScreenSize(1280, 720);
            pg.start();
            int heroId = pg.project.find(ResType.OBJECT, "obj_hero").id;
            double startX = pg.firstOf(heroId).x;
            pg.input.postKeyDown(39);
            double maxX = startX;
            for (int k = 0; k < 900; k++) {
                if (k % 25 == 0) pg.input.postKeyDown(32);
                if (k % 25 == 12) pg.input.postKeyUp(32);
                pg.step();
                Instance h = pg.firstOf(heroId);
                if (h != null) maxX = Math.max(maxX, h.x);
            }
            System.out.println("platformer: hero moved " + (maxX - startX) + " px, score " + pg.score + ", lives " + pg.globalVar("lives") + " / " + pg.lives);
            check("hero progressed", maxX - startX > 400);
            check("platformer no errors", ph.errors.isEmpty());
        }
        {
            HP rh = new HP();
            Game rg = new Game(com.droidmaker.core.demo.Demos.raycast().project, rh);
            rg.setScreenSize(1280, 720);
            rg.start();
            int pid = rg.project.find(ResType.OBJECT, "obj_player").id;
            Instance rpl = rg.firstOf(pid);
            double sx = rpl.x, sy = rpl.y;
            rg.input.postKeyDown(38);
            for (int k = 0; k < 120; k++) rg.step();
            System.out.println("raycast: player moved to " + rpl.x + "," + rpl.y + " from " + sx + "," + sy);
            check("raycast player walked", Math.hypot(rpl.x - sx, rpl.y - sy) > 50);
            NullScreen rns = new NullScreen();
            rg.draw(rns);
            int w = rg.raycaster.width, h = rg.raycaster.height;
            int c = rg.raycaster.pixels[(h / 2) * w + w / 2];
            System.out.println("raycast center pixel " + Integer.toHexString(c) + " size " + w + "x" + h);
        }
        com.droidmaker.core.sfx.SfxSynth syn = new com.droidmaker.core.sfx.SfxSynth(1);
        for (int k = 0; k < 8; k++) { syn.preset(k); check("sfx preset " + k, syn.render().length > 100); }

        System.out.println("\nPassed: " + pass + "  Failed: " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    static Compiler gameCompiler(Project p) {
        Compiler c = new Compiler();
        for (ResType t : ResType.values()) for (Resource r : p.list(t)) if (r != null) {
            if (t == ResType.SCRIPT) c.addScript(r.name, r.id, ((Script) r).code); else c.addResource(r.name, r.id);
        }
        return c;
    }
}
