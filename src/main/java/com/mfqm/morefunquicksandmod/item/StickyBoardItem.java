package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.block.StickyBoardBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import java.util.function.Consumer;

/** One stable item identity, with explicit empty/coated names and preserved coating components. */
public final class StickyBoardItem extends BlockItem {
    public StickyBoardItem(Block block,Properties properties){super(block,properties);}
    public static int charge(ItemStack stack) {
        var property=stack.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(StickyBoardBlock.CHARGE);
        return property==null?0:property;
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(charge(stack)>0?"item.mfqm.sticky_board.coated":"item.mfqm.sticky_board.empty");
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) {
        int amount=charge(stack);
        lines.accept(Component.translatable(amount>0?"tooltip.mfqm.sticky_board.coated":"tooltip.mfqm.sticky_board.empty",amount)
                .withStyle(amount>0?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.GRAY));
    }
}
