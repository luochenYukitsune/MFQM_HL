package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE, MFQM.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUD_BUBBLE =
            PARTICLES.register("mud_bubble", () -> new SimpleParticleType(false));
    private ModParticles() {}
}
