package com.mfqm.morefunquicksandmod.gameplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Standalone Java 21 behavioral tests; no Minecraft classes or game bootstrap are required. */
public final class AdhesiveRulesTest {
    private static int checks;

    public static void main(String[] args) {
        materialProfiles();
        distanceAndDirection();
        capAndCancellation();
        lifecycleAndLimits();
        invalidAndExtremeInputs();
        System.out.println("AdhesiveRules: " + checks + " behavioral checks passed");
    }

    private static void materialProfiles() {
        close(profile("glue").maxDistance(), 2.5, "glue stretches farthest");
        close(profile("honey").maxDistance(), 1.8, "honey distance");
        close(profile("tar").maxDistance(), 1.2, "tar distance");
        for (String id : List.of("sinking_slime", "mucus")) {
            close(profile(id).maxDistance(), 1.8, "slime distance: " + id);
        }
        for (String id : List.of("mud", "bog", "morass", "mire", "moor", "wet_peat",
                "brown_clay", "sinking_clay", "slurry")) {
            close(profile(id).maxDistance(), .8, "short viscous mud connection: " + id);
        }
        check(profile("glue").stiffness() > profile("tar").stiffness()
                        && profile("tar").stiffness() > profile("honey").stiffness(),
                "equal stretch pulls glue harder than tar, and tar harder than honey");
        check(profile("sticky_board").stiffness() > profile("glue").stiffness(),
                "fresh board initially pulls harder than glue");
        close(profile("sticky_board").maxDistance(), 1.1, "board has a shorter local trapping radius");
        for (String id : List.of("dry_quicksand", "quicksand", "jungle_quicksand",
                "soft_quicksand", "liquid_mire", "stable_liquid_mire", "acid", "unknown")) {
            check(AdhesiveRules.profileFor(id) == null, "no strands for non-adhesive medium: " + id);
        }
        check(AdhesiveRules.profileFor(null) == null, "absent material has no profile");
    }

