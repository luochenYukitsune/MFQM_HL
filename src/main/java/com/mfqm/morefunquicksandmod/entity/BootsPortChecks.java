package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Real server item-transfer and held-rope checks in the isolated development world. */
public final class BootsPortChecks {
    public static List<String> verify(ServerLevel level, BlockPos origin) {
        var passed = new ArrayList<String>();
        var probes = new ArrayList<Entity>();
        var area = new AABB(origin).inflate(8);
        var saved = level.getBlockState(origin);
        var existing = level.getEntitiesOfClass(ItemEntity.class, area).stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
        var profile = new GameProfile(UUID.fromString("7627e69f-629d-40dc-9a30-04df390351e8"), "MFQMBootProbe");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new FakePlayer(level, profile).connection;
        player.setPos(origin.getX()+.5, origin.getY()+1, origin.getZ()+.5);
        try {
            require(ModEntities.byId("stuck_boots") == ModEntities.STUCK_BOOTS.get(), "persistent boots identity");
            var boots = new ItemStack(Items.DIAMOND_BOOTS);
            boots.setDamageValue(73);
            boots.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
            boots.set(DataComponents.CUSTOM_NAME, Component.literal("保留组件的靴子"));
            player.setItemSlot(EquipmentSlot.FEET, boots.copy());
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2);
            require(!StuckBootsEntity.tryLeaveBehind(player, origin) && ItemStack.matches(player.getItemBySlot(EquipmentSlot.FEET), boots), "dry ground cannot steal footwear");
            level.setBlock(origin, ModBlocks.byId("glue").defaultBlockState(), 2);
            require(!StuckBootsEntity.tryLeaveBehind(player, origin, entity -> false)
                    && ItemStack.matches(player.getItemBySlot(EquipmentSlot.FEET), boots), "rejected world insertion preserves feet item");
            player.getAbilities().instabuild = true;
            require(!StuckBootsEntity.tryLeaveBehind(player, origin), "creative feet item is exempt");
            player.getAbilities().instabuild = false;
            require(StuckBootsEntity.tryLeaveBehind(player, origin, entity -> {
                require(!entity.recover(player,InteractionHand.MAIN_HAND,2.5),"pending insertion callback cannot recover a duplicate of feet equipment");
                entity.tick();
                return level.addFreshEntity(entity);
            }), "glue transfers feet item atomically despite insertion callbacks");
            require(player.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "feet cleared only after spawn");
            var stuck = level.getEntitiesOfClass(StuckBootsEntity.class, area).stream().filter(e -> !e.isRemoved()).findFirst().orElseThrow();
            probes.add(stuck);
            var bootRope = ModEntities.ROPE.get().create(level, EntitySpawnReason.TRIGGERED); require(bootRope != null, "boots rope"); probes.add(bootRope);
            bootRope.setOwner(player); bootRope.bindTarget(stuck);
            require(bootRope.visibleTarget() == stuck && bootRope.attached(), "rope can bind the nonliving boots target");
            require(ItemStack.matches(stuck.stack(), boots), "all item components preserved on transfer");
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            stuck.addAdditionalSaveData(output);
            var loaded = ModEntities.STUCK_BOOTS.get().create(level, EntitySpawnReason.TRIGGERED);
            require(loaded != null, "load boots entity"); probes.add(loaded);
            loaded.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
            require(ItemStack.matches(loaded.stack(), boots) && loaded.anchor().equals(origin), "save round-trip preserves full stack and anchor");
            loaded.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            require(level.getEntitiesOfClass(ItemEntity.class, area).stream().noneMatch(e -> !existing.contains(e.getUUID())), "chunk unload must not release another copy");
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // The sole empty hand is deliberately filled during transfer capacity checking.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 64));
            require(!stuck.recover(player, InteractionHand.MAIN_HAND, 5), "wrong held tool cannot recover");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.byId("long_stick")));
            require(!stuck.recover(player, InteractionHand.MAIN_HAND, 5) && !stuck.isRemoved() && ItemStack.matches(stuck.stack(), boots), "full inventory preserves stuck equipment");
            player.getInventory().setItem(1, ItemStack.EMPTY);
            require(stuck.recover(player, InteractionHand.MAIN_HAND, 5) && stuck.isRemoved(), "long stick recovers actual item");
            require(!stuck.recover(player, InteractionHand.MAIN_HAND, 5), "repeat interaction cannot duplicate");
            require(ItemStack.matches(player.getInventory().getItem(1), boots), "recovered name and durability preserved");
            passed.add("glue boots preserve complete stack on transfer/save/recovery; full inventory and repeated retrieval are safe");
            player.setItemSlot(EquipmentSlot.FEET, boots.copy());
            require(StuckBootsEntity.tryLeaveBehind(player, origin), "second transfer");
            var release = level.getEntitiesOfClass(StuckBootsEntity.class, area).stream().filter(e -> !e.isRemoved()).findFirst().orElseThrow(); probes.add(release);
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2); release.tick();
            require(release.isRemoved(), "removed medium releases entity");
            require(level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(e -> !existing.contains(e.getUUID()) && ItemStack.matches(e.getItem(), boots)).count() == 1, "medium removal releases exactly one complete item");
            passed.add("removing glue releases exactly one item while chunk unload never releases a duplicate");
            level.setBlock(origin, com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7), 2);
            player.setItemSlot(EquipmentSlot.FEET, boots.copy());
            require(StuckBootsEntity.tryLeaveBehind(player, origin), "coated board supports boots");
            var boardBoots = level.getEntitiesOfClass(StuckBootsEntity.class, area).stream().filter(e -> !e.isRemoved()).findFirst().orElseThrow(); probes.add(boardBoots);
            level.setBlock(origin, com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(0), 2); boardBoots.tick();
            require(boardBoots.isRemoved(), "empty board releases retained footwear");
            player.setItemSlot(EquipmentSlot.FEET, boots.copy());
            require(!StuckBootsEntity.tryLeaveBehind(player, origin), "empty board cannot steal footwear");
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2);
            passed.add("coated boards can hold boots; depleted boards release them and cannot take another pair");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.byId("rope")));
            var rope = ModEntities.ROPE.get().create(level, EntitySpawnReason.TRIGGERED); require(rope != null, "rope"); probes.add(rope);
            var victim = net.minecraft.world.entity.EntityType.PIG.create(level, EntitySpawnReason.TRIGGERED); require(victim != null, "rope target"); probes.add(victim);
            victim.setPos(player.position().add(2, 0, 0)); require(level.addFreshEntity(victim), "spawn rope target");
            rope.setOwner(player); rope.bindTarget(victim); require(level.addFreshEntity(rope), "spawn rope");
            player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
            require(!rope.isRemoved() && player.isUsingItem(), "held right click preserves existing connection and starts use");
            player.stopUsingItem(); float oldLength = rope.ropeLength(); player.setShiftKeyDown(true); rope.tick();
            require(rope.ropeLength() == oldLength, "sneaking alone never reels or pays attached rope");
            var tool = player.getMainHandItem(); player.startUsingItem(InteractionHand.MAIN_HAND);
            tool.getItem().onUseTick(level, player, tool, 71999);
            require(rope.ropeLength() > oldLength, "held sneak right click pays attached rope out");
            player.stopUsingItem(); oldLength = rope.ropeLength();
            player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); rope.control(player, 0);
            require(rope.ropeLength() == oldLength, "unheld rope rejects control");
            passed.add("held rope right click retains connection; sneaking alone and unheld controls cannot alter length");
            return List.copyOf(passed);
        } finally {
            player.stopUsingItem();
            for (var e : probes) e.discard();
            for (var e : level.getEntitiesOfClass(ItemEntity.class, area)) if (!existing.contains(e.getUUID())) e.discard();
            level.setBlock(origin, saved, 3);
        }
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException("MFQM boots verification failed: " + message); }
    private BootsPortChecks() {}
}
