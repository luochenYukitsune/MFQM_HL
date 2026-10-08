package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in empirical sample of untouched terrain, using the normal chunk generator. */
public final class NaturalGlueChecks {
    private static final int SIDE = 16;
    private static final int SAMPLE_SIZE = SIDE * SIDE;
    private static final int MIN_ESTIMATED_FOREST = 128;
    private static final int MAX_LOCATOR_REGIONS = 12;
    private static final Set<String> FORESTS = Set.of("forest", "flower_forest", "birch_forest",
            "old_growth_birch_forest", "dark_forest");
    private static final List<BlockPos> observations = new ArrayList<>();
    private static int ticks, generated, eligibleCenters, glueChunks, glueBlocks;
    private static ChunkPos first;
    private static List<ChunkPos> candidates;
    private static int candidateIndex, estimatedIndex, estimatedForest;
    private static boolean finished;

    public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("mfqm.naturalGlueChecks") || finished || ++ticks < 20) return;
        ServerLevel level = event.getServer().overworld();
        try {
            if (first == null) {
                require(ModConfig.SERVER.genGluePools.get(), "glue pool generation is disabled in this world");
                if (!selectSurfaceForestSample(level)) return;
                GluePoolFeature.resetDiagnostics();
                MFQM.LOGGER.info("MFQM_NATURAL_GLUE_START seed={} firstChunk={},{} chunks={} estimatedLandForest={} chance={} shallow={} deep={}..{}",
                        level.getSeed(), first.x, first.z, SAMPLE_SIZE, estimatedForest, ModConfig.SERVER.gluePoolChance.get(),
                        ModConfig.SERVER.gluePoolShallowDepth.get(), ModConfig.SERVER.gluePoolMinDeepDepth.get(),
                        ModConfig.SERVER.gluePoolMaxDeepDepth.get());
            }
            // Bound work per tick: one chunk, generated with the real configured features.
            ChunkPos at = new ChunkPos(first.x + generated % SIDE, first.z + generated / SIDE);
            LevelChunk chunk = level.getChunk(at.x, at.z);
            int centerY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
            if (level.getBiome(new BlockPos(at.getMinBlockX() + 8, centerY, at.getMinBlockZ() + 8))
                    .is(GluePoolFeature.ELIGIBLE_BIOMES)) eligibleCenters++;
            inspect(chunk);
            generated++;
            if (generated % 16 == 0) MFQM.LOGGER.info("MFQM_NATURAL_GLUE_PROGRESS chunks={}/{} eligibleCenters={} glueChunks={} glueBlocks={}",
                    generated, SAMPLE_SIZE, eligibleCenters, glueChunks, glueBlocks);
            if (generated == SAMPLE_SIZE) {
                finished = true;
                MFQM.LOGGER.info("MFQM_NATURAL_GLUE_DIAGNOSTICS {}", GluePoolFeature.diagnostics());
                require(eligibleCenters > 0, "forest locator sample contains no eligible surface biome centers");
                if (glueChunks == 0) {
                    MFQM.LOGGER.warn("MFQM_NATURAL_GLUE_NOT_OBSERVED chunks={} eligibleCenters={} seed={}; a zero observation is not proof that generation works",
                            generated, eligibleCenters, level.getSeed());
                    throw new IllegalStateException("No natural glue observed in this bounded sample; investigate terrain rejection or use an additional independent sample");
                }
                MFQM.LOGGER.info("MFQM_NATURAL_GLUE_CHECKS_COMPLETE chunks={} eligibleCenters={} glueChunks={} glueBlocks={} observations={}",
                        generated, eligibleCenters, glueChunks, glueBlocks, observations);
            }
        } catch (Throwable failure) {
            finished = true;
            MFQM.LOGGER.error("MFQM_NATURAL_GLUE_CHECKS_FAILED", failure);
        }
    }

    private static List<ChunkPos> findFreshForestCandidates(ServerLevel level) {
        var root = level.getServer().getWorldPath(LevelResource.ROOT).resolve("region");
        var choices = new java.util.LinkedHashSet<ChunkPos>();
        // A far-away, previously absent region rules out the manually constructed port-check fixtures.
        for (int attempt = 0; attempt < MAX_LOCATOR_REGIONS; attempt++) {
            int offset = 96_000 + attempt * 8192;
            var found = level.findClosestBiome3d(NaturalGlueChecks::isForest,
                    new BlockPos(offset, 80, offset), 4096, 64, 64);
            if (found == null) continue;
            ChunkPos center = new ChunkPos(found.getFirst());
            for (int shiftX = -SIDE; shiftX <= SIDE; shiftX += SIDE) for (int shiftZ = -SIDE; shiftZ <= SIDE; shiftZ += SIDE) {
                ChunkPos start = new ChunkPos(center.x + shiftX - SIDE / 2, center.z + shiftZ - SIDE / 2);
                var regions = new HashSet<ChunkPos>();
                boolean fresh = true;
                for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) {
                    ChunkPos region = new ChunkPos((start.x + x) >> 5, (start.z + z) >> 5);
                    if (regions.add(region) && Files.exists(root.resolve("r." + region.x + "." + region.z + ".mca"))) fresh = false;
                }
                if (fresh) choices.add(start);
            }
        }
        require(!choices.isEmpty(), "Could not locate an untouched region candidate within the bounded searches");
        MFQM.LOGGER.info("MFQM_NATURAL_GLUE_LOCATOR freshCandidates={} requiredEstimatedLandForest={}/{}", choices.size(), MIN_ESTIMATED_FOREST, SAMPLE_SIZE);
        return new ArrayList<>(choices);
    }

    private static boolean isForest(Holder<Biome> holder) {
        return holder.is(GluePoolFeature.ELIGIBLE_BIOMES)
                && holder.unwrapKey().map(key -> key.identifier().getNamespace().equals("minecraft")
                && FORESTS.contains(key.identifier().getPath())).orElse(false);
    }

    private static boolean selectSurfaceForestSample(ServerLevel level) {
        if (candidates == null) candidates = findFreshForestCandidates(level);
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        // Noise estimates do not request or generate chunks. Keep these reads bounded
        // per tick, too: a 3-D biome locator alone may have found an underground forest.
        for (int reads = 0; reads < 16; reads++) {
            require(candidateIndex < candidates.size(), "No untouched candidate has enough predicted surface forest; do not replace this failure with a generated or modified site");
            ChunkPos start = candidates.get(candidateIndex);
            int x = ((start.x + estimatedIndex % SIDE) << 4) + 8;
            int z = ((start.z + estimatedIndex / SIDE) << 4) + 8;
            int height = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            if (isForest(level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(height), QuartPos.fromBlock(z)))
                    && generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) == height)
                estimatedForest++;
            estimatedIndex++;
            if (estimatedForest + SAMPLE_SIZE - estimatedIndex < MIN_ESTIMATED_FOREST) {
                MFQM.LOGGER.info("MFQM_NATURAL_GLUE_SAMPLE_REJECT firstChunk={},{} estimatedLandForest={}/{} examined={} reason=surface_forest_density",
                        start.x, start.z, estimatedForest, SAMPLE_SIZE, estimatedIndex);
                candidateIndex++; estimatedIndex = estimatedForest = 0;
            } else if (estimatedIndex == SAMPLE_SIZE) {
                first = start;
                return true;
            }
        }
        return false;
    }

    private static void inspect(LevelChunk chunk) {
        var glue = ModBlocks.byId("glue");
        boolean found = false;
        BlockPos highest = null;
        var sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (!section.getStates().maybeHas(state -> state.is(glue))) continue;
            int bottom = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (!section.getBlockState(x, y, z).is(glue)) continue;
                glueBlocks++;
                found = true;
                if (highest == null || bottom + y > highest.getY())
                    highest = new BlockPos(chunk.getPos().getMinBlockX() + x, bottom + y, chunk.getPos().getMinBlockZ() + z);
            }
        }
        if (found) {
            glueChunks++;
            observations.add(highest);
            MFQM.LOGGER.info("MFQM_NATURAL_GLUE_POOL chunk={},{} surface={} biome={}", chunk.getPos().x, chunk.getPos().z,
                    highest, chunk.getLevel().getBiome(highest).unwrapKey().orElseThrow().identifier());
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private NaturalGlueChecks() {}
}
