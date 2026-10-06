package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE, MFQM.MOD_ID);
    private ModDataComponents() {}
}
