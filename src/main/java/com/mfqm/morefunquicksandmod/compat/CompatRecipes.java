package com.mfqm.morefunquicksandmod.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.item.ItemRecipeCondition;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Resolves legacy cross-mod semantics through explicit, pack-owned item tags. */
public final class CompatRecipes {
    private static final FileToIdConverter TEMPLATES = FileToIdConverter.json("mfqm_compat_recipe");
    private static final FileToIdConverter NORMAL_RECIPES = FileToIdConverter.json("recipe");
    // RecipeHolder.equals compares only IDs. Ownership must use object identity instead.
    private static final Map<RecipeManager, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>>> INJECTED = new WeakHashMap<>();

    private CompatRecipes() {}

    public static void register(IEventBus bus) {
        bus.addListener(CompatRecipes::starting);
        bus.addListener(CompatRecipes::syncing);
        bus.addListener((ServerStoppedEvent event) -> INJECTED.clear());
    }

    private static void starting(ServerAboutToStartEvent event) { refresh(event.getServer()); }

    private static void syncing(OnDatapackSyncEvent event) {
        RecipeManager manager = event.getPlayerList().getServer().getRecipeManager();
        boolean changed = event.getPlayer() == null && refresh(event.getPlayerList().getServer());
        if (changed || !INJECTED.getOrDefault(manager, Map.of()).isEmpty())
            event.sendRecipes(RecipeType.CRAFTING, RecipeType.SMELTING);
    }

    /** Called after static tags are applied, before recipe displays and properties are synchronized. */
    private static boolean refresh(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        ResourceManager resources = server.getResourceManager();
        var registries = server.registryAccess();
        var features = server.getWorldData().enabledFeatures();
        var context = context(registries, features);
        TagResolver tags = tag -> registries.lookupOrThrow(Registries.ITEM).get(tag)
                .map(set -> set.stream().map(holder -> holder.value()).filter(item -> item != Items.AIR && item.isEnabled(features)).toList())
                .orElse(List.of());
        List<RecipeHolder<?>> candidates = new ArrayList<>();
        for (Template template : readTemplates(resources)) {
            try {
                resolve(template, registries, tags, context, CompatRecipes::integrationEnabled, ItemRecipeCondition::enabled,
                        reason -> MFQM.LOGGER.warn("MFQM compatibility recipe {} skipped: {}", template.id(), reason))
                        .ifPresent(candidates::add);
            } catch (RuntimeException failure) {
                MFQM.LOGGER.warn("MFQM compatibility recipe {} is invalid; leaving it disabled", template.id(), failure);
            }
        }
        Merge merged = merge(manager.getRecipes(), INJECTED.getOrDefault(manager, Map.of()), candidates,
                key -> resources.getResource(NORMAL_RECIPES.idToFile(key.identifier())).isPresent());
        if (merged.changed()) {
            manager.recipes = RecipeMap.create(merged.recipes());
            manager.finalizeRecipeLoading(features);
            MFQM.LOGGER.info("MFQM compatibility recipes resolved {} explicit mappings", merged.owned().size());
        }
        INJECTED.put(manager, merged.owned());
        return merged.changed();
    }

