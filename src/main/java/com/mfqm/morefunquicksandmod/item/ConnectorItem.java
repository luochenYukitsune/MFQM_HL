package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;

public final class ConnectorItem extends Item {
    private final String kind;
    public ConnectorItem(String kind, Properties properties) { super(properties); this.kind = kind; }
    @Override public InteractionResult use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        if (kind.equals("rescue")) { player.startUsingItem(hand); return InteractionResult.SUCCESS; }
        if ((kind.equals("rope") || kind.equals("hook")) && player.getAbilities().invulnerable) return InteractionResult.FAIL;
        if (kind.equals("long_stick") && player.getFoodData().getFoodLevel() <= 6) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer server) ModEntities.spawnConnector(server, kind, player.getItemInHand(hand));
        if (kind.equals("long_stick") || kind.equals("hook") || kind.equals("rescue")) player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        return context.getPlayer() == null ? InteractionResult.PASS : use(context.getLevel(), context.getPlayer(), context.getHand());
    }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public void onUseTick(net.minecraft.world.level.Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (kind.equals("long_stick") && entity instanceof ServerPlayer player && level.getGameTime() % 256 == 0) {
            var at = level.getBlockState(player.blockPosition());
            if (at.getBlock().builtInRegistryHolder().getRegisteredName().startsWith("mfqm:") && player.getFoodData().getFoodLevel() > 6)
                player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - 1);
        }
    }
    @Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (kind.equals("rescue") && entity instanceof Player player) {
            if (player.getMainHandItem() != stack && player.getOffhandItem() != stack) stack.setCount(0);
            else if (!player.isUsingItem()) player.startUsingItem(player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
    }
    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (kind.equals("rescue")) { entity.discard(); return true; }
        return false;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        String prefix = switch (kind) { case "long_stick" -> "itemLongStick"; case "rope" -> "itemRope"; case "hook" -> "itemGrapplingHook"; default -> ""; };
        if (prefix.isEmpty()) return;
        int descriptionLines = kind.equals("long_stick") ? 4 : 3;
        for (int i = 1; i <= descriptionLines; i++) lines.accept(Component.translatable(prefix + ".instruction" + i));
        int heading = descriptionLines + 1;
        lines.accept(Component.translatable(prefix + ".instruction" + heading));
        int last = kind.equals("long_stick") ? 9 : 10;
        for (int i = heading + 1; i < last; i += 2) lines.accept(Component.translatable(prefix + ".instruction" + i).append(" - ")
                .append(Component.translatable(prefix + ".instruction" + (i + 1))));
    }
}
