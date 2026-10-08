package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Independent small sealed pools, separate from the legacy terrain lottery. */
public final class GluePoolFeature extends Feature<NoneFeatureConfiguration> {
    public static final TagKey<Biome> ELIGIBLE_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("mfqm", "glue_pool_eligible"));

    public GluePoolFeature() { super(NoneFeatureConfiguration.CODEC); }
    private static final ConcurrentHashMap<String, LongAdder> DIAGNOSTICS = new ConcurrentHashMap<>();
    private static void count(String reason) {
        if (Boolean.getBoolean("mfqm.naturalGlueChecks")) DIAGNOSTICS.computeIfAbsent(reason, ignored -> new LongAdder()).increment();
    }
    private static boolean reject(String reason) { count(reason); return false; }
    static void resetDiagnostics() { DIAGNOSTICS.clear(); }
    static java.util.Map<String, Long> diagnostics() {
        var snapshot = new java.util.TreeMap<String, Long>();
        DIAGNOSTICS.forEach((reason, number) -> snapshot.put(reason, number.sum()));
        return snapshot;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        count("calls");
        boolean configured = ModConfig.SERVER_SPEC.isLoaded();
        if (!level.getLevel().dimension().equals(Level.OVERWORLD)
                || configured && !ModConfig.SERVER.genGluePools.get()) return reject("dimension_or_disabled");
        BlockPos origin = context.origin();
        if (!level.getBiome(origin).is(ELIGIBLE_BIOMES)) return reject("origin_biome");
        var random = context.random();
        if (random.nextInt(configured ? ModConfig.SERVER.gluePoolChance.get() : 24) != 0) return reject("rarity");
        count("rolled");
        int radius = 2 + random.nextInt(2);
        ChunkPos chunk = new ChunkPos(origin);
        // Keep the entire wall and floor in this candidate chunk, including its outer seal.
        int x = Math.clamp(origin.getX(), chunk.getMinBlockX() + 4, chunk.getMaxBlockX() - 4);
        int z = Math.clamp(origin.getZ(), chunk.getMinBlockZ() + 4, chunk.getMaxBlockZ() - 4);
        int y = x == origin.getX() && z == origin.getZ() ? origin.getY()
                : level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        // WG surface may be a canopy. Resolve the actual natural soil, and allow a
        // low bank while keeping one level fluid surface and an intact outer seal.
        int groundY = groundSurface(level, x, y, z, radius);
        if (groundY == Integer.MIN_VALUE) return false;
        BlockPos surface = new BlockPos(x, groundY, z);
        if (!level.getBiome(surface).is(ELIGIBLE_BIOMES)) return reject("surface_biome");
        int shallow = configured ? ModConfig.SERVER.gluePoolShallowDepth.get() : 1;
        int first = configured ? ModConfig.SERVER.gluePoolMinDeepDepth.get() : 3;
        int second = configured ? ModConfig.SERVER.gluePoolMaxDeepDepth.get() : 4;
        int min = Math.min(first, second), max = Math.max(first, second);
        int depth = random.nextBoolean() ? shallow : min + random.nextInt(max - min + 1);
        if (surface.getY() - depth - 1 < level.getMinY() || surface.getY() + 1 >= level.getMaxY()) return reject("height_bounds");

        List<BlockPos> fill = new ArrayList<>();
        List<BlockPos> clear = new ArrayList<>();
        // Preflight the whole volume. A cave, liquid, protected block or rejected write aborts it.
        for (int dx = -radius - 1; dx <= radius + 1; dx++) for (int dz = -radius - 1; dz <= radius + 1; dz++) {
            boolean inside = dx * dx + dz * dz <= radius * radius;
            if (!inside && dx * dx + dz * dz > (radius + 1) * (radius + 1)) continue;
            for (int dy = -depth - 1; dy <= (inside ? 1 : -1); dy++) {
                BlockPos at = surface.offset(dx, dy, dz);
                if (!new ChunkPos(at).equals(chunk) || !level.ensureCanWrite(at)) return reject("write_or_chunk_bounds");
                BlockState state = level.getBlockState(at);
                if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK)) return reject("protected_or_liquid");
                if (dy >= 0) {
                    if (!state.canBeReplaced() && !state.is(BlockTags.LEAVES) && !naturalGround(state)) return reject("headroom");
                    if (!state.isAir()) clear.add(at);
                } else {
                    if (!state.isSolidRender()) return reject("non_solid_ground");
                    if (!naturalGround(state)) return reject("unnatural_ground");
                    if (inside && dy >= -depth) fill.add(at);
                }
            }
        }
        BlockState glue = ModBlocks.byId("glue").defaultBlockState();
        for (BlockPos at : clear) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos at : fill) level.setBlock(at, glue, 2);
        count("placed");
        return true;
    }

    private static boolean naturalGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
                || state.is(Blocks.CLAY) || state.is(Blocks.MUD) || state.is(Blocks.GRAVEL);
    }

    private static int groundSurface(WorldGenLevel level, int x, int initialY, int z, int radius) {
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) for (int dz = -radius - 1; dz <= radius + 1; dz++) {
            if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) continue;
            boolean located = false;
            // Read only within this candidate's footprint and a bounded canopy range.
            for (int scanY = Math.min(initialY + 2, level.getMaxY() - 1);
                    scanY >= Math.max(initialY - 64, level.getMinY()); scanY--) {
                BlockState state = level.getBlockState(new BlockPos(x + dx, scanY, z + dz));
                if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK)) {
                    reject("surface_protected_or_liquid"); return Integer.MIN_VALUE;
                }
                if (state.isSolidRender() && naturalGround(state)) {
                    lowest = Math.min(lowest, scanY + 1); highest = Math.max(highest, scanY + 1);
                    located = true; break;
                }
                // Tree trunks and artificial blocks remain protected; leafy overhangs
                // are only read here, and leaves above the pool's headroom stay intact.
                if (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)) {
                    reject("surface_obstacle"); return Integer.MIN_VALUE;
                }
            }
            if (!located) { reject("surface_not_found"); return Integer.MIN_VALUE; }
        }
        if (highest - lowest > 2) { reject("steep_bank"); return Integer.MIN_VALUE; }
        return lowest;
    }
}
