package com.mfqm.morefunquicksandmod.data;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.registry.ModDamageTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;

import java.util.concurrent.CompletableFuture;

public final class ModDamageTypeTagProvider extends TagsProvider<DamageType> {
    public ModDamageTypeTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, Registries.DAMAGE_TYPE, lookupProvider, MFQM.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        getOrCreateRawBuilder(DamageTypeTags.BYPASSES_ARMOR)
                .addElement(ModDamageTypes.QUICKSAND_SUFFOCATION.identifier())
                .addElement(ModDamageTypes.TAR_BURN.identifier())
                .addElement(ModDamageTypes.GAS_ASPHYXIATION.identifier())
                .addElement(ModDamageTypes.ACID_DISSOLVE.identifier())
                .addElement(ModDamageTypes.FLESH_CONSUMPTION.identifier());

        getOrCreateRawBuilder(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                .addElement(ModDamageTypes.QUICKSAND_SUFFOCATION.identifier());
    }
}
