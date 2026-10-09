package com.droidmaker.core.runtime;

import com.droidmaker.core.lang.Ctx;
import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.lang.V;
import com.droidmaker.core.project.Room;

import static com.droidmaker.core.lang.Builtins.*;

/** Instances, motion, collisions, rooms, input, sound, views and the D&D helper actions. */
public final class FnGame {
    private FnGame() {}

    static Instance self(Ctx c) {
        if (c.self == null) throw new ScriptError("This function needs an instance (it was called outside an object)");
        return c.self;
    }

    static Object id(Instance i) { return V.num(i == null ? -4 : i.id); }

    public static void register() {
        // ============================================================ instances
        category("Instances");
        fn("instance_create(x, y, obj)", "Create an instance; returns its id", (c, a) -> {
            Instance i = c.game.createInstance(i(a, 2), n(a, 0), n(a, 1), true);
            return V.num(i.id);
        });
        fn("instance_destroy([id])", "Destroy this (or the given) instance", (c, a) -> {
            if (a.length > 0) for (Instance i : c.game.targets(a[0], c)) c.game.destroyInstance(i, true);
            else c.game.destroyInstance(self(c), true);
            return null;
        });
        fn("instance_exists(obj)", "Whether an instance of obj (or with that id) exists", (c, a) -> {
            int t = i(a, 0);
            if (t == -1) return V.bool(c.self != null && !c.self.destroyed);
            if (t == -2) return V.bool(c.other != null && !c.other.destroyed);
            return V.bool(c.game.firstTarget(a[0], c) != null);
        });
        fn("instance_number(obj)", "Number of instances of obj", (c, a) -> V.num(c.game.countOf(i(a, 0))));
        fn("instance_find(obj, n)", "The n-th instance of obj", (c, a) -> {
            java.util.List<Instance> l = c.game.targets(a[0], c);
            int k = i(a, 1);
            return k >= 0 && k < l.size() ? V.num(l.get(k).id) : V.num(-4);
        });
        fn("instance_nearest(x, y, obj)", "Nearest instance of obj to a point", (c, a) -> {
            Instance best = null;
            double bd = Double.MAX_VALUE;
            for (Instance i : c.game.targets(a[2], c)) {
                double d = Math.hypot(i.x - n(a, 0), i.y - n(a, 1));
                if (d < bd) { bd = d; best = i; }
            }
            return id(best);
        });
        fn("instance_furthest(x, y, obj)", (c, a) -> {
            Instance best = null;
            double bd = -1;
            for (Instance i : c.game.targets(a[2], c)) {
                double d = Math.hypot(i.x - n(a, 0), i.y - n(a, 1));
                if (d > bd) { bd = d; best = i; }
            }
            return id(best);
        });
        fn("instance_place(x, y, obj)", "Instance of obj colliding if self were at (x, y)",
                (c, a) -> id(c.game.placeMeeting(self(c), n(a, 0), n(a, 1), i(a, 2), false)));
        fn("instance_position(x, y, obj)", "Instance of obj at a point", (c, a) -> id(c.game.pointHit(n(a, 0), n(a, 1), i(a, 2), null)));
        fn("instance_change(obj, perform_events)", (c, a) -> { c.game.changeInstance(self(c), i(a, 0), b(a, 1)); return null; });
        fn("instance_copy(perform_create)", (c, a) -> {
            Instance s = self(c);
            Instance i = c.game.createInstance(s.obj.id, s.x, s.y, false);
            copyState(s, i);
            if (b(a, 0)) c.game.fire(i, com.droidmaker.core.project.Ev.key(com.droidmaker.core.project.Ev.CREATE, 0), null);
            return V.num(i.id);
        });
        fn("position_destroy(x, y)", (c, a) -> {
            Instance h;
            int guard = 0;
            while ((h = c.game.pointHit(n(a, 0), n(a, 1), -3, null)) != null && guard++ < 1000) c.game.destroyInstance(h, true);
            return null;
        });
        fn("position_change(x, y, obj, perform)", (c, a) -> {
            for (Instance i : c.game.snapshot()) {
                if (i.destroyed) continue;
                i.updateBBox(c.game);
                if (i.hasBox && n(a, 0) >= i.bl && n(a, 0) < i.br && n(a, 1) >= i.bt && n(a, 1) < i.bb)
                    c.game.changeInstance(i, i(a, 2), b(a, 3));
            }
            return null;
        });
        fn("object_index_of(id)", (c, a) -> {
            Instance i = c.game.instance(i(a, 0));
            return V.num(i == null ? -1 : i.obj.id);
        });

        // ============================================================ collisions
        category("Collisions");
        fn("place_free(x, y)", "No solid instance at (x, y) for self", (c, a) -> V.bool(c.game.placeFree(self(c), n(a, 0), n(a, 1))));
        fn("place_empty(x, y)", "No instance at all at (x, y) for self",
                (c, a) -> V.bool(c.game.placeMeeting(self(c), n(a, 0), n(a, 1), -3, false) == null));
        fn("place_meeting(x, y, obj)", "Self would touch obj at (x, y)",
                (c, a) -> V.bool(c.game.placeMeeting(self(c), n(a, 0), n(a, 1), i(a, 2), false) != null));
        fn("place_snapped(hsnap, vsnap)", (c, a) -> {
            Instance s = self(c);
            boolean h = n(a, 0) <= 0 || Math.abs(s.x / n(a, 0) - Math.rint(s.x / n(a, 0))) < 1e-6;
            boolean v = n(a, 1) <= 0 || Math.abs(s.y / n(a, 1) - Math.rint(s.y / n(a, 1))) < 1e-6;
            return V.bool(h && v);
        });
        fn("position_empty(x, y)", (c, a) -> V.bool(c.game.pointHit(n(a, 0), n(a, 1), -3, null) == null));
        fn("position_meeting(x, y, obj)", (c, a) -> V.bool(c.game.pointHit(n(a, 0), n(a, 1), i(a, 2), null) != null));
        fn("collision_point(x, y, obj, prec, notme)", "Instance of obj at a point",
                (c, a) -> id(c.game.pointHit(n(a, 0), n(a, 1), i(a, 2), b(a, 4) ? c.self : null)));
        fn("collision_rectangle(x1, y1, x2, y2, obj, prec, notme)",
                (c, a) -> id(c.game.rectHit(n(a, 0), n(a, 1), n(a, 2), n(a, 3), i(a, 4), b(a, 6) ? c.self : null, false)));
        fn("collision_ellipse(x1, y1, x2, y2, obj, prec, notme)",
                (c, a) -> id(c.game.rectHit(n(a, 0), n(a, 1), n(a, 2), n(a, 3), i(a, 4), b(a, 6) ? c.self : null, true)));
        fn("collision_circle(x, y, r, obj, prec, notme)", (c, a) -> {
            double r = n(a, 2);
            return id(c.game.rectHit(n(a, 0) - r, n(a, 1) - r, n(a, 0) + r, n(a, 1) + r, i(a, 3), b(a, 5) ? c.self : null, true));
        });
        fn("collision_line(x1, y1, x2, y2, obj, prec, notme)", "First instance of obj along a line",
                (c, a) -> id(c.game.lineHit(n(a, 0), n(a, 1), n(a, 2), n(a, 3), i(a, 4), b(a, 6) ? c.self : null)));
        fn("distance_to_object(obj)", "Distance between bounding boxes", (c, a) -> {
            Instance s = self(c);
            s.updateBBox(c.game);
            double best = 1e9;
            for (Instance o : c.game.targets(a[0], c)) {
                if (o == s) continue;
                o.updateBBox(c.game);
                double dx = Math.max(0, Math.max(o.bl - s.br, s.bl - o.br));
                double dy = Math.max(0, Math.max(o.bt - s.bb, s.bt - o.bb));
                best = Math.min(best, Math.hypot(dx, dy));
            }
            return V.num(best);
        });
        fn("distance_to_point(x, y)", (c, a) -> {
            Instance s = self(c);
            s.updateBBox(c.game);
            double px = n(a, 0), py = n(a, 1);
            if (!s.hasBox) return V.num(Math.hypot(px - s.x, py - s.y));
            double dx = Math.max(0, Math.max(s.bl - px, px - s.br)), dy = Math.max(0, Math.max(s.bt - py, py - s.bb));
            return V.num(Math.hypot(dx, dy));
        });

        // ============================================================ motion
        category("Motion");
        fn("motion_set(dir, speed)", "Set direction and speed", (c, a) -> { self(c).motionSet(n(a, 0), n(a, 1)); return null; });
        fn("motion_add(dir, speed)", "Add a motion vector", (c, a) -> { self(c).motionAdd(n(a, 0), n(a, 1)); return null; });
        fn("move_towards_point(x, y, speed)", "Start moving towards a point", (c, a) -> {
            Instance s = self(c);
            double dx = n(a, 0) - s.x, dy = n(a, 1) - s.y;
            if (dx == 0 && dy == 0) { s.setSpeed(0); return null; }
            s.motionSet(Math.toDegrees(Math.atan2(-dy, dx)), n(a, 2));
            return null;
        });
        fn("move_contact_solid(dir, maxdist)", "Move until touching a solid", (c, a) -> { moveContact(c, self(c), n(a, 0), n(a, 1), true); return null; });
        fn("move_contact_all(dir, maxdist)", (c, a) -> { moveContact(c, self(c), n(a, 0), n(a, 1), false); return null; });
        fn("move_outside_solid(dir, maxdist)", (c, a) -> { moveOutside(c, self(c), n(a, 0), n(a, 1), true); return null; });
        fn("move_outside_all(dir, maxdist)", (c, a) -> { moveOutside(c, self(c), n(a, 0), n(a, 1), false); return null; });
        fn("move_bounce_solid(advanced)", "Bounce off solid instances", (c, a) -> { bounce(c, self(c), true); return null; });
        fn("move_bounce_all(advanced)", (c, a) -> { bounce(c, self(c), false); return null; });
        fn("move_wrap(hor, vert, margin)", "Wrap around the room edges", (c, a) -> {
            Instance s = self(c);
            RoomState r = c.game.rs;
            double m = n(a, 2);
            if (b(a, 0)) {
                if (s.x < -m) s.x += r.width + 2 * m;
                else if (s.x >= r.width + m) s.x -= r.width + 2 * m;
            }
            if (b(a, 1)) {
                if (s.y < -m) s.y += r.height + 2 * m;
                else if (s.y >= r.height + m) s.y -= r.height + 2 * m;
            }
            return null;
        });
        fn("move_snap(hsnap, vsnap)", (c, a) -> {
            Instance s = self(c);
            if (n(a, 0) > 0) s.x = Math.rint(s.x / n(a, 0)) * n(a, 0);
            if (n(a, 1) > 0) s.y = Math.rint(s.y / n(a, 1)) * n(a, 1);
            return null;
        });
        fn("move_random(hsnap, vsnap)", (c, a) -> { jumpRandom(c, self(c), n(a, 0), n(a, 1)); return null; });
        fn("mp_linear_step(x, y, stepsize, checkall)", "Step towards a point; returns true when there", (c, a) -> {
            Instance s = self(c);
            return V.bool(linearStep(c, s, n(a, 0), n(a, 1), n(a, 2), b(a, 3) ? -3 : -1000));
        });
        fn("mp_linear_step_object(x, y, stepsize, obj)", (c, a) -> V.bool(linearStep(c, self(c), n(a, 0), n(a, 1), n(a, 2), i(a, 3))));
        fn("mp_potential_step(x, y, stepsize, checkall)", "Step towards a point, steering around obstacles", (c, a) -> {
            Instance s = self(c);
            double tx = n(a, 0), ty = n(a, 1), st = n(a, 2);
            double d = Math.hypot(tx - s.x, ty - s.y);
            if (d <= st) { if (free(c, s, tx, ty, b(a, 3) ? -3 : -1000)) { s.x = tx; s.y = ty; } return V.bool(s.x == tx && s.y == ty); }
            double dir = Math.toDegrees(Math.atan2(-(ty - s.y), tx - s.x));
            for (int k = 0; k <= 8; k++) {
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double dd = Math.toRadians(dir + sgn * k * 20);
                    double nx = s.x + Math.cos(dd) * st, ny = s.y - Math.sin(dd) * st;
                    if (free(c, s, nx, ny, b(a, 3) ? -3 : -1000)) {
                        s.x = nx; s.y = ny;
                        s.direction = Instance.norm(Math.toDegrees(dd));
                        return V.ZERO;
                    }
                    if (k == 0) break;
                }
            }
            return V.ZERO;
        });

