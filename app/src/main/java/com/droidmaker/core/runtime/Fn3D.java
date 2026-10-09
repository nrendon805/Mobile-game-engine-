package com.droidmaker.core.runtime;

import com.droidmaker.core.lang.Ctx;
import com.droidmaker.core.lang.ScriptError;
import com.droidmaker.core.lang.V;
import com.droidmaker.core.project.Room;
import com.droidmaker.core.project.Sprite;

import java.util.ArrayList;
import java.util.List;

import static com.droidmaker.core.lang.Builtins.*;

/** 3D (d3d_*) and raycast (rc_*) functions. */
public final class Fn3D {
    private Fn3D() {}

    /** Screen if currently drawing a 3D pass. */
    static Screen s3(Ctx c) {
        Game g = c.game;
        return g.drawing && g.drawPass == 3 && g.screen != null ? g.screen : null;
    }

    static float[] worldOrNull(Ctx c) { return c.game.d3.worldIsIdentity() ? null : c.game.d3.world; }

    static void drawScratch(Ctx c, int tex) {
        Screen s = s3(c);
        if (s == null) return;
        D3State d = c.game.d3;
        s.mesh(d.scratch, worldOrNull(c), tex, d.color, d.alpha * c.game.drawAlpha);
    }

    // immediate-mode primitive building
    private static int primKind;
    private static int primTex = -1;
    private static final List<double[]> primVerts = new ArrayList<>();

