package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MFQM.MOD_ID);

    public static final Supplier<CreativeModeTab> MFQM_TAB = CREATIVE_TABS.register("mfqm_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mfqm"))
                    .icon(() -> new ItemStack(ModItems.byId("grappling_hook")))
                    .displayItems((params, output) -> {
                        ModItems.entries().forEach((id, holder) -> {
                            if(id.equals("sticky_board"))output.accept(com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedStack(7));
                            if (ModItems.isCreativeVisible(id) && holder.get().isEnabled(params.enabledFeatures())) output.accept(holder.get());
                        });
                    })
                    .build());

    private ModCreativeTabs() {}
}
