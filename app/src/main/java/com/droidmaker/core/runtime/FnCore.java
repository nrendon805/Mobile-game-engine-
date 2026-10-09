package com.droidmaker.core.runtime;

import com.droidmaker.core.lang.Code;
import com.droidmaker.core.lang.Compiler;
import com.droidmaker.core.lang.GArray;
import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.lang.V;
import com.droidmaker.core.project.Keys;

import java.util.Arrays;
import java.util.Random;

import static com.droidmaker.core.lang.Builtins.*;

/** Math, strings, randomness, constants and general utility functions. */
public final class FnCore {
    private FnCore() {}

    public static final Random RNG = new Random();
    private static long seed = System.nanoTime();

    public static void register() {
        constants();

        // ============================================================ math
        category("Math");
        fn("abs(x)", "Absolute value", (c, a) -> V.num(Math.abs(n(a, 0))));
        fn("sign(x)", "-1, 0 or 1", (c, a) -> V.num(Math.signum(n(a, 0))));
        fn("round(x)", "Round to nearest integer", (c, a) -> V.num(Math.rint(n(a, 0))));
        fn("floor(x)", "Round down", (c, a) -> V.num(Math.floor(n(a, 0))));
        fn("ceil(x)", "Round up", (c, a) -> V.num(Math.ceil(n(a, 0))));
        fn("frac(x)", "Fractional part", (c, a) -> { double x = n(a, 0); return V.num(x - (long) x); });
        fn("sqrt(x)", "Square root", (c, a) -> {
            double x = n(a, 0);
            if (x < 0) throw new ScriptError("sqrt of a negative number");
            return V.num(Math.sqrt(x));
        });
        fn("sqr(x)", "x squared", (c, a) -> V.num(n(a, 0) * n(a, 0)));
        fn("power(x, n)", "x to the power n", (c, a) -> V.num(Math.pow(n(a, 0), n(a, 1))));
        fn("exp(x)", "e to the power x", (c, a) -> V.num(Math.exp(n(a, 0))));
        fn("ln(x)", "Natural logarithm", (c, a) -> V.num(Math.log(n(a, 0))));
        fn("log2(x)", (c, a) -> V.num(Math.log(n(a, 0)) / Math.log(2)));
        fn("log10(x)", (c, a) -> V.num(Math.log10(n(a, 0))));
        fn("logn(n, x)", (c, a) -> V.num(Math.log(n(a, 1)) / Math.log(n(a, 0))));
        fn("sin(rad)", (c, a) -> V.num(Math.sin(n(a, 0))));
        fn("cos(rad)", (c, a) -> V.num(Math.cos(n(a, 0))));
        fn("tan(rad)", (c, a) -> V.num(Math.tan(n(a, 0))));
        fn("arcsin(x)", (c, a) -> V.num(Math.asin(n(a, 0))));
        fn("arccos(x)", (c, a) -> V.num(Math.acos(n(a, 0))));
        fn("arctan(x)", (c, a) -> V.num(Math.atan(n(a, 0))));
        fn("arctan2(y, x)", (c, a) -> V.num(Math.atan2(n(a, 0), n(a, 1))));
        fn("dsin(deg)", "Sine of degrees", (c, a) -> V.num(Math.sin(Math.toRadians(n(a, 0)))));
        fn("dcos(deg)", "Cosine of degrees", (c, a) -> V.num(Math.cos(Math.toRadians(n(a, 0)))));
        fn("dtan(deg)", (c, a) -> V.num(Math.tan(Math.toRadians(n(a, 0)))));
        fn("darcsin(x)", (c, a) -> V.num(Math.toDegrees(Math.asin(n(a, 0)))));
        fn("darccos(x)", (c, a) -> V.num(Math.toDegrees(Math.acos(n(a, 0)))));
        fn("darctan(x)", (c, a) -> V.num(Math.toDegrees(Math.atan(n(a, 0)))));
        fn("darctan2(y, x)", (c, a) -> V.num(Math.toDegrees(Math.atan2(n(a, 0), n(a, 1)))));
        fn("degtorad(deg)", (c, a) -> V.num(Math.toRadians(n(a, 0))));
        fn("radtodeg(rad)", (c, a) -> V.num(Math.toDegrees(n(a, 0))));
        fn("min(a, b, ...)", "Smallest value", (c, a) -> {
            double m = n(a, 0);
            for (int i = 1; i < a.length; i++) m = Math.min(m, n(a, i));
            return V.num(m);
        });
        fn("max(a, b, ...)", "Largest value", (c, a) -> {
            double m = n(a, 0);
            for (int i = 1; i < a.length; i++) m = Math.max(m, n(a, i));
            return V.num(m);
        });
        fn("mean(a, ...)", (c, a) -> {
            double s = 0;
            for (int i = 0; i < a.length; i++) s += n(a, i);
            return V.num(a.length == 0 ? 0 : s / a.length);
        });
        fn("median(a, ...)", (c, a) -> {
            double[] v = new double[a.length];
            for (int i = 0; i < a.length; i++) v[i] = n(a, i);
            Arrays.sort(v);
            return V.num(v.length == 0 ? 0 : v[(v.length - 1) / 2]);
        });
        fn("clamp(value, min, max)", "Limit value to a range", (c, a) -> V.num(Math.max(n(a, 1), Math.min(n(a, 2), n(a, 0)))));
        fn("lerp(a, b, t)", "Linear interpolation", (c, a) -> V.num(n(a, 0) + (n(a, 1) - n(a, 0)) * n(a, 2)));
        fn("random(x)", "Random real number 0..x", (c, a) -> V.num(RNG.nextDouble() * n(a, 0)));
        fn("random_range(a, b)", "Random real between a and b", (c, a) -> V.num(n(a, 0) + RNG.nextDouble() * (n(a, 1) - n(a, 0))));
        fn("irandom(x)", "Random integer 0..x", (c, a) -> V.num(Math.floor(RNG.nextDouble() * (Math.floor(n(a, 0)) + 1))));
        fn("irandom_range(a, b)", "Random integer a..b", (c, a) -> {
            double lo = Math.ceil(Math.min(n(a, 0), n(a, 1))), hi = Math.floor(Math.max(n(a, 0), n(a, 1)));
            return V.num(lo + Math.floor(RNG.nextDouble() * (hi - lo + 1)));
        });
        fn("choose(a, ...)", "Pick one of the arguments at random", (c, a) -> a.length == 0 ? V.ZERO : a[RNG.nextInt(a.length)]);
        fn("random_set_seed(seed)", (c, a) -> { seed = (long) n(a, 0); RNG.setSeed(seed); return null; });
        fn("random_get_seed()", (c, a) -> V.num(seed));
        fn("randomize()", (c, a) -> { seed = System.nanoTime(); RNG.setSeed(seed); return null; });
        fn("point_distance(x1, y1, x2, y2)", "Distance between two points",
                (c, a) -> V.num(Math.hypot(n(a, 2) - n(a, 0), n(a, 3) - n(a, 1))));
        fn("point_distance_3d(x1, y1, z1, x2, y2, z2)", (c, a) -> {
            double dx = n(a, 3) - n(a, 0), dy = n(a, 4) - n(a, 1), dz = n(a, 5) - n(a, 2);
            return V.num(Math.sqrt(dx * dx + dy * dy + dz * dz));
        });
        fn("point_direction(x1, y1, x2, y2)", "Direction in degrees from point 1 to point 2", (c, a) -> {
            double dx = n(a, 2) - n(a, 0), dy = n(a, 3) - n(a, 1);
            if (dx == 0 && dy == 0) return V.ZERO;
            return V.num(Instance.norm(Math.toDegrees(Math.atan2(-dy, dx))));
        });
        fn("lengthdir_x(len, dir)", "Horizontal part of a vector", (c, a) -> V.num(n(a, 0) * Math.cos(Math.toRadians(n(a, 1)))));
        fn("lengthdir_y(len, dir)", "Vertical part of a vector", (c, a) -> V.num(-n(a, 0) * Math.sin(Math.toRadians(n(a, 1)))));
        fn("angle_difference(a, b)", "Signed smallest difference a - b in degrees", (c, a) -> {
            double d = ((n(a, 0) - n(a, 1)) % 360 + 540) % 360 - 180;
            return V.num(d);
        });
        fn("dot_product(x1, y1, x2, y2)", (c, a) -> V.num(n(a, 0) * n(a, 2) + n(a, 1) * n(a, 3)));
        fn("dot_product_3d(x1, y1, z1, x2, y2, z2)", (c, a) -> V.num(n(a, 0) * n(a, 3) + n(a, 1) * n(a, 4) + n(a, 2) * n(a, 5)));
        fn("approach(value, target, amount)", "Move value towards target by amount", (c, a) -> {
            double v = n(a, 0), t = n(a, 1), s = Math.abs(n(a, 2));
            return V.num(v < t ? Math.min(t, v + s) : Math.max(t, v - s));
        });
        fn("wrap(value, min, max)", "Wrap value into [min, max)", (c, a) -> {
            double v = n(a, 0), lo = n(a, 1), hi = n(a, 2), r = hi - lo;
            if (r <= 0) return V.num(lo);
            return V.num(lo + (((v - lo) % r) + r) % r);
        });

        // ============================================================ types & strings
        category("Strings");
        fn("is_real(v)", (c, a) -> V.bool(a.length > 0 && a[0] instanceof Double));
        fn("is_string(v)", (c, a) -> V.bool(a.length > 0 && a[0] instanceof String));
        fn("is_array(v)", (c, a) -> V.bool(a.length > 0 && a[0] instanceof GArray));
        fn("real(str)", "Convert a string to a number", (c, a) -> {
            if (a[0] instanceof Double) return a[0];
            String s = V.str(a[0]).trim();
            try { return V.num(Double.parseDouble(s)); } catch (NumberFormatException e) {
                StringBuilder b = new StringBuilder();
                for (char ch : s.toCharArray()) if (Character.isDigit(ch) || ch == '.' || (ch == '-' && b.length() == 0)) b.append(ch); else if (b.length() > 0) break;
                try { return V.num(Double.parseDouble(b.toString())); } catch (NumberFormatException e2) { return V.ZERO; }
            }
        });
        fn("string(value)", "Convert a value to text", (c, a) -> V.str(a[0]));
        fn("string_format(value, total, decimals)", (c, a) -> {
            String s = String.format(java.util.Locale.US, "%." + Math.max(0, i(a, 2)) + "f", n(a, 0));
            while (s.length() < i(a, 1)) s = " " + s;
            return s;
        });
        fn("chr(code)", "Character from a code", (c, a) -> String.valueOf((char) i(a, 0)));
        fn("ansi_char(code)", (c, a) -> String.valueOf((char) i(a, 0)));
        fn("ord(char)", "Code of the first character", (c, a) -> { String s = s(a, 0); return V.num(s.isEmpty() ? 0 : Character.toUpperCase(s.charAt(0)) >= 'A' && Character.toUpperCase(s.charAt(0)) <= 'Z' && s.length() == 1 ? Character.toUpperCase(s.charAt(0)) : s.charAt(0)); });
        fn("string_length(str)", (c, a) -> V.num(s(a, 0).length()));
        fn("string_pos(substr, str)", "1-based position of substr, 0 if missing", (c, a) -> V.num(s(a, 1).indexOf(s(a, 0)) + 1));
        fn("string_copy(str, index, count)", "Copy count characters starting at index (1-based)", (c, a) -> {
            String s = s(a, 0);
            int st = Math.max(1, i(a, 1)) - 1, cnt = i(a, 2);
            if (st >= s.length() || cnt <= 0) return "";
            return s.substring(st, Math.min(s.length(), st + cnt));
        });
        fn("string_char_at(str, index)", "Character at 1-based index", (c, a) -> {
            String s = s(a, 0);
            int k = i(a, 1) - 1;
            return k >= 0 && k < s.length() ? String.valueOf(s.charAt(k)) : "";
        });
        fn("string_delete(str, index, count)", (c, a) -> {
            String s = s(a, 0);
            int st = Math.max(1, i(a, 1)) - 1, cnt = i(a, 2);
            if (st >= s.length() || cnt <= 0) return s;
            return s.substring(0, st) + s.substring(Math.min(s.length(), st + cnt));
        });
        fn("string_insert(substr, str, index)", (c, a) -> {
            String s = s(a, 1);
            int k = Math.max(0, Math.min(s.length(), i(a, 2) - 1));
            return s.substring(0, k) + s(a, 0) + s.substring(k);
        });
        fn("string_replace(str, substr, newstr)", "Replace the first occurrence", (c, a) -> {
            String s = s(a, 0), f = s(a, 1);
            int k = f.isEmpty() ? -1 : s.indexOf(f);
            return k < 0 ? s : s.substring(0, k) + s(a, 2) + s.substring(k + f.length());
        });
        fn("string_replace_all(str, substr, newstr)", (c, a) -> s(a, 1).isEmpty() ? s(a, 0) : s(a, 0).replace(s(a, 1), s(a, 2)));
        fn("string_count(substr, str)", (c, a) -> {
            String f = s(a, 0), s = s(a, 1);
            if (f.isEmpty()) return V.ZERO;
            int n = 0, k = 0;
            while ((k = s.indexOf(f, k)) >= 0) { n++; k += f.length(); }
            return V.num(n);
        });
        fn("string_lower(str)", (c, a) -> s(a, 0).toLowerCase());
        fn("string_upper(str)", (c, a) -> s(a, 0).toUpperCase());
        fn("string_repeat(str, count)", (c, a) -> {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < i(a, 1) && b.length() < 1_000_000; k++) b.append(s(a, 0));
            return b.toString();
        });
        fn("string_letters(str)", (c, a) -> filter(s(a, 0), true, false));
        fn("string_digits(str)", (c, a) -> filter(s(a, 0), false, true));
        fn("string_lettersdigits(str)", (c, a) -> filter(s(a, 0), true, true));
        fn("string_trim(str)", (c, a) -> s(a, 0).trim());
        fn("string_split(str, separator)", "Split into an array", (c, a) -> {
            GArray arr = new GArray();
            String sep = s(a, 1);
            String[] parts = sep.isEmpty() ? s(a, 0).split("") : s(a, 0).split(java.util.regex.Pattern.quote(sep), -1);
            for (int k = 0; k < parts.length; k++) arr.set(0, k, parts[k]);
            return arr;
        });
        fn("string_starts_with(str, prefix)", (c, a) -> V.bool(s(a, 0).startsWith(s(a, 1))));
        fn("string_ends_with(str, suffix)", (c, a) -> V.bool(s(a, 0).endsWith(s(a, 1))));