        // ============================================================ helper actions
        category("Helpers");
        fn("action_move_fixed(dir, speed, relative)", (c, a) -> {
            Instance s = self(c);
            double d = n(a, 0);
            if (d < 0) { if (!b(a, 2)) s.setSpeed(0); }
            else if (b(a, 2)) s.motionAdd(d, n(a, 1));
            else s.motionSet(d, n(a, 1));
            return null;
        });
        fn("action_jump_random(hsnap, vsnap)", (c, a) -> { jumpRandom(c, self(c), n(a, 0), n(a, 1)); return null; });
        fn("action_platformer(run, jump, gravity, wall)", "Ready-made platformer movement", (c, a) -> {
            platformer(c, self(c), n(a, 0), n(a, 1), n(a, 2), i(a, 3));
            return null;
        });
        fn("action_topdown(speed, wall)", "Ready-made 8-way movement", (c, a) -> { topdown(c, self(c), n(a, 0), i(a, 1)); return null; });
        fn("action_move_to_touch(speed)", (c, a) -> {
            Instance s = self(c);
            InputState in = c.game.input;
            if (!in.touchDown(firstTouch(in))) return null;
            double tx = in.mouseX, ty = in.mouseY, sp = n(a, 0);
            double d = Math.hypot(tx - s.x, ty - s.y);
            if (d <= sp) { s.x = tx; s.y = ty; }
            else { s.x += (tx - s.x) / d * sp; s.y += (ty - s.y) / d * sp; s.direction = Instance.norm(Math.toDegrees(Math.atan2(-(ty - s.y), tx - s.x))); }
            return null;
        });
        fn("action_drag_self()", (c, a) -> {
            Instance s = self(c);
            InputState in = c.game.input;
            if (touchPressedOn(c, s)) {
                s.setVar("drag_active", V.ONE);
                s.setVar("drag_dx", V.num(s.x - in.mouseX));
                s.setVar("drag_dy", V.num(s.y - in.mouseY));
            }
            if (V.truth(s.getVar("drag_active"))) {
                if (in.touchCount() == 0) s.setVar("drag_active", V.ZERO);
                else { s.x = in.mouseX + V.toNum(s.getVar("drag_dx")); s.y = in.mouseY + V.toNum(s.getVar("drag_dy")); }
            }
            return null;
        });
        fn("action_move_forward(forward, sideways, stop_at_solids)", "Move relative to the facing direction", (c, a) -> {
            moveForward(c, self(c), n(a, 0), n(a, 1), b(a, 2));
            return null;
        });
        fn("action_fps_controls(walk, turn)", "Joystick/keys/drag first-person controls", (c, a) -> {
            Instance s = self(c);
            InputState in = c.game.input;
            double walk = n(a, 0), turn = n(a, 1);
            double fwd = 0, side = 0, rot = 0;
            if (in.keyDown[38] || in.keyDown['W']) fwd += 1;
            if (in.keyDown[40] || in.keyDown['S']) fwd -= 1;
            if (in.keyDown['A']) side -= 1;
            if (in.keyDown['D']) side += 1;
            if (c.game.project.vpad == com.droidmaker.core.project.Project.PAD_STICK) {
                if (Math.abs(in.joyY) > 0.15 || Math.abs(in.joyX) > 0.15) {
                    // joystick handled analog: forward/back and turning (keys already mapped)
                    fwd = -in.joyY;
                    rot = -in.joyX;
                }
            } else {
                if (in.keyDown[37]) rot += 1;
                if (in.keyDown[39]) rot -= 1;
            }
            // dragging on the screen turns the camera
            Object last = s.getVar("fps_lastx");
            int t = firstTouch(in);
            if (in.touchDown(t)) {
                double gx = in.touches[t].dx;
                if (s.hasVar("fps_lastx") && V.toNum(s.getVar("fps_touching")) == 1) {
                    double dx = gx - V.toNum(last);
                    s.direction = Instance.norm(s.direction - dx * 0.35);
                }
                s.setVar("fps_lastx", V.num(gx));
                s.setVar("fps_touching", V.ONE);
            } else s.setVar("fps_touching", V.ZERO);
            s.direction = Instance.norm(s.direction + rot * turn);
            moveForward(c, s, fwd * walk, side * walk, true);
            return null;
        });
        fn("touch_on_self()", "A finger is on this instance", (c, a) -> V.bool(touchOn(c, self(c), false)));
        fn("touch_pressed_on_self()", "A finger just pressed this instance", (c, a) -> V.bool(touchPressedOn(c, self(c))));

