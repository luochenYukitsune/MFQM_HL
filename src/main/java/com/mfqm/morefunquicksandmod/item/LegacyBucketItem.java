package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** The original buckets include solids and non-drainable fluids, so placement uses source states. */
public final class LegacyBucketItem extends Item {
    private final String block;
    private final int variant;
    private final int fuel;
    public LegacyBucketItem(String block, int variant, int fuel, Properties properties) {
        super(properties.stacksTo(1).craftRemainder(Items.BUCKET));
        this.block = block; this.variant = variant; this.fuel = fuel;
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos clicked = hit.getBlockPos();
        BlockPos pos = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
        if (!level.mayInteract(player, clicked) || !player.mayUseItemAt(pos, hit.getDirection(), stack)) return InteractionResult.FAIL;
        if (block.equals("slurry") && hit.getDirection() == Direction.UP && FertilizerItem.grow(level, clicked, player, 3)) {
            if (!level.isClientSide()) player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            return InteractionResult.SUCCESS;
        }
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        if (level instanceof ServerLevel) {
            BlockState state = block.equals("minecraft:sand") ? Blocks.SAND.defaultBlockState() : ModBlocks.byId(block).defaultBlockState();
            if (state.hasProperty(LegacyBlockItem.VARIANT)) state = state.setValue(LegacyBlockItem.VARIANT, variant);
            if (!level.setBlock(pos, state, 3)) return InteractionResult.FAIL;
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1, 1);
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
        }
        return InteractionResult.SUCCESS;
    }
    @Override public int getBurnTime(ItemStack stack, RecipeType<?> recipeType, FuelValues values) {
        return fuel > 0 ? fuel : super.getBurnTime(stack, recipeType, values);
    }
}
