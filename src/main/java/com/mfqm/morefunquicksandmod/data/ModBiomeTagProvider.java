package com.mfqm.morefunquicksandmod.data;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.tags.MfqmTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.Identifier;

import java.util.concurrent.CompletableFuture;

public final class ModBiomeTagProvider extends TagsProvider<Biome> {
    public ModBiomeTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, Registries.BIOME, lookupProvider, MFQM.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        add(MfqmTags.SPAWNS_MUD, "swamp mangrove_swamp jungle sparse_jungle bamboo_jungle");
        add(MfqmTags.SPAWNS_TAR, "desert badlands swamp forest wooded_badlands");
        add(MfqmTags.SPAWNS_QS_DESERT, "desert badlands eroded_badlands wooded_badlands");
        add(MfqmTags.SPAWNS_QS_JUNGLE, "jungle sparse_jungle bamboo_jungle mangrove_swamp");
        add(MfqmTags.MUDDY_BLOB_SPAWNABLE, "swamp mangrove_swamp");
        add(MfqmTags.SAND_BLOB_SPAWNABLE, "desert badlands");
        add(MfqmTags.TAR_SLIME_SPAWNABLE, "badlands swamp");
    }
    private void add(net.minecraft.tags.TagKey<Biome> tag, String biomes) {
        for (String biome : biomes.split(" ")) getOrCreateRawBuilder(tag).addElement(Identifier.withDefaultNamespace(biome));
    }
}
