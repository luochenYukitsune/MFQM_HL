package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** The four legacy blobs share geometry but retain separate capture, combat and habitat rules. */
public final class BlobEntity extends Monster {
    private static final EntityDataAccessor<Float> DEPTH = SynchedEntityData.defineId(BlobEntity.class, EntityDataSerializers.FLOAT);
    private final java.util.List<ItemStack> stolenItems = new java.util.ArrayList<>();
    private int jumpDelay = 20;
    private int pullDelay;
    private int attackDelay;
    private int splitDelay;
    private boolean releasing;
    public float squish;
    public float oldSquish;
    private boolean wasGrounded;

    public enum Kind {
        VORE("sinking_slime", 20, 2, 45, 0.015F, 1F, 5),
        MUD("mire", 25, 2, 35, 0.015F, 1F, 2),
        SAND("soft_quicksand", 15, 1, 20, 0.020F, 0.75F, 0),
        TAR("tar", 30, 3, 55, 0.015F, 1F, 3);
        public final String coating;
        public final double health, damage;
        public final int pullTicks, effect;
        public final float sink, pulse;
        Kind(String coating, double health, double damage, int pullTicks, float sink, float pulse, int effect) {
            this.coating = coating; this.health = health; this.damage = damage;
            this.pullTicks = pullTicks; this.sink = sink; this.pulse = pulse; this.effect = effect;
        }
    }

