package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.worldgen.LegacyStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SinkingDrinkItem extends Item {
    public SinkingDrinkItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !level.getBlockState(pos).is(Blocks.CACTUS)) return InteractionResult.PASS;
        BlockPos base = pos.below(2);
        for (BlockPos part : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 9, 3))) {
            if (!level.hasChunkAt(part) || !level.mayInteract(player, part) || !player.mayUseItemAt(part, Direction.UP, context.getItemInHand())
                    || level.getBlockEntity(part) != null || part.getY() < level.getMinY()) return InteractionResult.FAIL;
            BlockState state = level.getBlockState(part);
            if (state.getDestroySpeed(level, part) < 0) return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            if (!LegacyStructures.growBlossom(level, base)) return InteractionResult.FAIL;
            player.setItemInHand(context.getHand(), ItemUtils.createFilledResult(context.getItemInHand(), player, new ItemStack(Items.GLASS_BOTTLE)));
        }
        return InteractionResult.SUCCESS;
    }
}
