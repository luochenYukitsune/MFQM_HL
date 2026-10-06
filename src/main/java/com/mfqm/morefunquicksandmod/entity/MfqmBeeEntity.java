package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Legacy hostile flying bee, independent from the vanilla pollination/hive lifecycle. */
public final class MfqmBeeEntity extends Monster {
    private Vec3 destination;
    private int flightDelay;
    private int stingDelay;
    public MfqmBeeEntity(EntityType<? extends MfqmBeeEntity> type, Level level) {
        super(type, level); setNoGravity(true); xpReward = 1;
    }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.ATTACK_DAMAGE, 1.25)
                .add(Attributes.FOLLOW_RANGE, 16).add(Attributes.MOVEMENT_SPEED, 0.3);
    }
    @Override protected void registerGoals() { targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true)); }
    @Override public void tick() {
        super.tick(); setNoGravity(true);
        if (!(level() instanceof ServerLevel server)) return;
        if (stingDelay > 0) --stingDelay;
        if (getTarget() != null && getTarget().isAlive() && hasLineOfSight(getTarget())) {
            destination = getTarget().getEyePosition().add(0, -0.3, 0);
            if (distanceToSqr(getTarget()) < 1.3 && stingDelay == 0) {
                Vec3 old = getTarget().getDeltaMovement();
                doHurtTarget(server, getTarget());
                getTarget().setDeltaMovement(old.scale(0.8));
                setDeltaMovement(position().subtract(destination).normalize().scale(0.25)); stingDelay = 25;
            }
        } else if (--flightDelay <= 0 || destination == null || position().distanceToSqr(destination) < 1 || horizontalCollision) {
            flightDelay = 25 + random.nextInt(25);
            BlockPos candidate = blockPosition().offset(random.nextInt(9)-4, random.nextInt(7)-3, random.nextInt(9)-4);
            destination = server.isEmptyBlock(candidate) ? Vec3.atCenterOf(candidate) : position().add(0, 1, 0);
        }
        if (destination != null && stingDelay < 20) {
            Vec3 to = destination.subtract(position()).normalize().scale(isInWater() ? 0.06 : 0.18);
            if (horizontalCollision) to = to.add(0, 0.1, 0);
            setDeltaMovement(getDeltaMovement().scale(0.7).add(to.scale(0.3)));
            setYRot((float)(-Math.atan2(to.x, to.z)*180/Math.PI)); yBodyRot = getYRot();
        }
    }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.BEE_SAY.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.BEE_HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return ModSounds.BEE_HURT.get(); }
}
