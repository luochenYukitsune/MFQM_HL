package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.block.StickyBoardBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class DesertTombPiece extends TemplateStructurePiece {
    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath("mfqm", "desert_tomb");
    public DesertTombPiece(StructureTemplateManager manager, BlockPos pos) {
        super(ModWorldgen.TOMB_PIECE.get(), 0, manager, TEMPLATE, TEMPLATE.toString(), new StructurePlaceSettings(), pos);
    }
    public DesertTombPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ModWorldgen.TOMB_PIECE.get(), tag, manager, id -> new StructurePlaceSettings());
    }
    @Override public void postProcess(net.minecraft.world.level.WorldGenLevel level,
            net.minecraft.world.level.StructureManager manager,
            net.minecraft.world.level.chunk.ChunkGenerator generator, RandomSource random,
            BoundingBox bounds, net.minecraft.world.level.ChunkPos chunk, BlockPos pivot) {
        super.postProcess(level, manager, generator, random, bounds, chunk, pivot);
        for (var info : template.filterBlocks(templatePosition, placeSettings, net.minecraft.world.level.block.Blocks.SPAWNER)) {
            if (bounds.isInside(info.pos()) && level.getBlockEntity(info.pos()) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner)
                spawner.setEntityId(random.nextBoolean() ? net.minecraft.world.entity.EntityType.SKELETON : net.minecraft.world.entity.EntityType.ZOMBIE, random);
        }
        placeStickyBoards(level, bounds);
    }
    private void placeStickyBoards(net.minecraft.world.level.WorldGenLevel level, BoundingBox bounds) {
        if (ModConfig.SERVER_SPEC.isLoaded() && !ModConfig.SERVER.genStickyBoards.get()) return;
        var wholeTemplate = placeSettings.copy().setBoundingBox(null);
        var air = new HashSet<BlockPos>();
        for (var info : template.filterBlocks(templatePosition, wholeTemplate, net.minecraft.world.level.block.Blocks.AIR)) air.add(info.pos());
        var floors = new HashSet<BlockPos>();
        for (var block : List.of(net.minecraft.world.level.block.Blocks.SANDSTONE,
                net.minecraft.world.level.block.Blocks.CUT_SANDSTONE, net.minecraft.world.level.block.Blocks.CHISELED_SANDSTONE))
            for (var info : template.filterBlocks(templatePosition, wholeTemplate, block)) floors.add(info.pos());
        var protectedPositions = new ArrayList<BlockPos>();
        for (var block : List.of(net.minecraft.world.level.block.Blocks.CHEST, net.minecraft.world.level.block.Blocks.SPAWNER))
            for (var info : template.filterBlocks(templatePosition, wholeTemplate, block)) protectedPositions.add(info.pos());
        var candidates = new ArrayList<BlockPos>();
        for (BlockPos pos : air) {
            BlockPos relative = pos.subtract(templatePosition);
            if (relative.getX() <= 3 || relative.getX() >= 44 || relative.getZ() <= 3 || relative.getZ() >= 23
                    || !air.contains(pos.above()) || !floors.contains(pos.below())) continue;
            if (protectedPositions.stream().anyMatch(other -> other.distSqr(pos) <= 9)) continue;
            candidates.add(pos);
        }
        // Stable selection for the whole piece, before clipping to each chunk. Never 1..3 per chunk.
        candidates.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        long seed = level.getSeed() ^ templatePosition.asLong() ^ 0x4D46514D474C5545L;
        java.util.Collections.shuffle(candidates, new java.util.Random(seed));
        int count = 1 + Math.floorMod(seed, 3);
        var selected = new ArrayList<BlockPos>();
        for (BlockPos pos : candidates) {
            if (selected.stream().anyMatch(other -> other.distSqr(pos) < 16)) continue;
            selected.add(pos);
            if (selected.size() >= count) break;
        }
        var board = StickyBoardBlock.coatedState(7);
        for (BlockPos pos : selected)
            if (bounds.isInside(pos) && level.ensureCanWrite(pos) && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir() && board.canSurvive(level, pos))
                level.setBlock(pos, board, 2);
    }
    @Override protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
