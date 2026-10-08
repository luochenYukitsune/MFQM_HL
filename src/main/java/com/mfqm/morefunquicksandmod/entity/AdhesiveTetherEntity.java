package com.mfqm.morefunquicksandmod.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** A synchronized visual anchor. AdhesionController alone applies physical forces. */
public final class AdhesiveTetherEntity extends Entity {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(AdhesiveTetherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIDE = SynchedEntityData.defineId(AdhesiveTetherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> MATERIAL = SynchedEntityData.defineId(AdhesiveTetherEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> STRENGTH = SynchedEntityData.defineId(AdhesiveTetherEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> BREAK_TICK = SynchedEntityData.defineId(AdhesiveTetherEntity.class, EntityDataSerializers.LONG);
    private long refreshed;
    private Vec3 breakAnkle,breakShin;
    public AdhesiveTetherEntity(EntityType<? extends AdhesiveTetherEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public void configure(Entity target, Vec3 anchor, String material, int side, float strength) {
        if(breaking())return;
        setPos(anchor); entityData.set(TARGET,target.getId()); entityData.set(MATERIAL,material);
        entityData.set(SIDE,side); entityData.set(STRENGTH,strength); refreshed=level().getGameTime();
    }
    public Entity target() { return level().getEntity(entityData.get(TARGET)); }
    public String material() { return entityData.get(MATERIAL); }
    public int side() { return entityData.get(SIDE); }
    public float strength() { return entityData.get(STRENGTH); }
    /** Controller removes the physical anchor first; this keeps only a short cosmetic recoil. */
    public void beginBreak() { if(!breaking())entityData.set(BREAK_TICK,level().getGameTime()); }
    public boolean breaking() { return entityData.get(BREAK_TICK)!=Long.MIN_VALUE; }
    public double breakProgress(float partialTick) {
        return breaking()?Math.clamp(((double)level().getGameTime()-entityData.get(BREAK_TICK)+partialTick)/6,0,1):0;
    }
    /** Client renderer captures the last visible model endpoint once, never follows the freed leg. */
    public Vec3 breakAnkle(Vec3 endpoint) { if(breakAnkle==null)breakAnkle=endpoint;return breakAnkle; }
    public Vec3 breakShin(Vec3 endpoint) { if(breakShin==null)breakShin=endpoint;return breakShin; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET,-1); builder.define(SIDE,0); builder.define(MATERIAL,""); builder.define(STRENGTH,1F);
        builder.define(BREAK_TICK,Long.MIN_VALUE);
    }
    @Override public void tick() {
        super.tick();
        if(level() instanceof ServerLevel && (target()==null || !target().isAlive()
                || breaking() && breakProgress(0)>=1 || !breaking() && level().getGameTime()-refreshed>3)) discard();
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {}
    @Override protected void readAdditionalSaveData(ValueInput input) { discard(); }
}
