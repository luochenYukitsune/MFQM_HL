package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.block.StickyBoardBlock;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** The actual equipment, not a visual copy. Unloading never drops it a second time. */
public final class StuckBootsEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(StuckBootsEntity.class, EntityDataSerializers.ITEM_STACK);
    private BlockPos anchor = BlockPos.ZERO;
    private boolean transferring;
    private double ropeProgress;
    private long lastRopeTick = Long.MIN_VALUE;

    public StuckBootsEntity(EntityType<? extends StuckBootsEntity> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(STACK, ItemStack.EMPTY); }
    public ItemStack stack() { return entityData.get(STACK).copy(); }
    public BlockPos anchor() { return anchor; }
    @Override public boolean isPickable() { return !isRemoved() && !entityData.get(STACK).isEmpty(); }
    @Override public boolean isPushable() { return false; }

    public static boolean tryLeaveBehind(LivingEntity victim, BlockPos anchor) {
        if (!(victim.level() instanceof ServerLevel level)) return false;
        return tryLeaveBehind(victim, anchor, level::addFreshEntity);
    }
    /** Injection seam lets checks reproduce a rejected world insertion without clearing equipment. */
    static boolean tryLeaveBehind(LivingEntity victim, BlockPos anchor, Predicate<StuckBootsEntity> insert) {
        if (!(victim.level() instanceof ServerLevel level) || !victim.isAlive() || victim.isSpectator()
                || victim.hasInfiniteMaterials() || !mediumPresent(level, anchor)) return false;
        ItemStack original = victim.getItemBySlot(EquipmentSlot.FEET);
        if (original.isEmpty()) return false;
        StuckBootsEntity entity = ModEntities.STUCK_BOOTS.get().create(level, EntitySpawnReason.TRIGGERED);
        if (entity == null) return false;
        entity.anchor = anchor.immutable();
        ItemStack snapshot = original.copy();
        entity.entityData.set(STACK, snapshot);
        entity.transferring = true; // Join callbacks must not access the snapshot before the feet slot is cleared.
        double top = StickyBoardBlock.isCoated(level.getBlockState(anchor)) ? .09 : .85;
        entity.setPos(anchor.getX()+.5, anchor.getY()+top, anchor.getZ()+.5);
        try {
            if (!insert.test(entity)) { entity.discard(); return false; }
        } catch(RuntimeException | Error failure) { entity.discard(); throw failure; }
        // Entity join callbacks may change equipment; never clear their replacement or retain a duplicate.
        if (entity.isRemoved() || !ItemStack.matches(snapshot, victim.getItemBySlot(EquipmentSlot.FEET))) { entity.discard(); return false; }
        victim.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        entity.transferring = false;
        level.playSound(null,anchor,net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL,net.minecraft.sounds.SoundSource.NEUTRAL,.35F,.65F);
        return true;
    }
    private static boolean mediumPresent(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return state.is(ModBlocks.byId("glue")) || StickyBoardBlock.isCoated(state);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || transferring || isRemoved()) return;
        if (entityData.get(STACK).isEmpty()) { discard(); return; }
        // An unloaded anchor is unknown, not removed. Do not force-load or release equipment.
        if (server.hasChunkAt(anchor) && !mediumPresent(server, anchor)) release(Vec3.ZERO);
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide()) return player.getItemInHand(hand).isEmpty() || player.getItemInHand(hand).is(ModItems.byId("long_stick")) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return player instanceof ServerPlayer server && recover(server, hand, 5) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
    public boolean recover(ServerPlayer player, InteractionHand hand, double requestedReach) {
        ItemStack held = player.getItemInHand(hand);
        double toolReach = held.isEmpty() ? 2.5 : held.is(ModItems.byId("long_stick")) ? 5 : 0;
        if (!Double.isFinite(requestedReach) || requestedReach <= 0 || !canRecover(player, Math.min(toolReach, requestedReach))) return false;
        return transferToInventory(player);
    }
    private boolean canRecover(ServerPlayer player, double reach) {
        return reach > 0 && !transferring && !isRemoved() && player.isAlive() && !player.isSpectator()
                && player.level() == level() && player.distanceToSqr(this) <= reach*reach
                && player.hasLineOfSight(this) && !entityData.get(STACK).isEmpty();
    }
    private boolean transferToInventory(ServerPlayer player) {
        transferring = true;
        try {
            ItemStack remainder = stack();
            player.getInventory().add(remainder);
            // add can partially merge a modded feet stack. Keep precisely the unaccepted remainder.
            entityData.set(STACK, remainder.copy());
            if (!remainder.isEmpty()) return false;
            discard(); return true;
        } finally { transferring = false; }
    }
    /** Server connector calls this only while a held rope is under actual positive tension. */
    public void pullWithRope(ServerPlayer player, double force) {
        if (!Double.isFinite(force) || force <= 0 || !canRecover(player, 32)
                || !(player.getMainHandItem().is(ModItems.byId("rope")) || player.getOffhandItem().is(ModItems.byId("rope")))) return;
        long tick = level().getGameTime(); if (lastRopeTick == tick) return; lastRopeTick = tick;
        ropeProgress += Math.min(.15, force);
        if (ropeProgress < 1) return;
        if (player.distanceToSqr(this) <= 6.25) transferToInventory(player);
        else release(player.getEyePosition().subtract(position()).normalize().scale(.25).add(0,.1,0));
    }
    private boolean release(Vec3 velocity) {
        if (!(level() instanceof ServerLevel server) || transferring || isRemoved()) return false;
        transferring = true;
        try {
            ItemStack contents = stack();
            if (contents.isEmpty()) { discard(); return true; }
            var drop = new ItemEntity(server, getX(), getY()+.1, getZ(), contents);
            drop.setDeltaMovement(velocity); drop.setDefaultPickUpDelay();
            if (!server.addFreshEntity(drop)) return false;
            entityData.set(STACK, ItemStack.EMPTY); discard(); return true;
        } finally { transferring = false; }
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.store("Item", ItemStack.CODEC, entityData.get(STACK));
        output.store("Anchor", BlockPos.CODEC, anchor);
        output.putDouble("RopeProgress", ropeProgress);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(STACK, input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        anchor = input.read("Anchor", BlockPos.CODEC).orElse(blockPosition()).immutable();
        double progress = input.getDoubleOr("RopeProgress", 0);
        ropeProgress = Double.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
    }
}
