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
        check(SinkingMotion.coatingLevel(.65, 1.8) == 3, "leg-only immersion never advances to the torso tier");
        check(SinkingMotion.coatingLevel(.73, 1.8) == 4, "torso tier starts only after its required height");
        check(SinkingMotion.coatingLevel(.08, 1.8) == 1, "very shallow contact leaves ankle residue");
        check(SinkingMotion.coatingLevel(1.42, 1.8) == 7, "head tier does not round immersion upward");
        for(int step=1;step<=10;step++) {
            double depth=1.8*step/10;
            check(SinkingMotion.coatingLevel(depth,1.8)==step,"exact height threshold " + step);
            if(step>1)check(SinkingMotion.coatingLevel(depth-.001,1.8)==step-1,"below threshold stays on lower tier " + step);
        }
        var legacySand = LegacyResistance.forMaterial("quicksand", 0);
        var mud = LegacyResistance.forMaterial("mud", 3);
        var tar = LegacyResistance.forMaterial("tar", 0);
        close(legacySand.mobility(.62, 1.62), 1 / Math.pow(2.5, 2), "legacy sand reference speed at waist depth");
        close(mud.mobility(.62, 1.62), 1 / Math.pow(1.06, 2), "legacy mud reference speed at same depth");
        check(mud.mobility(.62, 1.62) > tar.mobility(.62, 1.62), "mud is less adhesive than tar");
        check(legacySand.mobility(1.6, 1.62) < legacySand.mobility(.3, 1.62), "sand resistance rises with immersion");
        close(tar.mobility(3, 1.62), .02, "fully stuck tar retains original two percent speed floor");
        check(LegacyResistance.forMaterial("mire", 6).mobility(.7, 1.62)
                < LegacyResistance.forMaterial("mire", 0).mobility(.7, 1.62), "wet mire is stickier than crust state");
        check(LegacyResistance.forMaterial("wax", 5).mobility(1, 1.62)
                < LegacyResistance.forMaterial("wax", 0).mobility(1, 1.62), "soft wax retains variant resistance");
        var calibrated = SinkingMotion.calculate(sand, legacySand, 1.62, .62, 0, false, false, 0, false, false);
        close(calibrated.horizontalScale(), Math.min(.16,.12/(1+.62*.62*1.6)), "depth curve cannot remove base medium viscosity");
        close(calibrated.momentumRetention(), 0, "quicksand stops drift independently of input speed");
        var mudMotion = SinkingMotion.calculate(sand, mud, 1.62, .62, 0, false, false, 0, false, false);
        close(mudMotion.momentumRetention(), .71, "mud retains legacy momentum damping");
        var jump = SinkingMotion.calculate(sand, legacySand, 1.62, .62, 0, true, false, 0, false, false);
        check(jump.downwardSpeed() > calibrated.downwardSpeed(), "jumping meets legacy suction");
        check(LegacyResistance.forMaterial("liquid_mire", 0) == null, "water mire keeps its distinct movement rules");
        for (String id : new String[]{"mud", "quicksand", "tar", "mucus", "honey", "soft_snow", "dense_web"}) {
            for (double depth : new double[]{0, .1, .6, 1.5, 8}) {
                double scale = LegacyResistance.forMaterial(id, 0).mobility(depth, 1.62);
                check(Double.isFinite(scale) && scale >= .02 && scale <= 1, "bounded legacy mobility: " + id);
            }
        }
        var shallowTar=SinkingMotion.calculate(new SinkingMotion.Profile(.014,.035,.035,.3,false),tar,1.62,.05,0,false,false,0,false,false);
        var shallowHoney=SinkingMotion.calculate(new SinkingMotion.Profile(.01,.028,.045,.45,true),LegacyResistance.forMaterial("honey",0),1.62,.05,0,false,false,0,false,false);
        var glue=SinkingMotion.calculate(new SinkingMotion.Profile(0,0,.03,0,false,0),.05,0,false,false,0,false,false);
        check(shallowTar.horizontalScale()<.036 && shallowHoney.horizontalScale()<.046,"ankle-deep sticky media retain their base viscosity");
        check(glue.horizontalScale()<shallowTar.horizontalScale() && shallowTar.horizontalScale()<shallowHoney.horizontalScale(),"shallow glue is strongest, then tar, then honey");
        close(glue.momentumRetention(),0,"glue cannot accumulate ordinary swimming momentum");
        var ledger=new SinkingMotion.SinkLedger(.5);
        close(ledger.consume(.5),0,"first synchronized snapshot does not replay episode history");
        close(ledger.consume(.525),.025,"new accepted cost is consumed once");
        close(ledger.consume(.525),0,"repeated snapshot does not repeat the press");
        close(ledger.consume(.55),.025,"a replacement attachment can still add its next cost");
        close(ledger.consume(0),0,"dimension or episode reset discards old cumulative motion");
        close(ledger.consume(.025),.025,"new world can consume a fresh cost");
        System.out.println("SinkingMotion: 35 motion groups, 35 resistance boundaries and 23 coating height checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message + ": " + actual + " != " + expected);
    }
}
