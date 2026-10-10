package com.mfqm.morefunquicksandmod.validation;

import com.mfqm.morefunquicksandmod.entity.BlobEntity;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

/** Real callbacks for the full-audit regressions, enabled only in the isolated port-check world. */
public final class AuditPortChecks {
    public static List<String> verify(ServerLevel level, BlockPos at) {
        var passed = new ArrayList<String>();
        var failures = new ArrayList<String>();
        for (boolean drops : new boolean[]{true, false}) {
            check("blob-death-drops=" + drops, () -> returnedTool(level, at, drops, false), passed, failures);
        }
        check("blob-peaceful-return", () -> returnedTool(level, at, false, true), passed, failures);
        check("hostile-types-exclude-peaceful", () -> {
            for (String id : List.of("vore_slime", "muddy_blob", "sand_blob", "tar_slime", "bee"))
                require(!ModEntities.byId(id).isAllowedInPeaceful(), id + " must be disabled in peaceful");
        }, passed, failures);
        check("blob-rejected-hit-keeps-tool", () -> rejectedHit(level, at), passed, failures);
        check("capture-teleport", () -> teleport(level, at), passed, failures);
        check("capture-mob-teleport", () -> mobTeleport(level, at), passed, failures);
        check("shears-permissions-and-durability", () -> shears(level, at), passed, failures);
        check("reactions-keep-unloaded-neighbors", () -> unloadedNeighbors(level), passed, failures);
        if (!failures.isEmpty()) throw new IllegalStateException("MFQM audit regressions failed: " + String.join("; ", failures));
        return List.copyOf(passed);
    }
    private static void check(String name, Runnable test, List<String> passed, List<String> failures) {
        try { test.run(); passed.add("audit: " + name); }
        catch (RuntimeException error) { failures.add(name + ": " + error.getMessage()); }
    }
    private static ServerPlayer player(ServerLevel level, BlockPos at) {
        var profile = new GameProfile(UUID.randomUUID(), "MFQMAuditProbe");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        // The listener must belong to this player: borrowing a FakePlayer listener
        // would teleport its own FakePlayer and give misleading teleport results.
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), connection,
                player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
        };
        player.setPos(Vec3.atCenterOf(at));
        return player;
    }
    private static void returnedTool(ServerLevel level, BlockPos at, boolean drops, boolean peaceful) {
        var originalDifficulty = level.getDifficulty();
        boolean originalDrops = level.getGameRules().get(GameRules.MOB_DROPS);
        var area = new AABB(at).inflate(4);
        var existing = level.getEntitiesOfClass(ItemEntity.class, area).stream().map(Entity::getUUID).toList();
        var player = player(level, at);
        BlobEntity blob = ModEntities.MUDDY_BLOB.get().create(level, EntitySpawnReason.TRIGGERED);
        require(blob != null, "create blob");
        try {
            level.getServer().setDifficulty(Difficulty.HARD, true);
            level.getGameRules().set(GameRules.MOB_DROPS, drops, level.getServer());
            blob.setPos(player.position());
            require(level.addFreshEntity(blob), "spawn blob");
            var tool = new ItemStack(Items.WOODEN_SWORD);
            tool.setDamageValue(7); tool.set(DataComponents.CUSTOM_NAME, Component.literal("保留原始武器"));
            player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
            blob.hurtServer(level, level.damageSources().playerAttack(player), 1);
            require(player.getMainHandItem().isEmpty(), "actual theft");
            // Unload and dimension transfer preserve the saved copy's inventory,
            // even if another hook later discards the stale original reference.
            for (var reason : List.of(Entity.RemovalReason.UNLOADED_TO_CHUNK, Entity.RemovalReason.CHANGED_DIMENSION)) {
                var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                blob.saveWithoutId(saved);
                blob.remove(reason);
                blob.discard();
                require(level.getEntitiesOfClass(ItemEntity.class, area).stream()
                        .noneMatch(item -> !existing.contains(item.getUUID()) && ItemStack.matches(item.getItem(), tool)),
                        reason + " must not release stolen weapon");
                blob = ModEntities.MUDDY_BLOB.get().create(level, EntitySpawnReason.LOAD);
                require(blob != null, "create saved blob");
                blob.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
                require(level.addFreshEntity(blob), "restore saved blob");
                require(!blob.removeWhenFarAway(10000), "restored stolen equipment prevents natural despawn");
            }
            if (peaceful) {
                level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                blob.checkDespawn();
                require(blob.isRemoved(), "peaceful still removes hostile blob");
            } else {
                blob.invulnerableTime = 0;
                require(blob.hurtServer(level, level.damageSources().genericKill(), 1000), "actual lethal damage");
                require(!blob.isAlive(), "actual death");
            }
            var returned = level.getEntitiesOfClass(ItemEntity.class, area).stream()
                    .filter(item -> !existing.contains(item.getUUID()) && ItemStack.matches(item.getItem(), tool)).toList();
            require(returned.size() == 1, "original named/damaged weapon must return exactly once, got " + returned.size());
            blob.discard();
            require(level.getEntitiesOfClass(ItemEntity.class, area).stream()
                    .filter(item -> !existing.contains(item.getUUID()) && ItemStack.matches(item.getItem(), tool)).count() == 1,
                    "later removal must not duplicate returned weapon");
            if (!drops) require(level.getEntitiesOfClass(ItemEntity.class, area).stream()
                    .noneMatch(item -> !existing.contains(item.getUUID()) && item.getItem().is(Items.SLIME_BALL)),
                    "disabled random mob loot stays disabled");
        } finally {
            blob.discard(); discardPlayer(player);
            level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(item -> !existing.contains(item.getUUID())).forEach(Entity::discard);
            level.getGameRules().set(GameRules.MOB_DROPS, originalDrops, level.getServer());
            level.getServer().setDifficulty(originalDifficulty, true);
        }
    }
    private static void teleport(ServerLevel level, BlockPos at) {
        var player = player(level, at);
        var blob = ModEntities.MUDDY_BLOB.get().create(level, EntitySpawnReason.TRIGGERED);
        require(blob != null, "create capture blob");
        blob.setPos(player.position());
        try {
            require(player.startRiding(blob, true, true), "mount blob"); blob.tick();
            player.stopRiding(); require(player.getVehicle() == blob, "ordinary dismount still requires escape");
            var canceled = new EntityTravelToDimensionEvent(player, level.dimension()); canceled.setCanceled(true);
            NeoForge.EVENT_BUS.post(canceled);
            require(player.getVehicle() == blob, "canceled teleport does not release capture");
            var destination = Vec3.atCenterOf(at.east(3));
            java.util.function.Consumer<EntityTravelToDimensionEvent> cancelTravel = event -> {
                if (event.getEntity() == player) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, cancelTravel);
            try {
                require(player.teleport(new TeleportTransition(level, destination, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == null,
                        "late listener cancels actual teleport");
                require(player.getVehicle() == blob && blob.getPassengers().contains(player),
                        "late-canceled actual teleport must retain capture");
            } finally { NeoForge.EVENT_BUS.unregister(cancelTravel); }
            require(player.teleport(new TeleportTransition(level, player.position(), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)
                    .transitionAsPassenger()) == player, "passenger transition succeeds");
            require(player.getVehicle() == blob && blob.getPassengers().contains(player), "passenger transition preserves capture");
            require(player.teleport(new TeleportTransition(level, destination, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == player,
                    "same-level teleport succeeds");
            require(player.getVehicle() == null && blob.getPassengers().isEmpty(), "same-level teleport clears both mount references");
            require(player.position().distanceTo(destination) < .001, "same-level teleport reaches destination");
            require(player.startRiding(blob, true, true), "recapture before cross-level teleport"); blob.tick();
            var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            require(nether != null, "nether exists"); nether.getChunkAt(at);
            require(player.teleport(new TeleportTransition(nether, destination, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == player,
                    "cross-level teleport succeeds");
            require(player.level() == nether && player.getVehicle() == null && blob.getPassengers().isEmpty(),
                    "cross-level teleport has no old-world mount references");
        } finally {
            if (player.getVehicle() == blob) blob.releasePassenger(player);
            discardPlayer(player); blob.discard();
        }
    }
    private static void rejectedHit(ServerLevel level, BlockPos at) {
        var difficulty = level.getDifficulty();
        var player = player(level, at);
        var blob = ModEntities.MUDDY_BLOB.get().create(level, EntitySpawnReason.TRIGGERED);
        require(blob != null, "create combat blob"); blob.setPos(player.position());
        try {
            level.getServer().setDifficulty(Difficulty.HARD, true);
            require(blob.hurtServer(level, level.damageSources().generic(), 1), "initial damage starts invulnerability window");
            var sword = new ItemStack(Items.WOODEN_SWORD); player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            require(!blob.hurtServer(level, level.damageSources().playerAttack(player), 1), "equal damage inside invulnerability window is rejected");
            require(player.getMainHandItem() == sword && blob.removeWhenFarAway(10000), "rejected damage cannot steal equipment");
        } finally { blob.discard(); discardPlayer(player); level.getServer().setDifficulty(difficulty, true); }
    }
    private static void mobTeleport(ServerLevel level, BlockPos at) {
        Entity victim = net.minecraft.world.entity.EntityType.PIG.create(level, EntitySpawnReason.TRIGGERED);
        var blob = ModEntities.MUDDY_BLOB.get().create(level, EntitySpawnReason.TRIGGERED);
        require(victim != null && blob != null, "create captured mob");
        victim.setPos(Vec3.atCenterOf(at)); blob.setPos(victim.position());
        try {
            require(victim.startRiding(blob, true, true), "mount captured mob"); blob.tick();
            victim.stopRiding(); require(victim.getVehicle() == blob, "ordinary mob dismount remains constrained");
            require(victim.teleport(new TeleportTransition(level, victim.position(), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)
                    .transitionAsPassenger()) == victim, "mob passenger transition succeeds");
            require(victim.getVehicle() == blob, "mob passenger transition retains mount");
            var destination = Vec3.atCenterOf(at.east(3));
            var original = victim;
            java.util.function.Consumer<EntityTravelToDimensionEvent> cancelTravel = event -> {
                if (event.getEntity() == original) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, cancelTravel);
            try {
                require(victim.teleport(new TeleportTransition(level, destination, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == null,
                        "late listener cancels actual mob teleport");
                require(victim.getVehicle() == blob && blob.getPassengers().contains(victim), "canceled mob teleport retains capture");
            } finally { NeoForge.EVENT_BUS.unregister(cancelTravel); }
            require(victim.teleport(new TeleportTransition(level, destination, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == victim,
                    "same-level mob teleport succeeds");
            require(victim.getVehicle() == null && blob.getPassengers().isEmpty(), "same-level mob teleport clears capture");
            require(victim.startRiding(blob, true, true), "recapture mob"); blob.tick();
            var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            require(nether != null, "nether exists"); nether.getChunkAt(at);
            Entity transferred = victim.teleport(new TeleportTransition(nether, destination, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            require(transferred != null, "cross-level mob teleport succeeds");
            victim = transferred;
            require(victim.level() == nether && victim.getVehicle() == null && blob.getPassengers().isEmpty(),
                    "cross-level mob teleport clears old-world references");
        } finally {
            for (Entity passenger : List.copyOf(blob.getPassengers())) blob.releasePassenger(passenger);
            victim.discard(); blob.discard();
        }
    }
    private static void shears(ServerLevel level, BlockPos at) {
        var player = player(level, at);
        var original = level.getBlockState(at);
        var area = new AABB(at).inflate(4);
        var existing = level.getEntitiesOfClass(ItemEntity.class, area).stream().map(Entity::getUUID).toList();
        try {
            for (String id : List.of("leaves_pile", "tendrils", "moor_grass")) {
                var state = ModBlocks.byId(id).defaultBlockState();
                level.setBlock(at, state, 2);
                var tool = new ItemStack(Items.SHEARS); player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                var hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
                player.getAbilities().mayBuild = false;
                state.useItemOn(tool, level, player, InteractionHand.MAIN_HAND, hit);
                require(level.getBlockState(at).equals(state) && tool.getDamageValue() == 0, id + " unauthorized use cannot remove block or damage tool");
                player.getAbilities().mayBuild = true;
                require(state.useItemOn(tool, level, player, InteractionHand.MAIN_HAND, hit).consumesAction(), id + " permitted harvest");
                require(level.getBlockState(at).isAir() && tool.getDamageValue() == 1, id + " successful harvest wears shears exactly once");
            }
        } finally {
            discardPlayer(player); level.setBlock(at, original, 3);
            level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(item -> !existing.contains(item.getUUID())).forEach(Entity::discard);
        }
    }
    private static void discardPlayer(ServerPlayer player) {
        player.discard();
        ((EmbeddedChannel) player.connection.getConnection().channel()).finishAndReleaseAll();
    }
    private static void unloadedNeighbors(ServerLevel level) {
        // A FULL center does not require a FULL east neighbor. Choose a fresh
        // isolated chunk, then inspect immediately inside this single server task.
        int x = 300;
        while (x < 310 && level.getChunkSource().getChunkNow(x + 1, 300) != null) x++;
        require(x < 310, "find unloaded boundary");
        level.getChunk(x, 300);
        var at = new BlockPos(x * 16 + 15, 240, 300 * 16 + 8);
        require(!level.hasChunkAt(at.east()), "east neighbor begins unloaded");
        com.mfqm.morefunquicksandmod.gameplay.MediumReactions.nearFluid(level, at, net.minecraft.tags.FluidTags.WATER);
        com.mfqm.morefunquicksandmod.gameplay.MediumReactions.nearSolidEdge(level, at);
        com.mfqm.morefunquicksandmod.gameplay.MediumReactions.tick("audit_no_reaction", net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), level, at, level.random);
        ModBlocks.byId("mud").defaultBlockState().getCollisionShape(level, at);
        require(!level.hasChunkAt(at.east()), "passive neighbor probes must not request a FULL adjacent chunk");
        var previous = level.getBlockState(at);
        try {
            // Direct chunk placement avoids vanilla neighbor-shape notifications,
            // which can themselves load the east chunk before the callback is tested.
            level.getChunkAt(at).setBlockState(at, ModBlocks.byId("vore_hole").defaultBlockState(), 0);
            require(!level.hasChunkAt(at.east()), "hole fixture placement must start with unloaded east neighbor");
            com.mfqm.morefunquicksandmod.gameplay.MediumReactions.validateHole("vore_hole", level, at);
            require(level.getBlockState(at).is(ModBlocks.byId("vore_hole")) && !level.hasChunkAt(at.east()),
                    "possible unloaded parent defers orphan-hole removal without loading its chunk: state=" + level.getBlockState(at) + " eastLoaded=" + level.hasChunkAt(at.east()));
        } finally { level.getChunkAt(at).setBlockState(at, previous, 0); }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private AuditPortChecks() {}
}
