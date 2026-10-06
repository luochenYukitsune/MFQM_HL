package com.mfqm.morefunquicksandmod.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;

public final class LegacyFuelItem extends Item {
    private final int fuel;
    public LegacyFuelItem(int fuel, Properties properties) { super(properties); this.fuel = fuel; }
    @Override public int getBurnTime(ItemStack stack, RecipeType<?> type, FuelValues values) { return fuel; }
}
