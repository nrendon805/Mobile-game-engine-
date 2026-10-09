package com.droidmaker.core.runtime;

import com.droidmaker.core.lang.Ctx;
import com.droidmaker.core.lang.V;
import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.GameObject;
import com.droidmaker.core.project.Sound;
import com.droidmaker.core.project.Sprite;

import static com.droidmaker.core.lang.Builtins.*;

/** 2D drawing, colors and resource information functions. */
public final class FnDraw {
    private FnDraw() {}

    /** The current screen, or null when not drawing (drawing outside Draw events is ignored). */
    static Gfx g(Ctx c) { return c.game.drawing ? c.game.screen : null; }

    static int frame(Ctx c, int spr, double sub) {
        Sprite s = c.game.project.sprite(spr);
        if (s == null) return 0;
        if (sub < 0) sub = c.self != null ? c.self.imageIndex : 0;
        return Game.frameOf(s, sub);
    }

    static double a(Ctx c) { return c.game.drawAlpha; }

    public static void register() {
        category("Drawing");
        fn("draw_self()", "Draw this instance's sprite as it is", (c, a) -> { if (c.self != null && c.game.drawing) c.game.drawSelf(c.self); return null; });
        fn("draw_sprite(sprite, subimg, x, y)", "Draw a sprite frame (-1 = current frame)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.sprite(i(a, 0), frame(c, i(a, 0), n(a, 1)), n(a, 2), n(a, 3), 1, 1, 0, 0xFFFFFF, a(c));
            return null;
        });
        fn("draw_sprite_ext(sprite, subimg, x, y, xscale, yscale, rot, color, alpha)", "Draw a scaled/rotated/tinted sprite", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.sprite(i(a, 0), frame(c, i(a, 0), n(a, 1)), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), i(a, 7), n(a, 8));
            return null;
        });
        fn("draw_sprite_part(sprite, subimg, left, top, width, height, x, y)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.spritePart(i(a, 0), frame(c, i(a, 0), n(a, 1)), i(a, 2), i(a, 3), i(a, 4), i(a, 5), n(a, 6), n(a, 7), 1, 1, 0xFFFFFF, a(c));
            return null;
        });
        fn("draw_sprite_part_ext(sprite, subimg, left, top, width, height, x, y, xscale, yscale, color, alpha)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.spritePart(i(a, 0), frame(c, i(a, 0), n(a, 1)), i(a, 2), i(a, 3), i(a, 4), i(a, 5), n(a, 6), n(a, 7), n(a, 8), n(a, 9), i(a, 10), n(a, 11));
            return null;
        });
        fn("draw_sprite_stretched(sprite, subimg, x, y, w, h)", (c, a) -> {
            stretched(c, i(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), 0xFFFFFF, a(c));
            return null;
        });
        fn("draw_sprite_stretched_ext(sprite, subimg, x, y, w, h, color, alpha)", (c, a) -> {
            stretched(c, i(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), i(a, 6), n(a, 7));
            return null;
        });
        fn("draw_sprite_tiled(sprite, subimg, x, y)", (c, a) -> {
            Gfx g = g(c);
            Sprite s = c.game.project.sprite(i(a, 0));
            if (g == null || s == null || s.width <= 0 || s.height <= 0) return null;
            tiled(c, g, true, i(a, 0), frame(c, i(a, 0), n(a, 1)), s.width, s.height, n(a, 2) - s.originX, n(a, 3) - s.originY);
            return null;
        });
        fn("draw_sprite_general(sprite, subimg, x1, y1, x2, y2, x3, y3, x4, y4, color, alpha)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.spriteGeneral(i(a, 0), frame(c, i(a, 0), n(a, 1)), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7), n(a, 8), n(a, 9), i(a, 10), n(a, 11));
            return null;
        });
        fn("draw_background(back, x, y)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.background(i(a, 0), n(a, 1), n(a, 2), 1, 1, 0, 0xFFFFFF, a(c));
            return null;
        });
        fn("draw_background_ext(back, x, y, xscale, yscale, rot, color, alpha)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.background(i(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), i(a, 6), n(a, 7));
            return null;
        });
        fn("draw_background_stretched(back, x, y, w, h)", (c, a) -> {
            Gfx g = g(c);
            Background b = c.game.project.background(i(a, 0));
            if (g != null && b != null && b.width > 0 && b.height > 0)
                g.background(i(a, 0), n(a, 1), n(a, 2), n(a, 3) / b.width, n(a, 4) / b.height, 0, 0xFFFFFF, a(c));
            return null;
        });
        fn("draw_background_tiled(back, x, y)", (c, a) -> {
            Gfx g = g(c);
            Background b = c.game.project.background(i(a, 0));
            if (g == null || b == null || b.width <= 0 || b.height <= 0) return null;
            tiled(c, g, false, i(a, 0), 0, b.width, b.height, n(a, 1), n(a, 2));
            return null;
        });
        fn("draw_background_part(back, left, top, width, height, x, y)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.backgroundPart(i(a, 0), i(a, 1), i(a, 2), i(a, 3), i(a, 4), n(a, 5), n(a, 6), 1, 1, 0xFFFFFF, a(c));
            return null;
        });

        category("Shapes");
        fn("draw_set_color(color)", "Color for shapes and text", (c, a) -> { c.game.drawColor = i(a, 0); return null; });
        fn("draw_get_color()", (c, a) -> V.num(c.game.drawColor));
        fn("draw_set_alpha(alpha)", "Transparency 0..1 for drawing", (c, a) -> { c.game.drawAlpha = Math.max(0, Math.min(1, n(a, 0))); return null; });
        fn("draw_get_alpha()", (c, a) -> V.num(c.game.drawAlpha));
        fn("draw_set_blend_mode(mode)", (c, a) -> { Gfx g = g(c); if (g != null) g.blendMode(i(a, 0)); return null; });
        fn("draw_clear(color)", (c, a) -> { Gfx g = g(c); if (g != null) g.clear(i(a, 0), 1); return null; });
        fn("draw_clear_alpha(color, alpha)", (c, a) -> { Gfx g = g(c); if (g != null) g.clear(i(a, 0), n(a, 1)); return null; });
        fn("draw_point(x, y)", (c, a) -> { Gfx g = g(c); if (g != null) g.point(n(a, 0), n(a, 1), c.game.drawColor, a(c)); return null; });
        fn("draw_point_color(x, y, color)", (c, a) -> { Gfx g = g(c); if (g != null) g.point(n(a, 0), n(a, 1), i(a, 2), a(c)); return null; });
        fn("draw_line(x1, y1, x2, y2)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.line(n(a, 0), n(a, 1), n(a, 2), n(a, 3), 1, c.game.drawColor, c.game.drawColor, a(c));
            return null;
        });
        fn("draw_line_width(x1, y1, x2, y2, w)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.line(n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), c.game.drawColor, c.game.drawColor, a(c));
            return null;
        });
        fn("draw_line_color(x1, y1, x2, y2, col1, col2)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.line(n(a, 0), n(a, 1), n(a, 2), n(a, 3), 1, i(a, 4), i(a, 5), a(c));
            return null;
        });
        fn("draw_line_width_color(x1, y1, x2, y2, w, col1, col2)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.line(n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), i(a, 5), i(a, 6), a(c));
            return null;
        });
        fn("draw_rectangle(x1, y1, x2, y2, outline)", "Rectangle in the draw color", (c, a) -> {
            Gfx g = g(c);
            int col = c.game.drawColor;
            if (g != null) g.rect(n(a, 0), n(a, 1), n(a, 2), n(a, 3), col, col, col, col, a(c), b(a, 4));
            return null;
        });
        fn("draw_rectangle_color(x1, y1, x2, y2, col1, col2, col3, col4, outline)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.rect(n(a, 0), n(a, 1), n(a, 2), n(a, 3), i(a, 4), i(a, 5), i(a, 6), i(a, 7), a(c), b(a, 8));
            return null;
        });
        fn("draw_roundrect(x1, y1, x2, y2, outline)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) roundRect(g, n(a, 0), n(a, 1), n(a, 2), n(a, 3), Math.min(8, Math.min(Math.abs(n(a, 2) - n(a, 0)), Math.abs(n(a, 3) - n(a, 1))) / 3), c.game.drawColor, a(c), b(a, 4));
            return null;
        });
        fn("draw_roundrect_ext(x1, y1, x2, y2, radius, outline)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) roundRect(g, n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), c.game.drawColor, a(c), b(a, 5));
            return null;
        });
        fn("draw_triangle(x1, y1, x2, y2, x3, y3, outline)", (c, a) -> {
            Gfx g = g(c);
            int col = c.game.drawColor;
            if (g != null) g.triangle(n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), col, col, col, a(c), b(a, 6));
            return null;
        });
        fn("draw_triangle_color(x1, y1, x2, y2, x3, y3, col1, col2, col3, outline)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.triangle(n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), i(a, 6), i(a, 7), i(a, 8), a(c), b(a, 9));
            return null;
        });
        fn("draw_circle(x, y, r, outline)", "Circle in the draw color", (c, a) -> {
            Gfx g = g(c);
            double r = n(a, 2);
            if (g != null) g.ellipse(n(a, 0) - r, n(a, 1) - r, n(a, 0) + r, n(a, 1) + r, c.game.drawColor, c.game.drawColor, a(c), b(a, 3), segs(r));
            return null;
        });
        fn("draw_circle_color(x, y, r, col1, col2, outline)", (c, a) -> {
            Gfx g = g(c);
            double r = n(a, 2);
            if (g != null) g.ellipse(n(a, 0) - r, n(a, 1) - r, n(a, 0) + r, n(a, 1) + r, i(a, 3), i(a, 4), a(c), b(a, 5), segs(r));
            return null;
        });
        fn("draw_ellipse(x1, y1, x2, y2, outline)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.ellipse(n(a, 0), n(a, 1), n(a, 2), n(a, 3), c.game.drawColor, c.game.drawColor, a(c), b(a, 4),
                    segs(Math.max(Math.abs(n(a, 2) - n(a, 0)), Math.abs(n(a, 3) - n(a, 1))) / 2));
            return null;
        });
        fn("draw_ellipse_color(x1, y1, x2, y2, col1, col2, outline)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.ellipse(n(a, 0), n(a, 1), n(a, 2), n(a, 3), i(a, 4), i(a, 5), a(c), b(a, 6),
                    segs(Math.max(Math.abs(n(a, 2) - n(a, 0)), Math.abs(n(a, 3) - n(a, 1))) / 2));
            return null;
        });
        fn("draw_arrow(x1, y1, x2, y2, size)", (c, a) -> {
            Gfx g = g(c);
            if (g == null) return null;
            double x1 = n(a, 0), y1 = n(a, 1), x2 = n(a, 2), y2 = n(a, 3), s = n(a, 4);
            int col = c.game.drawColor;
            g.line(x1, y1, x2, y2, 1, col, col, a(c));
            double ang = Math.atan2(y2 - y1, x2 - x1);
            g.triangle(x2, y2, x2 - Math.cos(ang - 0.45) * s, y2 - Math.sin(ang - 0.45) * s,
                    x2 - Math.cos(ang + 0.45) * s, y2 - Math.sin(ang + 0.45) * s, col, col, col, a(c), false);
            return null;
        });
        fn("draw_healthbar(x1, y1, x2, y2, amount, backcol, mincol, [maxcol], [direction], [showback], [showborder])", "Health bar (amount 0..100)", (c, a) -> {
            Gfx g = g(c);
            if (g == null) return null;
            double x1 = n(a, 0), y1 = n(a, 1), x2 = n(a, 2), y2 = n(a, 3);
            double amt = Math.max(0, Math.min(100, n(a, 4))) / 100;
            int back = i(a, 5), minc = i(a, 6), maxc = a.length > 7 ? i(a, 7) : minc;
            int dir = i(a, 8, 0);
            boolean showBack = b(a, 9, true), border = b(a, 10, true);
            if (showBack) g.rect(x1, y1, x2, y2, back, back, back, back, a(c), false);
            int col = mergeColor(minc, maxc, amt);
            double bx1 = x1, by1 = y1, bx2 = x2, by2 = y2;
            switch (dir) {
                case 1: bx1 = x2 - (x2 - x1) * amt; break;
                case 2: by2 = y1 + (y2 - y1) * amt; break;
                case 3: by1 = y2 - (y2 - y1) * amt; break;
                default: bx2 = x1 + (x2 - x1) * amt;
            }
            if (amt > 0) g.rect(bx1, by1, bx2, by2, col, col, col, col, a(c), false);
            if (border) g.rect(x1, y1, x2, y2, 0, 0, 0, 0, a(c), true);
            return null;
        });
        fn("draw_button(x1, y1, x2, y2, up)", (c, a) -> {
            Gfx g = g(c);
            if (g == null) return null;
            double x1 = n(a, 0), y1 = n(a, 1), x2 = n(a, 2), y2 = n(a, 3);
            int face = c.game.drawColor;
            g.rect(x1, y1, x2, y2, face, face, face, face, a(c), false);
            int lt = b(a, 4) ? 0xFFFFFF : 0x404040, dk = b(a, 4) ? 0x404040 : 0xFFFFFF;
            g.line(x1, y1, x2, y1, 2, lt, lt, a(c));
            g.line(x1, y1, x1, y2, 2, lt, lt, a(c));
            g.line(x1, y2, x2, y2, 2, dk, dk, a(c));
            g.line(x2, y1, x2, y2, 2, dk, dk, a(c));
            return null;
        });
        fn("gui_button(x1, y1, x2, y2, text)", "Touch button for the Draw GUI event; true when tapped", (c, a) -> {
            Gfx g = g(c);
            double x1 = n(a, 0), y1 = n(a, 1), x2 = n(a, 2), y2 = n(a, 3);
            boolean held = false, tapped = false;
            for (InputState.Touch t : c.game.input.touches) {
                boolean in = t.dx >= x1 && t.dx < x2 && t.dy >= y1 && t.dy < y2;
                if (!in) continue;
                if (t.down) held = true;
                if (t.released && !t.moved && t.consumedStep != c.game.stepCount) { tapped = true; t.consumedStep = c.game.stepCount; }
            }
            if (g != null) {
                int base = c.game.drawColor;
                int face = held ? mergeColor(base, 0x000000, 0.3) : base;
                roundRect(g, x1, y1, x2, y2, Math.min(12, (y2 - y1) / 3), face, Math.max(0.15, a(c)), false);
                roundRect(g, x1, y1, x2, y2, Math.min(12, (y2 - y1) / 3), 0xFFFFFF, 0.5 * a(c), true);
                double size = Math.max(8, Math.min(c.game.fontSize, (y2 - y1) * 0.55));
                int tc = brightness(face) > 140 ? 0x000000 : 0xFFFFFF;
                g.text((x1 + x2) / 2, (y1 + y2) / 2, s(a, 4), size, true, 1, 1, 1, 1, 0, tc, a(c), -1, -1);
            }
            return V.bool(tapped);
        });

        fn("gui_area_tapped(x1, y1, x2, y2)", "A finger was tapped and released inside a GUI rectangle this step", (c, a) -> {
            for (InputState.Touch t : c.game.input.touches)
                if (t.released && !t.moved && t.dx >= n(a, 0) && t.dx < n(a, 2) && t.dy >= n(a, 1) && t.dy < n(a, 3)) return V.ONE;
            return V.ZERO;
        });
        fn("gui_area_held(x1, y1, x2, y2)", "A finger is held inside a GUI rectangle", (c, a) -> {
            for (InputState.Touch t : c.game.input.touches)
                if (t.down && t.dx >= n(a, 0) && t.dx < n(a, 2) && t.dy >= n(a, 1) && t.dy < n(a, 3)) return V.ONE;
            return V.ZERO;
        });

        category("Text");
        fn("draw_text(x, y, string)", "Draw text in the draw color", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.text(n(a, 0), n(a, 1), V.str(a[2]), c.game.fontSize, c.game.fontBold, c.game.halign, c.game.valign, 1, 1, 0, c.game.drawColor, a(c), -1, -1);
            return null;
        });
        fn("draw_text_ext(x, y, string, sep, w)", "Text wrapped to width w", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.text(n(a, 0), n(a, 1), V.str(a[2]), c.game.fontSize, c.game.fontBold, c.game.halign, c.game.valign, 1, 1, 0, c.game.drawColor, a(c), n(a, 3), n(a, 4));
            return null;
        });
        fn("draw_text_transformed(x, y, string, xscale, yscale, angle)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.text(n(a, 0), n(a, 1), V.str(a[2]), c.game.fontSize, c.game.fontBold, c.game.halign, c.game.valign, n(a, 3), n(a, 4), n(a, 5), c.game.drawColor, a(c), -1, -1);
            return null;
        });
        fn("draw_text_ext_transformed(x, y, string, sep, w, xscale, yscale, angle)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.text(n(a, 0), n(a, 1), V.str(a[2]), c.game.fontSize, c.game.fontBold, c.game.halign, c.game.valign, n(a, 5), n(a, 6), n(a, 7), c.game.drawColor, a(c), n(a, 3), n(a, 4));
            return null;
        });
        fn("draw_text_color(x, y, string, c1, c2, c3, c4, alpha)", (c, a) -> {
            Gfx g = g(c);
            if (g != null) g.text(n(a, 0), n(a, 1), V.str(a[2]), c.game.fontSize, c.game.fontBold, c.game.halign, c.game.valign, 1, 1, 0, i(a, 3), n(a, 7), -1, -1);
            return null;
        });
        fn("draw_set_font_size(size)", "Text size in pixels", (c, a) -> { c.game.fontSize = Math.max(4, Math.min(400, n(a, 0))); return null; });
        fn("draw_set_font_bold(bold)", (c, a) -> { c.game.fontBold = b(a, 0); return null; });
        fn("draw_set_halign(align)", "fa_left, fa_center or fa_right", (c, a) -> { c.game.halign = i(a, 0); return null; });
        fn("draw_set_valign(align)", "fa_top, fa_middle or fa_bottom", (c, a) -> { c.game.valign = i(a, 0); return null; });
        fn("string_width(string)", (c, a) -> V.num(c.game.screen == null ? V.str(a[0]).length() * c.game.fontSize * 0.55
                : c.game.screen.textWidth(V.str(a[0]), c.game.fontSize, c.game.fontBold, -1)));
        fn("string_height(string)", (c, a) -> V.num(c.game.screen == null ? c.game.fontSize * 1.2
                : c.game.screen.textHeight(V.str(a[0]), c.game.fontSize, c.game.fontBold, -1, -1)));
        fn("string_width_ext(string, sep, w)", (c, a) -> V.num(c.game.screen == null ? 0 : c.game.screen.textWidth(V.str(a[0]), c.game.fontSize, c.game.fontBold, n(a, 2))));
        fn("string_height_ext(string, sep, w)", (c, a) -> V.num(c.game.screen == null ? 0 : c.game.screen.textHeight(V.str(a[0]), c.game.fontSize, c.game.fontBold, n(a, 1), n(a, 2))));

        category("Colors");
        fn("make_color_rgb(red, green, blue)", "Color from 0..255 components", (c, a) -> V.num(rgb(i(a, 0), i(a, 1), i(a, 2))));
        fn("make_colour_rgb(red, green, blue)", (c, a) -> V.num(rgb(i(a, 0), i(a, 1), i(a, 2))));
        fn("make_color_hsv(hue, sat, val)", "Color from 0..255 hue/saturation/value", (c, a) -> V.num(hsv(n(a, 0), n(a, 1), n(a, 2))));
        fn("make_colour_hsv(hue, sat, val)", (c, a) -> V.num(hsv(n(a, 0), n(a, 1), n(a, 2))));
        fn("color_get_red(color)", (c, a) -> V.num((i(a, 0) >> 16) & 255));
        fn("color_get_green(color)", (c, a) -> V.num((i(a, 0) >> 8) & 255));
        fn("color_get_blue(color)", (c, a) -> V.num(i(a, 0) & 255));
        fn("color_get_hue(color)", (c, a) -> V.num(toHsv(i(a, 0))[0]));
        fn("color_get_saturation(color)", (c, a) -> V.num(toHsv(i(a, 0))[1]));
        fn("color_get_value(color)", (c, a) -> V.num(toHsv(i(a, 0))[2]));
        fn("merge_color(col1, col2, amount)", "Blend two colors", (c, a) -> V.num(mergeColor(i(a, 0), i(a, 1), n(a, 2))));
        fn("merge_colour(col1, col2, amount)", (c, a) -> V.num(mergeColor(i(a, 0), i(a, 1), n(a, 2))));

        category("Resources");
        fn("sprite_exists(sprite)", (c, a) -> V.bool(c.game.project.sprite(i(a, 0)) != null));
        fn("sprite_get_name(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return s == null ? "<undefined>" : s.name; });
        fn("sprite_get_number(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.frames.size()); });
        fn("sprite_get_width(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.width); });
        fn("sprite_get_height(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.height); });
        fn("sprite_get_xoffset(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.originX); });
        fn("sprite_get_yoffset(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.originY); });
        fn("sprite_get_bbox_left(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.bboxLeft); });
        fn("sprite_get_bbox_right(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.bboxRight); });
        fn("sprite_get_bbox_top(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.bboxTop); });
        fn("sprite_get_bbox_bottom(sprite)", (c, a) -> { Sprite s = c.game.project.sprite(i(a, 0)); return V.num(s == null ? 0 : s.bboxBottom); });
        fn("background_exists(back)", (c, a) -> V.bool(c.game.project.background(i(a, 0)) != null));
        fn("background_get_name(back)", (c, a) -> { Background b = c.game.project.background(i(a, 0)); return b == null ? "<undefined>" : b.name; });
        fn("background_get_width(back)", (c, a) -> { Background b = c.game.project.background(i(a, 0)); return V.num(b == null ? 0 : b.width); });
        fn("background_get_height(back)", (c, a) -> { Background b = c.game.project.background(i(a, 0)); return V.num(b == null ? 0 : b.height); });
        fn("sound_exists(snd)", (c, a) -> V.bool(c.game.project.sound(i(a, 0)) != null));
        fn("sound_get_name(snd)", (c, a) -> { Sound s = c.game.project.sound(i(a, 0)); return s == null ? "<undefined>" : s.name; });
        fn("object_exists(obj)", (c, a) -> V.bool(c.game.project.object(i(a, 0)) != null));
        fn("object_get_name(obj)", (c, a) -> { GameObject o = c.game.project.object(i(a, 0)); return o == null ? "<undefined>" : o.name; });
        fn("object_get_sprite(obj)", (c, a) -> { GameObject o = c.game.project.object(i(a, 0)); return V.num(o == null ? -1 : o.sprite); });
        fn("object_get_parent(obj)", (c, a) -> { GameObject o = c.game.project.object(i(a, 0)); return V.num(o == null ? -1 : o.parent); });
        fn("object_get_depth(obj)", (c, a) -> { GameObject o = c.game.project.object(i(a, 0)); return V.num(o == null ? 0 : o.depth); });
        fn("object_get_solid(obj)", (c, a) -> { GameObject o = c.game.project.object(i(a, 0)); return V.bool(o != null && o.solid); });
        fn("object_is_ancestor(obj, parent)", (c, a) -> {
            ObjectRt o = c.game.object(i(a, 0));
            return V.bool(o != null && o.id != i(a, 1) && o.isA(i(a, 1)));
        });
        fn("script_exists(script)", (c, a) -> V.bool(c.game.project.script(i(a, 0)) != null));
        fn("script_get_name(script)", (c, a) -> { com.droidmaker.core.project.Script s = c.game.project.script(i(a, 0)); return s == null ? "<undefined>" : s.name; });
    }

    static int segs(double r) { return (int) Math.max(12, Math.min(96, r * 0.8)); }

    private static void stretched(Ctx c, int spr, double sub, double x, double y, double w, double h, int col, double alpha) {
        Gfx g = g(c);
        Sprite s = c.game.project.sprite(spr);
        if (g == null || s == null || s.width <= 0 || s.height <= 0) return;
        double xs = w / s.width, ys = h / s.height;
        g.sprite(spr, frame(c, spr, sub), x + s.originX * xs, y + s.originY * ys, xs, ys, 0, col, alpha);
    }

    private static void tiled(Ctx c, Gfx g, boolean sprite, int res, int frame, int w, int h, double x, double y) {
        RoomState r = c.game.rs;
        double vx = 0, vy = 0, vw = r == null ? 1000 : r.width, vh = r == null ? 1000 : r.height;
        if (r != null && r.viewsEnabled) {
            RoomState.ViewRt v = r.views[c.game.viewCurrent];
            vx = v.xview; vy = v.yview; vw = v.wview; vh = v.hview;
        }
        double x0 = x + Math.floor((vx - x) / w) * w, y0 = y + Math.floor((vy - y) / h) * h;
        int guard = 0;
        for (double yy = y0; yy < vy + vh && guard < 4000; yy += h)
            for (double xx = x0; xx < vx + vw && guard++ < 4000; xx += w) {
                if (sprite) {
                    Sprite s = c.game.project.sprite(res);
                    g.sprite(res, frame, xx + s.originX, yy + s.originY, 1, 1, 0, 0xFFFFFF, c.game.drawAlpha);
                } else g.background(res, xx, yy, 1, 1, 0, 0xFFFFFF, c.game.drawAlpha);
            }
    }

    static void roundRect(Gfx g, double x1, double y1, double x2, double y2, double r, int col, double alpha, boolean outline) {
        double l = Math.min(x1, x2), t = Math.min(y1, y2), rr = Math.max(x1, x2), b = Math.max(y1, y2);
        r = Math.max(0, Math.min(r, Math.min(rr - l, b - t) / 2));
        if (r < 1) { g.rect(l, t, rr, b, col, col, col, col, alpha, outline); return; }
        int seg = 6;
        double[][] corners = {{rr - r, t + r, -90}, {rr - r, b - r, 0}, {l + r, b - r, 90}, {l + r, t + r, 180}};
        double[] px = new double[4 * (seg + 1)], py = new double[4 * (seg + 1)];
        int k = 0;
        for (double[] cn : corners)
            for (int s = 0; s <= seg; s++) {
                double ang = Math.toRadians(cn[2] + 90.0 * s / seg);
                px[k] = cn[0] + Math.cos(ang) * r;
                py[k] = cn[1] + Math.sin(ang) * r;
                k++;
            }
        double cx = (l + rr) / 2, cy = (t + b) / 2;
        for (int i = 0; i < k; i++) {
            int j = (i + 1) % k;
            if (outline) g.line(px[i], py[i], px[j], py[j], 1, col, col, alpha);
            else g.triangle(cx, cy, px[i], py[i], px[j], py[j], col, col, col, alpha, false);
        }
    }

    public static int rgb(int r, int g, int b) {
        return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    public static int hsv(double h, double s, double v) {
        h = ((h % 256) + 256) % 256 / 255.0 * 360;
        s = Math.max(0, Math.min(255, s)) / 255.0;
        v = Math.max(0, Math.min(255, v)) / 255.0;
        double cc = v * s, x = cc * (1 - Math.abs((h / 60) % 2 - 1)), m = v - cc;
        double r, g, b;
        if (h < 60) { r = cc; g = x; b = 0; }
        else if (h < 120) { r = x; g = cc; b = 0; }
        else if (h < 180) { r = 0; g = cc; b = x; }
        else if (h < 240) { r = 0; g = x; b = cc; }
        else if (h < 300) { r = x; g = 0; b = cc; }
        else { r = cc; g = 0; b = x; }
        return rgb((int) Math.round((r + m) * 255), (int) Math.round((g + m) * 255), (int) Math.round((b + m) * 255));
    }

    static double[] toHsv(int c) {
        double r = ((c >> 16) & 255) / 255.0, g = ((c >> 8) & 255) / 255.0, b = (c & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        double h = 0;
        if (d > 0) {
            if (max == r) h = 60 * (((g - b) / d) % 6);
            else if (max == g) h = 60 * ((b - r) / d + 2);
            else h = 60 * ((r - g) / d + 4);
        }
        if (h < 0) h += 360;
        return new double[]{Math.round(h / 360 * 255), Math.round(max == 0 ? 0 : d / max * 255), Math.round(max * 255)};
    }

    public static int mergeColor(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return rgb(r, g, bl);
    }

    static int brightness(int c) { return (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10; }
}