    private static void distanceAndDirection() {
        var glue = profile("glue");
        var atLimit = AdhesiveRules.evaluate(List.of(bond(2.5, glue)));
        check(atLimit.activeBonds() == 1 && atLimit.brokenBonds() == 0, "exact maximum distance remains attached");
        var pastLimit = AdhesiveRules.evaluate(List.of(bond(Math.nextUp(2.5), glue)));
        check(pastLimit.activeBonds() == 0 && pastLimit.brokenBonds() == 1, "distance just beyond maximum breaks");
        close(pastLimit.force().length(), 0, "broken connection cannot pull");
        var zero = AdhesiveRules.evaluate(List.of(bond(0, glue)));
        check(zero.activeBonds() == 1 && zero.brokenBonds() == 0, "zero-length connection remains attached");
        close(zero.force().length(), 0, "zero length creates no divide-by-zero force");
        close(AdhesiveRules.evaluate(List.of(bond(glue.restLength(), glue))).force().length(), 0,
                "slack connection exerts no compression");
        close(AdhesiveRules.evaluate(List.of(bond(glue.restLength() / 2, glue))).force().length(), 0,
                "shorter than rest length does not push feet away");
        var noSpring = AdhesiveRules.evaluate(List.of(bond(1, new AdhesiveRules.Profile(2, 0, .2))));
        check(noSpring.activeBonds() == 1 && noSpring.force().length() == 0,
                "zero stiffness permits an attached visual connection without force");
        var pull = AdhesiveRules.evaluate(List.of(bond(1, glue)));
        close(pull.force().x(), -glue.stiffness() * (1 - glue.restLength()), "pull points from foot toward anchor");
        close(pull.force().y(), 0, "horizontal attachment has no vertical pull");
        close(pull.force().z(), 0, "horizontal attachment has no lateral pull");
        var diagonal = new AdhesiveRules.Bond(new AdhesiveRules.Point(1, 2, 3),
                new AdhesiveRules.Point(1.6, 2.8, 3), glue, 1);
        var diagonalForce = AdhesiveRules.evaluate(List.of(diagonal)).force();
        close(diagonalForce.x(), -.6 * glue.stiffness() * (1 - glue.restLength()), "diagonal pull x");
        close(diagonalForce.y(), -.8 * glue.stiffness() * (1 - glue.restLength()), "diagonal pull y");
        close(AdhesiveRules.evaluate(List.of(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO,
                        new AdhesiveRules.Point(1, 0, 0), glue, .5))).force().length(),
                pull.force().length() / 2, "weakened connection has proportionally less pull");
        close(AdhesiveRules.evaluate(List.of(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO,
                        new AdhesiveRules.Point(1, 0, 0), glue, 20))).force().length(),
                pull.force().length(), "strength cannot amplify beyond the material profile");
    }

    private static void capAndCancellation() {
        var profile = new AdhesiveRules.Profile(3, .1, 0);
        var four = List.of(bond(2, profile), bond(2, profile), bond(2, profile), bond(2, profile));
        var result = AdhesiveRules.evaluate(four);
        close(result.force().length(), .045, "four anchors obey total force ceiling");
        check(result.activeBonds() == 4, "four legitimate anchors retained");
        close(AdhesiveRules.evaluate(four, .01).force().length(), .01, "lower configured ceiling honored");
        close(AdhesiveRules.evaluate(four, 10).force().length(), .045, "configuration cannot bypass safety ceiling");
        var disabledForce = AdhesiveRules.evaluate(four, 0);
        close(disabledForce.force().length(), 0, "zero ceiling disables force");
        check(disabledForce.activeBonds() == 4, "zero ceiling preserves visual connections");
        var opposite = AdhesiveRules.evaluate(List.of(bond(2, profile), bond(-2, profile)));
        close(opposite.force().length(), 0, "opposite anchors cancel before total force cap");
        var asymmetric = AdhesiveRules.evaluate(List.of(bond(2, profile), bond(-1, profile)));
        close(asymmetric.force().x(), -.045, "cap applies to resultant, not independently to every anchor");
        var diagonal = AdhesiveRules.evaluate(List.of(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO,
                new AdhesiveRules.Point(1, 1, 1), profile, 1)));
        close(diagonal.force().length(), .045, "diagonal force is capped by norm, not separately per axis");
        close(diagonal.force().x(), -.045 / Math.sqrt(3), "force cap preserves direction");
    }

    private static void lifecycleAndLimits() {
        var glue = profile("glue");
        var bonds = new ArrayList<AdhesiveRules.Bond>();
        bonds.add(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO, new AdhesiveRules.Point(1, 0, 0), glue, 1, false));
        for (int i = 0; i < 6; i++) bonds.add(bond(1, glue));
        var result = AdhesiveRules.evaluate(bonds);
        check(result.activeBonds() == 4 && result.brokenBonds() == 3,
                "invalid anchor does not consume a slot; extra live anchors are dropped");
        var ordered = new ArrayList<AdhesiveRules.Bond>();
        for (int i = 0; i < 4; i++) ordered.add(bond(.3, glue));
        ordered.add(bond(-2, glue));
        close(AdhesiveRules.evaluate(ordered).force().x(), -.0128,
                "surplus anchor cannot change the force of the four accepted anchors");
        check(bonds.size() == 7, "pure evaluation does not mutate caller list");
        var invalid = AdhesiveRules.evaluate(List.of(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO,
                new AdhesiveRules.Point(1, 0, 0), glue, 1, false)));
        check(invalid.activeBonds() == 0 && invalid.brokenBonds() == 1, "removed anchor immediately breaks");
        close(invalid.force().length(), 0, "invalid anchor cannot pull");
        for (double strength : new double[]{0, -1}) {
            var weak = AdhesiveRules.evaluate(List.of(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO,
                    new AdhesiveRules.Point(1, 0, 0), glue, strength)));
            check(weak.activeBonds() == 0 && weak.brokenBonds() == 1, "depleted or negative bond strength releases");
        }
        var empty = AdhesiveRules.evaluate(List.of());
        check(empty.activeBonds() == 0 && empty.brokenBonds() == 0, "empty connection list has no lifecycle work");
        close(empty.force().length(), 0, "empty list has no pull");
        var absent = AdhesiveRules.evaluate(null);
        check(absent.activeBonds() == 0 && absent.brokenBonds() == 0, "absent connection list is empty");
    }

    private static void invalidAndExtremeInputs() {
        var glue = profile("glue");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            rejected(new AdhesiveRules.Bond(new AdhesiveRules.Point(bad, 0, 0), AdhesiveRules.Point.ZERO, glue, 1),
                    "invalid anchor coordinates");
            rejected(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO, new AdhesiveRules.Point(0, bad, 0), glue, 1),
                    "invalid foot coordinates");
            rejected(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO, AdhesiveRules.Point.ZERO, glue, bad),
                    "invalid bond strength");
            rejected(bond(1, new AdhesiveRules.Profile(bad, .03, .2)), "invalid break distance");
            rejected(bond(1, new AdhesiveRules.Profile(2, bad, .2)), "invalid stiffness");
            rejected(bond(1, new AdhesiveRules.Profile(2, .03, bad)), "invalid rest length");
            rejectsCap(bad);
        }
        for (AdhesiveRules.Profile invalid : List.of(new AdhesiveRules.Profile(0, .03, .2),
                new AdhesiveRules.Profile(-1, .03, .2), new AdhesiveRules.Profile(2, -.03, .2),
                new AdhesiveRules.Profile(2, .03, -1), new AdhesiveRules.Profile(2, .03, 3))) {
            rejected(bond(1, invalid), "invalid physical profile");
        }
        rejected(null, "null bond");
        rejected(new AdhesiveRules.Bond(null, AdhesiveRules.Point.ZERO, glue, 1), "null anchor");
        rejected(new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO, null, glue, 1), "null foot");
        rejected(bond(1, null), "null profile");
        rejectsCap(-.01);
        var overflowDelta = new AdhesiveRules.Bond(new AdhesiveRules.Point(-Double.MAX_VALUE, 0, 0),
                new AdhesiveRules.Point(Double.MAX_VALUE, 0, 0), glue, 1);
        rejected(overflowDelta, "unrepresentable finite coordinate difference");
        var overflowSpring = new AdhesiveRules.Profile(Double.MAX_VALUE, Double.MAX_VALUE, 0);
        rejected(bond(2, overflowSpring), "unrepresentable spring force rejected");
        var large = new AdhesiveRules.Profile(2, Double.MAX_VALUE / 2, 0);
        var extreme = AdhesiveRules.evaluate(List.of(bond(1, large), bond(1, large), bond(1, large), bond(1, large)));
        check(extreme.activeBonds() == 4 && extreme.force().isFinite(), "finite extreme forces do not overflow when summed");
        close(extreme.force().length(), .045, "extreme sum still obeys ceiling");
        var cancellingExtreme = AdhesiveRules.evaluate(List.of(bond(1, large), bond(1, large),
                bond(-1, large), bond(-1, large)));
        check(cancellingExtreme.activeBonds() == 4 && cancellingExtreme.force().isFinite(),
                "extreme opposing forces retain active bonds without overflow");
        close(cancellingExtreme.force().length(), 0, "extreme opposing forces cancel correctly");
        var hugePosition = new AdhesiveRules.Bond(new AdhesiveRules.Point(1e300, 1e300, 1e300),
                new AdhesiveRules.Point(1e300, 1e300, 1e300), glue, 1);
        var hugeZero = AdhesiveRules.evaluate(List.of(hugePosition));
        check(hugeZero.activeBonds() == 1 && hugeZero.force().isFinite(), "large coincident finite world positions are safe");
        close(hugeZero.force().length(), 0, "large coincident coordinates produce no force");
        var tiny = new AdhesiveRules.Profile(1, .03, 0);
        var tinyResult = AdhesiveRules.evaluate(List.of(bond(Double.MIN_VALUE, tiny)));
        check(tinyResult.activeBonds() == 1 && tinyResult.force().isFinite(), "subnormal separation remains finite");
    }

    private static AdhesiveRules.Profile profile(String id) {
        var profile = AdhesiveRules.profileFor(id);
        check(profile != null, "missing material profile: " + id);
        return profile;
    }

    private static AdhesiveRules.Bond bond(double footX, AdhesiveRules.Profile profile) {
        return new AdhesiveRules.Bond(AdhesiveRules.Point.ZERO, new AdhesiveRules.Point(footX, 0, 0), profile, 1);
    }

    private static void rejected(AdhesiveRules.Bond bond, String description) {
        var result = AdhesiveRules.evaluate(Arrays.asList(bond));
        check(result.activeBonds() == 0 && result.brokenBonds() == 1, description + " breaks connection");
        check(result.force().isFinite(), description + " cannot return NaN or infinity");
        close(result.force().length(), 0, description + " has no force");
        var mixed = AdhesiveRules.evaluate(Arrays.asList(bond, bond(1, profile("glue"))));
        check(mixed.activeBonds() == 1 && mixed.brokenBonds() == 1 && mixed.force().isFinite(),
                description + " does not poison a valid connection");
    }

    private static void rejectsCap(double cap) {
        try {
            AdhesiveRules.evaluate(List.of(), cap);
            throw new AssertionError("invalid force ceiling was accepted: " + cap);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void close(double actual, double expected, String description) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-12,
                description + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}
