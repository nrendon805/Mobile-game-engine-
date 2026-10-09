package com.droidmaker.core.runtime;

/**
 * Frame-level rendering backend. The game calls these to set up each pass, then issues
 * {@link Gfx} and {@link Gfx3D} draw calls. Port rectangles are in display (game) units.
 */
public interface Screen extends Gfx, Gfx3D {
    /** 2D pass showing the view rectangle (room coords) in the port rectangle. */
    void beginView2D(double vx, double vy, double vw, double vh, double angle,
                     double px, double py, double pw, double ph);

    /** 3D pass into the given port; matrices are set through {@link Gfx3D#matrices}. */
    void begin3D(double px, double py, double pw, double ph, int clearColor);

    void end3D();

    /** Shows a software-rendered raycast image in the port. */
    void raycastImage(int[] pixels, int w, int h, double px, double py, double pw, double ph);

    /** GUI pass in display coordinates (0..w, 0..h). */
    void beginGui(double w, double h);
}
