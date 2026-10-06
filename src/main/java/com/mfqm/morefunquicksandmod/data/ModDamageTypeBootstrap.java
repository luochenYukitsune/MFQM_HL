package com.mfqm.morefunquicksandmod.data;

import com.mfqm.morefunquicksandmod.registry.ModDamageTypes;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypeBootstrap {
    public static void bootstrap(BootstrapContext<DamageType> context) {
        register(context, ModDamageTypes.QUICKSAND_SUFFOCATION);
        register(context, ModDamageTypes.TAR_BURN);
        register(context, ModDamageTypes.GAS_ASPHYXIATION);
        register(context, ModDamageTypes.ACID_DISSOLVE);
        register(context, ModDamageTypes.FLESH_CONSUMPTION);
    }

    private static void register(BootstrapContext<DamageType> context, ResourceKey<DamageType> key) {
        // Preserve the existing death.attack.<path> translation keys and vanilla defaults.
        // Environmental damage does not add hunger exhaustion; damage amounts belong to future gameplay code.
        context.register(key, new DamageType(key.identifier().getPath(), 0.0F));
    }

    private ModDamageTypeBootstrap() {}
}
