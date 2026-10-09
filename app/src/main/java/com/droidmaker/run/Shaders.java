package com.droidmaker.run;

import android.opengl.GLES20;
import android.util.Log;

/** GLSL sources and a small helper to compile programs. */
final class Shaders {
    private Shaders() {}

    static final String V2D =
            "uniform mat4 uMVP;\n" +
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "attribute vec4 aCol;\n" +
            "varying vec2 vUV;\n" +
            "varying vec4 vCol;\n" +
            "void main() {\n" +
            "  vUV = aUV;\n" +
            "  vCol = aCol;\n" +
            "  gl_Position = uMVP * vec4(aPos, 0.0, 1.0);\n" +
            "}\n";

    static final String F2D =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "varying vec2 vUV;\n" +
            "varying vec4 vCol;\n" +
            "void main() {\n" +
            "  vec4 c = texture2D(uTex, vUV) * vCol;\n" +
            "  if (c.a < 0.004) discard;\n" +
            "  gl_FragColor = c;\n" +
            "}\n";

    static final String V3D =
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uWorld;\n" +
            "uniform mat4 uView;\n" +
            "uniform float uLit;\n" +
            "uniform vec3 uAmbient;\n" +
            "uniform vec4 uLightPos[8];\n" +
            "uniform vec4 uLightCol[8];\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNorm;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "varying vec3 vLight;\n" +
            "varying float vDist;\n" +
            "void main() {\n" +
            "  vec4 wp = uWorld * vec4(aPos, 1.0);\n" +
            "  vUV = aUV;\n" +
            "  vDist = length((uView * wp).xyz);\n" +
            "  if (uLit > 0.5) {\n" +
            "    vec3 n = normalize((uWorld * vec4(aNorm, 0.0)).xyz);\n" +
            "    vec3 l = uAmbient;\n" +
            "    for (int i = 0; i < 8; i++) {\n" +
            "      vec4 lp = uLightPos[i];\n" +
            "      vec4 lc = uLightCol[i];\n" +
            "      if (lp.w < 0.5) {\n" +
            "        vec3 d = normalize(-lp.xyz + vec3(0.0, 0.0, 0.00001));\n" +
            "        l += lc.rgb * max(dot(n, d), 0.0);\n" +
            "      } else {\n" +
            "        vec3 d = lp.xyz - wp.xyz;\n" +
            "        float dist = length(d);\n" +
            "        float att = clamp(1.0 - dist / max(lc.a, 0.001), 0.0, 1.0);\n" +
            "        l += lc.rgb * max(dot(n, d / max(dist, 0.0001)), 0.0) * att;\n" +
            "      }\n" +
            "    }\n" +
            "    vLight = min(l, vec3(1.5));\n" +
            "  } else vLight = vec3(1.0);\n" +
            "  gl_Position = uMVP * wp;\n" +
            "}\n";

    static final String F3D =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uColor;\n" +
            "uniform float uFog;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform vec2 uFogRange;\n" +
            "varying vec2 vUV;\n" +
            "varying vec3 vLight;\n" +
            "varying float vDist;\n" +
            "void main() {\n" +
            "  vec4 c = texture2D(uTex, vUV) * uColor;\n" +
            "  if (c.a < 0.1) discard;\n" +
            "  vec3 rgb = c.rgb * vLight;\n" +
            "  if (uFog > 0.5) {\n" +
            "    float f = clamp((vDist - uFogRange.x) / max(uFogRange.y - uFogRange.x, 0.001), 0.0, 1.0);\n" +
            "    rgb = mix(rgb, uFogColor, f);\n" +
            "  }\n" +
            "  gl_FragColor = vec4(rgb, c.a);\n" +
            "}\n";

    static int program(String vs, String fs) {
        int v = shader(GLES20.GL_VERTEX_SHADER, vs);
        int f = shader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            Log.e("DroidMaker", "Link failed: " + log);
            throw new RuntimeException("Shader link failed: " + log);
        }
        return p;
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            Log.e("DroidMaker", "Shader compile failed: " + log + "\n" + src);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }
}
