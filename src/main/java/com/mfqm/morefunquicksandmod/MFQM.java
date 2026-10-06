package com.mfqm.morefunquicksandmod;

import com.mfqm.morefunquicksandmod.registry.*;
import com.mfqm.morefunquicksandmod.item.ItemGameplayHooks;
import com.mfqm.morefunquicksandmod.item.ItemRecipeCondition;
import com.mfqm.morefunquicksandmod.compat.CompatRecipes;
import com.mfqm.morefunquicksandmod.network.ModNetworking;
import com.mfqm.morefunquicksandmod.worldgen.ModWorldgen;
import com.mfqm.morefunquicksandmod.worldgen.LegacyChunkConversions;
import com.mfqm.morefunquicksandmod.validation.ModPortChecks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MFQM.MOD_ID)
public final class MFQM {
    public static final String MOD_ID = "mfqm";
    public static final Logger LOGGER = LoggerFactory.getLogger("MFQM");

    public MFQM(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("MFQM initializing...");

        // Register all DeferredRegister holders to the mod event bus
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModEntities.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModWorldgen.register(modEventBus);
        com.mfqm.morefunquicksandmod.worldgen.RegistryBonusLootEntry.TYPES.register(modEventBus);
        ItemRecipeCondition.CONDITIONS.register(modEventBus);
        ItemRecipeCondition.register(NeoForge.EVENT_BUS);
        CompatRecipes.register(NeoForge.EVENT_BUS);
        ModNetworking.register(modEventBus);
        ItemGameplayHooks.register(NeoForge.EVENT_BUS);
        LegacyChunkConversions.register(NeoForge.EVENT_BUS);
        if (Boolean.getBoolean("mfqm.portChecks")) ModPortChecks.register(NeoForge.EVENT_BUS);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModAttachmentTypes.ATTACHMENTS.register(modEventBus);

        // Register configs
        modContainer.registerConfig(Type.SERVER, ModConfig.SERVER_SPEC);
        modContainer.registerConfig(Type.CLIENT, ModConfig.CLIENT_SPEC);
        modContainer.registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);

        LOGGER.info("MFQM initialized.");
    }
}
