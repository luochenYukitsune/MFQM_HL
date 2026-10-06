package com.mfqm.morefunquicksandmod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;

public final class FertilizerItem extends Item {
    public FertilizerItem(Properties properties) { super(properties); }
    public static boolean grow(Level level, BlockPos pos, Player player, int attempts) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BonemealableBlock crop) || !crop.isValidBonemealTarget(level, pos, state)) return false;
        if (level instanceof ServerLevel server) {
            for (int i = 0; i < attempts; i++) {
                state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof BonemealableBlock current) || !current.isValidBonemealTarget(level, pos, state)) break;
                if (current.isBonemealSuccess(level, level.random, pos, state)) current.performBonemeal(server, level.random, pos, state);
            }
            level.levelEvent(1505, pos, 15);
        }
        return true;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !context.getLevel().mayInteract(player, context.getClickedPos())
                || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), context.getItemInHand())) return InteractionResult.FAIL;
        if (!grow(context.getLevel(), context.getClickedPos(), player, 2)) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide() && !player.hasInfiniteMaterials()) context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("itemFertilizer.instruction1"));
    }
}
