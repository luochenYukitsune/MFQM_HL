package com.mfqm.morefunquicksandmod.gameplay;

/** Legacy mr_kof curves and CustomPotionEvent speed formula, without old client-only position rewinds. */
public final class LegacyResistance {
    public record Profile(double coefficient, double exponent, int cap, double momentumRetention) {
        public double mobility(double depth, double referenceHeight) {
            // 1.7 player posY included yOffset; modern entity Y is the feet position.
            double clearance = Math.max(0, referenceHeight - Math.max(0, depth));
            double power = Math.max(0, 1.5 - clearance);
            double level = Math.min(cap, 5 + Math.floor(Math.pow(coefficient * power, exponent)));
            double severity = Math.max(0, level - 5);
            return Math.max(.02, 1 / Math.pow(1 + severity * 1.5 / 50, 2));
        }
    }

    public static Profile forMaterial(String id, int variant) {
        return switch (id) {
            case "mud" -> p(4, 1.5, 145, .71);
            case "bog", "sinky_liquid" -> p(10, 1.5, 100, 0);
            case "soft_snow" -> p(10, 1.5, 60, 0);
            case "dry_quicksand" -> p(20, 1.5, 128, 0);
            case "soft_quicksand" -> p(12.5, 1.75, 175, 0);
            case "morass" -> p(2 * (1 + variant / 3), 1.75, 145, 0);
            case "wet_peat" -> p(8, 2, 75, 0);
            case "brown_clay" -> p(20, 1.5, 128, 0);
            case "wax" -> p(1 + variant / 2.0, 2, 175, 0);
            case "quicksand", "sinking_rug" -> p(100, 1, 145, 0);
            case "jungle_quicksand" -> p(50, 1.25, 135, 0);
            case "sinking_slime" -> p(10, 2, 175, 0);
            case "mucus" -> p(5, 2.5, 150, 0);
            case "mire" -> p(3 * (1 + Math.min(variant * 2, 12)), 1.6, 145, 0);
            case "sinking_clay" -> p(2 * Math.max(1, 1 + (Math.min(variant, 12) - 4) / 2), 1.75, 135, 0);
            case "tangleroot_moss" -> p(25, 1.75, 255, 0);
            case "dense_web" -> p(50, 1.5, 255, 0);
            case "tar" -> p(20, 2.25, 255, 0);
            case "larvae" -> p(100, 1, 128, 0);
            case "corrupted_sand" -> p(5, 1.85, 132, 0);
            case "swallowing_flesh" -> p(100, 1, 135, 0);
            case "slurry" -> p(10, 1.5, 50, 0);
            case "soft_gravel" -> p(150, 1, 125, 0);
            case "honey" -> p(25, 1.5, 140, 0);
            case "liquid_chocolate" -> p(56.25, 1.25, 135, 0);
            // These legacy classes use different mechanics; retain their dedicated modern profile.
            default -> null;
        };
    }

    private static Profile p(double coefficient, double exponent, int cap, double retention) {
        return new Profile(coefficient, exponent, cap, retention);
    }

    private LegacyResistance() {}
}
