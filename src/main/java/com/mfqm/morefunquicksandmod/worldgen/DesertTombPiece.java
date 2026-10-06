package com.mfqm.morefunquicksandmod.worldgen;

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
    }
    @Override protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
