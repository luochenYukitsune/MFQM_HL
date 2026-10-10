package com.mfqm.morefunquicksandmod.mixin;

import com.mfqm.morefunquicksandmod.entity.BlobEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Release only after the final travel-event decision and only for independent travel. */
@Mixin(ServerPlayer.class)
abstract class CaptureTeleportMixin {
    @Inject(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;removeVehicle()V"),remap=false)
    private void mfqm$releaseCaptureBeforeTeleport(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callback) {
        var player = (ServerPlayer)(Object)this;
        if (player.getVehicle() instanceof BlobEntity blob) blob.releasePassenger(player);
    }
}
