package com.tacz.legacy.util.math;

/**
 * Port of upstream TACZ SecondOrderDynamics — a critically-damped spring system
 * that smoothly follows a target value.
 * <p>
 * Unlike upstream which runs on a background thread at ~6ms intervals, this
 * version is updated explicitly each render frame via {@link #update(float, float)}.
 */
public class SecondOrderDynamics {
    private final float k1;
    private final float k2;
    private final float k3;

    private float py;
    private float pyd;
    private float px;

    /**
     * @param f  Natural frequency
     * @param z  Damping coefficient
     * @param r  Initial velocity
     * @param x0 Initial position
     */
    public SecondOrderDynamics(float f, float z, float r, float x0) {
        k1 = (float) (z / (Math.PI * f));
        k2 = (float) (1 / ((2 * Math.PI * f) * (2 * Math.PI * f)));
        k3 = (float) (r * z / (2 * Math.PI * f));

        py = px = x0;
        pyd = 0;
    }

    /**
     * Step the dynamics with an explicit time delta.
     *
     * @param x  target value
     * @param dt time step in seconds (e.g. 0.05 for one tick, or render-frame delta)
     * @return smoothed output
     */
    public float update(float x, float dt) {
        if (Float.isNaN(py)) py = 0;
        if (Float.isNaN(pyd)) pyd = 0;
        if (dt <= 0) dt = 0.001f;

        float xd = (x - px) / dt;
        float y = py + dt * pyd;
        pyd = pyd + dt * (px + k3 * xd - py - k1 * pyd) / k2;
        px = x;
        py = y;
        return py + dt * pyd;
    }

    /**
     * Convenience: update with a fixed dt matching upstream's ~6ms background thread
     * but called at render frequency. Uses the upstream default 0.05 step.
     */
    public float update(float x) {
        return update(x, 0.05f);
    }

    public float get() {
        if (Float.isNaN(py)) py = 0;
        if (Float.isNaN(pyd)) pyd = 0;
        return py + 0.05f * pyd;
    }
}
