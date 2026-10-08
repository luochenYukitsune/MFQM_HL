package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.common.ModConfigSpec;

/** One terrain choice per placement preserves the old generator's exclusion between pits. */
public final class LegacyTerrainFeature extends Feature<NoneFeatureConfiguration> {
    private record Site(String block, int radius, int depth, int variant) {}
    public LegacyTerrainFeature() { super(NoneFeatureConfiguration.CODEC); }
    private static boolean flag(ModConfigSpec.BooleanValue flag) { return !ModConfig.SERVER_SPEC.isLoaded() || flag.get(); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos pos = context.origin();
        Identifier biome = level.getBiome(pos).unwrapKey().orElseThrow().identifier();
        if (!compatEnabled(biome.getNamespace())) return false;
        String path = biome.getPath();
        boolean nether = level.getLevel().dimension().equals(net.minecraft.world.level.Level.NETHER);
        if (nether) {
            for (int n = 0; n < 32 && level.getBlockState(pos.below()).isAir(); n++) pos = pos.below();
            if (level.getBlockState(pos.below()).isAir()) return false;
        }
        boolean swamp = path.contains("swamp") || path.contains("marsh") || path.contains("moor") || path.contains("bog");
        boolean jungle = path.contains("jungle") || path.contains("gardencia") || path.contains("rainforest");
        boolean desert = path.contains("desert") || path.contains("badlands") || path.contains("dune");
        boolean cold = path.contains("snow") || path.contains("frozen") || path.contains("ice") || path.contains("taiga");
        boolean forest = path.contains("forest") || jungle;
        List<Site> sites = new ArrayList<>();
        if (nether) {
            add(sites, ModConfig.SERVER.genCorruptedSand, "corrupted_sand", 3, 4, 0);
            add(sites, ModConfig.SERVER.genMeat, "meat_wall", 3, 5, 0);
            add(sites, ModConfig.SERVER.genMeatSwallow, "swallowing_flesh", 3, 4, 0);
            add(sites, ModConfig.SERVER.genNetherWastePit, "meat_wall", 3, 5, 10);
            add(sites, ModConfig.SERVER.genNetherHoney, "honey", 3, 3, 0);
            add(sites, ModConfig.SERVER.genTar, "tar", 3, 3, 0);
        } else {
            if (swamp) {
                add(sites, ModConfig.SERVER.genMud, "mud", 4, 3, 0);
                add(sites, ModConfig.SERVER.genDeepMud, "mud", 3, 7, 2);
                add(sites, ModConfig.SERVER.genMire, "mire", 4, 4, 0);
                add(sites, ModConfig.SERVER.genLiquidMire, "liquid_mire", 3, 5, 0);
                add(sites, ModConfig.SERVER.genBog, "bog", 3, 4, 0);
                add(sites, ModConfig.SERVER.genMorass, "morass", 4, 3, 0);
                add(sites, ModConfig.SERVER.genMoor, "moor", 4, 3, 0);
                add(sites, ModConfig.SERVER.genMoss, "tangleroot_moss", 3, 3, 0);
            }
            if (desert) {
                add(sites, ModConfig.SERVER.genQuicksand, "quicksand", 3, 5, 0);
                add(sites, ModConfig.SERVER.genSinkingSand, "dry_quicksand", 4, 5, 0);
                add(sites, ModConfig.SERVER.genBrownClay, "brown_clay", 3, 4, 0);
                add(sites, ModConfig.SERVER.genMineralClay, "brown_clay", 3, 4, 4);
            }
            if (jungle) {
                if (flag(ModConfig.SERVER.genMucusBlossom) && random.nextInt(8) == 0) return LegacyStructures.growBlossom(level, pos.below(2));
                if (flag(ModConfig.SERVER.genBeeHive) && random.nextInt(8) == 0) return hive(level, pos, random);
                if (flag(ModConfig.SERVER.genWax) && random.nextInt(8) == 0) return waxTree(level, pos, random);
                add(sites, ModConfig.SERVER.genJungleQuicksand, "jungle_quicksand", 3, 5, 0);
                add(sites, ModConfig.SERVER.genSoftQuicksand, "soft_quicksand", 4, 4, 0);
                add(sites, ModConfig.SERVER.genHardenedClayPath, "hardened_clay", 4, 1, 0);
                add(sites, ModConfig.SERVER.genLarvae, "larvae", 3, 4, 0);
            }
            if (forest) {
                add(sites, ModConfig.SERVER.genSoftQuicksandForest, "soft_quicksand", 3, 4, 0);
                if (flag(ModConfig.SERVER.genWeb) && random.nextInt(6) == 0) return webNest(level, pos, random);
            }
            if (cold) add(sites, ModConfig.SERVER.genSoftSnow, "soft_snow", 4, 4, 0);
            if (path.contains("wasteland")) add(sites, ModConfig.SERVER.genWastePit, "slurry", 4, 4, 0);
            add(sites, ModConfig.SERVER.genSinkingClay, "sinking_clay", 2, 3, 0);
            add(sites, ModConfig.SERVER.genHardenedClay, "hardened_clay", 2, 1, 0);
            add(sites, ModConfig.SERVER.genGravelPit, "soft_gravel", 3, 4, 0);
            if (random.nextInt(4) == 0) add(sites, ModConfig.SERVER.genTar, "tar", 2, 3, 0);
            if (flag(ModConfig.SERVER.genSlime) && random.nextInt(6) == 0) {
                int base = ModConfig.SERVER_SPEC.isLoaded() ? ModConfig.SERVER.altitudeShift.get() : 62;
                int minimum = level.getMinY() + 8;
                int maximum = Math.max(minimum, pos.getY() - 4);
                BlockPos cave = new BlockPos(pos.getX(), Math.clamp(base - 24, minimum, maximum), pos.getZ());
                if (level.getBlockState(cave).isAir() && !level.getBlockState(cave.below()).isAir()) return pond(level, cave, new Site("sinking_slime", 3, 3, 0), random);
            }
        }
        if (sites.isEmpty() || random.nextInt(3) != 0) return false;
        Site site = sites.get(random.nextInt(sites.size()));
        boolean placed = pond(level, pos, site, random);
        if (placed && (site.block.equals("morass") || site.block.equals("moor")) && flag(ModConfig.SERVER.genMarshReforming)) {
            for (int n = 0; n < 3; n++) {
                BlockPos nearby = pos.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, nearby.getX(), nearby.getZ());
                pond(level, new BlockPos(nearby.getX(), y, nearby.getZ()), new Site(n == 0 ? "bog" : "morass", 2, 2, 0), random);
            }
        }
        if (placed && site.block.equals("soft_quicksand")) {
            BlockPos rock = pos.offset(site.radius + 1, 0, 0);
            for (int y = 0; y < 3; y++) put(level, rock.above(y), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
        return placed;
    }

    private static void add(List<Site> sites, ModConfigSpec.BooleanValue flag, String block, int radius, int depth, int variant) {
        if (flag(flag)) sites.add(new Site(block, radius, depth, variant));
    }
    public static boolean compatEnabled(String namespace) {
        if (!ModConfig.COMMON_SPEC.isLoaded()) return true;
        if (ModConfig.COMMON.forceCustomWorldGen.get()) return true;
        return switch (namespace) {
            case "minecraft" -> ModConfig.COMMON.defaultWorld.get();
            case "biomesoplenty" -> ModConfig.COMMON.biomesOPlenty.get();
            case "twilightforest" -> ModConfig.COMMON.twilightForest.get();
            case "thebetweenlands", "betweenlands" -> ModConfig.COMMON.betweenlands.get();
            case "abyssalcraft" -> ModConfig.COMMON.abyssalcraft.get();
            case "wildycraft" -> ModConfig.COMMON.wildycraft.get();
            case "aoa3", "adventofascension", "nevermine" -> ModConfig.COMMON.adventOfAscension.get();
            case "extrautils", "extrautils2", "extrautilities" -> ModConfig.COMMON.extraUtilities.get();
            default -> false;
        };
    }
    private static boolean put(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (!level.ensureCanWrite(pos) || level.isOutsideBuildHeight(pos) || level.getBlockState(pos).hasBlockEntity() || level.getBlockState(pos).is(Blocks.BEDROCK)) return false;
        return level.setBlock(pos, state, 2);
    }
    private static boolean pond(WorldGenLevel level, BlockPos surface, Site site, RandomSource random) {
        if (surface.getY() - site.depth <= level.getMinY() || level.getBlockState(surface.below()).isAir() || level.getBlockState(surface.below()).is(Blocks.BEDROCK)) return false;
        for (int x = -site.radius; x <= site.radius; x++) for (int z = -site.radius; z <= site.radius; z++) {
            if (x * x + z * z > site.radius * site.radius) continue;
            for (int y = -site.depth; y <= 1; y++) {
                BlockPos pos = surface.offset(x, y, z);
                if (!level.ensureCanWrite(pos) || level.getBlockState(pos).hasBlockEntity() || level.getBlockState(pos).is(Blocks.BEDROCK)) return false;
            }
        }
        for (int x = -site.radius; x <= site.radius; x++) for (int z = -site.radius; z <= site.radius; z++) {
            if (x * x + z * z > site.radius * site.radius) continue;
            for (int y = -site.depth; y < 0; y++) {
                BlockState fill = LegacyStructures.state(site.block, site.variant);
                if (site.block.equals("meat_wall")) {
                    boolean edge = x * x + z * z >= (site.radius - 1) * (site.radius - 1);
                    fill = edge ? LegacyStructures.state("meat_wall", site.variant == 10 ? 10 : x > 0 ? 9 : x < 0 ? 7 : z > 0 ? 6 : 8)
                            : y == -site.depth ? ModBlocks.byId("acid").defaultBlockState() : Blocks.AIR.defaultBlockState();
                }
                put(level, surface.offset(x, y, z), fill);
            }
            put(level, surface.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            put(level, surface.offset(x, 1, z), Blocks.AIR.defaultBlockState());
            if ((site.block.equals("moor") || site.block.equals("morass")) && random.nextInt(4) == 0) {
                put(level, surface.offset(x, 0, z), LegacyStructures.state("moor_grass", random.nextInt(6)));
            } else if (site.block.equals("swallowing_flesh") && random.nextInt(3) == 0) {
                put(level, surface.offset(x, 0, z), LegacyStructures.state("tendrils", random.nextInt(4)));
            }
        }
        if (site.block.equals("meat_wall") && site.variant == 10) placeWasteOutlet(level, surface.below(4));
        return true;
    }
    /** Six supported cells above a clear outlet make the original waste mechanism reachable. */
    public static void placeWasteOutlet(WorldGenLevel level, BlockPos base) {
        put(level, base.below(), Blocks.AIR.defaultBlockState());
        put(level, base, LegacyStructures.state("meat_wall", 10));
        for (int up = 1; up <= 5; up++) {
            put(level, base.above(up), LegacyStructures.state("meat_wall", 0));
            if (up < 5) for (Direction direction : Direction.Plane.HORIZONTAL)
                put(level, base.above(up).relative(direction), LegacyStructures.state("meat_wall", 0));
        }
    }
    private static boolean waxTree(WorldGenLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.below()).isSolidRender()) return false;
        int height = 5 + random.nextInt(4);
        for (int y = 0; y <= height; y++) if (!level.getBlockState(pos.above(y)).canBeReplaced()) return false;
        if (!pond(level, pos, new Site("wax", 2, 2, 0), random)) return false;
        for (int y = 0; y < height; y++) put(level, pos.above(y), LegacyStructures.state("wax_wood", 0));
        for (int y = height - 2; y <= height; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos leaf = pos.offset(x, y, z);
            if (level.getBlockState(leaf).canBeReplaced() && x * x + z * z < 7) put(level, leaf, Blocks.JUNGLE_LEAVES.defaultBlockState());
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) put(level, pos.relative(direction, 3), LegacyStructures.state("leaves_pile", 0));
        return true;
    }
    private static boolean hive(WorldGenLevel level, BlockPos pos, RandomSource random) {
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 5; y++) for (int z = -3; z <= 3; z++) {
            if (x * x + z * z > 10) continue;
            BlockPos at = pos.offset(x, y, z);
            if (!level.ensureCanWrite(at) || level.isOutsideBuildHeight(at) || level.getBlockState(at).hasBlockEntity() || level.getBlockState(at).is(Blocks.BEDROCK)) return false;
        }
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 5; y++) for (int z = -3; z <= 3; z++) {
            if (x * x + z * z > 10) continue;
            boolean shell = x * x + z * z >= 5 || y == 0 || y == 5;
            put(level, pos.offset(x, y, z), shell ? LegacyStructures.state("honeycomb", 2) : y < 2 ? ModBlocks.byId("honey").defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        put(level, pos.above(2), Blocks.SPAWNER.defaultBlockState());
        if (level.getBlockEntity(pos.above(2)) instanceof SpawnerBlockEntity spawner) {
            var data = new net.minecraft.nbt.CompoundTag();
            var entity = new net.minecraft.nbt.CompoundTag();
            entity.putString("id", "mfqm:bee");
            var spawn = new net.minecraft.nbt.CompoundTag();
            spawn.put("entity", entity);
            data.put("SpawnData", spawn);
            data.putShort("MinSpawnDelay", (short) 400);
            data.putShort("MaxSpawnDelay", (short) 1600);
            data.putShort("MaxNearbyEntities", (short) 16);
            data.putShort("RequiredPlayerRange", (short) 48);
            data.putShort("SpawnRange", (short) 24);
            spawner.getSpawner().load(level.getLevel(), pos.above(2), net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), data));
        }
        BlockPos chestPos = pos.offset(1, 2, 0);
        put(level, chestPos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("mfqm", "chests/honey")));
            chest.setLootTableSeed(random.nextLong());
            chest.applyComponents(net.minecraft.core.component.DataComponentMap.builder().set(
                    net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.translatable("container.mfqm.honey_chest")).build(),
                    net.minecraft.core.component.DataComponentPatch.EMPTY);
        }
        return true;
    }
    private static boolean webNest(WorldGenLevel level, BlockPos pos, RandomSource random) {
        boolean placed = pond(level, pos, new Site("dense_web", 3, 3, 0), random);
        if (placed && flag(ModConfig.SERVER.genWebSpawner)) {
            put(level, pos.below(2), Blocks.SPAWNER.defaultBlockState());
            if (level.getBlockEntity(pos.below(2)) instanceof SpawnerBlockEntity spawner) spawner.setEntityId(random.nextBoolean() ? EntityType.SPIDER : EntityType.CAVE_SPIDER, random);
        }
        return placed;
    }
}
