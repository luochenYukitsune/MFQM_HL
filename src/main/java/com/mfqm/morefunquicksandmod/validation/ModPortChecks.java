package com.mfqm.morefunquicksandmod.validation;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.entity.EntityPortChecks;
import com.mfqm.morefunquicksandmod.gameplay.PhysicsPortChecks;
import com.mfqm.morefunquicksandmod.item.ItemPortChecks;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import com.mfqm.morefunquicksandmod.worldgen.LegacyStructures;
import com.mfqm.morefunquicksandmod.worldgen.LegacyChunkConversions;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Explicit -PmfqmPortChecks runner. Use only the isolated validation world. */
public final class ModPortChecks {
    private static boolean initialChecksComplete;
    public static void register(IEventBus bus) {
        bus.addListener(ModPortChecks::started);
        bus.addListener(ModPortChecks::reloaded);
        bus.addListener(com.mfqm.morefunquicksandmod.entity.BootPersistenceChecks::tick);
        bus.addListener(com.mfqm.morefunquicksandmod.worldgen.NaturalGlueChecks::tick);
    }
    private static void reloaded(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        if (!initialChecksComplete || event.getPlayer() != null) return;
        try {
            verifyAcquisitionRecipe(event.getPlayerList().getServer().overworld());
            var server = event.getPlayerList().getServer();
            for (String result : com.mfqm.morefunquicksandmod.compat.CompatRecipeChecks.verifyLive(server.overworld()))
                MFQM.LOGGER.info("MFQM_RELOAD_PASS {}", result);
            long compat = server.getRecipeManager().getRecipes().stream().filter(holder -> holder.id().identifier().getNamespace().equals("mfqm") && holder.id().identifier().getPath().startsWith("compat/")).count();
            MFQM.LOGGER.info("MFQM_RELOAD_CHECKS_COMPLETE longStick={} compat={}", com.mfqm.morefunquicksandmod.ModConfig.SERVER.enableLongStick.get(), compat);
        } catch (Throwable failure) {
            MFQM.LOGGER.error("MFQM_RELOAD_CHECKS_FAILED", failure);
        }
    }
    private static void started(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        event.getServer().execute(() -> {
            try {
                // Empty height, away from spawn protection; no normal player world invokes this runner.
                for (int x = 4; x <= 10; x++) for (int z = 4; z <= 7; z++) level.getChunk(x, z);
                var results = new ArrayList<String>();
                results.addAll(AuditPortChecks.verify(level, new BlockPos(104, 240, 104)));
                results.addAll(PhysicsPortChecks.verify(level, new BlockPos(80, 240, 80)));
                results.addAll(com.mfqm.morefunquicksandmod.gameplay.CoatingPortChecks.verify(level,new BlockPos(84,240,84)));
                results.addAll(com.mfqm.morefunquicksandmod.gameplay.ViscosityPortChecks.verify(level, new BlockPos(140, 240, 80)));
                results.addAll(ItemPortChecks.verify(level, new BlockPos(96, 240, 80)));
                results.addAll(com.mfqm.morefunquicksandmod.block.StickyBoardPortChecks.verify(level, new BlockPos(96, 240, 96)));
                results.addAll(EntityPortChecks.verify(level, new BlockPos(112, 240, 80)));
                results.addAll(com.mfqm.morefunquicksandmod.gameplay.AdhesionPortChecks.verify(level,new BlockPos(124,240,80)));
                results.addAll(com.mfqm.morefunquicksandmod.entity.BootsPortChecks.verify(level, new BlockPos(112, 240, 96)));
                results.addAll(worldgen(level));
                results.addAll(com.mfqm.morefunquicksandmod.worldgen.GlueWorldgenPortChecks.verify(level));
                results.addAll(com.mfqm.morefunquicksandmod.compat.CompatRecipeChecks.verify(level));
                for (String result : results) MFQM.LOGGER.info("MFQM_PORT_PASS {}", result);
                initialChecksComplete = true;
                MFQM.LOGGER.info("MFQM_PORT_CHECKS_COMPLETE checks={} items={}", results.size(), ModItems.entries().size());
            } catch (Throwable failure) {
                MFQM.LOGGER.error("MFQM_PORT_CHECKS_FAILED", failure);
            }
        });
    }
    private static java.util.List<String> worldgen(ServerLevel level) {
        var results = new ArrayList<String>();
        var template = level.getStructureManager().get(Identifier.fromNamespaceAndPath("mfqm", "desert_tomb")).orElseThrow();
        if (!template.getSize().equals(new net.minecraft.core.Vec3i(48, 20, 27))) throw new IllegalStateException("Tomb dimensions differ from original");
        results.add("legacy desert tomb NBT resolves with original 48x20x27 dimensions");
        BlockPos base = new BlockPos(144, 240, 96);
        if (!LegacyStructures.growBlossom(level, base)) throw new IllegalStateException("Shared flower placement rejected empty volume");
        if (!LegacyStructures.growBlossom(level, base)) throw new IllegalStateException("Existing MFQM flower cannot grow");
        results.add("197-placement mucus blossom grows both into air and over its own mechanism blocks");
        BlockPos trap = new BlockPos(160, 240, 96);
        level.setBlock(trap, Blocks.TNT.defaultBlockState(), 2);
        level.setBlock(trap.above(), Blocks.SANDSTONE.defaultBlockState(), 2);
        LegacyChunkConversions.convert(level, new ChunkPos(trap));
        if (!level.getBlockState(trap).is(com.mfqm.morefunquicksandmod.registry.ModBlocks.byId("quicksand"))) throw new IllegalStateException("Temple TNT conversion failed");
        for (int y = -2; y <= 1; y++) level.setBlock(trap.above(y), Blocks.AIR.defaultBlockState(), 2);
        results.add("new-chunk temple sandstone/TNT trap conversion uses server placement");
        verifyAcquisitionRecipe(level);
        boolean stickEnabled = com.mfqm.morefunquicksandmod.ModConfig.SERVER.enableLongStick.get();
        results.add("cold-start long-stick registry recipe and actual crafting match per-world config=" + stickEnabled);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(base))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        for (String name : java.util.List.of("desert_tomb", "desert_tomb_rare", "honey")) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("mfqm", "chests/" + name));
            var table = level.getServer().reloadableRegistries().getLootTable(key);
            if (table == net.minecraft.world.level.storage.loot.LootTable.EMPTY) throw new IllegalStateException("Missing chest table " + name);
            for (int seed = 1; seed <= 8; seed++) for (var item : table.getRandomItems(params, seed))
                if (item.isEmpty() || item.getCount() > item.getMaxStackSize()) throw new IllegalStateException("Invalid chest reward " + name);
        }
        results.add("all three chest tables decode and generate valid stacks, including registry hive bonus");
        var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) throw new IllegalStateException("Nether missing");
        BlockPos outlet = new BlockPos(184, 240, 96);
        nether.getChunkAt(outlet);
        var saved = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int up = -1; up <= 5; up++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var at = outlet.offset(x, up, z); saved.put(at, nether.getBlockState(at)); nether.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        }
        try {
            com.mfqm.morefunquicksandmod.worldgen.LegacyTerrainFeature.placeWasteOutlet(nether, outlet);
            if (!com.mfqm.morefunquicksandmod.gameplay.MediumReactions.validMeatColumn(nether, outlet)
                    || !com.mfqm.morefunquicksandmod.gameplay.MediumReactions.ejectWaste(nether, outlet)
                    || !nether.getBlockState(outlet.below()).is(com.mfqm.morefunquicksandmod.registry.ModBlocks.byId("slurry")))
                throw new IllegalStateException("Generated waste outlet cannot eject slurry");
            nether.setBlock(outlet.above(3).east(), Blocks.AIR.defaultBlockState(), 2);
            if (com.mfqm.morefunquicksandmod.gameplay.MediumReactions.ejectWaste(nether, outlet)) throw new IllegalStateException("Unsupported meat column produces waste");
        } finally { for (var entry : saved.entrySet()) nether.setBlock(entry.getKey(), entry.getValue(), 3); }
        results.add("generated Nether waste column actually ejects slurry and rejects broken support");
        return results;
    }
    private static void verifyAcquisitionRecipe(ServerLevel level) {
        var recipeKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.fromNamespaceAndPath("mfqm", "legacy_014_long_stick"));
        boolean enabled = com.mfqm.morefunquicksandmod.ModConfig.SERVER.enableLongStick.get();
        if (level.getServer().getRecipeManager().byKey(recipeKey).isPresent() != enabled) throw new IllegalStateException("Recipe availability ignores server config");
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, java.util.List.of(
                net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK),
                net.minecraft.world.item.ItemStack.EMPTY, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL), net.minecraft.world.item.ItemStack.EMPTY,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK), net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY));
        var crafted = level.getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
        if (crafted.isPresent() != enabled) throw new IllegalStateException("Actual crafting ignores server config");
    }
    private ModPortChecks() {}
}
