package com.droidmaker.run;

import android.opengl.GLES20;

import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.Project;
import com.droidmaker.core.project.Sprite;
import com.droidmaker.core.runtime.D3State;
import com.droidmaker.core.runtime.Fn3D;
import com.droidmaker.core.runtime.Game;
import com.droidmaker.core.runtime.Mat4;
import com.droidmaker.core.runtime.Mesh;
import com.droidmaker.core.runtime.Screen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OpenGL ES 2.0 implementation of the game's drawing backend (2D batch, text, raycast image, 3D meshes). */
public final class GLScreen implements Screen, Fn3D.Depth {
    private final Game game;
    private final Project project;
    final Textures tex;
    final TextAtlas atlas = new TextAtlas();

    // ---- 2D batch
    private static final int MAX_VERTS = 6000;
    private static final int STRIDE = 8; // x y u v r g b a
    private final float[] verts = new float[MAX_VERTS * STRIDE];
    private int nverts;
    private FloatBuffer vbuf;
    private int curTex = -1;
    private int prog2d, a2dPos, a2dUV, a2dCol, u2dMVP, u2dTex;
    private float[] mvp2d = Mat4.identity();

    // ---- 3D
    private int prog3d, a3Pos, a3Norm, a3UV, u3MVP, u3World, u3View, u3Lit, u3Ambient, u3LightPos, u3LightCol, u3Tex, u3Color,
            u3Fog, u3FogColor, u3FogRange;
    private float[] view3 = Mat4.identity(), proj3 = Mat4.identity(), world2d;
    private boolean in3D;
    private FloatBuffer meshBuf;
    private final Map<Integer, int[]> meshVbo = new HashMap<>(); // mesh uid -> {vbo, version, count}
    private final float[] lightPos = new float[32], lightCol = new float[32];
    private float[] ambient = {0.3f, 0.3f, 0.3f};
    private boolean lit, fogOn;
    private float[] fogCol = {0.5f, 0.5f, 0.6f};
    private float fogStart = 200, fogEnd = 800;

    // ---- raycast image
    private int rcTex, rcW, rcH;

    // ---- screen
    private int screenW = 1, screenH = 1;
    private int blend;

    public GLScreen(Game game, Project project) {
        this.game = game;
        this.project = project;
        this.tex = new Textures(project);
    }

    /** Called when the GL context is (re)created. */
    void init() {
        prog2d = Shaders.program(Shaders.V2D, Shaders.F2D);
        a2dPos = GLES20.glGetAttribLocation(prog2d, "aPos");
        a2dUV = GLES20.glGetAttribLocation(prog2d, "aUV");
        a2dCol = GLES20.glGetAttribLocation(prog2d, "aCol");
        u2dMVP = GLES20.glGetUniformLocation(prog2d, "uMVP");
        u2dTex = GLES20.glGetUniformLocation(prog2d, "uTex");
        prog3d = Shaders.program(Shaders.V3D, Shaders.F3D);
        a3Pos = GLES20.glGetAttribLocation(prog3d, "aPos");
        a3Norm = GLES20.glGetAttribLocation(prog3d, "aNorm");
        a3UV = GLES20.glGetAttribLocation(prog3d, "aUV");
        u3MVP = GLES20.glGetUniformLocation(prog3d, "uMVP");
        u3World = GLES20.glGetUniformLocation(prog3d, "uWorld");
        u3View = GLES20.glGetUniformLocation(prog3d, "uView");
        u3Lit = GLES20.glGetUniformLocation(prog3d, "uLit");
        u3Ambient = GLES20.glGetUniformLocation(prog3d, "uAmbient");
        u3LightPos = GLES20.glGetUniformLocation(prog3d, "uLightPos");
        u3LightCol = GLES20.glGetUniformLocation(prog3d, "uLightCol");
        u3Tex = GLES20.glGetUniformLocation(prog3d, "uTex");
        u3Color = GLES20.glGetUniformLocation(prog3d, "uColor");
        u3Fog = GLES20.glGetUniformLocation(prog3d, "uFog");
        u3FogColor = GLES20.glGetUniformLocation(prog3d, "uFogColor");
        u3FogRange = GLES20.glGetUniformLocation(prog3d, "uFogRange");
        vbuf = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        tex.init();
        atlas.init();
        meshVbo.clear();
        rcTex = 0;
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        setBlend(0);
    }

