package com.mfqm.morefunquicksandmod.data;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = MFQM.MOD_ID, value = Dist.CLIENT)
public final class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        // Register definitions first so tag validation can resolve all custom damage types.
        event.createDatapackRegistryObjects(new RegistrySetBuilder()
                .add(Registries.DAMAGE_TYPE, ModDamageTypeBootstrap::bootstrap));

        // Legacy model/animation definitions are converted by tools/restore_legacy_resources.py.
        event.createProvider(ModDamageTypeTagProvider::new);
        event.createProvider(ModTagProvider::new);
        event.createProvider(ModFluidTagProvider::new);
        event.createProvider(ModBiomeTagProvider::new);
        // Translations are maintained in src/main/resources/assets/mfqm/lang/en_us.json.
    }

    private DataGenerators() {}
}
