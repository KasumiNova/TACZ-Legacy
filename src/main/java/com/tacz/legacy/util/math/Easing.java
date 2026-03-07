package com.tacz.legacy.util.math;

/**
 * Port of upstream TACZ Easing — easing functions for procedural animations.
 */
public class Easing {
    public static double easeOutCubic(double x) {
        return 1 - Math.pow(1 - x, 3);
    }
}
