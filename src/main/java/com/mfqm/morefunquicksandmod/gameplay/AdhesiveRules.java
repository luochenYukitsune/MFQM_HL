package com.mfqm.morefunquicksandmod.gameplay;

import java.util.List;

/** Pure adhesion physics: no Minecraft objects or global configuration. */
public final class AdhesiveRules {
    public static final int MAX_BONDS = 4;
    public static final double MAX_TOTAL_FORCE = .045;
    private static final Profile GLUE = new Profile(2.5, .032, .2);
    private static final Profile BOARD = new Profile(1.1, .04, .12);
    private static final Profile TAR = new Profile(1.2, .028, .2);
    private static final Profile HONEY = new Profile(1.8, .018, .2);
    private static final Profile SLIME = new Profile(1.8, .022, .2);
    private static final Profile MUD = new Profile(.8, .025, .12);

    /** Distances are blocks; stiffness adds velocity per tick for each block of extension. */
    public record Profile(double maxDistance, double stiffness, double restLength) {}

    public record Point(double x, double y, double z) {
        public static final Point ZERO = new Point(0, 0, 0);
        public double length() { return Math.hypot(Math.hypot(x, y), z); }
        public boolean isFinite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }

    /** Strength is clamped to one; zero strength or an invalidated anchor releases the connection. */
    public record Bond(Point anchor, Point foot, Profile profile, double strength, boolean valid) {
        public Bond(Point anchor, Point foot, Profile profile, double strength) {
            this(anchor, foot, profile, strength, true);
        }
    }

    public record Result(Point force, int activeBonds, int brokenBonds) {}

    /** Explicit bare material IDs: dry sand and water-like liquid mire never form adhesive strands. */
    public static Profile profileFor(String id) {
        if (id == null) return null;
        return switch (id) {
            case "glue" -> GLUE;
            case "sticky_board" -> BOARD;
            case "tar" -> TAR;
            case "honey" -> HONEY;
            case "sinking_slime", "mucus" -> SLIME;
            case "mud", "bog", "morass", "mire", "moor", "wet_peat", "brown_clay", "sinking_clay", "slurry" ->
                    MUD;
            default -> null;
        };
    }
    public static Result evaluate(List<Bond> bonds) { return evaluate(bonds, MAX_TOTAL_FORCE); }

    /**
     * Keeps the first four valid connections in list order, counting invalid and surplus entries as broken.
     * The caller owns anchor existence, lifetime and dimension checks and supplies them through Bond.valid.
     * The force points from feet toward anchors; summation precedes the norm cap so opposite pulls cancel.
     * A configured ceiling may reduce the hard limit but cannot increase it. Evaluation never mutates bonds.
     */
    public static Result evaluate(List<Bond> bonds, double maxTotalForce) {
        if (!Double.isFinite(maxTotalForce) || maxTotalForce < 0) {
            throw new IllegalArgumentException("Force ceiling must be finite and nonnegative");
        }
        if (bonds == null || bonds.isEmpty()) return new Result(Point.ZERO, 0, 0);
        int active = 0;
        int broken = 0;
        Point[] forces = new Point[MAX_BONDS];
        double largestComponent = 0;
        for (Bond bond : bonds) {
            Point force = forceFor(bond);
            if (force == null || active == MAX_BONDS) {
                broken++;
                continue;
            }
            forces[active++] = force;
            largestComponent = Math.max(largestComponent,
                    Math.max(Math.abs(force.x()), Math.max(Math.abs(force.y()), Math.abs(force.z()))));
        }
        double ceiling = Math.min(MAX_TOTAL_FORCE, maxTotalForce);
        return new Result(cappedSum(forces, active, largestComponent, ceiling), active, broken);
    }

    private static Point cappedSum(Point[] forces, int count, double largestComponent, double ceiling) {
        if (largestComponent == 0 || ceiling == 0) return Point.ZERO;
        // Scale before adding: even four enormous but finite forces cannot overflow into NaN velocity.
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < count; i++) {
            x += forces[i].x() / largestComponent;
            y += forces[i].y() / largestComponent;
            z += forces[i].z() / largestComponent;
        }
        double length = Math.hypot(Math.hypot(x, y), z);
        if (length == 0) return Point.ZERO;
        if (length > ceiling / largestComponent) {
            return new Point(x / length * ceiling, y / length * ceiling, z / length * ceiling);
        }
        return new Point(x * largestComponent, y * largestComponent, z * largestComponent);
    }

    private static Point forceFor(Bond bond) {
        if (bond == null || !bond.valid() || bond.anchor() == null || bond.foot() == null
                || !bond.anchor().isFinite() || !bond.foot().isFinite()
                || !Double.isFinite(bond.strength()) || bond.strength() <= 0 || !valid(bond.profile())) return null;
        double dx = bond.anchor().x() - bond.foot().x();
        double dy = bond.anchor().y() - bond.foot().y();
        double dz = bond.anchor().z() - bond.foot().z();
        Point offset = new Point(dx, dy, dz);
        double distance = offset.length();
        if (!offset.isFinite() || !Double.isFinite(distance) || distance > bond.profile().maxDistance()) return null;
        if (distance == 0) return Point.ZERO;
        double stretch = Math.max(0, distance - bond.profile().restLength());
        double magnitude = bond.profile().stiffness() * Math.min(1, bond.strength()) * stretch;
        if (!Double.isFinite(magnitude)) return null;
        return new Point(dx / distance * magnitude, dy / distance * magnitude, dz / distance * magnitude);
    }

    private static boolean valid(Profile profile) {
        return profile != null && Double.isFinite(profile.maxDistance()) && profile.maxDistance() > 0
                && Double.isFinite(profile.stiffness()) && profile.stiffness() >= 0
                && Double.isFinite(profile.restLength()) && profile.restLength() >= 0
                && profile.restLength() <= profile.maxDistance();
    }
    private AdhesiveRules() {}
}
