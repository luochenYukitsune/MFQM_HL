package com.mfqm.morefunquicksandmod.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/** Namespace/config driven membership also works in optional dimensions without numeric biome IDs. */
public record TerrainBiomeModifier(Holder<PlacedFeature> surface, Holder<PlacedFeature> nether) implements BiomeModifier {
    public static final MapCodec<TerrainBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PlacedFeature.CODEC.fieldOf("surface_feature").forGetter(TerrainBiomeModifier::surface),
            PlacedFeature.CODEC.fieldOf("nether_feature").forGetter(TerrainBiomeModifier::nether)).apply(instance, TerrainBiomeModifier::new));
    @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || biome.is(BiomeTags.IS_END)) return;
        String namespace = biome.unwrapKey().orElseThrow().identifier().getNamespace();
        if (!LegacyTerrainFeature.compatEnabled(namespace)) return;
        builder.getGenerationSettings().addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, biome.is(BiomeTags.IS_NETHER) ? nether : surface);
    }
    @Override public MapCodec<? extends BiomeModifier> codec() { return CODEC; }
}