        // ============================================================ rooms & game
        category("Rooms");
        fn("room_goto(room)", "Go to a room (at the end of this step)", (c, a) -> { c.game.gotoRoom(i(a, 0)); return null; });
        fn("room_goto_next()", (c, a) -> {
            int r = c.game.roomNext(c.game.roomIndex);
            if (r < 0) throw new ScriptError("There is no next room");
            c.game.gotoRoom(r);
            return null;
        });
        fn("room_goto_previous()", (c, a) -> {
            int r = c.game.roomPrevious(c.game.roomIndex);
            if (r < 0) throw new ScriptError("There is no previous room");
            c.game.gotoRoom(r);
            return null;
        });
        fn("room_restart()", (c, a) -> { c.game.gotoRoom(c.game.roomIndex); return null; });
        fn("room_next(room)", (c, a) -> V.num(c.game.roomNext(i(a, 0))));
        fn("room_previous(room)", (c, a) -> V.num(c.game.roomPrevious(i(a, 0))));
        fn("room_exists(room)", (c, a) -> V.bool(c.game.project.room(i(a, 0)) != null));
        fn("room_get_name(room)", (c, a) -> { Room r = c.game.project.room(i(a, 0)); return r == null ? "<undefined>" : r.name; });
        fn("game_restart()", (c, a) -> { c.game.restart(); return null; });
        fn("game_end()", (c, a) -> { c.game.requestEnd(); return null; });

