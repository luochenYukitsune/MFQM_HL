package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class SplashSinkingPotionItem extends Item {
    public SplashSinkingPotionItem(Properties properties) { super(properties.stacksTo(16)); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            ModEntities.throwSinkingPotion(server);
            if (!player.hasInfiniteMaterials()) player.getItemInHand(hand).shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
