package com.mfqm.morefunquicksandmod.data;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.tags.MfqmTags;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.resources.Identifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import java.util.concurrent.CompletableFuture;

public final class ModTagProvider extends BlockTagsProvider {
    public ModTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, MFQM.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        ModBlocks.entries().keySet().stream().filter(id -> !java.util.Set.of("peat", "wax_wood", "hardened_clay", "honeycomb", "sandstone_trap", "custom_lily_pad", "moor_grass", "lure", "blossom", "blossom_slab", "gas").contains(id))
                .forEach(id -> getOrCreateRawBuilder(MfqmTags.SINKING_BLOCKS).addElement(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, id)));
        for (String id : "mud morass wax mucus sinking_slime dense_web tar swallowing_flesh honey larvae sinking_clay brown_clay".split(" ")) getOrCreateRawBuilder(MfqmTags.STICKY_BLOCKS).addElement(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, id));
        getOrCreateRawBuilder(MfqmTags.GAS_BLOCKS).addElement(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "gas"));
        for (String id : "moor_grass moor morass blossom".split(" ")) getOrCreateRawBuilder(MfqmTags.FERTILIZABLE).addElement(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, id));
    }
}
