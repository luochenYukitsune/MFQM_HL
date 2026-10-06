package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> QUICKSAND_SUFFOCATION =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "quicksand_suffocation"));
    public static final ResourceKey<DamageType> TAR_BURN =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "tar_burn"));
    public static final ResourceKey<DamageType> GAS_ASPHYXIATION =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "gas_asphyxiation"));
    public static final ResourceKey<DamageType> ACID_DISSOLVE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "acid_dissolve"));
    public static final ResourceKey<DamageType> FLESH_CONSUMPTION =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "flesh_consumption"));

    private ModDamageTypes() {}
}
