package com.mfqm.morefunquicksandmod.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's fluid jump adds upward velocity even when canSwim is false. Prevent
 * just that vanilla impulse before travel moves the entity, on both logical sides.
 * Raw jumping/input remains intact for struggle rules; rescue velocity is untouched.
 */
@Mixin(LivingEntity.class)
abstract class GlueJumpMixin {
    @Inject(method="travel(Lnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),cancellable=true,remap=false)
    private void mfqm$travelInSinkingMedium(Vec3 input,CallbackInfo callback) {
        if(QuicksandPhysics.travel((LivingEntity)(Object)this,input))callback.cancel();
    }

    @Inject(method="travel(Lnet/minecraft/world/phys/Vec3;)V",at=@At("TAIL"),remap=false)
    private void mfqm$keepBondsPastMediumEdge(Vec3 input,CallbackInfo callback) {
        QuicksandPhysics.predictOutsidePull((LivingEntity)(Object)this);
    }

    @WrapWithCondition(method="aiStep()V",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/LivingEntity;jumpInFluid(Lnet/neoforged/neoforge/fluids/FluidType;)V"),
            remap=false,require=3,expect=3)
    private boolean mfqm$allowFluidJump(LivingEntity entity,FluidType type) {
        return !QuicksandPhysics.isJumpHeld(entity);
    }

    @WrapWithCondition(method="aiStep()V",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/LivingEntity;jumpFromGround()V"),remap=false,require=1)
    private boolean mfqm$allowGroundJump(LivingEntity entity) {
        return !QuicksandPhysics.isJumpHeld(entity);
    }
}
