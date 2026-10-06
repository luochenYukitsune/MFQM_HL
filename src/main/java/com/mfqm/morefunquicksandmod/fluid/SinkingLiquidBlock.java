package com.mfqm.morefunquicksandmod.fluid;

import com.mfqm.morefunquicksandmod.gameplay.MediumReactions;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.BlockHitResult;

/** Actual flowing fluid block, separate from the porous solid sinking blocks. */
public final class SinkingLiquidBlock extends LiquidBlock {
    public final String legacyId;
    public SinkingLiquidBlock(String id, FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
        this.legacyId = id;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 20);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MediumReactions.tick(legacyId, state, level, pos, random);
        if (level.getBlockState(pos).is(this)) level.scheduleTick(pos, this, 20);
    }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        MediumReactions.tick(legacyId, state, level, pos, random);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if ((legacyId.equals("honey") || legacyId.equals("liquid_chocolate")) && player.getFoodData().needsFood()) {
            if (!level.isClientSide()) player.getFoodData().eat(legacyId.equals("honey") ? 1 : 2, .1f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if ((legacyId.equals("liquid_mire") || legacyId.equals("stable_liquid_mire")) && stack.is(Items.GLASS_BOTTLE)) {
            if (!level.isClientSide()) {
                var filled = new ItemStack(ModItems.byId("bottle_of_mire"));
                if (!player.hasInfiniteMaterials()) stack.shrink(1);
                if (stack.isEmpty()) player.setItemInHand(hand, filled);
                else if (!player.getInventory().add(filled)) player.drop(filled, false);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
    @Override public ItemStack pickupBlock(net.minecraft.world.entity.LivingEntity picker, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
        if (fluid.getBucket() == Items.AIR) return ItemStack.EMPTY;
        return super.pickupBlock(picker, level, pos, state);
    }
}
