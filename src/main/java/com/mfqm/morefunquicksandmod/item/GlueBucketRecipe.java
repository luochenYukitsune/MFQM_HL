package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/** Shapeless glue crafting transfers the water bucket into the result instead of cloning it. */
public record GlueBucketRecipe(ShapelessRecipe delegate) implements CraftingRecipe {
    @Override public boolean matches(CraftingInput input, Level level) { return delegate.matches(input, level); }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return delegate.assemble(input, registries); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public String group() { return delegate.group(); }
    @Override public PlacementInfo placementInfo() { return delegate.placementInfo(); }
    @Override public List<RecipeDisplay> display() { return delegate.display(); }
    @Override public RecipeSerializer<GlueBucketRecipe> getSerializer() { return ModRecipes.GLUE_BUCKET.get(); }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remains = CraftingRecipe.defaultCraftingReminder(input);
        // The output already contains this input's bucket. Keep unrelated ingredient remainders.
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).is(Items.WATER_BUCKET)) remains.set(slot, ItemStack.EMPTY);
        }
        return remains;
    }

    public static final class Serializer implements RecipeSerializer<GlueBucketRecipe> {
        private static final ShapelessRecipe.Serializer SHAPELESS = new ShapelessRecipe.Serializer();
        private static final MapCodec<GlueBucketRecipe> CODEC = SHAPELESS.codec().xmap(GlueBucketRecipe::new, GlueBucketRecipe::delegate);
        private static final StreamCodec<RegistryFriendlyByteBuf, GlueBucketRecipe> STREAM_CODEC =
                SHAPELESS.streamCodec().map(GlueBucketRecipe::new, GlueBucketRecipe::delegate);
        @Override public MapCodec<GlueBucketRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, GlueBucketRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
