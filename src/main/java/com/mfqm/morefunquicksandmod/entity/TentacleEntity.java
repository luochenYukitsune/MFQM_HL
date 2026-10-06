package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** A destructible capture appendage tied to a pit and a single victim UUID. */
public final class TentacleEntity extends Entity {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.FLOAT);
    private EntityReference<LivingEntity> target;
    private float health = 10;
    private int hurtDelay;
    public TentacleEntity(EntityType<? extends TentacleEntity> type, Level level) { super(type, level); setNoGravity(true); }
    public boolean isMud() { return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath().equals("mud_tentacles"); }
    public void bind(LivingEntity victim) { target = EntityReference.of(victim); entityData.set(TARGET, victim.getId()); }
    public float extension() { return entityData.get(HEIGHT); }
    public LivingEntity victim() {
        if (level().isClientSide()) return level().getEntity(entityData.get(TARGET)) instanceof LivingEntity living ? living : null;
        return EntityReference.get(target, level(), LivingEntity.class);
    }
    public static boolean acceptsPit(String id, boolean mud) {
        return mud ? java.util.Set.of("mud", "jungle_quicksand", "soft_quicksand").contains(id) : id.equals("swallowing_flesh");
    }
    private boolean validPit() { return acceptsPit(QuicksandPhysics.id(level().getBlockState(blockPosition())), isMud()); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(TARGET, -1); builder.define(HEIGHT, 0F); }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel server)) return;
        if (hurtDelay > 0) --hurtDelay;
        LivingEntity victim = victim();
        boolean active = validPit() && health > 0 && victim != null && victim.isAlive() && !victim.isSpectator()
                && !victim.hasInfiniteMaterials() && victim.level() == level() && distanceToSqr(victim) < 16
                && (isMud() ? ModConfig.SERVER.mudTentacles.get() : ModConfig.SERVER.tentaclesInFlesh.get());
        float height = extension();
        entityData.set(HEIGHT, net.minecraft.util.Mth.clamp(height + (active ? 0.08F : -0.12F), 0, 2));
        if (!active) { if (extension() <= 0) discard(); return; }
        entityData.set(TARGET, victim.getId());
        Vec3 direction = new Vec3(getX()-victim.getX(), 0, getZ()-victim.getZ());
        double reach = direction.length();
        if (height > 0.5) {
            Vec3 pull = direction.scale(0.06).add(0, victim.isShiftKeyDown() ? -0.01 : -0.035, 0);
            victim.setDeltaMovement(victim.getDeltaMovement().add(pull)); victim.hurtMarked = true;
            if (tickCount % 20 == 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 1));
            }
            QuicksandPhysics.coat(victim, isMud() ? "mud" : "swallowing_flesh", 5, 600);
            // Pulling away with a rescue connector or repeated crouching tears the appendage loose.
            if (reach > 2 || (victim.isShiftKeyDown() && tickCount % 20 == 0)) health -= 1;
        }
        if (tickCount % 32 == 0 && isMud()) {
            BlockPos pos = blockPosition();
            var state = server.getBlockState(pos);
            for (var property : state.getProperties()) if (property.getName().equals("variant") && property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty integer && integer.getPossibleValues().contains(3))
                server.setBlock(pos, state.setValue(integer, 3), 3);
        }
    }
    @Override public boolean isPickable() { return health > 0; }
    @Override public boolean hurtServer(ServerLevel server, DamageSource source, float damage) {
        if (hurtDelay > 0 || isInvulnerableToBase(source)) return false;
        health -= damage; hurtDelay = 10; playSound(SoundEvents.SLIME_HURT, 0.5F, 0.5F); return true;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        EntityReference.store(target, output, "Target"); output.putFloat("Health", health); output.putFloat("Extension", extension());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        target = input.read("Target", EntityReference.<LivingEntity>codec()).orElse(null);
        health = input.getFloatOr("Health", 10); entityData.set(HEIGHT, input.getFloatOr("Extension", 0));
    }
}
