package com.mfqm.morefunquicksandmod.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.item.ItemRecipeCondition;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/** Opt-in integration checks. Fixture mappings never alter real tags or the server's recipe manager. */
public final class CompatRecipeChecks {
    private static final Set<Integer> LEGACY_LINES = Set.of(2279, 2313, 2317, 2321, 2356, 2359, 2363, 2410, 2447);

    private CompatRecipeChecks() {}

    public static List<String> verify(ServerLevel level) {
        requireEnabled();
        var registries = level.registryAccess();
        var features = level.getServer().getWorldData().enabledFeatures();
        var context = CompatRecipes.context(registries, features);
        var templates = CompatRecipes.readTemplates(level.getServer().getResourceManager()).stream()
                .filter(template -> template.id().getNamespace().equals(MFQM.MOD_ID) && LEGACY_LINES.contains(template.sourceLine())).toList();
        require(templates.size() == 9 && templates.stream().map(CompatRecipes.Template::sourceLine).collect(java.util.stream.Collectors.toSet()).equals(LEGACY_LINES),
                "All nine original compatibility call sites must have distinct templates");
        Map<String, Item> fixture = fixtureItems();
        CompatRecipes.TagResolver mapped = tag -> fixture.containsKey(tag.location().toString()) ? List.of(fixture.get(tag.location().toString())) : List.of();
        List<String> warnings = new ArrayList<>();
        List<RecipeHolder<?>> decoded = new ArrayList<>();
        List<String> passed = new ArrayList<>();
        for (var template : templates) {
            require(CompatRecipes.resolve(template, registries, tag -> List.of(), context, group -> true, item -> true, warnings::add).isEmpty(),
                    "Unmapped optional dependency must be silent and absent: " + template.id());
            var recipe = CompatRecipes.resolve(template, registries, mapped, context, group -> true, item -> true, warnings::add).orElseThrow();
            decoded.add(recipe);
            verifyBehavior(level, template, recipe);
        }
        require(warnings.isEmpty(), "Absent mappings and valid fixtures must not emit warnings");
        passed.add("compat: all nine source call sites decode, match exact inputs, reject wrong inputs and preserve outputs/remainders");
        passed.add("compat: absent optional mappings yield zero recipes without warnings");

        for (var template : templates) {
            if (template.resultTag() == null) continue;
            var ambiguous = CompatRecipes.resolve(template, registries,
                    tag -> tag.location().toString().equals(template.resultTag()) ? List.of(Items.BONE_MEAL, Items.DIAMOND) : mapped.get(tag),
                    context, group -> true, item -> true, warnings::add);
            require(ambiguous.isEmpty(), "Ambiguous external output must not choose an arbitrary item");
        }
        require(warnings.size() == 2, "Both ambiguous external output mappings must explain why they were skipped");
        var mudTemplate = templates.stream().filter(template -> template.sourceLine() == 2356).findFirst().orElseThrow();
        var alternatives = CompatRecipes.resolve(mudTemplate, registries,
                tag -> tag.location().toString().equals("mfqm:compat/bop/mud") ? List.of(Items.CLAY, Items.DIRT) : mapped.get(tag),
                context, group -> true, item -> true, warnings::add).orElseThrow();
        var shaped = (ShapedRecipe) alternatives.value();
        for (Item option : List.of(Items.CLAY, Items.DIRT)) {
            var stacks = List.of(ItemStack.EMPTY, new ItemStack(option), ItemStack.EMPTY,
                    new ItemStack(option), new ItemStack(Items.WATER_BUCKET), new ItemStack(option),
                    ItemStack.EMPTY, new ItemStack(option), ItemStack.EMPTY);
            require(shaped.matches(CraftingInput.of(3, 3, stacks), level), "Input tag alternatives must occupy one ingredient slot");
        }
        JsonObject nested = mudTemplate.metadata().deepCopy();
        JsonObject intersection = new JsonObject();
        intersection.addProperty("neoforge:ingredient_type", "neoforge:intersection");
        JsonArray children = new JsonArray(); children.add("#mfqm:compat/bop/mud"); children.add("minecraft:clay");
        intersection.add("children", children);
        nested.getAsJsonObject("recipe").getAsJsonObject("key").add("m", intersection);
        var nestedRecipe = (ShapedRecipe) CompatRecipes.resolve(CompatRecipes.parse(mudTemplate.id(), nested), registries,
                tag -> tag.location().toString().equals("mfqm:compat/bop/mud") ? List.of(Items.CLAY, Items.DIRT) : mapped.get(tag),
                context, group -> true, item -> true, warnings::add).orElseThrow().value();
        var nestedIngredient = nestedRecipe.placementInfo().ingredients().stream()
                .filter(ingredient -> ingredient.isCustom()).findFirst().orElseThrow();
        require(nestedIngredient.test(new ItemStack(Items.CLAY)) && !nestedIngredient.test(new ItemStack(Items.DIRT)),
                "Nested custom ingredient predicates must retain tag alternatives inside their own child");
        passed.add("compat: ambiguous outputs fail closed; multiple input mappings remain alternatives");

        var ic2 = templates.stream().filter(template -> template.sourceLine() == 2279).findFirst().orElseThrow();
        JsonObject gated = ic2.metadata().deepCopy();
        gated.remove("result_tag");
        gated.getAsJsonObject("recipe").getAsJsonObject("result").addProperty("id", "mfqm:long_stick");
        var gatedTemplate = CompatRecipes.parse(ic2.id(), gated);
        require(CompatRecipes.resolve(gatedTemplate, registries, mapped, context, group -> true,
                item -> !item.equals("long_stick"), warnings::add).isEmpty(), "Compatibility output must honor acquisition switches");
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "mfqm:item_enabled"); condition.addProperty("item", "long_stick");
        JsonArray conditions = new JsonArray(); conditions.add(condition);
        gated.add("neoforge:conditions", conditions);
        require(CompatRecipes.resolve(CompatRecipes.parse(ic2.id(), gated), registries, mapped, context,
                group -> true, item -> true, warnings::add).isPresent() == ItemRecipeCondition.enabled("long_stick"),
                "Real mfqm:item_enabled condition must use current per-world configuration");
        long remaining = templates.stream().filter(template -> CompatRecipes.resolve(template, registries, mapped, context,
                group -> !group.equals("bop"), item -> true, warnings::add).isPresent()).count();
        require(remaining == 2, "Disabling BOP integration must remove its seven recipes and retain IC2/AOA");
        passed.add("compat: integration settings, acquisition settings and actual item_enabled condition all apply");

