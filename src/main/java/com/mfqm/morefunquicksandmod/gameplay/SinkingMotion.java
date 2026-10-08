package com.mfqm.morefunquicksandmod.gameplay;

/** Pure motion model. Positive downwardSpeed means sinking, negative means buoyancy. */
public final class SinkingMotion {
    public record Profile(double stillSink, double struggleSink, double shallowMobility,
                          double bootLimit, boolean acceptsBuoyancy, double momentumRetention) {
        public Profile(double stillSink, double struggleSink, double shallowMobility,
                       double bootLimit, boolean acceptsBuoyancy) {
            this(stillSink, struggleSink, shallowMobility, bootLimit, acceptsBuoyancy, .71);
        }
    }
    public record Result(double downwardSpeed, double horizontalScale, double momentumRetention) {}

    /** Local prediction only: a repeated network snapshot cannot replay an old sinking cost. */
    public static final class SinkLedger {
        private double consumed;
        public SinkLedger(double initialTotal) { discard(initialTotal); }
        public void discard(double total) {
            if(!Double.isFinite(total) || total<0)throw new IllegalArgumentException("Invalid sinking total");
            consumed=total;
        }
        public double consume(double total) {
            if(!Double.isFinite(total) || total<0)throw new IllegalArgumentException("Invalid sinking total");
            if(total<consumed){consumed=total;return 0;}
            double amount=Math.min(.12,total-consumed);consumed+=amount;return amount;
        }
    }

    private SinkingMotion() {}

    public static Result calculate(Profile material, double depth, double movement, boolean jumping,
                                   boolean sneaking, double load, boolean wadingBoots, boolean lifeJacket) {
        return calculate(material, null, 1.62, depth, movement, jumping, sneaking, load, wadingBoots, lifeJacket);
    }

    public static Result calculate(Profile material, LegacyResistance.Profile resistance, double referenceHeight,
                                   double depth, double movement, boolean jumping,
                                   boolean sneaking, double load, boolean wadingBoots, boolean lifeJacket) {
        depth = Math.max(0, depth);
        double struggle = Math.min(1, movement * 4) + (jumping ? 1 : 0);
        double sink = material.stillSink + material.struggleSink * struggle
                + Math.min(4, Math.max(0, load)) * 0.009;
        if (sneaking && !jumping) sink *= 0.6;
        if (wadingBoots && depth < material.bootLimit) sink -= 0.035;
        if (lifeJacket && !sneaking && material.acceptsBuoyancy) sink -= 0.026;
        double horizontal = material.shallowMobility / (1 + depth * depth * 1.6);
        double retention = material.momentumRetention;
        if (resistance != null) {
            // The legacy curve is an additional depth limit, not permission for
            // shallow tar/honey to regain ordinary walking acceleration.
            horizontal = Math.min(horizontal, resistance.mobility(depth, referenceHeight));
            retention = resistance.momentumRetention();
            // Old suction grew as a body rose above the surface; holding jump cannot bypass it.
            if (jumping) sink += .025 * (1 + Math.min(.75, Math.max(0, referenceHeight - depth)));
        }
        if (sneaking && !jumping) horizontal *= 1.2;
        return new Result(Math.clamp(sink, -0.045, 0.12), Math.clamp(horizontal, 0.005, 1), retention);
    }

    public static int nextAir(int air, boolean eyeImmersion) {
        return eyeImmersion ? Math.max(-1, air - 1) : Math.min(300, air + 5);
    }

    public static int coatingLevel(double depth, double height) {
        if (depth <= 0) return 0;
        // Each atlas tier ends at its declared height. Rounding up would paint
        // a dry torso/head before the actor actually reached that surface.
        return Math.clamp((int) Math.floor(depth / Math.max(0.01, height) * 10 + 1e-6), 1, 10);
    }
}