    public static void register() {
        category("3D");
        fn("d3d_start()", "3D is chosen per room (room mode); kept for compatibility", (c, a) -> null);
        fn("d3d_end()", (c, a) -> null);
        fn("d3d_set_projection(xfrom, yfrom, zfrom, xto, yto, zto, xup, yup, zup)", "Place the camera", (c, a) -> {
            double[] e = new double[9];
            for (int k = 0; k < 9; k++) e[k] = n(a, k);
            c.game.setProjection(e, true);
            return null;
        });
        fn("d3d_set_projection_ext(xfrom, yfrom, zfrom, xto, yto, zto, xup, yup, zup, angle, aspect, znear, zfar)", (c, a) -> {
            D3State d = c.game.d3;
            d.fov = Math.max(1, Math.min(170, n(a, 9)));
            d.znear = Math.max(0.01, n(a, 11));
            d.zfar = Math.max(d.znear + 1, n(a, 12));
            double[] e = new double[9];
            for (int k = 0; k < 9; k++) e[k] = n(a, k);
            c.game.setProjection(e, true);
            return null;
        });
        fn("d3d_set_projection_ortho(x, y, w, h, angle)", "2D overlay inside a 3D Draw event", (c, a) -> {
            Screen s = s3(c);
            if (s == null) return null;
            float[] proj = Mat4.ortho(n(a, 0), n(a, 0) + n(a, 2), n(a, 1) + n(a, 3), n(a, 1), -32000, 32000);
            float[] view = n(a, 4) == 0 ? Mat4.identity() : Mat4.rotation(n(a, 4), 0, 0, 1);
            s.matrices(view, proj);
            return null;
        });
        fn("d3d_set_fov(degrees)", "Camera field of view", (c, a) -> { c.game.d3.fov = Math.max(10, Math.min(170, n(a, 0))); return null; });
        fn("d3d_camera_fps(eye_height)", "First-person camera on this instance (uses direction and pitch)", (c, a) -> {
            D3State d = c.game.d3;
            d.camMode = D3State.CAM_FPS;
            d.camInstance = c.self == null ? -4 : c.self.id;
            d.eyeHeight = n(a, 0);
            return null;
        });
        fn("d3d_camera_third(distance, height)", "Third-person camera behind this instance", (c, a) -> {
            D3State d = c.game.d3;
            d.camMode = D3State.CAM_THIRD;
            d.camInstance = c.self == null ? -4 : c.self.id;
            d.camDist = n(a, 0);
            d.camHeight = n(a, 1);
            return null;
        });
        fn("d3d_set_hidden(enable)", "Depth testing (hidden surface removal)", (c, a) -> {
            Screen s = s3(c);
            if (s instanceof Depth) ((Depth) s).depthTest(b(a, 0));
            return null;
        });
        fn("d3d_set_culling(enable)", "Hide back faces", (c, a) -> {
            c.game.d3.culling = b(a, 0);
            Screen s = s3(c);
            if (s != null) s.culling(b(a, 0));
            return null;
        });
        fn("d3d_set_perspective(enable)", (c, a) -> null);
        fn("d3d_set_shading(smooth)", (c, a) -> null);
        fn("d3d_set_color(color)", "Tint for 3D shapes", (c, a) -> { c.game.d3.color = i(a, 0); return null; });
        fn("d3d_set_alpha(alpha)", (c, a) -> { c.game.d3.alpha = Math.max(0, Math.min(1, n(a, 0))); return null; });
        fn("d3d_set_fog(enable, color, start, end)", "Distance fog", (c, a) -> {
            D3State d = c.game.d3;
            d.fog = b(a, 0); d.fogColor = i(a, 1); d.fogStart = n(a, 2); d.fogEnd = Math.max(n(a, 2) + 1, n(a, 3));
            Screen s = s3(c);
            if (s != null) s.fog(d.fog, d.fogColor, d.fogStart, d.fogEnd);
            return null;
        });
        fn("d3d_set_lighting(enable)", (c, a) -> { c.game.d3.lighting = b(a, 0); relight(c); return null; });
        fn("d3d_light_set_ambient(color)", (c, a) -> { c.game.d3.ambient = i(a, 0); relight(c); return null; });
        fn("d3d_light_define_ambient(color)", (c, a) -> { c.game.d3.ambient = i(a, 0); relight(c); return null; });
        fn("d3d_light_define_direction(ind, dx, dy, dz, color)", "Directional light (like the sun)", (c, a) -> {
            D3State.Light l = light(c, i(a, 0));
            l.point = false; l.x = n(a, 1); l.y = n(a, 2); l.z = n(a, 3); l.color = i(a, 4);
            relight(c);
            return null;
        });
        fn("d3d_light_define_point(ind, x, y, z, range, color)", "Point light (like a lamp)", (c, a) -> {
            D3State.Light l = light(c, i(a, 0));
            l.point = true; l.x = n(a, 1); l.y = n(a, 2); l.z = n(a, 3); l.range = Math.max(1, n(a, 4)); l.color = i(a, 5);
            relight(c);
            return null;
        });
        fn("d3d_light_enable(ind, enable)", (c, a) -> { light(c, i(a, 0)).enabled = b(a, 1); relight(c); return null; });

        fn("d3d_draw_block(x1, y1, z1, x2, y2, z2, tex, hrepeat, vrepeat)", "Draw a box", (c, a) -> {
            if (s3(c) == null) return null;
            Mesh m = c.game.d3.scratch;
            m.clear();
            Prims.block(m, n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 7), n(a, 8));
            drawScratch(c, i(a, 6));
            return null;
        });
        fn("d3d_draw_floor(x1, y1, z1, x2, y2, z2, tex, hrepeat, vrepeat)", "Draw a horizontal (or sloped) floor", (c, a) -> {
            if (s3(c) == null) return null;
            Mesh m = c.game.d3.scratch;
            m.clear();
            Prims.floor(m, n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 7), n(a, 8));
            drawScratch(c, i(a, 6));
            return null;
        });
        fn("d3d_draw_wall(x1, y1, z1, x2, y2, z2, tex, hrepeat, vrepeat)", "Draw a vertical wall", (c, a) -> {
            if (s3(c) == null) return null;
            Mesh m = c.game.d3.scratch;
            m.clear();
            Prims.wall(m, n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 7), n(a, 8));
            drawScratch(c, i(a, 6));
            return null;
        });
        fn("d3d_draw_cylinder(x1, y1, z1, x2, y2, z2, tex, hrepeat, vrepeat, closed, steps)", (c, a) -> {
            if (s3(c) == null) return null;
            Mesh m = c.game.d3.scratch;
            m.clear();
            Prims.cylinder(m, n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 7), n(a, 8), b(a, 9), i(a, 10));
            drawScratch(c, i(a, 6));
            return null;
        });
        fn("d3d_draw_cone(x1, y1, z1, x2, y2, z2, tex, hrepeat, vrepeat, closed, steps)", (c, a) -> {
            if (s3(c) == null) return null;
            Mesh m = c.game.d3.scratch;
            m.clear();
            Prims.cone(m, n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 7), n(a, 8), b(a, 9), i(a, 10), 0);
            drawScratch(c, i(a, 6));
            return null;
        });
        fn("d3d_draw_ellipsoid(x1, y1, z1, x2, y2, z2, tex, hrepeat, vrepeat, steps)", (c, a) -> {
            if (s3(c) == null) return null;
            Mesh m = c.game.d3.scratch;
            m.clear();
            Prims.ellipsoid(m, n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 7), n(a, 8), i(a, 9));
            drawScratch(c, i(a, 6));
            return null;
        });
        fn("d3d_draw_billboard(sprite, subimg, x, y, z, [scale])", "Sprite always facing the camera, standing on z", (c, a) -> {
            Screen s = s3(c);
            Sprite sp = c.game.project.sprite(i(a, 0));
            if (s == null || sp == null) return null;
            double sc = n(a, 5, 1);
            Mesh m = c.game.d3.scratch;
            m.clear();
            Prims.billboard(m, n(a, 2), n(a, 3), n(a, 4), sp.width * sc, sp.height * sc, sp.originX * sc, c.game.d3.camX, c.game.d3.camY);
            int frame = FnDraw.frame(c, i(a, 0), n(a, 1));
            s.mesh(m, null, D3State.spriteTex(i(a, 0), frame), c.game.d3.color, c.game.d3.alpha);
            return null;
        });
        fn("sprite_get_texture(sprite, subimg)", "Texture id of a sprite frame for 3D", (c, a) -> {
            Sprite sp = c.game.project.sprite(i(a, 0));
            if (sp == null) return V.num(-1);
            return V.num(D3State.spriteTex(i(a, 0), FnDraw.frame(c, i(a, 0), n(a, 1))));
        });
        fn("background_get_texture(back)", "Texture id of a background for 3D",
                (c, a) -> V.num(c.game.project.background(i(a, 0)) == null ? -1 : D3State.bgTex(i(a, 0))));

        // ---- transforms
        fn("d3d_transform_set_identity()", (c, a) -> { c.game.d3.world = Mat4.identity(); world2D(c); return null; });
        fn("d3d_transform_set_translation(x, y, z)", (c, a) -> { c.game.d3.world = Mat4.translation(n(a, 0), n(a, 1), n(a, 2)); world2D(c); return null; });
        fn("d3d_transform_set_scaling(xs, ys, zs)", (c, a) -> { c.game.d3.world = Mat4.scaling(n(a, 0), n(a, 1), n(a, 2)); world2D(c); return null; });
        fn("d3d_transform_set_rotation_x(angle)", (c, a) -> { c.game.d3.world = Mat4.rotation(n(a, 0), 1, 0, 0); world2D(c); return null; });
        fn("d3d_transform_set_rotation_y(angle)", (c, a) -> { c.game.d3.world = Mat4.rotation(n(a, 0), 0, 1, 0); world2D(c); return null; });
        fn("d3d_transform_set_rotation_z(angle)", (c, a) -> { c.game.d3.world = Mat4.rotation(-n(a, 0), 0, 0, 1); world2D(c); return null; });
        fn("d3d_transform_set_rotation_axis(xa, ya, za, angle)", (c, a) -> { c.game.d3.world = Mat4.rotation(n(a, 3), n(a, 0), n(a, 1), n(a, 2)); world2D(c); return null; });
        fn("d3d_transform_add_translation(x, y, z)", (c, a) -> { c.game.d3.add(Mat4.translation(n(a, 0), n(a, 1), n(a, 2))); world2D(c); return null; });
        fn("d3d_transform_add_scaling(xs, ys, zs)", (c, a) -> { c.game.d3.add(Mat4.scaling(n(a, 0), n(a, 1), n(a, 2))); world2D(c); return null; });
        fn("d3d_transform_add_rotation_x(angle)", (c, a) -> { c.game.d3.add(Mat4.rotation(n(a, 0), 1, 0, 0)); world2D(c); return null; });
        fn("d3d_transform_add_rotation_y(angle)", (c, a) -> { c.game.d3.add(Mat4.rotation(n(a, 0), 0, 1, 0)); world2D(c); return null; });
        fn("d3d_transform_add_rotation_z(angle)", (c, a) -> { c.game.d3.add(Mat4.rotation(-n(a, 0), 0, 0, 1)); world2D(c); return null; });
        fn("d3d_transform_add_rotation_axis(xa, ya, za, angle)", (c, a) -> { c.game.d3.add(Mat4.rotation(n(a, 3), n(a, 0), n(a, 1), n(a, 2))); world2D(c); return null; });
        fn("d3d_transform_stack_push()", (c, a) -> { c.game.d3.push(); return null; });
        fn("d3d_transform_stack_pop()", (c, a) -> { c.game.d3.pop(); world2D(c); return null; });
        fn("d3d_transform_stack_clear()", (c, a) -> { c.game.d3.world = Mat4.identity(); world2D(c); return null; });

        // ---- immediate primitives
        fn("d3d_primitive_begin(kind)", "Start a custom shape (pr_trianglelist ...)", (c, a) -> { primKind = i(a, 0); primTex = -1; primVerts.clear(); return null; });
        fn("d3d_primitive_begin_texture(kind, tex)", (c, a) -> { primKind = i(a, 0); primTex = i(a, 1); primVerts.clear(); return null; });
        fn("d3d_vertex(x, y, z)", (c, a) -> { primVerts.add(new double[]{n(a, 0), n(a, 1), n(a, 2), Double.NaN, 0, 0, 0, 0}); return null; });
        fn("d3d_vertex_texture(x, y, z, u, v)", (c, a) -> { primVerts.add(new double[]{n(a, 0), n(a, 1), n(a, 2), Double.NaN, 0, 0, n(a, 3), n(a, 4)}); return null; });
        fn("d3d_vertex_normal(x, y, z, nx, ny, nz)", (c, a) -> { primVerts.add(new double[]{n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), 0, 0}); return null; });
        fn("d3d_vertex_normal_texture(x, y, z, nx, ny, nz, u, v)", (c, a) -> { primVerts.add(new double[]{n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7)}); return null; });
        fn("d3d_primitive_end()", (c, a) -> {
            Mesh m = c.game.d3.scratch;
            m.clear();
            buildPrimitive(m, primKind, primVerts);
            drawScratch(c, primTex);
            primVerts.clear();
            return null;
        });

        // ---- models
        fn("d3d_model_create()", "New empty 3D model; returns its index", (c, a) -> {
            Mesh m = new Mesh();
            m.cacheable = true;
            c.game.d3.models.add(m);
            return V.num(c.game.d3.models.size() - 1);
        });
        fn("d3d_model_destroy(ind)", (c, a) -> { model(c, i(a, 0)).clear(); return null; });
        fn("d3d_model_clear(ind)", (c, a) -> { model(c, i(a, 0)).clear(); return null; });
        fn("d3d_model_block(ind, x1, y1, z1, x2, y2, z2, hrepeat, vrepeat)", (c, a) -> {
            Prims.block(model(c, i(a, 0)), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7), n(a, 8));
            return null;
        });
        fn("d3d_model_floor(ind, x1, y1, z1, x2, y2, z2, hrepeat, vrepeat)", (c, a) -> {
            Prims.floor(model(c, i(a, 0)), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7), n(a, 8));
            return null;
        });
        fn("d3d_model_wall(ind, x1, y1, z1, x2, y2, z2, hrepeat, vrepeat)", (c, a) -> {
            Prims.wall(model(c, i(a, 0)), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7), n(a, 8));
            return null;
        });
        fn("d3d_model_cylinder(ind, x1, y1, z1, x2, y2, z2, hrepeat, vrepeat, closed, steps)", (c, a) -> {
            Prims.cylinder(model(c, i(a, 0)), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7), n(a, 8), b(a, 9), i(a, 10));
            return null;
        });
        fn("d3d_model_cone(ind, x1, y1, z1, x2, y2, z2, hrepeat, vrepeat, closed, steps)", (c, a) -> {
            Prims.cone(model(c, i(a, 0)), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7), n(a, 8), b(a, 9), i(a, 10), 0);
            return null;
        });
        fn("d3d_model_ellipsoid(ind, x1, y1, z1, x2, y2, z2, hrepeat, vrepeat, steps)", (c, a) -> {
            Prims.ellipsoid(model(c, i(a, 0)), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7), n(a, 8), i(a, 9));
            return null;
        });
        fn("d3d_model_primitive_begin(ind, kind)", (c, a) -> { primKind = i(a, 1); primVerts.clear(); return null; });
        fn("d3d_model_vertex(ind, x, y, z)", (c, a) -> { primVerts.add(new double[]{n(a, 1), n(a, 2), n(a, 3), Double.NaN, 0, 0, 0, 0}); return null; });
        fn("d3d_model_vertex_texture(ind, x, y, z, u, v)", (c, a) -> { primVerts.add(new double[]{n(a, 1), n(a, 2), n(a, 3), Double.NaN, 0, 0, n(a, 4), n(a, 5)}); return null; });
        fn("d3d_model_vertex_normal(ind, x, y, z, nx, ny, nz)", (c, a) -> { primVerts.add(new double[]{n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), 0, 0}); return null; });
        fn("d3d_model_vertex_normal_texture(ind, x, y, z, nx, ny, nz, u, v)", (c, a) -> { primVerts.add(new double[]{n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5), n(a, 6), n(a, 7), n(a, 8)}); return null; });
        fn("d3d_model_primitive_end(ind)", (c, a) -> { buildPrimitive(model(c, i(a, 0)), primKind, primVerts); primVerts.clear(); return null; });
        fn("d3d_model_draw(ind, x, y, z, tex)", "Draw a model at a position", (c, a) -> {
            Screen s = s3(c);
            if (s == null) return null;
            Mesh m = model(c, i(a, 0));
            float[] w = Mat4.mul(c.game.d3.world, Mat4.translation(n(a, 1), n(a, 2), n(a, 3)));
            s.mesh(m, w, i(a, 4), c.game.d3.color, c.game.d3.alpha * c.game.drawAlpha);
            return null;
        });

        // ============================================================ raycast
        category("Raycast");
        fn("rc_set_colors(floor, ceiling)", "Raycast floor and ceiling colors", (c, a) -> { c.game.raycaster.floorColor = i(a, 0); c.game.raycaster.ceilColor = i(a, 1); return null; });
        fn("rc_set_textures(floorbg, ceilingbg)", "Raycast floor/ceiling textures (backgrounds, -1 for none)", (c, a) -> {
            c.game.raycaster.floorTex = i(a, 0);
            c.game.raycaster.ceilTex = i(a, 1);
            return null;
        });
        fn("rc_set_fov(degrees)", (c, a) -> { c.game.raycaster.fov = Math.max(20, Math.min(160, n(a, 0))); return null; });
        fn("rc_set_fog(distance, color)", "Fade to color with distance (0 = off)", (c, a) -> { c.game.raycaster.fogDist = n(a, 0); c.game.raycaster.fogColor = i(a, 1); return null; });
        fn("rc_set_resolution(width)", "Render width in pixels (lower is faster)", (c, a) -> { c.game.raycaster.resolution = Math.max(80, Math.min(1280, i(a, 0))); return null; });
        fn("rc_camera_follow(id)", "Use an instance as the raycast camera", (c, a) -> {
            Instance i = c.game.firstTarget(a[0], c);
            c.game.raycaster.cameraId = i == null ? -4 : i.id;
            return null;
        });
        fn("rc_wall_at(x, y)", "Is there a raycast wall at a room position", (c, a) -> {
            c.game.raycaster.ensureGrid();
            return V.bool(c.game.raycaster.wallAt(n(a, 0), n(a, 1)));
        });
        fn("rc_cast(x, y, direction, maxdist)", "Distance to the nearest wall along a direction", (c, a) -> {
            c.game.raycaster.ensureGrid();
            return V.num(c.game.raycaster.cast(n(a, 0), n(a, 1), n(a, 2), n(a, 3) <= 0 ? 10000 : n(a, 3)));
        });
        fn("rc_can_see(obj)", "Line of sight from self (or the camera) to the nearest instance of obj", (c, a) -> {
            Raycaster r = c.game.raycaster;
            r.ensureGrid();
            Instance from = c.self != null ? c.self : r.camera();
            if (from == null) return V.ZERO;
            Instance best = null;
            double bd = Double.MAX_VALUE;
            for (Instance i : c.game.targets(a[0], c)) {
                if (i == from) continue;
                double d = Math.hypot(i.x - from.x, i.y - from.y);
                if (d < bd) { bd = d; best = i; }
            }
            return V.bool(best != null && r.lineClear(from.x, from.y, best.x, best.y));
        });
        fn("rc_line_clear(x1, y1, x2, y2)", "No raycast wall between two points", (c, a) -> {
            c.game.raycaster.ensureGrid();
            return V.bool(c.game.raycaster.lineClear(n(a, 0), n(a, 1), n(a, 2), n(a, 3)));
        });
        fn("rc_cell_size()", (c, a) -> V.num(c.game.raycaster.cellSize()));
        fn("room_mode()", "0 = 2D, 1 = raycast, 2 = 3D", (c, a) -> V.num(c.game.rs == null ? 0 : c.game.rs.mode));
    }

    /** Optional depth-test control implemented by the GL screen. */
    public interface Depth { void depthTest(boolean on); }

    static void world2D(Ctx c) {
        Screen s = s3(c);
        if (s != null) s.world2D(worldOrNull(c));
    }

    static void relight(Ctx c) {
        Screen s = s3(c);
        if (s != null) s.lighting(c.game.d3.lighting, c.game.d3.ambient, c.game.d3.lights);
    }

    static D3State.Light light(Ctx c, int k) {
        if (k < 0 || k > 7) throw new ScriptError("Light index must be 0..7");
        return c.game.d3.lights[k];
    }

    static Mesh model(Ctx c, int k) {
        List<Mesh> l = c.game.d3.models;
        if (k < 0 || k >= l.size()) throw new ScriptError("Model " + k + " does not exist");
        return l.get(k);
    }

    static void buildPrimitive(Mesh m, int kind, List<double[]> v) {
        int n = v.size();
        switch (kind) {
            case 4: // list
                for (int k = 0; k + 2 < n; k += 3) addTri(m, v.get(k), v.get(k + 1), v.get(k + 2));
                break;
            case 5: // strip
                for (int k = 0; k + 2 < n; k++) {
                    if (k % 2 == 0) addTri(m, v.get(k), v.get(k + 1), v.get(k + 2));
                    else addTri(m, v.get(k + 1), v.get(k), v.get(k + 2));
                }
                break;
            case 6: // fan
                for (int k = 1; k + 1 < n; k++) addTri(m, v.get(0), v.get(k), v.get(k + 1));
                break;
            default:
                break; // points and lines are not supported in 3D models
        }
    }

    private static void addTri(Mesh m, double[] a, double[] b, double[] c) {
        double[] na = a.clone(), nb = b.clone(), nc = c.clone();
        if (Double.isNaN(a[3]) || Double.isNaN(b[3]) || Double.isNaN(c[3])) {
            double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            double nx = -(uy * vz - uz * vy), ny = -(uz * vx - ux * vz), nz = -(ux * vy - uy * vx);
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-12) { nx = 0; ny = 0; nz = 1; l = 1; }
            for (double[] p : new double[][]{na, nb, nc}) { p[3] = nx / l; p[4] = ny / l; p[5] = nz / l; }
        }
        m.tri(na, nb, nc);
    }
}