        var first = decoded.getFirst();
        var replacement = new RecipeHolder<>(first.id(), first.value());
        require(replacement.equals(first) && replacement != first, "Ownership fixture must expose ID-only RecipeHolder equality");
        var collision = CompatRecipes.merge(List.of(replacement), Map.of(first.id(), first), List.of(first), key -> false);
        require(collision.recipes().size() == 1 && collision.recipes().getFirst() == replacement && collision.owned().isEmpty(),
                "A datapack or another mod's replacement must not be erased by old ownership");
        var suppressed = CompatRecipes.merge(List.of(), Map.of(), List.of(first), key -> true);
        require(suppressed.recipes().isEmpty(), "A normal datapack recipe resource must win even when its conditions suppress loading");
        var duplicate = CompatRecipes.merge(List.of(), Map.of(), List.of(first, replacement), key -> false);
        require(duplicate.recipes().size() == 1 && duplicate.owned().size() == 1, "Duplicate candidate IDs must not reach RecipeMap");
        var removed = CompatRecipes.merge(List.of(first), Map.of(first.id(), first), List.of(), key -> false);
        require(removed.changed() && removed.recipes().isEmpty(), "Removed mapping must remove the bridge's old holder");
        passed.add("compat: duplicate IDs, normal-resource overrides, identity ownership and stale-holder removal are protected");

