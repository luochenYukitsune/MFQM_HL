package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.MFQM;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

@EventBusSubscriber(modid = MFQM.MOD_ID)
public final class CaptureEvents {
    @SubscribeEvent public static void mounting(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting().isAlive() && !event.getEntityMounting().isSpectator()
                && event.getEntityBeingMounted() instanceof BlobEntity blob && blob.preventsDismount()) event.setCanceled(true);
    }
    private CaptureEvents() {}
}
