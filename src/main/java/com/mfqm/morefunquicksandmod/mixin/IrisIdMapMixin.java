package com.mfqm.morefunquicksandmod.mixin;

import com.mfqm.morefunquicksandmod.client.IterationShaderBridge;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.irisshaders.iris.shaderpack.IdMap",remap=false)
abstract class IrisIdMapMixin {
    @Inject(method="readProperties",at=@At("RETURN"),cancellable=true,require=0)
    private static void mfqm$stickyFluidIds(Path shaderPath,String name,CallbackInfoReturnable<String> callback) {
        callback.setReturnValue(IterationShaderBridge.properties(shaderPath,name,callback.getReturnValue()));
    }
}
