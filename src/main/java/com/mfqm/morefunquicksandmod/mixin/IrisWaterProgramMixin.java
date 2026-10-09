package com.mfqm.morefunquicksandmod.mixin;

import com.mfqm.morefunquicksandmod.client.IterationShaderBridge;
import java.util.Optional;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional Iris-only source getters. Draw-buffer directives stay unchanged. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.shaderpack.programs.ProgramSource",remap=false)
abstract class IrisWaterProgramMixin {
    @Shadow @Final private String name;
    @Inject(method="getVertexSource",at=@At("RETURN"),cancellable=true,require=0)
    private void mfqm$viscousAlphaPath(CallbackInfoReturnable<Optional<String>> callback) {
        callback.setReturnValue(callback.getReturnValue().map(source->IterationShaderBridge.vertex(name,source)));
    }
    @Inject(method="getFragmentSource",at=@At("RETURN"),cancellable=true,require=0)
    private void mfqm$viscousDepthAndLight(CallbackInfoReturnable<Optional<String>> callback) {
        callback.setReturnValue(callback.getReturnValue().map(source->IterationShaderBridge.fragment(name,source)));
    }
}