    public BlobEntity(EntityType<? extends BlobEntity> type, Level level) { super(type, level); this.xpReward = 3; }
    public Kind kind() {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
        return switch (id) { case "muddy_blob" -> Kind.MUD; case "sand_blob" -> Kind.SAND; case "tar_slime" -> Kind.TAR; default -> Kind.VORE; };
    }
    public static AttributeSupplier.Builder attributes(Kind kind) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ATTACK_DAMAGE, kind.damage).add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 16).add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 12));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(DEPTH, 0F); }
    public float swallowDepth() { return entityData.get(DEPTH); }
    private void setDepth(float value) { entityData.set(DEPTH, Mth.clamp(value, 0, 14)); }
    @Override public void tick() {
        oldSquish = squish;
        super.tick();
        squish *= 0.8F;
        if (onGround() != wasGrounded) squish = onGround() ? -0.5F : 1F;
        wasGrounded = onGround();
        if (!(level() instanceof ServerLevel server)) return;
        if (splitDelay > 0) --splitDelay;
        if (attackDelay > 0) --attackDelay;
        Kind kind = kind();
        if (kind == Kind.SAND && (isInWater() || server.isRainingAt(blockPosition())) && tickCount % 20 == 0)
            super.hurtServer(server, damageSources().drown(), 1);
        LivingEntity victim = getFirstPassenger() instanceof LivingEntity living ? living : null;
        if (victim != null) {
            if (victim.level() != level() || !victim.isAlive() || victim.isSpectator() || victim.hasInfiniteMaterials() || isInWater()) { releasePassenger(victim); return; }
            float depth = swallowDepth() + kind.sink;
            if (--pullDelay <= 0) {
                pullDelay = kind.pullTicks + random.nextInt(10); depth += kind.pulse; squish = 1.5F;
                playSound(SoundEvents.MAGMA_CUBE_JUMP, 0.25F, 0.3F);
            }
            // Crouching struggles against capture; helpers or attacking the blob give a faster escape.
            if (victim.isShiftKeyDown()) depth -= 0.06F;
            setDepth(depth);
            if (swallowDepth() <= 0) { releasePassenger(victim); return; }
            if (kind != Kind.SAND && tickCount % 20 == 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, kind.effect, false, false));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, kind.effect, false, false));
                if (kind == Kind.MUD || kind == Kind.TAR) victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, kind == Kind.TAR ? 3 : 0, false, false));
            }
            if (kind != Kind.SAND) QuicksandPhysics.coat(victim, kind.coating, Mth.clamp((int)(depth * 0.8F), 1, 10), kind == Kind.VORE ? 500 : 1000);
            if (depth >= 12.5F && tickCount % (kind == Kind.TAR ? 2 : 4) == 0 && random.nextInt(5) == 0)
                victim.hurtServer(server, kind == Kind.SAND ? damageSources().inWall() : damageSources().drown(),
                        (float)(victim.getMaxHealth() * 0.1 * ModConfig.SERVER.damageMultiplier.get()));
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
        } else {
            setDepth(swallowDepth() - 0.5F);
            LivingEntity target = getTarget();
            if (onGround() && --jumpDelay <= 0) {
                jumpDelay = (target == null ? 40 : 12) + random.nextInt(10);
                Vec3 direction = target == null ? new Vec3(random.nextDouble()-0.5, 0, random.nextDouble()-0.5) : target.position().subtract(position());
                direction = new Vec3(direction.x, 0, direction.z).normalize();
                setDeltaMovement(direction.scale(0.24).add(0, 0.42, 0));
                if (target != null) getLookControl().setLookAt(target, 30, 30);
                playSound(SoundEvents.SLIME_JUMP, 0.5F, 0.7F);
            }
            if (target instanceof Player player && !player.hasInfiniteMaterials() && !player.isPassenger()
                    && attackDelay == 0 && getBoundingBox().inflate(0.15).intersects(player.getBoundingBox()) && hasLineOfSight(player)) {
                attackDelay = 20;
                if (doHurtTarget(server, player)) {
                    float normalizedHealth = player.getHealth() / Math.max(1, player.getMaxHealth() / 20);
                    if (!isInWater() && random.nextInt(Math.max(1, (int)(normalizedHealth / 4))) == 0 && player.startRiding(this, true, true)) {
                        setDepth(0.5F); pullDelay = kind.pullTicks;
                    }
                }
            }
        }
    }
    @Override protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        move.accept(passenger, getX(), getY() + getBbHeight() * 0.75 - Math.min(1.56, swallowDepth() * 0.125), getZ());
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }
    public boolean preventsDismount() { return isAlive() && !releasing && swallowDepth() > 0.05F; }
    public void rescuePassenger(Entity victim, double strength) {
        if (victim.getVehicle() != this) return;
        setDepth(swallowDepth() - (float)Math.max(0.05, strength * 8));
        if (swallowDepth() <= 0) releasePassenger(victim);
    }
    public void releasePassenger(Entity victim) {
        releasing = true;
        try { victim.stopRiding(); } finally { releasing = false; }
        setDepth(0);
        victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.25, 0));
        victim.hurtMarked = true;
    }
    @Override public boolean hurtServer(ServerLevel server, DamageSource source, float damage) {
        Kind kind = kind();
        Player theftTarget = null;
        ItemStack theftStack = ItemStack.EMPTY;
        ItemStack theftSnapshot = ItemStack.EMPTY;
        if (kind == Kind.TAR && source.is(DamageTypeTags.IS_FIRE)) { damage *= 2; igniteForSeconds(5); }
        if ((kind == Kind.MUD || kind == Kind.TAR) && server.getDifficulty() == Difficulty.HARD
                && source.getEntity() instanceof Player player && source.getDirectEntity() == player
                && !player.hasInfiniteMaterials() && damage < 3F * getMaxHealth()/25F && damage < getHealth()
                && stolenItems.size() < 5 && (player.getMainHandItem().is(ItemTags.MELEE_WEAPON_ENCHANTABLE) || player.getMainHandItem().is(ItemTags.MINING_ENCHANTABLE))) {
            theftTarget = player; theftStack = player.getMainHandItem(); theftSnapshot = theftStack.copy();
        }
        if (kind == Kind.SAND && !source.is(DamageTypeTags.IS_DROWNING)) {
            damage *= 0.2F;
            if (splitDelay == 0 && source.getDirectEntity() instanceof Player && random.nextInt(5) == 0 && getHealth() > damage) {
                splitDelay = 200;
                BlobEntity split = ModEntities.SAND_BLOB.get().create(server, EntitySpawnReason.TRIGGERED);
                if (split != null) { split.setPos(getX()+0.8, getY(), getZ()); split.splitDelay = 200; server.addFreshEntity(split); setHealth(getMaxHealth()); }
            }
        }
        boolean result = super.hurtServer(server, source, damage);
        // Damage hooks and invulnerability may reject a hit or change its held item.
        // Transfer only the exact equipment that qualified for an accepted, nonlethal hit.
        if (result && isAlive() && theftTarget != null && stolenItems.size() < 5
                && theftTarget.getMainHandItem() == theftStack && ItemStack.matches(theftStack, theftSnapshot)) {
            stolenItems.add(theftSnapshot); theftTarget.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            setPersistenceRequired(); theftTarget.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            playSound(SoundEvents.SLIME_ATTACK, 0.5F, 0.6F);
        }
        if (result && !getPassengers().isEmpty()) {
            setDepth(swallowDepth() - damage * 0.45F);
            if (!isAlive() || swallowDepth() <= 0) for (Entity passenger : java.util.List.copyOf(getPassengers())) releasePassenger(passenger);
        }
        return result;
    }
    @Override protected void dropCustomDeathLoot(ServerLevel server, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(server, source, recentlyHit);
        int looting = source.getEntity() instanceof LivingEntity attacker
                ? net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentLevel(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOOTING), attacker) : 0;
        if (kind() == Kind.SAND) {
            dropCount(server, Items.SLIME_BALL, Math.max(0, (random.nextInt(2) + random.nextInt(1 + looting)) * 2 - 2));
            dropCount(server, ModItems.byId("soft_quicksand"), Math.max(0, random.nextInt(2) + random.nextInt(1 + looting) - 1));
            dropCount(server, Items.SAND, Math.max(0, (random.nextInt(2) + random.nextInt(1 + looting)) * 3 - 1));
        } else {
            dropCount(server, Items.SLIME_BALL, (kind() == Kind.VORE ? 8 : 4) + random.nextInt(8) + random.nextInt(1 + looting));
            if (kind() == Kind.MUD) dropCount(server, ModItems.byId("peat_item"), random.nextInt(2) + random.nextInt(1 + looting));
            if (kind() == Kind.TAR) dropCount(server, Items.COAL, 1 + random.nextInt(4) + random.nextInt(1 + looting));
        }
    }
    @Override protected void dropEquipment(ServerLevel server) {
        super.dropEquipment(server);
        returnStolenItems(server);
    }
    @Override public void remove(RemovalReason reason) {
        // Unloading and dimension transfer preserve the saved inventory. Destructive
        // removal (including peaceful despawn) must return player property once.
        if (!isRemoved() && reason.shouldDestroy() && level() instanceof ServerLevel server) returnStolenItems(server);
        super.remove(reason);
    }
    private void returnStolenItems(ServerLevel server) {
        if (stolenItems.isEmpty()) return;
        var items = java.util.List.copyOf(stolenItems);
        stolenItems.clear();
        for (ItemStack stack : items) spawnAtLocation(server, stack);
    }
    private void dropCount(ServerLevel level, net.minecraft.world.item.Item item, int amount) { if (amount > 0) spawnAtLocation(level, new ItemStack(item, amount)); }
    @Override public boolean removeWhenFarAway(double distance) { return stolenItems.isEmpty() && getPassengers().isEmpty(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SLIME_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SLIME_DEATH; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putFloat("SwallowDepth", swallowDepth()); output.putInt("SplitCooldown", splitDelay);
        var list = output.list("StolenItems", ItemStack.CODEC); stolenItems.forEach(list::add);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); setDepth(input.getFloatOr("SwallowDepth", 0)); splitDelay = Math.max(0, input.getIntOr("SplitCooldown", 0));
        stolenItems.clear(); for (ItemStack stack : input.listOrEmpty("StolenItems", ItemStack.CODEC)) if (stolenItems.size() < 5 && !stack.isEmpty()) stolenItems.add(stack);
        if (!stolenItems.isEmpty()) setPersistenceRequired();
    }
    public static boolean canSpawn(EntityType<BlobEntity> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !ModConfig.SERVER.enableCustomSlimes.get()) return false;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        boolean enabled = switch (id) { case "muddy_blob" -> ModConfig.SERVER.spawnMuddyBlob.get(); case "sand_blob" -> ModConfig.SERVER.spawnSandBlob.get(); case "tar_slime" -> ModConfig.SERVER.spawnTarSlime.get(); default -> ModConfig.SERVER.spawnVoreSlime.get(); };
        if (!enabled) return false;
        if (id.equals("vore_slime")) return pos.getY() < level.getSeaLevel() - 12 && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
        if (id.equals("tar_slime")) {
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-3,-2,-3), pos.offset(3,1,3)))
                if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(near).getBlock()).getPath().equals("tar")) return true;
            return false;
        }
        return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
    }
}
