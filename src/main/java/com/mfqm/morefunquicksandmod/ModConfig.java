package com.mfqm.morefunquicksandmod;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class ModConfig {
    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final CommonConfig COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        Pair<ServerConfig, ModConfigSpec> serverPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER = serverPair.getLeft();
        SERVER_SPEC = serverPair.getRight();

        Pair<ClientConfig, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();

        Pair<CommonConfig, ModConfigSpec> commonPair = new ModConfigSpec.Builder().configure(CommonConfig::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();
    }

    public static final class ServerConfig {
        // Worldgen toggles (~33 entries)
        public final ModConfigSpec.BooleanValue genMud;
        public final ModConfigSpec.BooleanValue genMire;
        public final ModConfigSpec.BooleanValue genDeepMud;
        public final ModConfigSpec.BooleanValue genLiquidMire;
        public final ModConfigSpec.BooleanValue genMoor;
        public final ModConfigSpec.BooleanValue genBog;
        public final ModConfigSpec.BooleanValue genMorass;
        public final ModConfigSpec.BooleanValue genQuicksand;
        public final ModConfigSpec.BooleanValue genSoftQuicksand;
        public final ModConfigSpec.BooleanValue genSoftQuicksandForest;
        public final ModConfigSpec.BooleanValue genJungleQuicksand;
        public final ModConfigSpec.BooleanValue genSinkingSand;
        public final ModConfigSpec.BooleanValue genSoftSnow;
        public final ModConfigSpec.BooleanValue genHardenedClay;
        public final ModConfigSpec.BooleanValue genSinkingClay;
        public final ModConfigSpec.BooleanValue genLarvae;
        public final ModConfigSpec.BooleanValue genWeb;
        public final ModConfigSpec.BooleanValue genWebSpawner;
        public final ModConfigSpec.BooleanValue genTar;
        public final ModConfigSpec.BooleanValue genSlime;
        public final ModConfigSpec.BooleanValue genCorruptedSand;
        public final ModConfigSpec.BooleanValue genMeat;
        public final ModConfigSpec.BooleanValue genMeatSwallow;
        public final ModConfigSpec.BooleanValue genWastePit;
        public final ModConfigSpec.BooleanValue genMucusBlossom;
        public final ModConfigSpec.BooleanValue genMoss;
        public final ModConfigSpec.BooleanValue genBrownClay;
        public final ModConfigSpec.BooleanValue genMineralClay;
        public final ModConfigSpec.BooleanValue genWax;
        public final ModConfigSpec.BooleanValue genBeeHive;
        public final ModConfigSpec.BooleanValue genNetherHoney;
        public final ModConfigSpec.BooleanValue genGravelPit;
        public final ModConfigSpec.BooleanValue genDesertTombs;
        public final ModConfigSpec.BooleanValue genHardenedClayPath;
        public final ModConfigSpec.BooleanValue genNetherWastePit;
        public final ModConfigSpec.BooleanValue genTempleQuicksand;
        public final ModConfigSpec.BooleanValue genMarshReforming;
        public final ModConfigSpec.BooleanValue customSwampWaterColor;
        public final ModConfigSpec.BooleanValue enableCustomSlimes;
        public final ModConfigSpec.BooleanValue enableHandRescue;
        public final ModConfigSpec.IntValue altitudeShift;

        // Mob spawn toggles
        public final ModConfigSpec.BooleanValue spawnVoreSlime;
        public final ModConfigSpec.BooleanValue spawnMuddyBlob;
        public final ModConfigSpec.BooleanValue spawnSandBlob;
        public final ModConfigSpec.BooleanValue spawnTarSlime;

        // Runtime recipe/acquisition toggles; never omit registrations or datagen output.
        public final ModConfigSpec.BooleanValue enableWadingBoots;
        public final ModConfigSpec.BooleanValue enableLongStick;
        public final ModConfigSpec.BooleanValue enableRope;
        public final ModConfigSpec.BooleanValue enableGrapplingHook;
        public final ModConfigSpec.BooleanValue enableLifeJacket;
        public final ModConfigSpec.BooleanValue enableGasMask;
        public final ModConfigSpec.BooleanValue enableSinkingPotion;
        public final ModConfigSpec.BooleanValue enableLiqGun;

        // Option toggles
        public final ModConfigSpec.BooleanValue hotTar;
        public final ModConfigSpec.BooleanValue tentaclesInFlesh;
        public final ModConfigSpec.BooleanValue mudTentacles;
        public final ModConfigSpec.BooleanValue gasSlurry;
        public final ModConfigSpec.BooleanValue bubblesOnSurface;
        public final ModConfigSpec.BooleanValue tarTreads;
        public final ModConfigSpec.BooleanValue hookAsRider;
        public final ModConfigSpec.BooleanValue realisticSuffocation;
        public final ModConfigSpec.BooleanValue realisticMobSuffocation;
        public final ModConfigSpec.BooleanValue realisticBoots;
        public final ModConfigSpec.BooleanValue realisticArmor;
        public final ModConfigSpec.BooleanValue weightCalc;
        public final ModConfigSpec.DoubleValue damageMultiplier;
        public final ModConfigSpec.BooleanValue adhesiveBonds;
        public final ModConfigSpec.BooleanValue creativeGroundPhysics;
        public final ModConfigSpec.DoubleValue glueBondDistance, honeyBondDistance, tarBondDistance, slimeBondDistance, mudBondDistance;
        public final ModConfigSpec.DoubleValue glueActivityRadius, honeyActivityRadius, tarActivityRadius, slimeActivityRadius, mudActivityRadius, boardActivityRadius;
        public final ModConfigSpec.DoubleValue bootLossChance;
        public final ModConfigSpec.DoubleValue glueVerticalDistance,honeyVerticalDistance,tarVerticalDistance,slimeVerticalDistance,boardVerticalDistance;
        public final ModConfigSpec.BooleanValue genGluePools, genStickyBoards;
        public final ModConfigSpec.IntValue gluePoolChance;
        public final ModConfigSpec.IntValue gluePoolShallowDepth, gluePoolMinDeepDepth, gluePoolMaxDeepDepth;

        ServerConfig(ModConfigSpec.Builder builder) {
            builder.push("adhesive");
            adhesiveBonds = builder.define("adhesiveBonds", true);
            glueVerticalDistance=builder.defineInRange("glueVerticalDistance",4.,.25,16);
            honeyVerticalDistance=builder.defineInRange("honeyVerticalDistance",2.7,.25,16);
            slimeVerticalDistance=builder.defineInRange("slimeVerticalDistance",2.7,.25,16);
            tarVerticalDistance=builder.defineInRange("tarVerticalDistance",2.,.25,16);
            boardVerticalDistance=builder.defineInRange("boardVerticalDistance",2.,.25,16);
            creativeGroundPhysics = builder.comment("Apply trapping physics to grounded creative players; flying and spectator players remain exempt")
                    .define("creativeGroundPhysics", true);
            glueBondDistance = builder.defineInRange("glueBondDistance", 2.5, .25, 8);
            honeyBondDistance = builder.defineInRange("honeyBondDistance", 1.8, .25, 8);
            tarBondDistance = builder.defineInRange("tarBondDistance", 1.2, .25, 8);
            slimeBondDistance = builder.defineInRange("slimeBondDistance", 1.8, .25, 8);
            mudBondDistance = builder.defineInRange("mudBondDistance", .8, .25, 8);
            glueActivityRadius = builder.defineInRange("glueActivityRadius", 1.2, .1, 4);
            honeyActivityRadius = builder.defineInRange("honeyActivityRadius", 1., .1, 4);
            tarActivityRadius = builder.defineInRange("tarActivityRadius", .8, .1, 4);
            slimeActivityRadius = builder.defineInRange("slimeActivityRadius", 1., .1, 4);
            mudActivityRadius = builder.defineInRange("mudActivityRadius", .6, .1, 4);
            boardActivityRadius = builder.defineInRange("boardActivityRadius", .7, .1, 4);
            bootLossChance = builder.comment("One roll per glue/board episode, only on forceful pull or break").defineInRange("bootLossChance", .1, 0, 1);
            genGluePools = builder.define("genGluePools", true);
            genStickyBoards = builder.define("genStickyBoards", true);
            gluePoolChance = builder.comment("One candidate per this many eligible chunks").defineInRange("gluePoolChance", 24, 1, 10000);
            gluePoolShallowDepth = builder.defineInRange("gluePoolShallowDepth", 1, 1, 8);
            gluePoolMinDeepDepth = builder.defineInRange("gluePoolMinDeepDepth", 3, 1, 8);
            gluePoolMaxDeepDepth = builder.defineInRange("gluePoolMaxDeepDepth", 4, 1, 8);
            builder.pop();
            builder.push("worldgen");
            genMud = builder.comment("Generate Mud in swamps").define("genMud", true);
            genMire = builder.comment("Generate Mire in swamps").define("genMire", true);
            genDeepMud = builder.comment("Generate deep mud in swamps").define("genDeepMud", true);
            genLiquidMire = builder.comment("Generate Liquid Mire in swamps").define("genLiquidMire", true);
            genMoor = builder.comment("Generate Moor in marsh").define("genMoor", true);
            genBog = builder.comment("Generate Bog in swamps").define("genBog", true);
            genMorass = builder.comment("Generate Peat Bogs").define("genMorass", true);
            genQuicksand = builder.comment("Generate Quicksand in desert").define("genQuicksand", true);
            genSoftQuicksand = builder.comment("Generate Soft Quicksand in jungle").define("genSoftQuicksand", true);
            genSoftQuicksandForest = builder.comment("Generate rare Soft Quicksand in forest").define("genSoftQuicksandForest", true);
            genJungleQuicksand = builder.comment("Generate Jungle Quicksand").define("genJungleQuicksand", true);
            genSinkingSand = builder.comment("Generate Dry Quicksand pits in desert").define("genSinkingSand", true);
            genSoftSnow = builder.comment("Generate Soft Snow").define("genSoftSnow", true);
            genHardenedClay = builder.comment("Generate Hardened Clay").define("genHardenedClay", true);
            genHardenedClayPath = builder.comment("Generate Hardened Clay paths in jungles").define("genHardenedClayPath", true);
            genSinkingClay = builder.comment("Generate Sinking Clay pits").define("genSinkingClay", true);
            genLarvae = builder.comment("Generate Larvae Pits").define("genLarvae", true);
            genWeb = builder.comment("Generate Webbing Nests").define("genWeb", true);
            genWebSpawner = builder.comment("Generate Spider Spawners in nests").define("genWebSpawner", true);
            genTar = builder.comment("Generate Tar pits").define("genTar", true);
            genSlime = builder.comment("Generate Sinking Slime pits").define("genSlime", true);
            genCorruptedSand = builder.comment("Generate Corrupted Sands in Nether").define("genCorruptedSand", true);
            genMeat = builder.comment("Generate Fleshy Pits in Nether").define("genMeat", true);
            genMeatSwallow = builder.comment("Generate Swallowing Flesh in Nether").define("genMeatSwallow", true);
            genWastePit = builder.comment("Generate slurry pits in Wasteland").define("genWastePit", true);
            genMucusBlossom = builder.comment("Generate Mucus Blossom in jungle").define("genMucusBlossom", true);
            genMoss = builder.comment("Generate Tangleroot Moss in swamps").define("genMoss", true);
            genBrownClay = builder.comment("Generate Brown Clay pits").define("genBrownClay", true);
            genMineralClay = builder.comment("Generate Mineral Clay pits").define("genMineralClay", true);
            genWax = builder.comment("Generate Wax Trees in jungle").define("genWax", true);
            genBeeHive = builder.comment("Generate Bee Hives").define("genBeeHive", true);
            genNetherHoney = builder.comment("Generate Honey in Nether").define("genNetherHoney", true);
            genGravelPit = builder.comment("Generate natural gravel pits").define("genGravelPit", true);
            genDesertTombs = builder.comment("Generate Desert Tombs").define("genDesertTombs", true);
            genNetherWastePit = builder.comment("Generate waste traps in the Nether, separately from Wasteland slurry").define("genNetherWastePit", true);
            genTempleQuicksand = builder.comment("Replace desert temple TNT traps with quicksand").define("genTempleQuicksand", true);
            genMarshReforming = builder.comment("Apply MFQM marsh terrain to compatible biomes").define("genMarshReforming", true);
            customSwampWaterColor = builder.comment("Use the original muddy swamp water color").define("customSwampWaterColor", true);
            altitudeShift = builder.comment("Legacy water-level reference; 62 preserves the normal placement baseline").defineInRange("altitudeShift", 62, -64, 320);
            builder.pop();

            builder.push("mobs");
            enableCustomSlimes = builder.comment("Enable custom slime and blob spawning").define("enableCustomSlimes", true);
            spawnVoreSlime = builder.comment("Spawn Vore Slime in caves").define("spawnVoreSlime", true);
            spawnMuddyBlob = builder.comment("Spawn Muddy Blob in swamps").define("spawnMuddyBlob", true);
            spawnSandBlob = builder.comment("Spawn Sand Blob in desert").define("spawnSandBlob", true);
            spawnTarSlime = builder.comment("Spawn Tar Slime near tar pits").define("spawnTarSlime", true);
            builder.pop();

            builder.push("items");
            enableWadingBoots = builder.comment("Enable Wading Boots recipe").define("enableWadingBoots", true);
            enableLongStick = builder.comment("Enable Long Stick recipe").define("enableLongStick", true);
            enableRope = builder.comment("Enable Rope recipe").define("enableRope", true);
            enableGrapplingHook = builder.comment("Enable Grappling Hook recipe").define("enableGrapplingHook", true);
            enableLifeJacket = builder.comment("Enable Life Jacket recipe").define("enableLifeJacket", true);
            enableGasMask = builder.comment("Enable Gas Mask recipe").define("enableGasMask", true);
            enableSinkingPotion = builder.comment("Enable Sinking Potion recipe").define("enableSinkingPotion", true);
            enableLiqGun = builder.comment("Enable Liquid Gun recipe").define("enableLiqGun", true);
            builder.pop();

            builder.push("options");
            hotTar = builder.comment("Make Tar deal heat damage").define("hotTar", true);
            tentaclesInFlesh = builder.comment("Spawn tentacles in fleshy pits").define("tentaclesInFlesh", true);
            mudTentacles = builder.comment("Spawn tentacles in mud and quicksand").define("mudTentacles", true);
            gasSlurry = builder.comment("Enable gas effects for Slurry").define("gasSlurry", true);
            bubblesOnSurface = builder.comment("Spawn bubbles on quicksand surface").define("bubblesOnSurface", true);
            tarTreads = builder.comment("Enable tar treads effect").define("tarTreads", true);
            hookAsRider = builder.comment("Allow the original hook riding interaction").define("hookAsRider", true);
            enableHandRescue = builder.comment("Allow empty-handed rescue of players and sneaking rescue of mobs").define("enableHandRescue", true);
            realisticSuffocation = builder.comment("Realistic player suffocation").define("realisticSuffocation", true);
            realisticMobSuffocation = builder.comment("Realistic mob suffocation").define("realisticMobSuffocation", true);
            realisticBoots = builder.comment("Realistic boots relations").define("realisticBoots", true);
            realisticArmor = builder.comment("Realistic armor relations").define("realisticArmor", true);
            weightCalc = builder.comment("Calculate inventory weight").define("weightCalc", true);
            damageMultiplier = builder.comment("Drowning damage multiplier").defineInRange("damageMultiplier", 1.0, 0.0, 100.0);
            builder.pop();
        }
    }

    public static final class ClientConfig {
        public final ModConfigSpec.BooleanValue forceFirstPerson;
        public final ModConfigSpec.BooleanValue coverPlayerWithMud;
        public final ModConfigSpec.BooleanValue customAirHud;
        public final ModConfigSpec.BooleanValue quicksandOpacity;
        public final ModConfigSpec.BooleanValue bubbleEffects;
        public final ModConfigSpec.BooleanValue tarTreadsEffect;
        public final ModConfigSpec.BooleanValue struggleAnimation, struggleCamera, adhesiveTethers;
        public final ModConfigSpec.BooleanValue glueCoating3d;
        public final ModConfigSpec.DoubleValue coatingOpacity,coatingThickness;
        public final ModConfigSpec.IntValue strandDisplayLimit,strandDensity;
        public final java.util.Map<String,MaterialVisuals> materialVisuals;
        public record MaterialVisuals(ModConfigSpec.DoubleValue opacity,ModConfigSpec.DoubleValue thickness,ModConfigSpec.IntValue strands){}
        public MaterialVisuals visuals(String family){return materialVisuals.get(family);}

        ClientConfig(ModConfigSpec.Builder builder) {
            builder.push("hud");
            forceFirstPerson = builder.comment("Force first-person camera in deep quicksand").define("forceFirstPerson", true);
            coverPlayerWithMud = builder.comment("Cover player model with mud texture").define("coverPlayerWithMud", true);
            customAirHud = builder.comment("Use custom air HUD when sinking").define("customAirHud", true);
            builder.pop();

            builder.push("rendering");
            quicksandOpacity = builder.comment("Render quicksand blocks as opaque").define("quicksandOpacity", false);
            bubbleEffects = builder.comment("Render surface bubble particles").define("bubbleEffects", true);
            tarTreadsEffect = builder.comment("Render tar treads visual").define("tarTreadsEffect", true);
            struggleAnimation = builder.define("struggleAnimation", true);
            struggleCamera = builder.comment("Subtle camera movement during accepted struggle actions").define("struggleCamera", true);
            adhesiveTethers = builder.define("adhesiveTethers", true);
            // Keep the existing saved key; it now controls 3D residue for every material.
            glueCoating3d = builder.comment("Subtle surface relief on skin-following residue films; disable for smooth films")
                    .define("glueCoating3d", true);
            coatingOpacity=builder.comment("Client only: source opacity multiplier; zero hides residue").defineInRange("coatingOpacity",1.,0,2);
            coatingThickness=builder.comment("Client only: thin-film micro-relief multiplier; zero uses a smooth skin surface").defineInRange("coatingThickness",1.,0,3);
            strandDisplayLimit=builder.comment("Client only: maximum visible contact groups per target; each contains strandDensity independent strands, physics unchanged").defineInRange("strandDisplayLimit",64,0,64);
            strandDensity=builder.comment("Client only: base strands per contact, 1-128; default target budget 512, maximum 8192. Fresh contact density adapts up to 4x inside this budget; high values increase rendering cost, not physics").defineInRange("strandDensity",8,1,128);
            var visuals=new java.util.LinkedHashMap<String,MaterialVisuals>();
            for(String family:new String[]{"glue","tar","honey","slime","mud"}) {
                builder.push(family+"Visuals");
                visuals.put(family,new MaterialVisuals(builder.defineInRange("materialOpacity",1.,0,2),
                        builder.defineInRange("materialThickness",1.,0,3),builder.defineInRange("materialStrands",64,0,64)));
                builder.pop();
            }
            materialVisuals=java.util.Map.copyOf(visuals);
            builder.pop();
        }
    }

    public static final class CommonConfig {
        public final ModConfigSpec.BooleanValue biomesOPlenty;
        public final ModConfigSpec.BooleanValue defaultWorld;
        public final ModConfigSpec.BooleanValue twilightForest;
        public final ModConfigSpec.BooleanValue betweenlands;
        public final ModConfigSpec.BooleanValue abyssalcraft;
        public final ModConfigSpec.BooleanValue wildycraft;
        public final ModConfigSpec.BooleanValue adventOfAscension;
        public final ModConfigSpec.BooleanValue extraUtilities;
        public final ModConfigSpec.BooleanValue forceCustomWorldGen;
        CommonConfig(ModConfigSpec.Builder builder) {
            builder.push("compat");
            biomesOPlenty = builder.define("biomesOPlenty", true);
            defaultWorld = builder.define("defaultWorld", true);
            twilightForest = builder.define("twilightForest", true);
            betweenlands = builder.define("betweenlands", true);
            abyssalcraft = builder.define("abyssalcraft", true);
            wildycraft = builder.define("wildycraft", true);
            adventOfAscension = builder.define("adventOfAscension", true);
            extraUtilities = builder.define("extraUtilities", true);
            forceCustomWorldGen = builder.define("forceCustomWorldGen", false);
            builder.pop();
        }
    }

    private ModConfig() {}
}