        // ============================================================ input
        category("Input");
        fn("keyboard_check(key)", "Key is held", (c, a) -> V.bool(key(c, i(a, 0), 0)));
        fn("keyboard_check_pressed(key)", "Key was pressed this step", (c, a) -> V.bool(key(c, i(a, 0), 1)));
        fn("keyboard_check_released(key)", "Key was released this step", (c, a) -> V.bool(key(c, i(a, 0), 2)));
        fn("keyboard_check_direct(key)", (c, a) -> V.bool(key(c, i(a, 0), 0)));
        fn("keyboard_clear(key)", (c, a) -> {
            int k = i(a, 0);
            if (k >= 0 && k < 256) { c.game.input.keyDown[k] = false; c.game.input.keyPressed[k] = false; }
            return null;
        });
        fn("io_clear()", (c, a) -> { c.game.input.releaseAll(); return null; });
        fn("keyboard_key_press(key)", (c, a) -> { c.game.input.postKeyDown(i(a, 0)); return null; });
        fn("keyboard_key_release(key)", (c, a) -> { c.game.input.postKeyUp(i(a, 0)); return null; });
        fn("mouse_check_button(button)", "Screen is touched (mb_left/mb_any)", (c, a) -> V.bool(mouse(c, i(a, 0), 0)));
        fn("mouse_check_button_pressed(button)", (c, a) -> V.bool(mouse(c, i(a, 0), 1)));
        fn("mouse_check_button_released(button)", (c, a) -> V.bool(mouse(c, i(a, 0), 2)));
        fn("mouse_clear(button)", (c, a) -> { c.game.input.releaseAll(); return null; });
        fn("touch_count()", "Number of fingers on the screen", (c, a) -> V.num(c.game.input.touchCount()));
        fn("touch_check(finger)", (c, a) -> V.bool(c.game.input.touchDown(i(a, 0))));
        fn("touch_check_pressed(finger)", (c, a) -> { int k = i(a, 0); return V.bool(k >= 0 && k < 10 && c.game.input.touches[k].pressed); });
        fn("touch_check_released(finger)", (c, a) -> { int k = i(a, 0); return V.bool(k >= 0 && k < 10 && c.game.input.touches[k].released); });
        fn("touch_x(finger)", "Room x of a finger", (c, a) -> { int k = i(a, 0); return V.num(k >= 0 && k < 10 ? c.game.input.touches[k].rx : 0); });
        fn("touch_y(finger)", "Room y of a finger", (c, a) -> { int k = i(a, 0); return V.num(k >= 0 && k < 10 ? c.game.input.touches[k].ry : 0); });
        fn("touch_gui_x(finger)", "Screen (GUI) x of a finger", (c, a) -> V.num(c.game.input.guiX(i(a, 0))));
        fn("touch_gui_y(finger)", "Screen (GUI) y of a finger", (c, a) -> V.num(c.game.input.guiY(i(a, 0))));
        fn("device_mouse_check_button(device, button)", (c, a) -> V.bool(c.game.input.touchDown(i(a, 0))));
        fn("device_mouse_x(device)", (c, a) -> { int k = i(a, 0); return V.num(k >= 0 && k < 10 ? c.game.input.touches[k].rx : 0); });
        fn("device_mouse_y(device)", (c, a) -> { int k = i(a, 0); return V.num(k >= 0 && k < 10 ? c.game.input.touches[k].ry : 0); });
        fn("device_mouse_x_to_gui(device)", (c, a) -> V.num(c.game.input.guiX(i(a, 0))));
        fn("device_mouse_y_to_gui(device)", (c, a) -> V.num(c.game.input.guiY(i(a, 0))));
        fn("vjoy_x()", "Virtual joystick x (-1..1)", (c, a) -> V.num(c.game.input.joyX));
        fn("vjoy_y()", "Virtual joystick y (-1..1)", (c, a) -> V.num(c.game.input.joyY));
        fn("vjoy_direction()", (c, a) -> V.num(Instance.norm(Math.toDegrees(Math.atan2(-c.game.input.joyY, c.game.input.joyX)))));
        fn("vjoy_distance()", (c, a) -> V.num(Math.min(1, Math.hypot(c.game.input.joyX, c.game.input.joyY))));
        fn("vbutton_check(button)", "Virtual button held (0=A 1=B 2=X 3=Y)", (c, a) -> { int k = i(a, 0); return V.bool(k >= 0 && k < 4 && c.game.input.vbDown[k]); });
        fn("vbutton_check_pressed(button)", (c, a) -> { int k = i(a, 0); return V.bool(k >= 0 && k < 4 && c.game.input.vbPressed[k]); });
        fn("vbutton_check_released(button)", (c, a) -> { int k = i(a, 0); return V.bool(k >= 0 && k < 4 && c.game.input.vbReleased[k]); });
        fn("swipe_left()", (c, a) -> V.bool(c.game.input.swipeLeft));
        fn("swipe_right()", (c, a) -> V.bool(c.game.input.swipeRight));
        fn("swipe_up()", (c, a) -> V.bool(c.game.input.swipeUp));
        fn("swipe_down()", (c, a) -> V.bool(c.game.input.swipeDown));
        fn("double_tap()", (c, a) -> V.bool(c.game.input.doubleTap));
        fn("long_press()", (c, a) -> V.bool(c.game.input.longPress));

