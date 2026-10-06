package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public record SwampColorModifier() implements BiomeModifier {
    public static final MapCodec<SwampColorModifier> CODEC = MapCodec.unit(new SwampColorModifier());
    @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || (ModConfig.SERVER_SPEC.isLoaded() && !ModConfig.SERVER.customSwampWaterColor.get())) return;
        if (biome.unwrapKey().map(key -> !java.util.Set.of("thebetweenlands", "betweenlands").contains(key.identifier().getNamespace())
                && (key.identifier().getPath().contains("swamp") || key.identifier().getPath().contains("marsh"))).orElse(false)) {
            int color = builder.getSpecialEffects().waterColor();
            int target = 13390080;
            int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
            int tr = target >> 16 & 255, tg = target >> 8 & 255, tb = target & 255;
            if (r >= 254) r -= (Math.abs(tg - g) + Math.abs(tb - b)) / 2;
            int blue = Math.clamp((int)Math.floor((tb - b) / 1.1F + b / 2), 0, 255);
            int green = Math.clamp((int)Math.floor((tg - g) / 2 / 1.1F + g), 0, 255);
            int red = Math.clamp((int)Math.floor((tr - r) / 1.1F + r), 0, 255);
            builder.getSpecialEffects().waterColor(red << 16 | green << 8 | blue);
        }
    }
    @Override public MapCodec<? extends BiomeModifier> codec() { return CODEC; }
}
