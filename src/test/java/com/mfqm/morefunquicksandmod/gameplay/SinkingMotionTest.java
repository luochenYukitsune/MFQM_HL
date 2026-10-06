package com.mfqm.morefunquicksandmod.gameplay;

/** Standalone Java 21 behavioral checks: run with tools/verify_sinking_motion.ps1. */
public final class SinkingMotionTest {
    public static void main(String[] args) {
        var sand = new SinkingMotion.Profile(0.012, 0.028, 0.12, 1.25, true);
        var quiet = SinkingMotion.calculate(sand, 0.6, 0, false, false, 0, false, false);
        var struggling = SinkingMotion.calculate(sand, 0.6, 0.2, true, false, 0, false, false);
        check(struggling.downwardSpeed() > quiet.downwardSpeed(), "struggling increases suction");
        var heavy = SinkingMotion.calculate(sand, 0.6, 0, false, false, 2, false, false);
        check(heavy.downwardSpeed() > quiet.downwardSpeed(), "heavy inventory increases sinking");
        var deep = SinkingMotion.calculate(sand, 1.7, 0.1, false, false, 0, false, false);
        check(deep.horizontalScale() < quiet.horizontalScale(), "deep immersion restricts horizontal movement");
        var boots = SinkingMotion.calculate(sand, 0.2, 0, false, false, 0, true, false);
        check(boots.downwardSpeed() <= 0, "wading boots support shallow immersion");
        var deepBoots = SinkingMotion.calculate(sand, 1.6, 0, false, false, 0, true, false);
        check(deepBoots.downwardSpeed() > 0, "boots cannot support deep immersion");
        var floating = SinkingMotion.calculate(sand, 1.1, 0, false, false, 0, false, true);
        check(floating.downwardSpeed() < quiet.downwardSpeed(), "life jacket adds buoyancy");
        var diving = SinkingMotion.calculate(sand, 1.1, 0, false, true, 0, false, true);
        check(floating.downwardSpeed() < 0 && diving.downwardSpeed() > 0, "crouching pauses life jacket buoyancy to dive");
        check(SinkingMotion.calculate(sand, 1.1, 0, false, false, 0, false, true).downwardSpeed() < 0, "releasing crouch restores buoyancy");
        check(SinkingMotion.nextAir(300, true) == 299, "eye immersion spends one air tick");
        check(SinkingMotion.nextAir(-1, true) == -1, "air stops at the damage sentinel");
        check(SinkingMotion.nextAir(298, false) == 300, "air recovery is capped");
        check(SinkingMotion.nextAir(-1, false) == 4, "surfacing restores five air");
        check(SinkingMotion.coatingLevel(0, 1.8) == 0, "dry body has no coating");
        check(SinkingMotion.coatingLevel(2, 1.8) == 10, "fully immersed body has complete coating");
        System.out.println("SinkingMotion: 14 behavioral checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