        // ============================================================ arrays
        category("Arrays");
        fn("array_create(size, [value])", (c, a) -> {
            GArray arr = new GArray();
            int nn = Math.max(0, Math.min(10_000_000, i(a, 0)));
            Object v = a.length > 1 ? a[1] : V.ZERO;
            for (int k = 0; k < nn; k++) arr.set(0, k, v);
            return arr;
        });
        fn("array_length_1d(array)", (c, a) -> V.num(a[0] instanceof GArray ? ((GArray) a[0]).length(0) : 0));
        fn("array_length(array)", (c, a) -> V.num(a[0] instanceof GArray ? ((GArray) a[0]).length(0) : 0));
        fn("array_length_2d(array, row)", (c, a) -> V.num(a[0] instanceof GArray ? ((GArray) a[0]).length(i(a, 1)) : 0));
        fn("array_height_2d(array)", (c, a) -> V.num(a[0] instanceof GArray ? ((GArray) a[0]).height() : 0));
        fn("array_push(array, value)", (c, a) -> {
            if (!(a[0] instanceof GArray)) throw new ScriptError("array_push needs an array");
            GArray arr = (GArray) a[0];
            arr.set(0, arr.length(0), a[1]);
            return null;
        });

        // ============================================================ dialogs & misc
        category("General");
        fn("show_message(text)", "Show a message box and wait", (c, a) -> { c.game.platform.showMessage(V.str(a[0])); c.game.input.releaseAll(); return null; });
        fn("show_question(text)", "Ask yes/no; returns true for yes", (c, a) -> { boolean r = c.game.platform.showQuestion(V.str(a[0])); c.game.input.releaseAll(); return V.bool(r); });
        fn("get_string(text, default)", "Ask the player for text", (c, a) -> {
            String r = c.game.platform.getString(V.str(a[0]), s(a, 1));
            c.game.input.releaseAll();
            return r == null ? s(a, 1) : r;
        });
        fn("get_integer(text, default)", "Ask the player for a number", (c, a) -> {
            String r = c.game.platform.getString(V.str(a[0]), V.str(a[1]));
            c.game.input.releaseAll();
            try { return V.num(Double.parseDouble(r.trim())); } catch (Exception e) { return a[1]; }
        });
        fn("show_toast(text)", "Short popup message that does not pause the game", (c, a) -> { c.game.platform.toast(V.str(a[0])); return null; });
        fn("show_debug_message(text)", "Write to the debug log", (c, a) -> { c.game.platform.log(V.str(a[0])); return null; });
        fn("url_open(url)", (c, a) -> { c.game.platform.openUrl(s(a, 0)); return null; });
        fn("vibrate(ms)", "Vibrate the phone", (c, a) -> { c.game.platform.vibrate(Math.max(1, Math.min(5000, i(a, 0)))); return null; });
        fn("get_timer()", "Microseconds since the game started", (c, a) -> V.num((c.game.platform.timeMillis() - c.game.startMillis) * 1000.0));
        fn("execute_string(code, ...)", "Run code given as text", (c, a) -> {
            Code code = c.game.compileCached(s(a, 0));
            if (code == null) return V.ZERO;
            Object[] args = Arrays.copyOfRange(a, 1, a.length);
            return code.call(c, args);
        });
        fn("script_execute(script, ...)", "Call a script by its index", (c, a) -> {
            int idx = i(a, 0);
            com.droidmaker.core.project.Script sc = c.game.project.script(idx);
            if (sc == null) throw new ScriptError("Script " + idx + " does not exist");
            Compiler.ScriptRef ref = c.game.compiler.script(sc.name);
            if (ref == null || ref.code == null) throw new ScriptError("Script " + sc.name + " did not compile");
            return ref.code.call(c, Arrays.copyOfRange(a, 1, a.length));
        });
        fn("event_inherited()", "Run the parent object's version of this event", (c, a) -> { c.game.inherited(); return null; });
        fn("event_user(n)", "Run user event n of this instance", (c, a) -> {
            if (c.self != null) c.game.perform(c.self, com.droidmaker.core.project.Ev.OTHER, com.droidmaker.core.project.Ev.O_USER0 + i(a, 0), c.other);
            return null;
        });
        fn("event_perform(type, number)", (c, a) -> {
            if (c.self != null) c.game.perform(c.self, i(a, 0), i(a, 1), c.other);
            return null;
        });
        fn("event_perform_object(obj, type, number)", (c, a) -> {
            ObjectRt o = c.game.object(i(a, 0));
            if (o != null && c.self != null) {
                ObjectRt owner = o.ownerOf(com.droidmaker.core.project.Ev.key(i(a, 1), i(a, 2)));
                if (owner != null) c.game.runEvent(owner, com.droidmaker.core.project.Ev.key(i(a, 1), i(a, 2)), c.self, c.other);
            }
            return null;
        });
        fn("typeof(value)", (c, a) -> a[0] instanceof String ? "string" : a[0] instanceof GArray ? "array" : "number");
    }

    private static String filter(String s, boolean letters, boolean digits) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray())
            if ((letters && Character.isLetter(ch)) || (digits && Character.isDigit(ch))) b.append(ch);
        return b.toString();
    }

    private static void constants() {
        constant("pi", Math.PI);
        // colors (0xRRGGBB)
        constant("c_aqua", 0x00FFFF);
        constant("c_black", 0x000000);
        constant("c_blue", 0x0000FF);
        constant("c_dkgray", 0x404040);
        constant("c_fuchsia", 0xFF00FF);
        constant("c_gray", 0x808080);
        constant("c_green", 0x008000);
        constant("c_lime", 0x00FF00);
        constant("c_ltgray", 0xC0C0C0);
        constant("c_maroon", 0x800000);
        constant("c_navy", 0x000080);
        constant("c_olive", 0x808000);
        constant("c_orange", 0xFFA040);
        constant("c_purple", 0x800080);
        constant("c_red", 0xFF0000);
        constant("c_silver", 0xC0C0C0);
        constant("c_teal", 0x008080);
        constant("c_white", 0xFFFFFF);
        constant("c_yellow", 0xFFFF00);
        // keys
        constant("vk_nokey", Keys.NOKEY);
        constant("vk_anykey", Keys.ANYKEY);
        constant("vk_backspace", Keys.BACKSPACE);
        constant("vk_tab", Keys.TAB);
        constant("vk_enter", Keys.ENTER);
        constant("vk_return", Keys.ENTER);
        constant("vk_shift", Keys.SHIFT);
        constant("vk_control", Keys.CONTROL);
        constant("vk_alt", Keys.ALT);
        constant("vk_escape", Keys.ESCAPE);
        constant("vk_space", Keys.SPACE);
        constant("vk_pageup", Keys.PAGEUP);
        constant("vk_pagedown", Keys.PAGEDOWN);
        constant("vk_end", Keys.END);
        constant("vk_home", Keys.HOME);
        constant("vk_left", Keys.LEFT);
        constant("vk_up", Keys.UP);
        constant("vk_right", Keys.RIGHT);
        constant("vk_down", Keys.DOWN);
        constant("vk_insert", Keys.INSERT);
        constant("vk_delete", Keys.DELETE);
        for (int k = 1; k <= 12; k++) constant("vk_f" + k, 111 + k);
        for (int k = 0; k <= 9; k++) constant("vk_numpad" + k, 96 + k);
        // mouse / touch
        constant("mb_any", -1);
        constant("mb_none", 0);
        constant("mb_left", 1);
        constant("mb_right", 2);
        constant("mb_middle", 3);
        // virtual buttons
        constant("vb_a", 0);
        constant("vb_b", 1);
        constant("vb_x", 2);
        constant("vb_y", 3);
        // alignment
        constant("fa_left", 0);
        constant("fa_center", 1);
        constant("fa_right", 2);
        constant("fa_top", 0);
        constant("fa_middle", 1);
        constant("fa_bottom", 2);
        // blend modes
        constant("bm_normal", 0);
        constant("bm_add", 1);
        constant("bm_subtract", 2);
        constant("bm_max", 3);
        // event types
        constant("ev_create", 0);
        constant("ev_destroy", 1);
        constant("ev_alarm", 2);
        constant("ev_step", 3);
        constant("ev_collision", 4);
        constant("ev_keyboard", 5);
        constant("ev_mouse", 6);
        constant("ev_other", 7);
        constant("ev_draw", 8);
        constant("ev_keypress", 9);
        constant("ev_keyrelease", 10);
        constant("ev_step_normal", 0);
        constant("ev_step_begin", 1);
        constant("ev_step_end", 2);
        constant("ev_user0", 10);
        // effects
        constant("ef_explosion", Effects.EXPLOSION);
        constant("ef_ring", Effects.RING);
        constant("ef_ellipse", Effects.ELLIPSE);
        constant("ef_firework", Effects.FIREWORK);
        constant("ef_smoke", Effects.SMOKE);
        constant("ef_spark", Effects.SPARK);
        constant("ef_star", Effects.STAR);
        constant("ef_rain", Effects.RAIN);
        constant("ef_snow", Effects.SNOW);
        // primitives
        constant("pr_pointlist", 1);
        constant("pr_linelist", 2);
        constant("pr_linestrip", 3);
        constant("pr_trianglelist", 4);
        constant("pr_trianglestrip", 5);
        constant("pr_trianglefan", 6);
        // room modes
        constant("rm_mode_2d", 0);
        constant("rm_mode_raycast", 1);
        constant("rm_mode_3d", 2);
        constant("os_android", 4);
    }
}
