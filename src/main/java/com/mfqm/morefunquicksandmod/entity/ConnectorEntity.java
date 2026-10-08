package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-owned rescue/rope/cable simulation. Client packets contain bounded control intentions only. */
public final class ConnectorEntity extends Projectile {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(ConnectorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(ConnectorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(ConnectorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ATTACHED = SynchedEntityData.defineId(ConnectorEntity.class, EntityDataSerializers.BOOLEAN);
    private EntityReference<Entity> grabbed;
    private BlockPos support;
    private float strain;
    private long lastControlTick = Long.MIN_VALUE;
    private int sandTicks;
    public ConnectorEntity(EntityType<? extends ConnectorEntity> type, Level level) { super(type, level); }
    public String kind() { return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath(); }
    public double maxLength() { return kind().equals("hook") ? 48 : kind().equals("rescue") ? 1.5 : 32; }
    public float ropeLength() { return entityData.get(LENGTH); }
    public boolean attached() { return entityData.get(ATTACHED); }
    public void bindTarget(Entity target) { grabbed = EntityReference.of(target); entityData.set(TARGET_ID, target.getId()); setPos(target.getX(),target.getY()+target.getBbHeight()*0.5,target.getZ()); attach(); }
    @Override public void setOwner(Entity entity) { super.setOwner(entity); entityData.set(OWNER_ID, entity == null ? -1 : entity.getId()); }
    public Entity visibleOwner() { return level().isClientSide() ? level().getEntity(entityData.get(OWNER_ID)) : getOwner(); }
    public Entity visibleTarget() { return level().isClientSide() ? level().getEntity(entityData.get(TARGET_ID)) : EntityReference.get(grabbed, level(), Entity.class); }
    public boolean isOwnedBy(ServerPlayer player) { return getOwner() == player && player.level() == level(); }
    public boolean holdingTool(ServerPlayer player) {
        String item = kind().equals("hook") ? "grappling_hook" : kind().equals("rescue") ? "rescuing" : "rope";
        return player.getMainHandItem().is(ModItems.byId(item)) || player.getOffhandItem().is(ModItems.byId(item))
                || (kind().equals("rescue") && player.getMainHandItem().isEmpty());
    }
    public void control(ServerPlayer player, int action) {
        if (!player.isAlive() || player.isSpectator() || !isOwnedBy(player) || !holdingTool(player) || action < 0 || action > 3 || distanceToSqr(player) > (maxLength()+4)*(maxLength()+4)) return;
        long tick = level().getGameTime(); if (lastControlTick == tick) return; lastControlTick = tick;
        if (action == 2) { discard(); return; }
        float change = action == 1 ? 0.15F : -0.15F;
        entityData.set(LENGTH, net.minecraft.util.Mth.clamp(ropeLength()+change, 0.5F, (float)maxLength()));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1); builder.define(TARGET_ID, -1); builder.define(LENGTH, 1.5F); builder.define(ATTACHED, false);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (!(getOwner() instanceof ServerPlayer player) || !player.isAlive() || player.level() != level() || !holdingTool(player)) { discard(); return; }
        entityData.set(OWNER_ID, player.getId());
        if (distanceToSqr(player) > (maxLength()+4)*(maxLength()+4) || tickCount > 12000) { breakConnection(player, false); return; }
        if (!attached()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, entity -> canHitEntity(entity) && (entity instanceof LivingEntity || (kind().equals("rope") && entity instanceof StuckBootsEntity)));
            setPos(position().add(getDeltaMovement()));
            if (hit.getType() != HitResult.Type.MISS) { setPos(hit.getLocation()); onHit(hit); }
            else setDeltaMovement(getDeltaMovement().scale(0.99).add(0,-0.03,0));
            updateRotation();
            if (distanceToSqr(player) > maxLength()*maxLength() || tickCount > 100) discard();
            return;
        }
        Entity target = EntityReference.get(grabbed, level(), Entity.class);
        if (grabbed != null) {
            if (target == null || !target.isAlive() || target.level()!=level()) { discard(); return; }
            setPos(target.getX(), target.getY()+target.getBbHeight()*0.5, target.getZ()); entityData.set(TARGET_ID, target.getId());
        } else if (support == null || server.getBlockState(support).isAir()) { discard(); return; }
        Vec3 hand = player.position().add(0, player.getBbHeight()*0.65, 0);
        double distance = position().distanceTo(hand);
        double stretch = distance-ropeLength();
        if (stretch > 0.1) {
            double resistance=target==null?resistance(player):resistance(target);
            double force = Math.min(kind().equals("hook") ? 0.24 : 0.15, stretch * 0.08)/resistance;
            Vec3 direction = position().subtract(hand).normalize();
            if (target instanceof StuckBootsEntity boots) {
                boots.pullWithRope(player, force);
            } else if (target instanceof LivingEntity living && (player.onGround() || QuicksandPhysics.state(player).depth < 0.25)) {
                Vec3 anchor = hand.add(0, 0.8, 0); QuicksandPhysics.rescue(living, anchor, force);
                if (!player.onGround()) player.setDeltaMovement(player.getDeltaMovement().add(direction.scale(force*0.3)));
            } else { QuicksandPhysics.rescue(player, position().add(0,0.5,0), force); }
            player.hurtMarked = true;
            strain += (float)Math.max(0, stretch - (kind().equals("hook") ? 6 : 3))*0.05F;
            if (strain>2 && tickCount%16==0 && !player.hasInfiniteMaterials()) {
                ItemStack tool=player.getMainHandItem().is(ModItems.byId(kind().equals("hook")?"grappling_hook":"rope"))?player.getMainHandItem():player.getOffhandItem();
                if(tool.isDamageableItem()){
                    if(tool.getDamageValue()+2>=tool.getMaxDamage()){breakConnection(player,true);return;}
                    tool.setDamageValue(tool.getDamageValue()+2);
                }
            }
        } else strain = Math.max(0, strain-0.1F);
        if (strain > 12 || distance > maxLength()+1) breakConnection(player, true);
        if (kind().equals("hook") && support != null && strain > 5 && (server.getBlockState(support).is(BlockTags.LOGS)
                || server.getBlockState(support).is(BlockTags.PLANKS) || server.getBlockState(support).is(BlockTags.WOOL))
                && server.mayInteract(player,support) && player.mayUseItemAt(support, net.minecraft.core.Direction.UP, player.getMainHandItem())) {
            server.destroyBlock(support, true, player); breakConnection(player, false);
        }
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        if (target == getOwner() || target.isSpectator() || (target instanceof LivingEntity living && living.hasInfiniteMaterials())) return;
        if (!(target instanceof LivingEntity) && !(kind().equals("rope") && target instanceof StuckBootsEntity)) return;
        if(kind().equals("hook") && target instanceof LivingEntity living && level() instanceof ServerLevel server)living.hurtServer(server,damageSources().thrown(this,getOwner()),1.5F);
        bindTarget(target);
        // Legacy HookAsRider was a helper mounting workaround, not permission to mount the player.
        if(target instanceof LivingEntity && ModConfig.SERVER.hookAsRider.get() && !(target instanceof BlobEntity) && !(target instanceof net.minecraft.world.entity.player.Player))startRiding(target,false,true);
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        var state = level().getBlockState(hit.getBlockPos());
        if (kind().equals("rescue")) { discard(); return; }
        if (kind().equals("rope") && !validRopeSupport(hit.getBlockPos(), state)) { discard(); return; }
        if (kind().equals("hook") && (state.is(BlockTags.SAND) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL))) {
            if(++sandTicks>10)discard();else{setDeltaMovement(getDeltaMovement().scale(.85));setPos(hit.getLocation().add(getDeltaMovement().scale(-.1)));}return;
        }
        if (kind().equals("hook") && (state.is(BlockTags.LEAVES) || state.getCollisionShape(level(),hit.getBlockPos()).isEmpty())) { discard(); return; }
        support = hit.getBlockPos().immutable(); setPos(hit.getLocation()); attach();
    }
    private boolean validRopeSupport(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if(state.is(BlockTags.LEAVES) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES))return true;
        if(state.is(BlockTags.SAND) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL) || state.getCollisionShape(level(),pos).isEmpty())return false;
        boolean x=clear(pos.east()) && clear(pos.west());boolean y=clear(pos.above()) && clear(pos.below());boolean z=clear(pos.north()) && clear(pos.south());
        return (x&&z) || (x&&y) || (y&&z);
    }
    private boolean clear(BlockPos pos){return level().getBlockState(pos).getCollisionShape(level(),pos).isEmpty();}
    private double resistance(Entity entity){var contact=QuicksandPhysics.state(entity);return 1+Math.max(0,contact.cachedLoad)*.35+Math.max(0,contact.depth)*.6;}
    private void attach() {
        entityData.set(ATTACHED, true); setNoGravity(true); setDeltaMovement(Vec3.ZERO);
        if (getOwner() != null) entityData.set(LENGTH, (float)Math.min(maxLength(), position().distanceTo(getOwner().getEyePosition())));
        playSound(SoundEvents.WOOD_HIT, 0.5F, 1);
    }
    private void breakConnection(ServerPlayer player, boolean damaged) {
        if (damaged && kind().equals("hook")) {
            ItemStack held = player.getMainHandItem().is(ModItems.byId("grappling_hook")) ? player.getMainHandItem() : player.getOffhandItem();
            if (!player.hasInfiniteMaterials()) { held.shrink(1); player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.byId("broken_grappling_hook"))); }
            spawnAtLocation(player.level(), ModItems.byId("hook"));
        }
        if(damaged && kind().equals("rope") && !player.hasInfiniteMaterials()){
            ItemStack held=player.getMainHandItem().is(ModItems.byId("rope"))?player.getMainHandItem():player.getOffhandItem();if(held.is(ModItems.byId("rope")))held.shrink(1);
        }
        playSound(SoundEvents.ITEM_BREAK.value(), 0.7F, 0.8F); discard();
    }
    @Override public void remove(Entity.RemovalReason reason){
        if(reason.shouldDestroy() && !level().isClientSide() && kind().equals("rescue") && getOwner() instanceof ServerPlayer player){
            if (player.isUsingItem() && player.getUseItem().is(ModItems.byId("rescuing"))) player.stopUsingItem();
            for(net.minecraft.world.InteractionHand hand:net.minecraft.world.InteractionHand.values())if(player.getItemInHand(hand).is(ModItems.byId("rescuing")))player.setItemInHand(hand,ItemStack.EMPTY);
        }
        super.remove(reason);
    }
    @Override public boolean hurtServer(ServerLevel server, DamageSource source, float damage) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); EntityReference.store(grabbed, output, "Grabbed");
        if (support != null) output.store("Support", BlockPos.CODEC, support);
        output.putBoolean("Attached", attached()); output.putFloat("Length", ropeLength()); output.putFloat("Strain", strain);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); grabbed = input.read("Grabbed", EntityReference.<Entity>codec()).orElse(null);
        support = input.read("Support", BlockPos.CODEC).orElse(null); entityData.set(ATTACHED, input.getBooleanOr("Attached", false));
        entityData.set(LENGTH, net.minecraft.util.Mth.clamp(input.getFloatOr("Length", 1.5F), 0.5F, (float)maxLength()));
        strain = Math.max(0, input.getFloatOr("Strain", 0));
    }
}
