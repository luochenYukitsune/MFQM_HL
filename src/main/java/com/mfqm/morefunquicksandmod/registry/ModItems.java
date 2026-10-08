package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.item.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.ITEM, MFQM.MOD_ID);
    private static final Map<String, Supplier<Item>> ENTRIES = new LinkedHashMap<>();
    static {
        ModBlocks.entries().forEach((id, block) -> {
            // Glue is obtained and placed with its bucket; a liquid block item cannot be used.
            if (id.equals("glue")) return;
            if (id.equals("sticky_board")) {
                register(id, properties -> new com.mfqm.morefunquicksandmod.item.StickyBoardItem(block.get(), properties.useBlockDescriptionPrefix()));
                return;
            }
            register(id, properties -> new LegacyBlockItem(block, 0, id.equals("peat") ? 9603 : 0, properties.useBlockDescriptionPrefix()));
            int variants = switch (id) {
                case "mud", "tendrils" -> 4;
                case "soft_snow", "soft_gravel" -> 2;
                case "morass" -> 9;
                case "brown_clay", "wax_wood" -> 8;
                case "honeycomb" -> 3;
                case "sinking_rug" -> 16;
                case "blossom" -> 12;
                case "blossom_slab", "moor_grass" -> 6;
                case "meat_wall" -> 11;
                default -> 1;
            };
            for (int n = 1; n < variants; n++) {
                int variant = n;
                register(id + "_variant_" + n, properties -> new LegacyBlockItem(block, variant, 0, properties));
            }
        });
        bucket("bog_bucket", "bog", 0, 0);
        bucket("brown_clay_bucket", "brown_clay", 0, 0);
        bucket("mineral_clay_bucket", "brown_clay", 4, 0);
        bucket("quicksand_bucket", "jungle_quicksand", 0, 0);
        bucket("sand_bucket", "minecraft:sand", 0, 0);
        bucket("mire_bucket", "stable_liquid_mire", 0, 0);
        bucket("slime_bucket", "sinking_slime", 0, 0);
        bucket("mucus_bucket", "mucus", 0, 0);
        bucket("tar_bucket", "tar", 0, 4800);
        bucket("acid_bucket", "acid", 0, 0);
        bucket("chocolate_bucket", "liquid_chocolate", 0, 0);
        register("chocolate_powder_bucket", p -> new Item(p.stacksTo(1).craftRemainder(Items.BUCKET)));
        bucket("honey_bucket", "honey", 0, 0);
        bucket("slurry_bucket", "slurry", 0, 0);
        bucket("glue_bucket", "glue", 0, 0);
        register("fertilizer", FertilizerItem::new);
        register("long_stick", p -> new ConnectorItem("long_stick", p.stacksTo(1).attributes(ItemAttributeModifiers.builder().add(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 3, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build())));
        register("rescuing", p -> new ConnectorItem("rescue", p.stacksTo(1)));
        register("rope", p -> new ConnectorItem("rope", p.durability(64)));
        register("cable", p -> new Item(p.stacksTo(1)));
        register("coil", Item::new);
        register("hook", Item::new);
        register("filter", Item::new);
        equipment("gas_mask", EquipmentSlot.HEAD);
        register("grappling_hook", p -> new ConnectorItem("hook", p.durability(64)));
        register("broken_grappling_hook", p -> new Item(p.stacksTo(1)));
        register("liquid_gun", LiquidGunItem::new);
        register("wax_piece", Item::new);
        equipment("life_jacket", EquipmentSlot.CHEST);
        register("peat_item", p -> new LegacyFuelItem(1067, p));
        food("raw_larva", 2, .1f, Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HUNGER, 600), .5f)).build());
        food("cooked_larva", 3, .15f, Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HUNGER, 600), .25f)).build());
        food("cranberry", 1, .1f, Consumables.defaultFood().consumeSeconds(.4f).build());
        food("donut", 3, .4f, Consumables.DEFAULT_FOOD);
        food("glazed_donut", 4, .5f, Consumables.DEFAULT_FOOD);
        food("sprinkled_donut", 5, .5f, Consumables.DEFAULT_FOOD);
        food("pink_sprinkled_donut", 8, .5f, Consumables.DEFAULT_FOOD);
        food("chocolate_donut", 8, .6f, Consumables.DEFAULT_FOOD);
        register("bottle_of_mire", p -> new Item(drink(p, Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(java.util.List.of(
                new MobEffectInstance(MobEffects.POISON, 200, 2), new MobEffectInstance(MobEffects.HUNGER, 1200, 2), new MobEffectInstance(MobEffects.NAUSEA, 1200, 2)))).build())));
        register("sinking_potion", p -> new SinkingDrinkItem(drink(p, Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(java.util.List.of(
                new MobEffectInstance(MobEffects.WITHER, 200, 2), new MobEffectInstance(MobEffects.HUNGER, 2400, 2), new MobEffectInstance(MobEffects.NAUSEA, 2400, 2)))).build())));
        register("hot_chocolate", p -> new Item(drink(p, Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 200))).build())
                .component(DataComponents.FOOD, new FoodProperties.Builder().nutrition(8).saturationModifier(1f).alwaysEdible().build())));
        register("splash_sinking_potion", SplashSinkingPotionItem::new);
        register("empty_honeycomb", Item::new);
        food("filled_honeycomb", 3, .4f, Consumables.DEFAULT_FOOD);
        equipment("wading_boots", EquipmentSlot.FEET);
        equipment("tall_leather_boots", EquipmentSlot.FEET);
        equipment("slimy_tall_leather_boots", EquipmentSlot.FEET);
        for (String id : new String[]{"vore_slime", "muddy_blob", "sand_blob", "tar_slime", "bee"}) {
            register(id + "_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(ModEntities.byId(id))));
        }
    }
    private static void register(String id, Function<Item.Properties, Item> factory) {
        ENTRIES.put(id, ITEMS.register(id, () -> factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, id))))));
    }
    private static void bucket(String id, String block, int variant, int fuel) {
        register(id, p -> new LegacyBucketItem(block, variant, fuel, p));
    }
    private static void equipment(String id, EquipmentSlot slot) {
        register(id, p -> new Item(p.stacksTo(1).component(DataComponents.EQUIPPABLE,
                Equippable.builder(slot).setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, id))).setDamageOnHurt(false).build())));
    }
    private static void food(String id, int nutrition, float saturation, Consumable consumable) {
        register(id, p -> new Item(p.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(), consumable)));
    }
    private static Item.Properties drink(Item.Properties p, Consumable consumable) {
        return p.stacksTo(16).component(DataComponents.CONSUMABLE, consumable)
                .component(DataComponents.USE_REMAINDER, new UseRemainder(new net.minecraft.world.item.ItemStack(Items.GLASS_BOTTLE)));
    }
    public static Item byId(String id) {
        Supplier<Item> value = ENTRIES.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown MFQM item: " + id);
        return value.get();
    }
    public static Map<String, Supplier<Item>> entries() { return Collections.unmodifiableMap(ENTRIES); }
    public static boolean isCreativeVisible(String id) {
        if (id.equals("rescuing") || id.equals("sandstone_trap") || id.equals("vore_hole") || id.equals("meat_hole")) return false;
        // Clay textures and log axes are placement states, not separately obtainable grades.
        if (id.startsWith("brown_clay_variant_") || id.startsWith("wax_wood_variant_")) return id.endsWith("_4");
        return true;
    }
    private ModItems() {}
}