    static List<Template> readTemplates(ResourceManager resources) {
        List<Template> result = new ArrayList<>();
        // listResources chooses the highest-priority resource, allowing ordinary datapack overrides.
        for (var entry : TEMPLATES.listMatchingResources(resources).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            Identifier id = TEMPLATES.fileToId(entry.getKey());
            try (var reader = entry.getValue().openAsReader()) {
                result.add(parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (IOException | RuntimeException failure) {
                MFQM.LOGGER.warn("Cannot load MFQM compatibility template {}; leaving it disabled", id, failure);
            }
        }
        return List.copyOf(result);
    }

    static Template parse(Identifier id, JsonObject json) {
        JsonObject recipe = json.getAsJsonObject("recipe");
        if (recipe == null) throw new JsonParseException("Compatibility template requires recipe");
        String integration = json.has("integration") ? json.get("integration").getAsString() : "none";
        String resultTag = json.has("result_tag") ? json.get("result_tag").getAsString() : null;
        return new Template(id, json.has("source_line") ? json.get("source_line").getAsInt() : 0,
                integration, !json.has("enabled") || json.get("enabled").getAsBoolean(), resultTag, json.deepCopy(), recipe.deepCopy());
    }

    /** Pure resolution: empty/missing mappings never become recipes; output tags must be unambiguous. */
    static Optional<RecipeHolder<?>> resolve(Template template, HolderLookup.Provider registries, TagResolver tags,
            ICondition.IContext context, Predicate<String> integrationEnabled, Predicate<String> itemEnabled, Consumer<String> warning) {
        if (!template.enabled() || !integrationEnabled.test(template.integration())) return Optional.empty();
        if (!conditionsMatch(template.metadata(), registries, context) || !conditionsMatch(template.recipe(), registries, context))
            return Optional.empty();
        JsonObject recipe = template.recipe().deepCopy();
        try {
            if (template.metadata().has("required_tags")) for (JsonElement value : template.metadata().getAsJsonArray("required_tags"))
                mappedItems(value.getAsString(), tags);
            if (template.resultTag() != null) {
                List<Item> output = mappedItems(template.resultTag(), tags);
                if (output.size() != 1) {
                    warning.accept("output tag " + template.resultTag() + " contains " + output.size() + " items; exactly one is required");
                    return Optional.empty();
                }
                recipe.getAsJsonObject("result").addProperty("id", BuiltInRegistries.ITEM.getKey(output.getFirst()).toString());
            }
            // Resolve each ingredient independently: alternatives must not become extra crafting slots.
            if (recipe.has("ingredient")) recipe.add("ingredient", expand(recipe.get("ingredient"), tags));
            if (recipe.has("ingredients")) {
                JsonArray ingredients = new JsonArray();
                for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) ingredients.add(expand(ingredient, tags));
                recipe.add("ingredients", ingredients);
            }
            if (recipe.has("key")) {
                JsonObject keys = recipe.getAsJsonObject("key");
                for (String key : List.copyOf(keys.keySet())) keys.add(key, expand(keys.get(key), tags));
            }
        } catch (MissingMapping ignored) {
            return Optional.empty();
        }
        Identifier output = Identifier.parse(recipe.getAsJsonObject("result").get("id").getAsString());
        if (output.getNamespace().equals(MFQM.MOD_ID) && !itemEnabled.test(output.getPath())) return Optional.empty();
        Recipe<?> decoded = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), recipe).getOrThrow(JsonParseException::new);
        return Optional.of(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, template.id()), decoded));
    }

    private static boolean conditionsMatch(JsonObject json, HolderLookup.Provider registries, ICondition.IContext context) {
        return !json.has("neoforge:conditions") || ICondition.LIST_CODEC
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), json.get("neoforge:conditions"))
                .getOrThrow(JsonParseException::new).stream().allMatch(condition -> condition.test(context));
    }

    private static JsonElement expand(JsonElement ingredient, TagResolver tags) {
        if (ingredient.isJsonPrimitive() && ingredient.getAsJsonPrimitive().isString() && ingredient.getAsString().startsWith("#")) {
            List<Item> values = mappedItems(ingredient.getAsString().substring(1), tags);
            if (values.size() == 1) return new JsonPrimitive(BuiltInRegistries.ITEM.getKey(values.getFirst()).toString());
            JsonArray alternatives = new JsonArray();
            values.forEach(item -> alternatives.add(BuiltInRegistries.ITEM.getKey(item).toString()));
            return alternatives;
        }
        if (ingredient.isJsonArray()) {
            JsonArray alternatives = new JsonArray();
            for (JsonElement value : ingredient.getAsJsonArray()) {
                JsonElement expanded = expand(value, tags);
                if (expanded.isJsonArray()) expanded.getAsJsonArray().forEach(alternatives::add);
                else alternatives.add(expanded);
            }
            return alternatives;
        }
        if (ingredient.isJsonObject()) {
            JsonObject object = ingredient.getAsJsonObject().deepCopy();
            // Component predicates are payloads, not item references. Leave their strings untouched.
            for (String key : List.copyOf(object.keySet())) {
                if ((key.equals("children") || key.equals("ingredients")) && object.get(key).isJsonArray()) {
                    // These are lists of predicates, unlike a holder-set list of item alternatives.
                    // Flattening a mapped tag here would change an intersection's meaning.
                    JsonArray children = new JsonArray();
                    for (JsonElement child : object.getAsJsonArray(key)) children.add(expand(child, tags));
                    object.add(key, children);
                } else if (!key.equals("components") && !key.equals("predicate") && !key.equals("custom_data") && !key.equals("display"))
                    object.add(key, expand(object.get(key), tags));
            }
            return object;
        }
        return ingredient.deepCopy();
    }

    private static List<Item> mappedItems(String name, TagResolver tags) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(name));
        List<Item> items = tags.get(tag).stream().filter(item -> item != Items.AIR).distinct()
                .sorted(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item))).toList();
        if (items.isEmpty()) throw new MissingMapping();
        return items;
    }

    static Merge merge(Collection<RecipeHolder<?>> existing, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> previous,
            List<RecipeHolder<?>> candidates, Predicate<ResourceKey<Recipe<?>>> normalRecipePresent) {
        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        boolean changed = false;
        for (RecipeHolder<?> holder : existing) {
            if (previous.get(holder.id()) == holder) changed = true;
            else recipes.put(holder.id(), holder);
        }
        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> owned = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : candidates) {
            // Even a normal recipe filtered out by its conditions explicitly owns this resource ID.
            if (normalRecipePresent.test(holder.id()) || recipes.containsKey(holder.id())) continue;
            recipes.put(holder.id(), holder);
            owned.put(holder.id(), holder);
            changed = true;
        }
        return new Merge(List.copyOf(recipes.values()), Map.copyOf(owned), changed);
    }

    static ICondition.IContext context(RegistryAccess registries, FeatureFlagSet features) {
        return new ICondition.IContext() {
            @Override public <T> boolean isTagLoaded(TagKey<T> tag) {
                return registries.lookup(tag.registry()).flatMap(registry -> registry.get(tag)).isPresent();
            }
            @Override public RegistryAccess registryAccess() { return registries; }
            @Override public FeatureFlagSet enabledFeatures() { return features; }
        };
    }

    static boolean integrationEnabled(String integration) {
        ModConfigSpec.BooleanValue config = switch (integration) {
            case "bop" -> ModConfig.COMMON.biomesOPlenty;
            case "aoa" -> ModConfig.COMMON.adventOfAscension;
            case "ic2", "none" -> null;
            default -> throw new JsonParseException("Unknown compatibility integration " + integration);
        };
        return config == null || (ModConfig.COMMON_SPEC.isLoaded() ? config.get() : config.getDefault());
    }

    @FunctionalInterface interface TagResolver { List<Item> get(TagKey<Item> tag); }
    record Template(Identifier id, int sourceLine, String integration, boolean enabled, String resultTag, JsonObject metadata, JsonObject recipe) {}
    record Merge(List<RecipeHolder<?>> recipes, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> owned, boolean changed) {}
    private static final class MissingMapping extends RuntimeException {}
}
