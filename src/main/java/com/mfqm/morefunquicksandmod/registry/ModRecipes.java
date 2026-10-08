package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.item.GlueBucketRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MFQM.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GlueBucketRecipe>> GLUE_BUCKET =
            SERIALIZERS.register("glue_bucket", GlueBucketRecipe.Serializer::new);
    private ModRecipes() {}
}
