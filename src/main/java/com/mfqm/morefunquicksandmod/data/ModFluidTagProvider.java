package com.mfqm.morefunquicksandmod.data;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.tags.MfqmTags;
import com.mfqm.morefunquicksandmod.registry.ModFluids;
import net.minecraft.resources.Identifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.material.Fluid;

import java.util.concurrent.CompletableFuture;

public final class ModFluidTagProvider extends TagsProvider<Fluid> {
    public ModFluidTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, Registries.FLUID, lookupProvider, MFQM.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        ModFluids.entries().keySet().forEach(id -> {
            getOrCreateRawBuilder(MfqmTags.QUICKSAND_FLUIDS).addElement(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, id));
            getOrCreateRawBuilder(MfqmTags.QUICKSAND_FLUIDS).addElement(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "flowing_" + id));
        });
    }
}
