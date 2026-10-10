package com.mfqm.morefunquicksandmod.mixin;

import com.mfqm.morefunquicksandmod.entity.BlobEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ordinary captured mobs use Entity's travel path; ServerPlayer overrides it. */
@Mixin(Entity.class)
abstract class CaptureEntityTeleportMixin {
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;stopRiding()V"),remap=false)
    private void mfqm$releaseCaptureBeforeTeleport(TeleportTransition transition, CallbackInfoReturnable<Entity> callback) {
        var entity = (Entity)(Object)this;
        if (entity.getVehicle() instanceof BlobEntity blob) blob.releasePassenger(entity);
    }
}
