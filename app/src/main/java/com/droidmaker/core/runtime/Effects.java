package com.droidmaker.core.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Simple built-in particle effects (explosion, ring, smoke, spark, star, rain, snow ...). */
public final class Effects {
    public static final int EXPLOSION = 0, RING = 1, ELLIPSE = 2, FIREWORK = 3, SMOKE = 4, SPARK = 5, STAR = 6, RAIN = 7, SNOW = 8;

    private static final class P {
        double x, y, vx, vy, size, grow, life, maxLife, gravity, alpha;
        int color, shape; // 0 dot, 1 ring, 2 line (rain), 3 star
    }

    private final List<P> below = new ArrayList<>(), above = new ArrayList<>();
    private final Random rnd = new Random();

    public void clear() { below.clear(); above.clear(); }

    public void create(int kind, double x, double y, int size, int color, boolean onTop, double roomW, double roomH) {
        List<P> l = onTop ? above : below;
        double s = size == 0 ? 0.6 : size == 2 ? 1.7 : 1.0;
        switch (kind) {
            case EXPLOSION:
                for (int i = 0; i < 18 * s; i++) {
                    P p = burst(x, y, 1 + rnd.nextDouble() * 3 * s, color, 20 + rnd.nextInt(20));
                    p.size = 4 * s + rnd.nextDouble() * 6 * s; p.grow = 0.3 * s;
                    l.add(p);
                }
                break;
            case RING: case ELLIPSE: {
                P p = new P();
                p.x = x; p.y = y; p.size = 4; p.grow = 3 * s; p.life = p.maxLife = 22; p.color = color; p.shape = 1; p.alpha = 1;
                l.add(p);
                break;
            }
            case FIREWORK:
                for (int i = 0; i < 40 * s; i++) {
                    P p = burst(x, y, 2 + rnd.nextDouble() * 3 * s, color, 30 + rnd.nextInt(25));
                    p.size = 2.5 * s; p.gravity = 0.06;
                    l.add(p);
                }
                break;
            case SMOKE:
                for (int i = 0; i < 8 * s; i++) {
                    P p = burst(x, y, 0.4 * s, color, 40 + rnd.nextInt(30));
                    p.vy -= 0.6; p.size = 6 * s; p.grow = 0.25 * s; p.alpha = 0.6;
                    l.add(p);
                }
                break;
            case SPARK:
                for (int i = 0; i < 10 * s; i++) {
                    P p = burst(x, y, 2 + rnd.nextDouble() * 4 * s, color, 10 + rnd.nextInt(10));
                    p.size = 1.5 * s; p.shape = 2;
                    l.add(p);
                }
                break;
            case STAR: {
                P p = new P();
                p.x = x; p.y = y; p.size = 10 * s; p.grow = -0.2 * s; p.life = p.maxLife = 40; p.color = color; p.shape = 3; p.alpha = 1;
                l.add(p);
                break;
            }
            case RAIN:
                for (int i = 0; i < 6 * s; i++) {
                    P p = new P();
                    p.x = rnd.nextDouble() * roomW; p.y = -10; p.vx = -1; p.vy = 9 + rnd.nextDouble() * 4;
                    p.size = 1; p.life = p.maxLife = (int) (roomH / p.vy) + 4; p.color = color; p.shape = 2; p.alpha = 0.7;
                    l.add(p);
                }
                break;
            case SNOW:
                for (int i = 0; i < 4 * s; i++) {
                    P p = new P();
                    p.x = rnd.nextDouble() * roomW; p.y = -10; p.vx = rnd.nextDouble() - 0.5; p.vy = 0.8 + rnd.nextDouble();
                    p.size = 1.5 + rnd.nextDouble() * 2; p.life = p.maxLife = (int) (roomH / p.vy) + 10; p.color = color; p.alpha = 0.9;
                    l.add(p);
                }
                break;
        }
        while (l.size() > 3000) l.remove(0);
    }

    private P burst(double x, double y, double speed, int color, int life) {
        P p = new P();
        double a = rnd.nextDouble() * Math.PI * 2;
        p.x = x; p.y = y; p.vx = Math.cos(a) * speed; p.vy = Math.sin(a) * speed;
        p.life = p.maxLife = life; p.color = color; p.alpha = 1;
        return p;
    }

    public void step() {
        step(below);
        step(above);
    }

    private void step(List<P> l) {
        for (Iterator<P> it = l.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.x += p.vx; p.y += p.vy; p.vy += p.gravity;
            p.vx *= 0.97; if (p.gravity == 0 && p.shape != 2) p.vy *= 0.97;
            p.size = Math.max(0, p.size + p.grow);
            if (--p.life <= 0) it.remove();
        }
    }

    public void draw(Gfx g, boolean onTop) {
        for (P p : onTop ? above : below) {
            double a = p.alpha * Math.min(1, p.life / (p.maxLife * 0.5));
            switch (p.shape) {
                case 1:
                    g.ellipse(p.x - p.size, p.y - p.size, p.x + p.size, p.y + p.size, p.color, p.color, a, true, 32);
                    break;
                case 2:
                    g.line(p.x, p.y, p.x - p.vx * 1.5, p.y - p.vy * 1.5, p.size, p.color, p.color, a);
                    break;
                case 3: {
                    double r = p.size;
                    for (int k = 0; k < 5; k++) {
                        double a0 = Math.toRadians(-90 + k * 72), a1 = Math.toRadians(-90 + k * 72 + 36), a2 = Math.toRadians(-90 + k * 72 - 36);
                        g.triangle(p.x + Math.cos(a0) * r, p.y + Math.sin(a0) * r, p.x + Math.cos(a1) * r * 0.4, p.y + Math.sin(a1) * r * 0.4,
                                p.x + Math.cos(a2) * r * 0.4, p.y + Math.sin(a2) * r * 0.4, p.color, p.color, p.color, a, false);
                    }
                    g.ellipse(p.x - r * 0.42, p.y - r * 0.42, p.x + r * 0.42, p.y + r * 0.42, p.color, p.color, a, false, 10);
                    break;
                }
                default:
                    g.ellipse(p.x - p.size, p.y - p.size, p.x + p.size, p.y + p.size, p.color, p.color, a, false, 12);
            }
        }
    }
}
