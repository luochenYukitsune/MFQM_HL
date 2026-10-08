package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.registry.ModFluids;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = MFQM.MOD_ID, value = Dist.CLIENT)
public final class LegacyFluidRendering {
    private static final Map<String, String> TEXTURES = Map.ofEntries(
            Map.entry("bog", "bog"), Map.entry("dry_quicksand", "sand"), Map.entry("jungle_quicksand", "junglequicksand"),
            Map.entry("liquid_mire", "mire"), Map.entry("stable_liquid_mire", "mire"), Map.entry("sinky_liquid", "sinkyliquid"),
            Map.entry("sinking_slime", "slime"), Map.entry("mucus", "mucus"), Map.entry("tar", "tar"), Map.entry("acid", "acid"),
            Map.entry("slurry", "slurry"), Map.entry("honey", "honey"), Map.entry("liquid_chocolate", "choco"), Map.entry("glue","glue"));
    @SubscribeEvent public static void register(RegisterClientExtensionsEvent event) {
        ModFluids.entries().forEach((id, entry) -> event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public Identifier getStillTexture() { return Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "blocks/" + TEXTURES.get(id) + "_still"); }
            @Override public Identifier getFlowingTexture() { return Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "blocks/" + TEXTURES.get(id) + (id.equals("glue")?"_flow":"_flowing")); }
        }, entry.type().get()));
    }
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        for(String id:new String[]{"glue","honey"}) {
            var entry=ModFluids.entry(id);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(entry.source().get(),net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(entry.flowing().get(),net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
        }
    }
    private LegacyFluidRendering() {}
}
