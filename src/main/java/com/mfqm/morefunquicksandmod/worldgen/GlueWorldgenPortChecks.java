package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.block.StickyBoardBlock;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Actual feature and structure placement in the isolated port-check world. */
public final class GlueWorldgenPortChecks {
    private static final Identifier POOL = Identifier.fromNamespaceAndPath("mfqm", "glue_pool");
    private static final TagKey<Biome> ELIGIBLE = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("mfqm", "glue_pool_eligible"));

    public static List<String> verify(ServerLevel level) {
        require(BuiltInRegistries.FEATURE.containsKey(POOL), "Glue pool feature must be registered");
        var configured = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, POOL)).value();
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var forest = biomes.getOrThrow(Biomes.FOREST);
        require(forest.is(ELIGIBLE) && biomes.getOrThrow(Biomes.SWAMP).is(ELIGIBLE), "Forests and swamps are glue-pool candidates");
        require(!biomes.getOrThrow(Biomes.DESERT).is(ELIGIBLE) && !biomes.getOrThrow(Biomes.NETHER_WASTES).is(ELIGIBLE), "Dry and Nether biomes are excluded");
        var placed = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, POOL)).value();
        require(forest.value().getGenerationSettings().hasFeature(placed)
                && biomes.getOrThrow(Biomes.SWAMP).value().getGenerationSettings().hasFeature(placed), "Biome modifier injects glue pool into forests and swamps");
        require(!biomes.getOrThrow(Biomes.DESERT).value().getGenerationSettings().hasFeature(placed), "Biome modifier excludes deserts");
        var results = new ArrayList<String>();
        results.add("glue pool configured feature and wet-Overworld biome tag resolve");
        BlockPos base = new BlockPos(216, 220, 120);
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int y = -9; y <= 2; y++) {
            var at = base.offset(x, y, z); level.getChunkAt(at); saved.put(at, level.getBlockState(at));
        }
        boolean enabled = ModConfig.SERVER.genGluePools.get();
        int chance = ModConfig.SERVER.gluePoolChance.get();
        int shallowSetting = ModConfig.SERVER.gluePoolShallowDepth.get();
        int minSetting = ModConfig.SERVER.gluePoolMinDeepDepth.get();
        int maxSetting = ModConfig.SERVER.gluePoolMaxDeepDepth.get();
        try {
            ModConfig.SERVER.genGluePools.set(true); ModConfig.SERVER.gluePoolChance.set(1);
            ModConfig.SERVER.gluePoolShallowDepth.set(1); ModConfig.SERVER.gluePoolMinDeepDepth.set(3); ModConfig.SERVER.gluePoolMaxDeepDepth.set(4);
            WorldGenLevel wet = fixture(level, forest, null);
            boolean shallow = false, deep = false;
            for (int seed = 0; seed < 64 && !(shallow && deep); seed++) {
                terrain(level, base, false);
                require(configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(seed), base), "Valid sealed wet terrain must produce a pool");
                int depth = 0;
                for (int y = -1; y >= -8; y--) if (level.getBlockState(base.above(y)).is(ModBlocks.byId("glue"))) depth++; else break;
                shallow |= depth == 1; deep |= depth >= 3 && depth <= 4;
                require(depth == 1 || depth >= 3 && depth <= 4, "Pool depth is shallow 1 or deep 3..4");
                require(!level.getBlockState(base.below(depth + 1)).isAir(), "Pool floor remains sealed");
                for (var at : saved.keySet()) if (level.getBlockState(at).is(ModBlocks.byId("glue")))
                    require(new ChunkPos(at).equals(new ChunkPos(base)), "Pool writes stay inside candidate chunk");
            }
            require(shallow && deep, "Deterministic seeds cover both shallow and deep glue pools");
            results.add("actual glue placement produces shallow/deep sealed pools inside its candidate chunk");
            terrain(level, base, false);
            for (int x = -4; x < 0; x++) for (int z = -4; z <= 4; z++)
                level.setBlock(base.offset(x, 0, z), Blocks.DIRT.defaultBlockState(), 2);
            require(configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base),
                    "A one-block natural bank must permit a sealed glue pool instead of requiring perfectly flat terrain");
            require(level.getBlockState(base.below()).is(ModBlocks.byId("glue"))
                    && !level.getBlockState(base.below(5)).isAir(), "Gentle bank pool remains underground and sealed");
            terrain(level, base, false);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                level.setBlock(base.offset(x, 2, z), Blocks.OAK_LEAVES.defaultBlockState(), 2);
            require(configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base.above(3)),
                    "Forest canopy height must resolve natural ground below leaves before pool excavation");
            require(level.getBlockState(base.below()).is(ModBlocks.byId("glue"))
                    && level.getBlockState(base.above(2)).is(Blocks.OAK_LEAVES), "Pool stays below its canopy without removing leaves above headroom");
            results.add("sealed pools accept gentle natural banks and resolve forest canopy to ground without flattening the area");
            terrain(level, base, false);
            for (int x = -4; x < 0; x++) for (int z = -4; z <= 4; z++) for (int y = 0; y < 3; y++)
                level.setBlock(base.offset(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
            require(!configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base)
                    && level.getBlockState(base.below()).is(Blocks.DIRT), "A steep bank is rejected without partial excavation");
            for (var protectedBlock : List.of(Blocks.BEDROCK, Blocks.WATER, Blocks.CHEST, Blocks.OAK_LOG, Blocks.STONE_BRICKS)) {
                terrain(level, base, false);
                level.setBlock(base.below(), protectedBlock.defaultBlockState(), 2);
                require(!configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base)
                        && level.getBlockState(base.below()).is(protectedBlock) && level.getBlockState(base.below(2)).is(Blocks.DIRT),
                        "Ground resolver preserves protected block, liquid, tree or structure: " + protectedBlock);
            }
            results.add("slope adaptation still rejects steep banks, liquids, bedrock, block entities, trunks and artificial structure blocks before excavation");
            terrain(level, base, false);
            ModConfig.SERVER.gluePoolShallowDepth.set(7); ModConfig.SERVER.gluePoolMinDeepDepth.set(7); ModConfig.SERVER.gluePoolMaxDeepDepth.set(7);
            require(configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base)
                    && level.getBlockState(base.below(7)).is(ModBlocks.byId("glue"))
                    && level.getBlockState(base.below(8)).is(Blocks.DIRT), "Configured pool depth changes actual excavation");
            ModConfig.SERVER.gluePoolShallowDepth.set(1); ModConfig.SERVER.gluePoolMinDeepDepth.set(3); ModConfig.SERVER.gluePoolMaxDeepDepth.set(4);
            terrain(level, base, false); ModConfig.SERVER.genGluePools.set(false);
            require(!configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base), "Disabled glue worldgen performs no placement");
            require(level.getBlockState(base.below()).is(Blocks.DIRT), "Disabled generator leaves terrain untouched");
            ModConfig.SERVER.genGluePools.set(true);
            ModConfig.SERVER.gluePoolChance.set(10000); terrain(level, base, false);
            require(!configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base)
                    && level.getBlockState(base.below()).is(Blocks.DIRT), "Configured rarity skips candidates without excavation");
            ModConfig.SERVER.gluePoolChance.set(1);
            var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            require(nether != null && !configured.place(fixture(nether, forest, null), nether.getChunkSource().getGenerator(), RandomSource.create(1), base), "Glue pools reject other dimensions even with an eligible biome");
            terrain(level, base, true);
            require(!configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base), "Open cave under pool rejects the whole placement");
            terrain(level, base, false);
            require(!configured.place(fixture(level, biomes.getOrThrow(Biomes.DESERT), null), level.getChunkSource().getGenerator(), RandomSource.create(1), base), "Feature itself rejects dry biome");
            terrain(level, base, false);
            require(!configured.place(fixture(level, forest, base.below()), level.getChunkSource().getGenerator(), RandomSource.create(1), base), "Forbidden write rejects pool before changing terrain");
            require(level.getBlockState(base.below()).is(Blocks.DIRT), "Rejected placement cannot leave a partial pool");
            terrain(level, base, false);
            // A live ServerLevel has discarded worldgen-only maps after chunk generation.
            // Rebuild the fixture's actual WG surface instead of triggering lazy-map ERROR logging.
            net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(level.getChunkAt(base),
                    java.util.EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG));
            require(configured.place(wet, level.getChunkSource().getGenerator(), RandomSource.create(1), base.west(8)), "Boundary candidate moves its complete seal inside its chunk");
            for (var at : saved.keySet()) if (level.getBlockState(at).is(ModBlocks.byId("glue")))
                require(new ChunkPos(at).equals(new ChunkPos(base.west(8))), "Boundary candidate writes cannot cross chunks");
            results.add("disabled, dry, leaking and unwritable glue sites are rejected before modifying terrain");
        } finally {
            ModConfig.SERVER.genGluePools.set(enabled); ModConfig.SERVER.gluePoolChance.set(chance);
            ModConfig.SERVER.gluePoolShallowDepth.set(shallowSetting); ModConfig.SERVER.gluePoolMinDeepDepth.set(minSetting); ModConfig.SERVER.gluePoolMaxDeepDepth.set(maxSetting);
            for (var entry : saved.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
        verifyTomb(level, results);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(base))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        for (String name : List.of("desert_tomb", "desert_tomb_rare")) {
            var table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath("mfqm", "chests/" + name)));
            boolean board = false, material = false;
            for (int seed = 0; seed < 2048 && !(board && material); seed++) for (var stack : table.getRandomItems(params, seed)) {
                board |= stack.is(com.mfqm.morefunquicksandmod.registry.ModItems.byId("sticky_board"));
                material |= stack.is(com.mfqm.morefunquicksandmod.registry.ModItems.byId("glue_bucket")) || stack.is(net.minecraft.world.item.Items.SLIME_BALL);
            }
            require(board && material, "Actual tomb chest loot must offer boards and glue materials: " + name);
        }
        results.add("both actual tomb chest tables generate sticky boards and glue materials");
        return results;
    }

    private static void terrain(ServerLevel level, BlockPos base, boolean cave) {
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int y = -9; y <= 2; y++)
            level.setBlock(base.offset(x, y, z), y < 0 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        if (cave) for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(base.offset(x, -2, z), Blocks.AIR.defaultBlockState(), 2);
    }

    private static WorldGenLevel fixture(ServerLevel level, Holder<Biome> biome, BlockPos rejected) {
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class}, (proxy, method, args) -> {
            if (method.getName().equals("getBiome")) return biome;
            if (method.getName().equals("ensureCanWrite") && rejected != null && rejected.equals(args[0])) return false;
            try { return method.invoke(level, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
    }

    private static void verifyTomb(ServerLevel level, List<String> results) {
        BlockPos origin = new BlockPos(320, 200, 320);
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = 0; x < 48; x++) for (int z = 0; z < 27; z++) for (int y = 0; y < 20; y++) {
            var at = origin.offset(x, y, z); level.getChunkAt(at); saved.put(at, level.getBlockState(at));
        }
        boolean enabled = ModConfig.SERVER.genStickyBoards.get();
        try {
            ModConfig.SERVER.genStickyBoards.set(true);
            var piece = new DesertTombPiece(level.getStructureManager(), origin);
            var bounds = BoundingBox.fromCorners(origin, origin.offset(47, 19, 26));
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(4), bounds, new ChunkPos(origin), origin);
            int count = 0;
            var boardPositions = new java.util.HashSet<BlockPos>();
            for (var at : saved.keySet()) if (level.getBlockState(at).getBlock() instanceof StickyBoardBlock) {
                count++; boardPositions.add(at); require(StickyBoardBlock.isCoated(level.getBlockState(at)) && level.getBlockState(at).canSurvive(level, at), "Generated trap is coated and supported");
                require(at.getX() > origin.getX() + 3 && at.getX() < origin.getX() + 44 && at.getZ() > origin.getZ() + 3 && at.getZ() < origin.getZ() + 23, "Boards avoid entrance boundary");
            }
            require(count >= 1 && count <= 3, "Actual mod tomb contains one to three sticky boards");
            for (var block : piece.template().filterBlocks(origin, piece.placeSettings(), Blocks.CHEST)) require(level.getBlockState(block.pos()).is(Blocks.CHEST), "Boards cannot replace tomb chests");
            for (var block : piece.template().filterBlocks(origin, piece.placeSettings(), Blocks.SPAWNER)) require(level.getBlockState(block.pos()).is(Blocks.SPAWNER), "Boards cannot replace tomb spawners");
            for (int cx = 20; cx <= 22; cx++) for (int cz = 20; cz <= 21; cz++) {
                var chunk = new ChunkPos(cx, cz);
                var clipped = new BoundingBox(chunk.getMinBlockX(), origin.getY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), origin.getY() + 19, chunk.getMaxBlockZ());
                piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(cx * 31L + cz), clipped, chunk, origin);
            }
            var splitPositions = new java.util.HashSet<BlockPos>();
            for (var at : saved.keySet()) if (level.getBlockState(at).getBlock() instanceof StickyBoardBlock) splitPositions.add(at);
            require(splitPositions.equals(boardPositions), "Chunk-by-chunk tomb placement uses the same global one-to-three traps");
            ModConfig.SERVER.genStickyBoards.set(false);
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(4), bounds, new ChunkPos(origin), origin);
            for (var at : saved.keySet()) require(!(level.getBlockState(at).getBlock() instanceof StickyBoardBlock), "Disabled tomb trap generation adds no board");
            results.add("actual tomb placement preserves chest/spawners, adds 1..3 supported interior boards and respects config");
        } finally {
            ModConfig.SERVER.genStickyBoards.set(enabled);
            for (var entry : saved.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private GlueWorldgenPortChecks() {}
}
