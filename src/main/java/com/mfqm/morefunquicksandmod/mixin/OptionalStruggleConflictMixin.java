package com.mfqm.morefunquicksandmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mfqm.morefunquicksandmod.client.MfqmClient;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A hidden action must not mark vanilla F as a duplicate in the controls menu. */
@Mixin(targets="net.minecraft.client.gui.screens.options.controls.KeyBindsList$KeyEntry")
abstract class OptionalStruggleConflictMixin {
    @ModifyExpressionValue(method="refreshEntry",at=@At(value="FIELD",
            target="Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"),remap=false)
    private KeyMapping[] mfqm$visibleConflicts(KeyMapping[] mappings) {
        return MfqmClient.visibleKeyMappings(mappings);
    }
}
