package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.registry.ModEntities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;

/** A 32-shot world-source sampler. Ammo is a registry identifier, never a numeric block ID. */
public final class LiquidGunItem extends Item {
    public LiquidGunItem(Properties properties) { super(properties.stacksTo(1)); }
    public static int shots(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("mfqm_shots", 32); }
    public static BlockState loadedState(ItemStack stack) {
        String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("mfqm_liquid", "minecraft:water");
        Identifier key = Identifier.tryParse(id);
        return key == null ? Blocks.WATER.defaultBlockState() : BuiltInRegistries.BLOCK.getOptional(key).orElse(Blocks.WATER).defaultBlockState();
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() && shots(stack) > 0) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || !level.mayInteract(player, hit.getBlockPos())
                || !player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), stack)) return InteractionResult.PASS;
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (!state.getFluidState().isSource() || !(state.getBlock() instanceof LiquidBlock)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (!level.setBlock(hit.getBlockPos(), Blocks.AIR.defaultBlockState(), 3)) return InteractionResult.FAIL;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putString("mfqm_liquid", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                tag.putInt("mfqm_shots", 32);
            });
            level.playSound(null, hit.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1, 1);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!(entity instanceof ServerPlayer player) || shots(stack) <= 0) return false;
        ModEntities.fireLiquid(player, loadedState(stack));
        if (!player.hasInfiniteMaterials()) CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("mfqm_shots", Math.max(0, shots(stack) - 1)));
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1, 1);
        return true;
    }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public boolean isBarVisible(ItemStack stack) { return shots(stack) < 32; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(Math.clamp(shots(stack), 0, 32) * 13f / 32); }
    @Override public int getBarColor(ItemStack stack) { return 0x38b7eb; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(shots(stack) == 0 ? Component.translatable("itemLiqGun.empty") : loadedState(stack).getBlock().getName());
        lines.accept(Component.literal(shots(stack) + "/32"));
        lines.accept(Component.translatable("itemLiqGun.instruction1"));
        lines.accept(Component.translatable("itemLiqGun.instruction2"));
    }
}
