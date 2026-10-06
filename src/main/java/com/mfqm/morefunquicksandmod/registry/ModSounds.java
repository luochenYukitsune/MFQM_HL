package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT, MFQM.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> BEE_SAY = SOUNDS.register("mob.bee.say",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "mob.bee.say")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BEE_HURT = SOUNDS.register("mob.bee.hurt",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "mob.bee.hurt")));
    private ModSounds() {}
}
