package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class DesertTombStructure extends Structure {
    public static final MapCodec<DesertTombStructure> CODEC = simpleCodec(DesertTombStructure::new);
    public DesertTombStructure(StructureSettings settings) { super(settings); }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (ModConfig.SERVER_SPEC.isLoaded() && !ModConfig.SERVER.genDesertTombs.get()) return Optional.empty();
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()) - 12;
        if (y <= context.heightAccessor().getMinY() || y + 20 > context.heightAccessor().getMaxY()) return Optional.empty();
        BlockPos origin = new BlockPos(x - 24, y, z - 13);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new DesertTombPiece(context.structureTemplateManager(), origin))));
    }
    @Override public StructureType<?> type() { return ModWorldgen.TOMB.get(); }
}
