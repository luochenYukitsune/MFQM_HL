package com.mfqm.morefunquicksandmod.gameplay;

import java.util.HashMap;
import java.util.Map;

/** Per-material gameplay profiles; decorative/structural blocks deliberately have no profile. */
public enum SinkingMaterial {
    MUD("mud", .007, .018, .32, .45, true, true),
    BOG("bog", .013, .018, .2, .65, true, true),
    SOFT_SNOW("soft_snow", .016, .008, .23, .5, false, true),
    DRY_QUICKSAND("dry_quicksand", .032, .014, .32, .6, false, true),
    SOFT_QUICKSAND("soft_quicksand", .018, .028, .14, .7, true, true),
    MORASS("morass", .007, .035, .15, .45, true, true),
    WET_PEAT("wet_peat", .007, .02, .3, .6, true, true),
    BROWN_CLAY("brown_clay", .009, .026, .16, .55, true, true),
    WAX("wax", .015, .022, .08, .3, false, true),
    QUICKSAND("quicksand", .012, .028, .12, 1.25, true, true),
    JUNGLE_QUICKSAND("jungle_quicksand", .012, .03, .15, .8, true, true),
    LIQUID_MIRE("liquid_mire", .008, .02, .25, .8, true, false),
    STABLE_LIQUID_MIRE("stable_liquid_mire", .008, .02, .25, .8, true, false),
    SINKY_LIQUID("sinky_liquid", .025, .045, .17, .45, false, true),
    SINKING_SLIME("sinking_slime", .011, .036, .08, .7, true, true),
    MUCUS("mucus", .017, .045, .08, .55, false, true),
    MIRE("mire", .015, .035, .11, .9, true, true),
    MOOR("moor", .008, .022, .25, .6, true, true),
    SINKING_CLAY("sinking_clay", .01, .024, .16, .65, true, true),
    TANGLEROOT_MOSS("tangleroot_moss", .011, .024, .07, .6, true, true),
    DENSE_WEB("dense_web", .003, .025, .025, 0, false, true),
    TAR("tar", .014, .035, .035, .3, false, true),
    LARVAE("larvae", .021, .022, .12, .4, false, false),
    CORRUPTED_SAND("corrupted_sand", .015, .038, .1, .5, false, true),
    SWALLOWING_FLESH("swallowing_flesh", .022, .045, .07, .4, false, true),
    ACID("acid", .006, .012, .4, .2, false, false),
    SLURRY("slurry", .018, .024, .12, .5, true, true),
    SOFT_GRAVEL("soft_gravel", .024, .006, .33, .5, false, true),
    HONEY("honey", .01, .028, .045, .45, true, true),
    LIQUID_CHOCOLATE("liquid_chocolate", .01, .022, .12, .6, true, true),
    SINKING_RUG("sinking_rug", .016, .018, .1, .45, false, true),
    VORE_HOLE("vore_hole", .03, .045, .05, 0, false, false),
    MEAT_HOLE("meat_hole", .03, .045, .05, 0, false, false);

    private static final Map<String, SinkingMaterial> BY_ID = new HashMap<>();
    static { for (var material : values()) BY_ID.put(material.id, material); }
    public final String id;
    public final SinkingMotion.Profile motion;
    public final boolean usesAir;

    SinkingMaterial(String id, double still, double struggle, double mobility, double bootDepth,
                    boolean buoyancy, boolean air) {
        this.id = id;
        this.motion = new SinkingMotion.Profile(still, struggle, mobility, bootDepth, buoyancy);
        this.usesAir = air;
    }

    public static SinkingMaterial byId(String id) { return BY_ID.get(id); }
}
