package com.mfqm.morefunquicksandmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mfqm.morefunquicksandmod.client.MfqmClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the disabled optional action out of the controls list without changing stored bindings. */
@Mixin(KeyBindsList.class)
abstract class OptionalStruggleMenuMixin {
    @ModifyExpressionValue(method="<init>",at=@At(value="FIELD",
            target="Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"),remap=false)
    private KeyMapping[] mfqm$visibleBindings(KeyMapping[] mappings) {
        return MfqmClient.visibleKeyMappings(mappings);
    }
}
