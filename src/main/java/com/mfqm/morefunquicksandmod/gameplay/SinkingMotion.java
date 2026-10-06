package com.mfqm.morefunquicksandmod.gameplay;

/** Pure motion model. Positive downwardSpeed means sinking, negative means buoyancy. */
public final class SinkingMotion {
    public record Profile(double stillSink, double struggleSink, double shallowMobility,
                          double bootLimit, boolean acceptsBuoyancy) {}
    public record Result(double downwardSpeed, double horizontalScale) {}

    private SinkingMotion() {}

    public static Result calculate(Profile material, double depth, double movement, boolean jumping,
                                   boolean sneaking, double load, boolean wadingBoots, boolean lifeJacket) {
        depth = Math.max(0, depth);
        double struggle = Math.min(1, movement * 4) + (jumping ? 1 : 0);
        double sink = material.stillSink + material.struggleSink * struggle
                + Math.min(4, Math.max(0, load)) * 0.009;
        if (sneaking && !jumping) sink *= 0.6;
        if (wadingBoots && depth < material.bootLimit) sink -= 0.035;
        if (lifeJacket && !sneaking && material.acceptsBuoyancy) sink -= 0.026;
        double horizontal = material.shallowMobility / (1 + depth * depth * 1.6);
        if (sneaking && !jumping) horizontal *= 1.2;
        return new Result(Math.clamp(sink, -0.045, 0.12), Math.clamp(horizontal, 0.005, 0.65));
    }

    public static int nextAir(int air, boolean eyeImmersion) {
        return eyeImmersion ? Math.max(-1, air - 1) : Math.min(300, air + 5);
    }

    public static int coatingLevel(double depth, double height) {
        if (depth <= 0) return 0;
        return Math.clamp((int) Math.ceil(depth / Math.max(0.01, height) * 10), 0, 10);
    }
}
