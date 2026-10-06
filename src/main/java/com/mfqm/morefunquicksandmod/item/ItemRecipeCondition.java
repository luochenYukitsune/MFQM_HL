package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Legacy Turn* switches govern acquisition recipes, never registry availability. */
public record ItemRecipeCondition(String item) implements ICondition {
    public static final MapCodec<ItemRecipeCondition> CODEC = Codec.STRING.fieldOf("item").xmap(ItemRecipeCondition::new, ItemRecipeCondition::item);
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS = DeferredRegister.create(NeoForgeRegistries.CONDITION_SERIALIZERS, MFQM.MOD_ID);
    static { CONDITIONS.register("item_enabled", () -> CODEC); }

    @Override public boolean test(IContext context) { return enabled(item); }
    @Override public MapCodec<? extends ICondition> codec() { return CODEC; }

    public static void register(net.neoforged.bus.api.IEventBus bus) { bus.addListener(ItemRecipeCondition::serverConfigLoaded); }
    private static void serverConfigLoaded(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
        var server = event.getServer();
        var manager = server.getRecipeManager();
        var resources = server.getResourceManager();
        var retained = new java.util.ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>();
        for (var recipe : manager.getRecipes()) {
            boolean allowed = true;
            var id = recipe.id().identifier();
            var resource = resources.getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json"));
            if (resource.isPresent()) try (var reader = resource.get().openAsReader()) {
                var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("neoforge:conditions")) for (var value : json.getAsJsonArray("neoforge:conditions")) {
                    var condition = value.getAsJsonObject();
                    if (condition.has("type") && condition.get("type").getAsString().equals("mfqm:item_enabled"))
                        allowed &= enabled(condition.get("item").getAsString());
                }
            } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot apply MFQM acquisition config to " + id, failure); }
            if (allowed) retained.add(recipe);
        }
        int removed = manager.getRecipes().size() - retained.size();
        if (removed > 0) {
            manager.recipes = net.minecraft.world.item.crafting.RecipeMap.create(retained);
            manager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());
            MFQM.LOGGER.info("MFQM acquisition config removed {} recipes before server startup", removed);
        }
    }

    public static boolean enabled(String item) {
        ModConfigSpec.BooleanValue value = switch (item) {
            case "long_stick" -> ModConfig.SERVER.enableLongStick;
            case "rope" -> ModConfig.SERVER.enableRope;
            case "grappling_hook", "hook", "cable", "coil" -> ModConfig.SERVER.enableGrapplingHook;
            case "gas_mask", "filter" -> ModConfig.SERVER.enableGasMask;
            case "life_jacket" -> ModConfig.SERVER.enableLifeJacket;
            case "wading_boots", "tall_leather_boots", "slimy_tall_leather_boots" -> ModConfig.SERVER.enableWadingBoots;
            case "sinking_potion", "splash_sinking_potion" -> ModConfig.SERVER.enableSinkingPotion;
            case "liquid_gun" -> ModConfig.SERVER.enableLiqGun;
            default -> null;
        };
        return value == null || (ModConfig.SERVER_SPEC.isLoaded() ? value.get() : value.getDefault());
    }
}
