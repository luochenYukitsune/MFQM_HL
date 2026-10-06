package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE, MFQM.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<BlobEntity>> VORE_SLIME = blob("vore_slime");
    public static final DeferredHolder<EntityType<?>, EntityType<BlobEntity>> MUDDY_BLOB = blob("muddy_blob");
    public static final DeferredHolder<EntityType<?>, EntityType<BlobEntity>> SAND_BLOB = blob("sand_blob");
    public static final DeferredHolder<EntityType<?>, EntityType<BlobEntity>> TAR_SLIME = blob("tar_slime");
    public static final DeferredHolder<EntityType<?>, EntityType<MfqmBeeEntity>> BEE = entity("bee", EntityType.Builder.of(MfqmBeeEntity::new, MobCategory.MONSTER).sized(0.7F,0.5F).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<TentacleEntity>> TENTACLES = tentacle("tentacles");
    public static final DeferredHolder<EntityType<?>, EntityType<TentacleEntity>> MUD_TENTACLES = tentacle("mud_tentacles");
    public static final DeferredHolder<EntityType<?>, EntityType<SurfaceEffectEntity>> BUBBLE = effect("bubble");
    public static final DeferredHolder<EntityType<?>, EntityType<SurfaceEffectEntity>> TAR_TREADS = effect("tar_treads");
    public static final DeferredHolder<EntityType<?>, EntityType<SurfaceEffectEntity>> SLIME_HOLE = effect("slime_hole");
    public static final DeferredHolder<EntityType<?>, EntityType<SurfaceEffectEntity>> LONG_STICK = effect("long_stick");
    public static final DeferredHolder<EntityType<?>, EntityType<ConnectorEntity>> ROPE = connector("rope");
    public static final DeferredHolder<EntityType<?>, EntityType<ConnectorEntity>> HOOK = connector("hook");
    public static final DeferredHolder<EntityType<?>, EntityType<ConnectorEntity>> RESCUE = connector("rescue");
    public static final DeferredHolder<EntityType<?>, EntityType<LiquidProjectileEntity>> SINKING_POTION = projectile("sinking_potion");
    public static final DeferredHolder<EntityType<?>, EntityType<LiquidProjectileEntity>> LIQUID_BALL = projectile("liquid_ball");

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> entity(String name, EntityType.Builder<T> builder) {
        return ENTITIES.register(name, () -> builder.build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, name))));
    }
    private static DeferredHolder<EntityType<?>, EntityType<BlobEntity>> blob(String id) { return entity(id, EntityType.Builder.of(BlobEntity::new, MobCategory.MONSTER).sized(1.8F,1.8F).clientTrackingRange(10)); }
    private static DeferredHolder<EntityType<?>, EntityType<TentacleEntity>> tentacle(String id) { return entity(id, EntityType.Builder.of(TentacleEntity::new, MobCategory.MISC).sized(0.6F,2).clientTrackingRange(10).updateInterval(2)); }
    private static DeferredHolder<EntityType<?>, EntityType<SurfaceEffectEntity>> effect(String id) { return entity(id, EntityType.Builder.of(SurfaceEffectEntity::new, MobCategory.MISC).sized(0.4F,0.2F).clientTrackingRange(6).updateInterval(3).noSave()); }
    private static DeferredHolder<EntityType<?>, EntityType<ConnectorEntity>> connector(String id) { return entity(id, EntityType.Builder.of(ConnectorEntity::new, MobCategory.MISC).sized(0.2F,0.2F).clientTrackingRange(12).updateInterval(1)); }
    private static DeferredHolder<EntityType<?>, EntityType<LiquidProjectileEntity>> projectile(String id) { return entity(id, EntityType.Builder.of(LiquidProjectileEntity::new, MobCategory.MISC).sized(0.25F,0.25F).clientTrackingRange(8).updateInterval(1)); }

    public static void register(IEventBus bus) { ENTITIES.register(bus); bus.addListener(ModEntities::attributes); bus.addListener(ModEntities::placements); }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(VORE_SLIME.get(), BlobEntity.attributes(BlobEntity.Kind.VORE).build()); event.put(MUDDY_BLOB.get(), BlobEntity.attributes(BlobEntity.Kind.MUD).build());
        event.put(SAND_BLOB.get(), BlobEntity.attributes(BlobEntity.Kind.SAND).build()); event.put(TAR_SLIME.get(), BlobEntity.attributes(BlobEntity.Kind.TAR).build()); event.put(BEE.get(), MfqmBeeEntity.attributes().build());
    }
    private static void placements(RegisterSpawnPlacementsEvent event) {
        for (var entry : java.util.List.of(VORE_SLIME, MUDDY_BLOB, SAND_BLOB, TAR_SLIME)) event.register(entry.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlobEntity::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(BEE.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> reason == EntitySpawnReason.SPAWNER && level.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
    public static EntityType<?> byId(String id) { return ENTITIES.getEntries().stream().filter(holder -> holder.getId().getPath().equals(id)).findFirst().orElseThrow().get(); }

    public static void spawnConnector(ServerPlayer player, String kind, ItemStack held) {
        if (kind.equals("long_stick")) {
            var hit = player.pick(5,0,false);
            if (hit instanceof BlockHitResult block) spawnEffect(player.level(), "long_stick", Vec3.atCenterOf(block.getBlockPos()), player.level().getBlockState(block.getBlockPos()), player, 2);
            return;
        }
        if (!java.util.Set.of("rope","hook","rescue").contains(kind)) return;
        for (ConnectorEntity existing : player.level().getEntitiesOfClass(ConnectorEntity.class, player.getBoundingBox().inflate(52))) if (existing.isOwnedBy(player)) { existing.discard(); return; }
        EntityType<ConnectorEntity> type = kind.equals("hook") ? HOOK.get() : kind.equals("rescue") ? RESCUE.get() : ROPE.get();
        ConnectorEntity entity = type.create(player.level(), EntitySpawnReason.TRIGGERED);
        if (entity == null) return;
        entity.setOwner(player); entity.setPos(player.getEyePosition().add(0,-0.2,0));
        entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, kind.equals("rescue") ? 0.4F : 1.3F, 0);
        player.level().addFreshEntity(entity);
    }
    public static void controlConnector(ServerPlayer player, int entityId, int action) {
        if (player.level().getEntity(entityId) instanceof ConnectorEntity connector) connector.control(player, action);
    }
    public static boolean startHandRescue(ServerPlayer player, LivingEntity victim) {
        if (victim.level()!=player.level() || !victim.isAlive() || victim.hasInfiniteMaterials() || !player.hasLineOfSight(victim)
                || player.distanceToSqr(victim)>6.25 || !player.getMainHandItem().isEmpty()) return false;
        for (ConnectorEntity existing : player.level().getEntitiesOfClass(ConnectorEntity.class, player.getBoundingBox().inflate(4))) if (existing.isOwnedBy(player)) existing.discard();
        ConnectorEntity rescue = RESCUE.get().create(player.level(), EntitySpawnReason.TRIGGERED);
        if (rescue == null) return false;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.byId("rescuing")));
        rescue.setOwner(player); rescue.bindTarget(victim);
        if (!player.level().addFreshEntity(rescue)) { player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); return false; }
        player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        return true;
    }
    public static void fireLiquid(ServerPlayer player, BlockState medium) { throwLiquid(player, LIQUID_BALL.get(), medium, 1.4F); }
    public static void throwSinkingPotion(ServerPlayer player) { throwLiquid(player, SINKING_POTION.get(), ModBlocks.byId("sinky_liquid").defaultBlockState(), 0.6F); }
    private static void throwLiquid(ServerPlayer player, EntityType<LiquidProjectileEntity> type, BlockState medium, float speed) {
        LiquidProjectileEntity entity = type.create(player.level(), EntitySpawnReason.TRIGGERED); if (entity == null) return;
        entity.setOwner(player); entity.setMedium(medium); entity.setPos(player.getEyePosition().add(0,-0.1,0));
        entity.setItem(new ItemStack(ModItems.byId("splash_sinking_potion"))); entity.shootFromRotation(player, player.getXRot(),player.getYRot(),0,speed,1);
        player.level().addFreshEntity(entity);
    }
    public static void spawnTentacle(LivingEntity victim, BlockPos pit, boolean mud) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (!TentacleEntity.acceptsPit(com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics.id(level.getBlockState(pit)), mud)) return;
        for (TentacleEntity existing : level.getEntitiesOfClass(TentacleEntity.class, victim.getBoundingBox().inflate(5))) if (existing.victim() == victim) return;
        TentacleEntity entity = (mud ? MUD_TENTACLES.get() : TENTACLES.get()).create(level, EntitySpawnReason.TRIGGERED);
        if (entity != null) { entity.setPos(pit.getX()+0.5, pit.getY(), pit.getZ()+0.5); entity.bind(victim); level.addFreshEntity(entity); }
    }
    public static void spawnEffect(Level level, String kind, Vec3 position, BlockState medium, Entity target, int ticks) {
        if (!(level instanceof ServerLevel server)) return;
        EntityType<SurfaceEffectEntity> type = switch (kind) { case "tar_treads" -> TAR_TREADS.get(); case "slime_hole" -> SLIME_HOLE.get(); case "long_stick" -> LONG_STICK.get(); default -> BUBBLE.get(); };
        SurfaceEffectEntity entity = type.create(server, EntitySpawnReason.TRIGGERED);
        if (entity != null) { entity.setPos(position); entity.configure(medium,target,ticks); server.addFreshEntity(entity); }
    }
    private ModEntities() {}
}
