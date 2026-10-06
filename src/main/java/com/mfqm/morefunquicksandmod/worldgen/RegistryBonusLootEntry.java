package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Hive bonus uses actual registered items in place of obsolete numeric item IDs. */
public final class RegistryBonusLootEntry extends LootPoolSingletonContainer {
    public static final MapCodec<RegistryBonusLootEntry> CODEC = RecordCodecBuilder.mapCodec(instance ->
            singletonFields(instance).apply(instance, RegistryBonusLootEntry::new));
    public static final DeferredRegister<LootPoolEntryType> TYPES = DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, MFQM.MOD_ID);
    private static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> TYPE = TYPES.register("registry_bonus", () -> new LootPoolEntryType(CODEC));
    private static List<Item> candidates;
    private RegistryBonusLootEntry(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
    }
    @Override public LootPoolEntryType getType() { return TYPE.get(); }
    @Override protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        if (candidates == null) candidates = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                .sorted(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        var random = context.getRandom();
        ItemStack stack = candidates.get(random.nextInt(candidates.size())).getDefaultInstance();
        int max = Math.max(stack.getMaxStackSize() - 1, 1);
        int count;
        if (max > 4) {
            int part = Math.max(max / 4, 1);
            count = 1 + random.nextInt(part);
            for (int i = 0; i < 3; i++) if (random.nextInt(part) == 0) count += random.nextInt(part + 1);
        } else count = 1 + random.nextInt(max);
        stack.setCount(Math.min(count, stack.getMaxStackSize()));
        output.accept(stack);
    }
}
