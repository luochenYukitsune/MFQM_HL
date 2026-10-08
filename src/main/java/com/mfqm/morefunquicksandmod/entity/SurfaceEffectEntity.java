package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Synced, bounded surface effects; probing is evaluated once on the server. */
public final class SurfaceEffectEntity extends Entity {
    private static final EntityDataAccessor<BlockState> MATERIAL = SynchedEntityData.defineId(SurfaceEffectEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(SurfaceEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SurfaceEffectEntity.class, EntityDataSerializers.INT);
    private EntityReference<Entity> target;
    public SurfaceEffectEntity(EntityType<? extends SurfaceEffectEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public String kind() { return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath(); }
    public void configure(BlockState material, Entity attached, int lifetime) {
        entityData.set(MATERIAL, material); entityData.set(LIFE, net.minecraft.util.Mth.clamp(lifetime, 1, 200));
        target = EntityReference.of(attached); entityData.set(TARGET, attached == null ? -1 : attached.getId());
    }
    public BlockState material() { return entityData.get(MATERIAL); }
    public int lifetime() { return entityData.get(LIFE); }
    public Entity target() { return level().isClientSide() ? level().getEntity(entityData.get(TARGET)) : EntityReference.getEntity(target, level()); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(MATERIAL, Blocks.SAND.defaultBlockState()); builder.define(TARGET, -1); builder.define(LIFE, 40); }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            Entity attached = target();
            if (attached != null) entityData.set(TARGET, attached.getId());
            if (kind().equals("long_stick") && tickCount == 1) {
                int depth = 0;
                BlockPos below = blockPosition();
                while (depth < 16 && server.hasChunkAt(below) && server.getBlockState(below).is(material().getBlock())) { ++depth; below = below.below(); }
                playSound(depth > 2 ? SoundEvents.SLIME_SQUISH : SoundEvents.WOOD_HIT, 0.7F, depth > 2 ? 0.5F : 1.2F);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, material()), getX(), getY()+0.5, getZ(), 10,0.15,0.1,0.15,0.04);
                if (attached instanceof net.minecraft.server.level.ServerPlayer player)
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.mfqm.probe_depth", depth), true);
            }
            if (kind().equals("tar_treads") && (attached == null || !attached.isAlive() || attached.level()!=level()
                    || attached.position().distanceToSqr(position()) > 9)) { discard(); return; }
            // Legacy tar_treads IDs remain readable; aggregate adhesion is applied only by the controller.
            if (tickCount >= lifetime()) {
                if (kind().equals("bubble")) { server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, material()), getX(),getY(),getZ(),6,0.1,0.02,0.1,0.03); playSound(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.25F, 0.8F); }
                discard();
            }
        }
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput output) { output.store("Material", BlockState.CODEC, material()); output.putInt("Lifetime", lifetime()); EntityReference.store(target, output, "Target"); }
    @Override protected void readAdditionalSaveData(ValueInput input) { entityData.set(MATERIAL, input.read("Material", BlockState.CODEC).orElse(Blocks.SAND.defaultBlockState())); entityData.set(LIFE, net.minecraft.util.Mth.clamp(input.getIntOr("Lifetime",40),1,200)); target = input.read("Target", EntityReference.<Entity>codec()).orElse(null); }
}
