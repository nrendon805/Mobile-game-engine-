package com.droidmaker.core.runtime;

/**
 * 2D drawing backend. Coordinates are in the current space (room space inside a view, display
 * space for the GUI layer). Colors are 0xRRGGBB; alpha is 0..1.
 */
public interface Gfx {
    /** Draw a sprite frame with its origin at (x, y). */
    void sprite(int spr, int frame, double x, double y, double xs, double ys, double angle, int color, double alpha);

    /** Draw part of a sprite frame; (x, y) is the top-left corner. */
    void spritePart(int spr, int frame, int left, int top, int w, int h, double x, double y, double xs, double ys, int color, double alpha);

    /** Draw a sprite frame stretched into a rectangle with 4 corner colors. */
    void spriteGeneral(int spr, int frame, double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4, int color, double alpha);

    void background(int bg, double x, double y, double xs, double ys, double angle, int color, double alpha);

    void backgroundPart(int bg, int left, int top, int w, int h, double x, double y, double xs, double ys, int color, double alpha);

    void rect(double x1, double y1, double x2, double y2, int c1, int c2, int c3, int c4, double alpha, boolean outline);

    void line(double x1, double y1, double x2, double y2, double width, int c1, int c2, double alpha);

    void triangle(double x1, double y1, double x2, double y2, double x3, double y3, int c1, int c2, int c3, double alpha, boolean outline);

    void ellipse(double x1, double y1, double x2, double y2, int cCenter, int cEdge, double alpha, boolean outline, int segments);

    void point(double x, double y, int color, double alpha);

    void text(double x, double y, String s, double size, boolean bold, int halign, int valign,
              double xs, double ys, double angle, int color, double alpha, double sep, double width);

    double textWidth(String s, double size, boolean bold, double width);

    double textHeight(String s, double size, boolean bold, double sep, double width);

    /** Fill the current view port with a color. */
    void clear(int color, double alpha);

    /** 0 = normal, 1 = additive, 2 = subtract, 3 = max */
    void blendMode(int mode);
}