    void size(int w, int h) {
        screenW = Math.max(1, w);
        screenH = Math.max(1, h);
    }

    int width() { return screenW; }
    int height() { return screenH; }

    void beginFrame() {
        nverts = 0;
        curTex = -1;
        in3D = false;
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glViewport(0, 0, screenW, screenH);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        setBlend(0);
    }

    void endFrame() {
        flush();
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    /** Overlay in raw screen pixels (virtual gamepad, error banners). */
    void beginOverlay() {
        flush();
        end3DState();
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glViewport(0, 0, screenW, screenH);
        mvp2d = Mat4.ortho(0, screenW, screenH, 0, -1, 1);
        setBlend(0);
    }

    // ================================================================= passes
    private int[] portPixels(double px, double py, double pw, double ph) {
        double[] r = game.displayRect();
        double sx = r[2] / game.displayW, sy = r[3] / game.displayH;
        int x = (int) Math.round(r[0] + px * sx);
        int w = (int) Math.round(pw * sx);
        int h = (int) Math.round(ph * sy);
        int yTop = (int) Math.round(r[1] + py * sy);
        int y = screenH - yTop - h;
        return new int[]{x, y, Math.max(1, w), Math.max(1, h)};
    }

    private void port(double px, double py, double pw, double ph) {
        int[] p = portPixels(px, py, pw, ph);
        GLES20.glViewport(p[0], p[1], p[2], p[3]);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(p[0], p[1], p[2], p[3]);
    }

    @Override
    public void beginView2D(double vx, double vy, double vw, double vh, double angle, double px, double py, double pw, double ph) {
        flush();
        end3DState();
        port(px, py, pw, ph);
        float[] o = Mat4.ortho(vx, vx + vw, vy + vh, vy, -1, 1);
        if (angle != 0) {
            double cx = vx + vw / 2, cy = vy + vh / 2;
            float[] t = Mat4.mul(Mat4.translation(cx, cy, 0), Mat4.mul(Mat4.rotation(-angle, 0, 0, 1), Mat4.translation(-cx, -cy, 0)));
            o = Mat4.mul(o, t);
        }
        mvp2d = o;
    }

    @Override
    public void beginGui(double w, double h) {
        flush();
        end3DState();
        port(0, 0, game.displayW, game.displayH);
        mvp2d = Mat4.ortho(0, w, h, 0, -1, 1);
    }

    @Override
    public void begin3D(double px, double py, double pw, double ph, int clearColor) {
        flush();
        port(px, py, pw, ph);
        GLES20.glClearColor(((clearColor >> 16) & 255) / 255f, ((clearColor >> 8) & 255) / 255f, (clearColor & 255) / 255f, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDepthMask(true);
        in3D = true;
        world2d = null;
        update2DMatrix();
    }

    @Override
    public void end3D() {
        flush();
        end3DState();
    }

    private void end3DState() {
        if (!in3D) return;
        in3D = false;
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    @Override
    public void depthTest(boolean on) {
        flush();
        if (on) GLES20.glEnable(GLES20.GL_DEPTH_TEST); else GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void raycastImage(int[] pixels, int w, int h, double px, double py, double pw, double ph) {
        flush();
        end3DState();
        if (rcTex == 0 || rcW != w || rcH != h) {
            if (rcTex != 0) GLES20.glDeleteTextures(1, new int[]{rcTex}, 0);
            rcTex = tex.upload(pixels, w, h, false, false);
            rcW = w;
            rcH = h;
        } else tex.update(rcTex, pixels, w, h);
        port(px, py, pw, ph);
        mvp2d = Mat4.ortho(0, 1, 1, 0, -1, 1);
        quad(rcTex, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0xFFFFFF, 1);
        flush();
    }

    // ================================================================= batch core
    private void setBlend(int mode) {
        blend = mode;
        switch (mode) {
            case 1: // additive
                GLES20.glBlendEquation(GLES20.GL_FUNC_ADD);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
                break;
            case 2: // subtract
                GLES20.glBlendEquation(GLES20.GL_FUNC_REVERSE_SUBTRACT);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
                break;
            case 3: // max-ish
                GLES20.glBlendEquation(GLES20.GL_FUNC_ADD);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_COLOR);
                break;
            default:
                GLES20.glBlendEquation(GLES20.GL_FUNC_ADD);
                GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    @Override
    public void blendMode(int mode) {
        if (mode == blend) return;
        flush();
        setBlend(mode);
    }

    private void ensure(int texId, int n) {
        if (texId != curTex || nverts + n > MAX_VERTS) {
            flush();
            curTex = texId;
        }
    }

    private void v(float x, float y, float u, float vv, float r, float g, float b, float a) {
        int o = nverts * STRIDE;
        verts[o] = x; verts[o + 1] = y; verts[o + 2] = u; verts[o + 3] = vv;
        verts[o + 4] = r; verts[o + 5] = g; verts[o + 6] = b; verts[o + 7] = a;
        nverts++;
    }

    private static float cr(int c) { return ((c >> 16) & 255) / 255f; }
    private static float cg(int c) { return ((c >> 8) & 255) / 255f; }
    private static float cb(int c) { return (c & 255) / 255f; }

    /** Textured quad with corners 1..4 (clockwise from top-left). */
    private void quad(int texId, double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4,
                      float u0, float v0, float u1, float v1, int color, double alpha) {
        ensure(texId, 6);
        float r = cr(color), g = cg(color), b = cb(color), a = (float) alpha;
        v((float) x1, (float) y1, u0, v0, r, g, b, a);
        v((float) x2, (float) y2, u1, v0, r, g, b, a);
        v((float) x3, (float) y3, u1, v1, r, g, b, a);
        v((float) x1, (float) y1, u0, v0, r, g, b, a);
        v((float) x3, (float) y3, u1, v1, r, g, b, a);
        v((float) x4, (float) y4, u0, v1, r, g, b, a);
    }

    private void tri(double x1, double y1, double x2, double y2, double x3, double y3, int c1, int c2, int c3, double alpha) {
        ensure(tex.white, 3);
        float a = (float) alpha;
        v((float) x1, (float) y1, 0.5f, 0.5f, cr(c1), cg(c1), cb(c1), a);
        v((float) x2, (float) y2, 0.5f, 0.5f, cr(c2), cg(c2), cb(c2), a);
        v((float) x3, (float) y3, 0.5f, 0.5f, cr(c3), cg(c3), cb(c3), a);
    }

    void flush() {
        if (nverts == 0) return;
        GLES20.glUseProgram(prog2d);
        GLES20.glUniformMatrix4fv(u2dMVP, 1, false, mvp2d, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curTex);
        GLES20.glUniform1i(u2dTex, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        vbuf.clear();
        vbuf.put(verts, 0, nverts * STRIDE);
        vbuf.position(0);
        GLES20.glVertexAttribPointer(a2dPos, 2, GLES20.GL_FLOAT, false, STRIDE * 4, vbuf);
        GLES20.glEnableVertexAttribArray(a2dPos);
        vbuf.position(2);
        GLES20.glVertexAttribPointer(a2dUV, 2, GLES20.GL_FLOAT, false, STRIDE * 4, vbuf);
        GLES20.glEnableVertexAttribArray(a2dUV);
        vbuf.position(4);
        GLES20.glVertexAttribPointer(a2dCol, 4, GLES20.GL_FLOAT, false, STRIDE * 4, vbuf);
        GLES20.glEnableVertexAttribArray(a2dCol);
        if (in3D) GLES20.glDepthMask(false);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, nverts);
        if (in3D) GLES20.glDepthMask(true);
        GLES20.glDisableVertexAttribArray(a2dPos);
        GLES20.glDisableVertexAttribArray(a2dUV);
        GLES20.glDisableVertexAttribArray(a2dCol);
        nverts = 0;
    }

    // ================================================================= Gfx
    @Override
    public void sprite(int spr, int frame, double x, double y, double xs, double ys, double angle, int color, double alpha) {
        Sprite s = project.sprite(spr);
        if (s == null) return;
        Textures.Tex t = tex.sprite(spr, frame);
        if (t == null) return;
        transformedQuad(t.id, x, y, -s.originX, -s.originY, s.width, s.height, xs, ys, angle, 0, 0, 1, 1, color, alpha);
    }

    /** Quad of size w x h placed with local offset (ox, oy) from (x, y), scaled and rotated about (x, y). */
    private void transformedQuad(int texId, double x, double y, double ox, double oy, double w, double h,
                                 double xs, double ys, double angle, float u0, float v0, float u1, float v1, int color, double alpha) {
        double lx1 = ox * xs, ly1 = oy * ys, lx2 = (ox + w) * xs, ly2 = (oy + h) * ys;
        if (angle == 0) {
            quad(texId, x + lx1, y + ly1, x + lx2, y + ly1, x + lx2, y + ly2, x + lx1, y + ly2, u0, v0, u1, v1, color, alpha);
            return;
        }
        double a = Math.toRadians(-angle), c = Math.cos(a), sn = Math.sin(a);
        quad(texId,
                x + lx1 * c - ly1 * sn, y + lx1 * sn + ly1 * c,
                x + lx2 * c - ly1 * sn, y + lx2 * sn + ly1 * c,
                x + lx2 * c - ly2 * sn, y + lx2 * sn + ly2 * c,
                x + lx1 * c - ly2 * sn, y + lx1 * sn + ly2 * c,
                u0, v0, u1, v1, color, alpha);
    }

    @Override
    public void spritePart(int spr, int frame, int left, int top, int w, int h, double x, double y, double xs, double ys, int color, double alpha) {
        Sprite s = project.sprite(spr);
        if (s == null || s.width <= 0 || s.height <= 0) return;
        Textures.Tex t = tex.sprite(spr, frame);
        if (t == null) return;
        part(t.id, s.width, s.height, left, top, w, h, x, y, xs, ys, color, alpha);
    }

    private void part(int texId, int tw, int th, int left, int top, int w, int h, double x, double y, double xs, double ys, int color, double alpha) {
        left = Math.max(0, left);
        top = Math.max(0, top);
        w = Math.min(w, tw - left);
        h = Math.min(h, th - top);
        if (w <= 0 || h <= 0) return;
        float u0 = left / (float) tw, v0 = top / (float) th, u1 = (left + w) / (float) tw, v1 = (top + h) / (float) th;
        quad(texId, x, y, x + w * xs, y, x + w * xs, y + h * ys, x, y + h * ys, u0, v0, u1, v1, color, alpha);
    }

    @Override
    public void spriteGeneral(int spr, int frame, double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4, int color, double alpha) {
        Textures.Tex t = tex.sprite(spr, frame);
        if (t == null) return;
        quad(t.id, x1, y1, x2, y2, x3, y3, x4, y4, 0, 0, 1, 1, color, alpha);
    }

    @Override
    public void background(int bg, double x, double y, double xs, double ys, double angle, int color, double alpha) {
        Background b = project.background(bg);
        if (b == null) return;
        Textures.Tex t = tex.background(bg);
        if (t == null) return;
        transformedQuad(t.id, x, y, 0, 0, b.width, b.height, xs, ys, angle, 0, 0, 1, 1, color, alpha);
    }

    @Override
    public void backgroundPart(int bg, int left, int top, int w, int h, double x, double y, double xs, double ys, int color, double alpha) {
        Background b = project.background(bg);
        if (b == null) return;
        Textures.Tex t = tex.background(bg);
        if (t == null) return;
        part(t.id, b.width, b.height, left, top, w, h, x, y, xs, ys, color, alpha);
    }

    @Override
    public void rect(double x1, double y1, double x2, double y2, int c1, int c2, int c3, int c4, double alpha, boolean outline) {
        double l = Math.min(x1, x2), t = Math.min(y1, y2), r = Math.max(x1, x2), b = Math.max(y1, y2);
        if (outline) {
            line(l, t + 0.5, r, t + 0.5, 1, c1, c2, alpha);
            line(r - 0.5, t, r - 0.5, b, 1, c2, c4, alpha);
            line(r, b - 0.5, l, b - 0.5, 1, c4, c3, alpha);
            line(l + 0.5, b, l + 0.5, t, 1, c3, c1, alpha);
            return;
        }
        // include the far edge pixel like Game Maker
        r += 1;
        b += 1;
        tri(l, t, r, t, r, b, c1, c2, c4, alpha);
        tri(l, t, r, b, l, b, c1, c4, c3, alpha);
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, double width, int c1, int c2, double alpha) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) { rect(x1, y1, x1, y1, c1, c1, c1, c1, alpha, false); return; }
        double hw = Math.max(0.5, width / 2);
        double nx = -dy / len * hw, ny = dx / len * hw;
        tri(x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, c1, c2, c2, alpha);
        tri(x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, c1, c2, c1, alpha);
    }

    @Override
    public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, int c1, int c2, int c3, double alpha, boolean outline) {
        if (outline) {
            line(x1, y1, x2, y2, 1, c1, c2, alpha);
            line(x2, y2, x3, y3, 1, c2, c3, alpha);
            line(x3, y3, x1, y1, 1, c3, c1, alpha);
        } else tri(x1, y1, x2, y2, x3, y3, c1, c2, c3, alpha);
    }

    @Override
    public void ellipse(double x1, double y1, double x2, double y2, int cc, int ce, double alpha, boolean outline, int segments) {
        double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2, rx = Math.abs(x2 - x1) / 2, ry = Math.abs(y2 - y1) / 2;
        int n = Math.max(8, Math.min(128, segments));
        double px = cx + rx, py = cy;
        for (int i = 1; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            double qx = cx + Math.cos(a) * rx, qy = cy + Math.sin(a) * ry;
            if (outline) line(px, py, qx, qy, 1, ce, ce, alpha);
            else tri(cx, cy, px, py, qx, qy, cc, ce, ce, alpha);
            px = qx;
            py = qy;
        }
    }

    @Override
    public void point(double x, double y, int color, double alpha) {
        tri(x, y, x + 1, y, x + 1, y + 1, color, color, color, alpha);
        tri(x, y, x + 1, y + 1, x, y + 1, color, color, color, alpha);
    }

    // ---- text
    private List<String> layout(String s, double size, boolean bold, double width) {
        List<String> lines = new ArrayList<>();
        float k = (float) (size / TextAtlas.BASE);
        for (String para : s.split("\n", -1)) {
            if (width <= 0) { lines.add(para); continue; }
            StringBuilder line = new StringBuilder();
            float lw = 0;
            for (String word : para.split(" ", -1)) {
                float ww = measure(word, bold) * k;
                float sp = line.length() > 0 ? measure(" ", bold) * k : 0;
                if (line.length() > 0 && lw + sp + ww > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                    lw = 0;
                    sp = 0;
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
                lw += sp + ww;
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private float measure(String s, boolean bold) {
        float w = 0;
        for (int i = 0; i < s.length(); ) {
            int ch = s.codePointAt(i);
            w += atlas.advance(ch, bold);
            i += Character.charCount(ch);
        }
        return w;
    }

    @Override
    public void text(double x, double y, String s, double size, boolean bold, int halign, int valign,
                     double xs, double ys, double angle, int color, double alpha, double sep, double width) {
        if (s == null || s.isEmpty()) return;
        float k = (float) (size / TextAtlas.BASE);
        double lineH = sep > 0 ? sep : atlas.lineHeight * k;
        List<String> lines = layout(s, size, bold, width);
        double total = lines.size() * lineH;
        double oy = valign == 1 ? -total / 2 : valign == 2 ? -total : 0;
        double a = Math.toRadians(-angle), c = Math.cos(a), sn = Math.sin(a);
        float r = cr(color), g = cg(color), b = cb(color), al = (float) alpha;
        for (int li = 0; li < lines.size(); li++) {
            String line = lines.get(li);
            double lw = measure(line, bold) * k;
            double ox = halign == 1 ? -lw / 2 : halign == 2 ? -lw : 0;
            double cxp = ox, cyp = oy + li * lineH;
            for (int i = 0; i < line.length(); ) {
                int ch = line.codePointAt(i);
                i += Character.charCount(ch);
                TextAtlas.Glyph gl = atlas.glyph(ch, bold);
                if (gl == null) continue;
                if (!Character.isWhitespace(ch)) {
                    double lx1 = (cxp - 2 * k) * xs, ly1 = (cyp - 1 * k) * ys;
                    double lx2 = lx1 + gl.w * k * xs, ly2 = ly1 + gl.h * k * ys;
                    ensure(atlas.texture, 6);
                    double[] p = {lx1, ly1, lx2, ly1, lx2, ly2, lx1, ly2};
                    for (int q = 0; q < 8; q += 2) {
                        double px = p[q], py = p[q + 1];
                        p[q] = x + px * c - py * sn;
                        p[q + 1] = y + px * sn + py * c;
                    }
                    v((float) p[0], (float) p[1], gl.u0, gl.v0, r, g, b, al);
                    v((float) p[2], (float) p[3], gl.u1, gl.v0, r, g, b, al);
                    v((float) p[4], (float) p[5], gl.u1, gl.v1, r, g, b, al);
                    v((float) p[0], (float) p[1], gl.u0, gl.v0, r, g, b, al);
                    v((float) p[4], (float) p[5], gl.u1, gl.v1, r, g, b, al);
                    v((float) p[6], (float) p[7], gl.u0, gl.v1, r, g, b, al);
                }
                cxp += gl.advance * k;
            }
        }
    }

    @Override
    public double textWidth(String s, double size, boolean bold, double width) {
        float k = (float) (size / TextAtlas.BASE);
        double w = 0;
        for (String l : layout(s, size, bold, width)) w = Math.max(w, measure(l, bold) * k);
        return w;
    }

    @Override
    public double textHeight(String s, double size, boolean bold, double sep, double width) {
        float k = (float) (size / TextAtlas.BASE);
        double lineH = sep > 0 ? sep : atlas.lineHeight * k;
        return layout(s, size, bold, width).size() * lineH;
    }

    @Override
    public void clear(int color, double alpha) {
        flush();
        GLES20.glClearColor(cr(color), cg(color), cb(color), (float) alpha);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    // ================================================================= 3D
    @Override
    public void matrices(float[] view, float[] proj) {
        flush();
        view3 = view;
        proj3 = proj;
        update2DMatrix();
    }

    private void update2DMatrix() {
        float[] vp = Mat4.mul(proj3, view3);
        mvp2d = world2d == null ? vp : Mat4.mul(vp, world2d);
    }

    @Override
    public void world2D(float[] world) {
        flush();
        world2d = world == null ? null : world.clone();
        if (in3D) update2DMatrix();
    }

    @Override
    public void fog(boolean on, int color, double start, double end) {
        fogOn = on;
        fogCol = new float[]{cr(color), cg(color), cb(color)};
        fogStart = (float) start;
        fogEnd = (float) end;
    }

    @Override
    public void lighting(boolean on, int amb, D3State.Light[] lights) {
        lit = on;
        ambient = new float[]{cr(amb), cg(amb), cb(amb)};
        java.util.Arrays.fill(lightPos, 0);
        java.util.Arrays.fill(lightCol, 0);
        int k = 0;
        for (D3State.Light l : lights) {
            if (!l.enabled || k >= 8) continue;
            lightPos[k * 4] = (float) l.x;
            lightPos[k * 4 + 1] = (float) l.y;
            lightPos[k * 4 + 2] = (float) l.z;
            lightPos[k * 4 + 3] = l.point ? 1 : 0;
            lightCol[k * 4] = cr(l.color);
            lightCol[k * 4 + 1] = cg(l.color);
            lightCol[k * 4 + 2] = cb(l.color);
            lightCol[k * 4 + 3] = (float) l.range;
            k++;
        }
    }

    @Override
    public void culling(boolean on) {
        flush();
        if (on) {
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
            GLES20.glFrontFace(GLES20.GL_CCW);
        } else GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    @Override
    public void mesh(Mesh m, float[] world, int texId, int color, double alpha) {
        if (m == null || m.count == 0) return;
        flush();
        GLES20.glUseProgram(prog3d);
        float[] w = world == null ? Mat4.identity() : world;
        float[] mvp = Mat4.mul(Mat4.mul(proj3, view3), w);
        GLES20.glUniformMatrix4fv(u3MVP, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(u3World, 1, false, w, 0);
        GLES20.glUniformMatrix4fv(u3View, 1, false, view3, 0);
        GLES20.glUniform1f(u3Lit, lit ? 1 : 0);
        GLES20.glUniform3fv(u3Ambient, 1, ambient, 0);
        GLES20.glUniform4fv(u3LightPos, 8, lightPos, 0);
        GLES20.glUniform4fv(u3LightCol, 8, lightCol, 0);
        GLES20.glUniform4f(u3Color, cr(color), cg(color), cb(color), (float) alpha);
        GLES20.glUniform1f(u3Fog, fogOn ? 1 : 0);
        GLES20.glUniform3fv(u3FogColor, 1, fogCol, 0);
        GLES20.glUniform2f(u3FogRange, fogStart, fogEnd);
        Textures.Tex t = texId == -1 ? null : tex.tex3D(texId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t == null ? tex.white : t.id);
        GLES20.glUniform1i(u3Tex, 0);
        int stride = Mesh.STRIDE * 4;
        if (m.cacheable) {
            int[] e = meshVbo.get(m.uid);
            if (e == null) {
                e = new int[3];
                int[] ids = new int[1];
                GLES20.glGenBuffers(1, ids, 0);
                e[0] = ids[0];
                e[1] = -1;
                meshVbo.put(m.uid, e);
            }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, e[0]);
            if (e[1] != m.version) {
                FloatBuffer fb = meshData(m);
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, m.count * stride, fb, GLES20.GL_STATIC_DRAW);
                e[1] = m.version;
                e[2] = m.count;
            }
            GLES20.glVertexAttribPointer(a3Pos, 3, GLES20.GL_FLOAT, false, stride, 0);
            GLES20.glVertexAttribPointer(a3Norm, 3, GLES20.GL_FLOAT, false, stride, 12);
            GLES20.glVertexAttribPointer(a3UV, 2, GLES20.GL_FLOAT, false, stride, 24);
        } else {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            FloatBuffer fb = meshData(m);
            fb.position(0);
            GLES20.glVertexAttribPointer(a3Pos, 3, GLES20.GL_FLOAT, false, stride, fb);
            fb.position(3);
            GLES20.glVertexAttribPointer(a3Norm, 3, GLES20.GL_FLOAT, false, stride, fb);
            fb.position(6);
            GLES20.glVertexAttribPointer(a3UV, 2, GLES20.GL_FLOAT, false, stride, fb);
        }
        GLES20.glEnableVertexAttribArray(a3Pos);
        GLES20.glEnableVertexAttribArray(a3Norm);
        GLES20.glEnableVertexAttribArray(a3UV);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, m.count);
        GLES20.glDisableVertexAttribArray(a3Pos);
        GLES20.glDisableVertexAttribArray(a3Norm);
        GLES20.glDisableVertexAttribArray(a3UV);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private FloatBuffer meshData(Mesh m) {
        int n = m.count * Mesh.STRIDE;
        if (meshBuf == null || meshBuf.capacity() < n)
            meshBuf = ByteBuffer.allocateDirect(Math.max(n, 4096) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        meshBuf.clear();
        meshBuf.put(m.data, 0, n);
        meshBuf.position(0);
        return meshBuf;
    }

    // ================================================================= overlay helpers (screen pixels)
    void fillRectPx(float x1, float y1, float x2, float y2, int color, float alpha) {
        tri(x1, y1, x2, y1, x2, y2, color, color, color, alpha);
        tri(x1, y1, x2, y2, x1, y2, color, color, color, alpha);
    }

    void circlePx(float cx, float cy, float r, int color, float alpha, boolean outline) {
        ellipse(cx - r, cy - r, cx + r, cy + r, color, color, alpha, outline, 40);
    }

    void textPx(float x, float y, String s, float size, int color, float alpha) {
        text(x, y, s, size, true, 1, 1, 1, 1, 0, color, alpha, -1, -1);
    }
}
