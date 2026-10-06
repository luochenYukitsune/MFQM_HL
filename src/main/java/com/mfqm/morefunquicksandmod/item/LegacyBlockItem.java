package com.mfqm.morefunquicksandmod.item;

import java.util.function.Supplier;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.entity.FuelValues;

/** Separate item IDs preserve selectable 1.7 metadata variants without duplicating blocks. */
public final class LegacyBlockItem extends BlockItem {
    public static final IntegerProperty VARIANT = com.mfqm.morefunquicksandmod.block.LegacyStateBlock.VARIANT;
    private final int fuel;
    private final int variant;

    public LegacyBlockItem(Supplier<? extends Block> block, int variant, int fuel, Item.Properties properties) {
        super(block.get(), properties.component(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY.with(VARIANT, variant)));
        this.fuel = fuel;
        this.variant = variant;
    }
    @Override public void registerBlocks(Map<Block, Item> blocks, Item item) {
        // Extra metadata items must not replace the block's canonical asItem() mapping.
        if (variant == 0) super.registerBlocks(blocks, item);
    }
    @Override public int getBurnTime(ItemStack stack, RecipeType<?> recipeType, FuelValues values) {
        return fuel > 0 ? fuel : super.getBurnTime(stack, recipeType, values);
    }
}