        // ============================================================ sound
        category("Sound");
        fn("sound_play(snd)", "Play a sound once", (c, a) -> { c.game.platform.soundPlay(i(a, 0), false); return null; });
        fn("sound_loop(snd)", "Play a sound looping", (c, a) -> { c.game.platform.soundPlay(i(a, 0), true); return null; });
        fn("sound_stop(snd)", (c, a) -> { c.game.platform.soundStop(i(a, 0)); return null; });
        fn("sound_stop_all()", (c, a) -> { c.game.platform.soundStopAll(); return null; });
        fn("sound_isplaying(snd)", (c, a) -> V.bool(c.game.platform.soundIsPlaying(i(a, 0))));
        fn("sound_volume(snd, volume)", (c, a) -> { c.game.platform.soundVolume(i(a, 0), n(a, 1)); return null; });
        fn("sound_pitch(snd, pitch)", (c, a) -> { c.game.platform.soundPitch(i(a, 0), n(a, 1)); return null; });
        fn("audio_play_sound(snd, priority, loop)", (c, a) -> { c.game.platform.soundPlay(i(a, 0), b(a, 2)); return null; });
        fn("audio_stop_sound(snd)", (c, a) -> { c.game.platform.soundStop(i(a, 0)); return null; });
        fn("audio_stop_all()", (c, a) -> { c.game.platform.soundStopAll(); return null; });
        fn("audio_is_playing(snd)", (c, a) -> V.bool(c.game.platform.soundIsPlaying(i(a, 0))));