        // A separate manager exercises the real finalization API without changing live gameplay.
        RecipeManager isolated = new RecipeManager(registries);
        isolated.recipes = RecipeMap.create(decoded);
        isolated.finalizeRecipeLoading(features);
        require(isolated.propertySet(RecipePropertySet.FURNACE_INPUT).test(new ItemStack(Items.BEE_NEST)),
                "Finalization must include mapped smelting inputs in synchronized furnace properties");
        for (var recipe : decoded) {
            List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> displays = new ArrayList<>();
            isolated.listDisplaysForRecipe(recipe.id(), displays::add);
            require(!displays.isEmpty(), "Finalization must create client recipe displays for " + recipe.id());
        }
        isolated.recipes = RecipeMap.EMPTY;
        isolated.finalizeRecipeLoading(features);
        require(!isolated.propertySet(RecipePropertySet.FURNACE_INPUT).test(new ItemStack(Items.BEE_NEST)),
                "Removing compatibility recipes must clear stale synchronized furnace properties");
        for (var recipe : decoded) {
            List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> displays = new ArrayList<>();
            isolated.listDisplaysForRecipe(recipe.id(), displays::add);
            require(displays.isEmpty(), "Removing compatibility recipes must clear stale client displays");
        }
        passed.add("compat: real finalization rebuilds and removes recipe displays and synchronized furnace inputs");
        passed.addAll(verifyLive(level));
        return List.copyOf(passed);
    }

    /** Safe on startup and after a real /reload, including the temporary external-mapping test pack. */
    public static List<String> verifyLive(ServerLevel level) {
        requireEnabled();
        var server = level.getServer();
        var registries = level.registryAccess();
        var features = server.getWorldData().enabledFeatures();
        var context = CompatRecipes.context(registries, features);
        CompatRecipes.TagResolver tags = tag -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).get(tag)
                .map(set -> set.stream().map(holder -> holder.value()).filter(item -> item != Items.AIR && item.isEnabled(features)).toList()).orElse(List.of());
        int active = 0;
        for (var template : CompatRecipes.readTemplates(server.getResourceManager())) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, template.id());
            if (server.getResourceManager().getResource(FileToIdConverter.json("recipe").idToFile(template.id())).isPresent()) continue;
            var expected = CompatRecipes.resolve(template, registries, tags, context, CompatRecipes::integrationEnabled,
                    ItemRecipeCondition::enabled, ignored -> {});
            var actual = server.getRecipeManager().byKey(key);
            require(expected.isPresent() == actual.isPresent(), "Live compatibility presence disagrees with current mappings: " + template.id());
            if (expected.isPresent()) {
                var ops = registries.createSerializationContext(JsonOps.INSTANCE);
                require(Recipe.CODEC.encodeStart(ops, expected.get().value()).getOrThrow()
                                .equals(Recipe.CODEC.encodeStart(ops, actual.orElseThrow().value()).getOrThrow()),
                        "Live compatibility recipe retains an old result or ingredient mapping: " + template.id());
                List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> displays = new ArrayList<>();
                server.getRecipeManager().listDisplaysForRecipe(key, displays::add);
                require(!displays.isEmpty(), "Live injected recipe must be finalized before synchronization");
                active++;
            }
        }
        return List.of("compat: live recipe manager and client displays match current datapack/config mappings; active=" + active);
    }

    private static void verifyBehavior(ServerLevel level, CompatRecipes.Template template, RecipeHolder<?> holder) {
        var recipe = holder.value();
        ItemStack output;
        if (recipe instanceof AbstractCookingRecipe cooking) {
            Item ingredient = cooking.input().items().findFirst().orElseThrow().value();
            var input = new SingleRecipeInput(new ItemStack(ingredient));
            require(cooking.matches(input, level) && !cooking.matches(new SingleRecipeInput(new ItemStack(Items.DIAMOND)), level),
                    "Smelting must match only mapped ingredients: " + template.id());
            require(Math.abs(cooking.experience() - .1F) < .00001F && cooking.cookingTime() == 200, "Original smelting XP/time must be preserved");
            output = cooking.assemble(input, level.registryAccess());
        } else {
            CraftingRecipe crafting = (CraftingRecipe) recipe;
            var placement = crafting.placementInfo();
            List<ItemStack> inputs = new ArrayList<>();
            for (int index : placement.slotsToIngredientIndex()) inputs.add(index < 0 ? ItemStack.EMPTY
                    : new ItemStack(placement.ingredients().get(index).items().findFirst().orElseThrow().value()));
            int width = crafting instanceof ShapedRecipe shaped ? shaped.getWidth() : inputs.size();
            int height = crafting instanceof ShapedRecipe shaped ? shaped.getHeight() : 1;
            var input = CraftingInput.of(width, height, inputs);
            require(crafting.matches(input, level), "Fixture must actually craft: " + template.id());
            var remains = crafting.getRemainingItems(input);
            for (int i = 0; i < input.size(); i++) if (input.getItem(i).is(Items.WATER_BUCKET))
                require(remains.get(i).is(Items.BUCKET), "Water buckets must return empty buckets: " + template.id());
            List<ItemStack> wrong = new ArrayList<>(input.items());
            for (int i = 0; i < wrong.size(); i++) if (!wrong.get(i).isEmpty()) { wrong.set(i, new ItemStack(Items.DIAMOND)); break; }
            require(!crafting.matches(CraftingInput.of(input.width(), input.height(), wrong), level), "Wrong ingredient must not craft: " + template.id());
            output = crafting.assemble(input, level.registryAccess());
        }
        Item expected = switch (template.sourceLine()) {
            case 2279 -> Items.BONE_MEAL;
            case 2313, 2317, 2321 -> ModItems.byId("wax_piece");
            case 2356 -> ModItems.byId("mud_variant_1");
            case 2359 -> ModItems.byId("mire_bucket");
            case 2363 -> ModItems.byId("mire");
            case 2410 -> Items.LEATHER_HELMET;
            case 2447 -> ModItems.byId("mud");
            default -> throw new IllegalStateException("Unknown original compatibility source line");
        };
        int count = switch (template.sourceLine()) {
            case 2313 -> 9;
            case 2317, 2321 -> 3;
            case 2356, 2363 -> 4;
            default -> 1;
        };
        require(output.is(expected) && output.getCount() == count, "Original compatibility result/count differs: " + template.id());
    }

    private static Map<String, Item> fixtureItems() {
        Map<String, Item> result = new LinkedHashMap<>();
        result.put("mfqm:compat/bop/hive", Items.BEE_NEST);
        result.put("mfqm:compat/bop/empty_honeycomb", Items.HONEYCOMB);
        result.put("mfqm:compat/bop/filled_honeycomb", Items.HONEY_BOTTLE);
        result.put("mfqm:compat/bop/mud", Items.CLAY);
        result.put("mfqm:compat/bop/mud_ball", Items.CLAY_BALL);
        result.put("mfqm:compat/aoa/face_mask", Items.LEATHER_HELMET);
        result.put("mfqm:compat/aoa/doomstone", Items.OBSIDIAN);
        result.put("mfqm:compat/aoa/toxic_lump", Items.SLIME_BALL);
        result.put("mfqm:compat/ic2/fertilizer", Items.BONE_MEAL);
        return Map.copyOf(result);
    }

    private static void requireEnabled() { require(Boolean.getBoolean("mfqm.portChecks"), "Compatibility assertions require -Dmfqm.portChecks=true"); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
