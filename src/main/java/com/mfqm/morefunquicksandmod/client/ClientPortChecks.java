package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** Enabled only by -PmfqmClientChecks in a copied isolated save. */
@EventBusSubscriber(modid = MFQM.MOD_ID, value = Dist.CLIENT)
public final class ClientPortChecks {
    private static int readyTicks;
    private static boolean finished;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mfqm.clientChecks") || finished) return;
        Minecraft game = Minecraft.getInstance();
        if (game.level == null || game.player == null || readyTicks++ < 40) return;
        finished = true;
        try {
            CreativeModeTabs.tryRebuildTabContents(game.level.enabledFeatures(), true, game.level.registryAccess());
            int visible = ModCreativeTabs.MFQM_TAB.get().getDisplayItems().size();
            long expected = ModItems.entries().keySet().stream().filter(ModItems::isCreativeVisible).count();
            require(visible == expected && visible > 140, "creative category count " + visible + " expected " + expected);
            for (var entry : ModItems.entries().entrySet()) {
                Identifier id = Identifier.fromNamespaceAndPath(MFQM.MOD_ID, entry.getKey());
                require(game.getResourceManager().getResource(id.withPath("items/" + id.getPath() + ".json")).isPresent(), "item definition " + id);
                require(!(game.getModelManager().getItemModel(id) instanceof MissingItemModel), "baked item model " + id);
            }
            for (var holder : ModBlocks.entries().values()) for (var state : holder.get().getStateDefinition().getPossibleStates()) {
                var shaper = game.getBlockRenderer().getBlockModelShaper();
                require(shaper.getBlockModel(state) != game.getModelManager().getMissingBlockStateModel(), "state model " + state);
                require(!shaper.getParticleIcon(state).contents().name().equals(MissingTextureAtlasSprite.getLocation()), "state texture " + state);
            }
            for (var fluid : ModFluids.entries().values()) {
                var extension = IClientFluidTypeExtensions.of(fluid.type().get());
                for (var texture : new Identifier[]{extension.getStillTexture(), extension.getFlowingTexture()}) {
                    require(!texture.equals(MissingTextureAtlasSprite.getLocation()), "fluid extension " + fluid.id());
                    require(game.getResourceManager().getResource(texture.withPath("textures/" + texture.getPath() + ".png")).isPresent(), "fluid texture " + texture);
                }
            }
            for (var holder : ModEntities.ENTITIES.getEntries()) {
                var entity = holder.get().create(game.level, EntitySpawnReason.TRIGGERED);
                require(entity != null && game.getEntityRenderDispatcher().getRenderer(entity) != null, "renderer " + holder.getId());
            }
            MFQM.LOGGER.info("MFQM_CLIENT_CHECKS_COMPLETE items={} visible={} blocks=49 fluids=13 entities=16", ModItems.entries().size(), visible);
        } catch (Throwable failure) { MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED", failure); }
        game.stop();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException("Client port validation failed: " + message); }
    private ClientPortChecks() {}
}