        // ============================================================ views & effects
        category("Views");
        fn("view_set_follow(view, obj)", "Make a view follow an object or instance", (c, a) -> {
            RoomState r = c.game.rs;
            int v = i(a, 0);
            if (r != null && v >= 0 && v < 8) {
                if (!r.viewsEnabled) {
                    r.viewsEnabled = true;
                    r.views[0].visible = true;
                    c.game.updateDisplaySize();
                }
                r.views[v].follow = i(a, 1);
                c.game.raycaster.cameraId = i(a, 1) >= 100000 ? i(a, 1) : c.game.raycaster.cameraId;
            }
            return null;
        });
        fn("view_shake(strength, steps)", "Shake the screen", (c, a) -> { c.game.shake(n(a, 0), i(a, 1)); return null; });
        fn("effect_create_above(kind, x, y, size, color)", (c, a) -> {
            c.game.effects.create(i(a, 0), n(a, 1), n(a, 2), i(a, 3), i(a, 4), true, c.game.rs.width, c.game.rs.height);
            return null;
        });
        fn("effect_create_below(kind, x, y, size, color)", (c, a) -> {
            c.game.effects.create(i(a, 0), n(a, 1), n(a, 2), i(a, 3), i(a, 4), false, c.game.rs.width, c.game.rs.height);
            return null;
        });
        fn("effect_clear()", (c, a) -> { c.game.effects.clear(); return null; });
        fn("alarm_set(index, steps)", (c, a) -> { int k = i(a, 0); if (k >= 0 && k < 12) self(c).alarm[k] = i(a, 1); return null; });
        fn("alarm_get(index)", (c, a) -> { int k = i(a, 0); return V.num(k >= 0 && k < 12 ? self(c).alarm[k] : -1); });
    }

    // ================================================================== helpers
    static void copyState(Instance s, Instance i) {
        i.hspeed = s.hspeed; i.vspeed = s.vspeed; i.speed = s.speed; i.direction = s.direction;
        i.friction = s.friction; i.gravity = s.gravity; i.gravityDirection = s.gravityDirection;
        i.spriteIndex = s.spriteIndex; i.imageIndex = s.imageIndex; i.imageSpeed = s.imageSpeed;
        i.imageXscale = s.imageXscale; i.imageYscale = s.imageYscale; i.imageAngle = s.imageAngle;
        i.imageAlpha = s.imageAlpha; i.imageBlend = s.imageBlend; i.depth = s.depth; i.visible = s.visible;
        i.solid = s.solid; i.z = s.z; i.pitch = s.pitch;
        System.arraycopy(s.alarm, 0, i.alarm, 0, 12);
        i.varsMap().putAll(s.varsMap());
    }

    static boolean key(Ctx c, int k, int mode) {
        InputState in = c.game.input;
        boolean[] arr = mode == 0 ? in.keyDown : mode == 1 ? in.keyPressed : in.keyReleased;
        if (k == 1) { for (int j = 2; j < 256; j++) if (arr[j]) return true; return false; }
        if (k == 0) { if (mode != 0) return false; for (int j = 2; j < 256; j++) if (in.keyDown[j]) return false; return true; }
        return k > 0 && k < 256 && arr[k];
    }

    static boolean mouse(Ctx c, int b, int mode) {
        InputState in = c.game.input;
        if (b == 0) { // mb_none
            for (InputState.Touch t : in.touches) if (t.down) return false;
            return mode == 0;
        }
        if (b == 2 || b == 3) return false; // no right/middle on touch screens
        for (InputState.Touch t : in.touches) {
            if (mode == 0 && t.down) return true;
            if (mode == 1 && t.pressed) return true;
            if (mode == 2 && t.released) return true;
        }
        return false;
    }

    static int firstTouch(InputState in) {
        for (int k = 0; k < InputState.MAX_TOUCH; k++) if (in.touches[k].down) return k;
        return 0;
    }

    static boolean touchOn(Ctx c, Instance s, boolean pressedOnly) {
        s.updateBBox(c.game);
        if (!s.hasBox) return false;
        for (InputState.Touch t : c.game.input.touches) {
            if (!(pressedOnly ? t.pressed : t.down)) continue;
            if (t.rx >= s.bl && t.rx < s.br && t.ry >= s.bt && t.ry < s.bb) return true;
        }
        return false;
    }

    static boolean touchPressedOn(Ctx c, Instance s) { return touchOn(c, s, true); }

    /** Free check: target -1000 = solids only; -3 = any instance; otherwise an object/instance. */
    static boolean free(Ctx c, Instance s, double x, double y, int target) {
        if (target == -1000) return c.game.placeFree(s, x, y);
        return c.game.placeMeeting(s, x, y, target, false) == null;
    }

    static void moveContact(Ctx c, Instance s, double dir, double max, boolean solid) {
        if (max <= 0) max = 1000;
        double r = Math.toRadians(dir), dx = Math.cos(r), dy = -Math.sin(r);
        int target = solid ? -1000 : -3;
        if (!free(c, s, s.x, s.y, target)) return;
        for (int k = 0; k < (int) max; k++) {
            double nx = s.x + dx, ny = s.y + dy;
            if (!free(c, s, nx, ny, target)) {
                // refine with half steps
                for (int j = 0; j < 4; j++) {
                    dx /= 2; dy /= 2;
                    if (free(c, s, s.x + dx, s.y + dy, target)) { s.x += dx; s.y += dy; }
                }
                return;
            }
            s.x = nx;
            s.y = ny;
        }
    }

    static void moveOutside(Ctx c, Instance s, double dir, double max, boolean solid) {
        if (max <= 0) max = 1000;
        double r = Math.toRadians(dir), dx = Math.cos(r), dy = -Math.sin(r);
        int target = solid ? -1000 : -3;
        for (int k = 0; k < (int) max && !free(c, s, s.x, s.y, target); k++) { s.x += dx; s.y += dy; }
    }

    static void bounce(Ctx c, Instance s, boolean solid) {
        int target = solid ? -1000 : -3;
        if (free(c, s, s.x + s.hspeed, s.y + s.vspeed, target)) return;
        boolean hcol = !free(c, s, s.x + s.hspeed, s.y, target);
        boolean vcol = !free(c, s, s.x, s.y + s.vspeed, target);
        double h = s.hspeed, v = s.vspeed;
        if (hcol) h = -h;
        if (vcol) v = -v;
        if (!hcol && !vcol) { h = -h; v = -v; }
        s.hspeed = h;
        s.setVspeed(v);
    }

    static void jumpRandom(Ctx c, Instance s, double hs, double vs) {
        RoomState r = c.game.rs;
        for (int k = 0; k < 100; k++) {
            double x = FnCore.RNG.nextDouble() * r.width, y = FnCore.RNG.nextDouble() * r.height;
            if (hs > 0) x = Math.floor(x / hs) * hs;
            if (vs > 0) y = Math.floor(y / vs) * vs;
            if (c.game.placeFree(s, x, y)) { s.x = x; s.y = y; return; }
        }
    }

    static boolean linearStep(Ctx c, Instance s, double tx, double ty, double st, int target) {
        double d = Math.hypot(tx - s.x, ty - s.y);
        if (d < 1e-6) return true;
        double nx, ny;
        if (d <= st) { nx = tx; ny = ty; }
        else { nx = s.x + (tx - s.x) / d * st; ny = s.y + (ty - s.y) / d * st; }
        if (target != -1001 && !free(c, s, nx, ny, target)) return false;
        s.direction = Instance.norm(Math.toDegrees(Math.atan2(-(ty - s.y), tx - s.x)));
        s.x = nx;
        s.y = ny;
        return d <= st;
    }

    /** Wall check for helper movement: solids, or a specific wall object. */
    static boolean blocked(Ctx c, Instance s, double x, double y, int wall) {
        if (wall >= 0) return c.game.placeMeeting(s, x, y, wall, false) != null;
        return !c.game.placeFree(s, x, y);
    }

    static void platformer(Ctx c, Instance s, double run, double jump, double grav, int wall) {
        InputState in = c.game.input;
        boolean left = in.keyDown[37] || in.joyX < -0.3, right = in.keyDown[39] || in.joyX > 0.3;
        boolean jumpPressed = in.keyPressed[38] || in.keyPressed[32] || in.vbPressed[0] || in.keyPressed['W'];
        boolean jumpHeld = in.keyDown[38] || in.keyDown[32] || in.vbDown[0] || in.keyDown['W'];
        double hsp = ((right ? 1 : 0) - (left ? 1 : 0)) * run;
        boolean onGround = blocked(c, s, s.x, s.y + 1, wall);
        double vsp = V.toNum(s.getVar("pf_vsp"));
        if (onGround && vsp >= 0) vsp = 0;
        vsp += grav;
        if (onGround && jumpPressed) vsp = -jump;
        if (!jumpHeld && vsp < -jump / 3) vsp = -jump / 3; // variable jump height
        if (vsp > jump * 2) vsp = jump * 2;
        // horizontal
        if (hsp != 0) {
            if (blocked(c, s, s.x + hsp, s.y, wall)) {
                // try stepping up small slopes
                boolean stepped = false;
                for (int up = 1; up <= Math.max(1, (int) Math.abs(hsp)) + 1; up++)
                    if (!blocked(c, s, s.x + hsp, s.y - up, wall) && onGround) { s.x += hsp; s.y -= up; stepped = true; break; }
                if (!stepped) {
                    double st = Math.signum(hsp);
                    int guard = 0;
                    while (!blocked(c, s, s.x + st, s.y, wall) && guard++ < 1000) s.x += st;
                    s.x = snap(s.x);
                }
            } else s.x += hsp;
            s.imageXscale = Math.abs(s.imageXscale) * Math.signum(hsp);
        }
        // vertical
        if (blocked(c, s, s.x, s.y + vsp, wall)) {
            double st = Math.signum(vsp);
            int guard = 0;
            while (st != 0 && !blocked(c, s, s.x, s.y + st, wall) && guard++ < 1000) s.y += st;
            vsp = 0;
            s.y = snap(s.y);
        } else s.y += vsp;
        s.setVar("pf_vsp", V.num(vsp));
        s.setVar("on_ground", V.bool(blocked(c, s, s.x, s.y + 1, wall)));
        s.setVar("moving", V.bool(hsp != 0));
    }

    /** Removes floating point dust (167.9999999 -> 168) after pixel stepping. */
    static double snap(double v) {
        double r = Math.rint(v);
        return Math.abs(v - r) < 1e-6 ? r : v;
    }

    static void topdown(Ctx c, Instance s, double speed, int wall) {
        InputState in = c.game.input;
        double dx = (in.keyDown[39] ? 1 : 0) - (in.keyDown[37] ? 1 : 0);
        double dy = (in.keyDown[40] ? 1 : 0) - (in.keyDown[38] ? 1 : 0);
        if (Math.abs(in.joyX) > 0.15 || Math.abs(in.joyY) > 0.15) { dx = in.joyX; dy = in.joyY; }
        double l = Math.hypot(dx, dy);
        if (l > 1) { dx /= l; dy /= l; }
        double mx = dx * speed, my = dy * speed;
        if (mx != 0) {
            if (!blocked(c, s, s.x + mx, s.y, wall)) s.x += mx;
            else { double st = Math.signum(mx); int g = 0; while (!blocked(c, s, s.x + st, s.y, wall) && g++ < 1000) s.x += st; }
        }
        if (my != 0) {
            if (!blocked(c, s, s.x, s.y + my, wall)) s.y += my;
            else { double st = Math.signum(my); int g = 0; while (!blocked(c, s, s.x, s.y + st, wall) && g++ < 1000) s.y += st; }
        }
        if (l > 0.1) s.direction = Instance.norm(Math.toDegrees(Math.atan2(-dy, dx)));
        s.setVar("moving", V.bool(l > 0.1));
    }

    static void moveForward(Ctx c, Instance s, double fwd, double side, boolean stop) {
        double a = Math.toRadians(s.direction);
        double dx = Math.cos(a) * fwd + Math.sin(a) * side;
        double dy = -Math.sin(a) * fwd + Math.cos(a) * side;
        boolean rc = c.game.rs != null && c.game.rs.mode == Room.MODE_RAYCAST;
        double rad = rc ? c.game.raycaster.cellSize() * 0.22 : 0;
        if (dx != 0 && !hits(c, s, s.x + dx, s.y, stop, rc, rad, dx, 0)) s.x += dx;
        if (dy != 0 && !hits(c, s, s.x, s.y + dy, stop, rc, rad, 0, dy)) s.y += dy;
    }

    private static boolean hits(Ctx c, Instance s, double x, double y, boolean stop, boolean rc, double rad, double dx, double dy) {
        if (rc) {
            Raycaster r = c.game.raycaster;
            r.ensureGrid();
            if (dx != 0) {
                double px = x + Math.signum(dx) * rad;
                if (r.wallAt(px, y - rad) || r.wallAt(px, y + rad)) return true;
            }
            if (dy != 0) {
                double py = y + Math.signum(dy) * rad;
                if (r.wallAt(x - rad, py) || r.wallAt(x + rad, py)) return true;
            }
        }
        return stop && !c.game.placeFree(s, x, y);
    }
}
