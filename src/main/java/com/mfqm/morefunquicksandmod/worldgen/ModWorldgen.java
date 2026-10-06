package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

public final class ModWorldgen {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MFQM.MOD_ID);
    public static final Supplier<LegacyTerrainFeature> TERRAIN = FEATURES.register("legacy_terrain", LegacyTerrainFeature::new);
    public static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MFQM.MOD_ID);
    public static final Supplier<StructureType<DesertTombStructure>> TOMB = STRUCTURES.register("desert_tomb", () -> () -> DesertTombStructure.CODEC);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MFQM.MOD_ID);
    public static final Supplier<StructurePieceType> TOMB_PIECE = PIECES.register("desert_tomb", () -> (context, tag) -> new DesertTombPiece(context.structureTemplateManager(), tag));
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MFQM.MOD_ID);
    public static final Supplier<MapCodec<SwampColorModifier>> SWAMP_COLOR = MODIFIERS.register("swamp_color", () -> SwampColorModifier.CODEC);
    public static final Supplier<MapCodec<TerrainBiomeModifier>> TERRAIN_MEMBERSHIP = MODIFIERS.register("terrain_membership", () -> TerrainBiomeModifier.CODEC);

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
        STRUCTURES.register(bus);
        PIECES.register(bus);
        MODIFIERS.register(bus);
    }
    private ModWorldgen() {}
}
