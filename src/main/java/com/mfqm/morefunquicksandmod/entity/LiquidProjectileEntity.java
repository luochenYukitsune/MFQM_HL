package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Sinking potion and gun liquid ammunition share authoritative impact placement. */
public final class LiquidProjectileEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<BlockState> MEDIUM = SynchedEntityData.defineId(LiquidProjectileEntity.class, EntityDataSerializers.BLOCK_STATE);
    public LiquidProjectileEntity(EntityType<? extends LiquidProjectileEntity> type, Level level) { super(type, level); }
    public void setMedium(BlockState state) { entityData.set(MEDIUM, state); }
    public BlockState medium() { return entityData.get(MEDIUM); }
    @Override protected Item getDefaultItem() { return Items.SPLASH_POTION; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(MEDIUM, Blocks.AIR.defaultBlockState()); }
    @Override public void tick() {
        super.tick(); if (tickCount > 200 && !level().isClientSide()) discard();
        if (level().isClientSide() && !medium().isAir()) level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, medium()), getX(), getY(), getZ(), 0,0,0);
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit); if (!(level() instanceof ServerLevel server)) return;
        BlockPos center = hit instanceof BlockHitResult block ? block.getBlockPos().relative(block.getDirection()) : BlockPos.containing(hit.getLocation());
        BlockState fill = medium().isAir() ? ModBlocks.byId("sinky_liquid").defaultBlockState() : medium();
        // Gun ammunition has only one quantum, so it cannot be collected back as a bucket source.
        if (fill.hasProperty(net.minecraft.world.level.block.LiquidBlock.LEVEL))
            fill = fill.setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 7);
        if (getOwner() instanceof ServerPlayer player && !fill.isAir() && server.mayInteract(player, center)) {
            BlockState centerFill = getType() == com.mfqm.morefunquicksandmod.registry.ModEntities.SINKING_POTION.get()
                    && fill.hasProperty(net.minecraft.world.level.block.LiquidBlock.LEVEL)
                    ? fill.setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 4) : fill;
            place(server, player, center, centerFill);
            for (Direction side : Direction.Plane.HORIZONTAL) place(server, player, center.relative(side), fill);
        }
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, fill), getX(), getY(), getZ(), 24,0.25,0.25,0.25,0.08);
        playSound(SoundEvents.SPLASH_POTION_BREAK, 0.8F, 1); discard();
    }
    private void place(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (level.hasChunkAt(pos) && level.mayInteract(player, pos) && player.mayUseItemAt(pos, Direction.UP, getItem())
                && level.getBlockState(pos).canBeReplaced()) {
            if (level.setBlock(pos, state, 3) && state.is(ModBlocks.byId("sinky_liquid")))
                com.mfqm.morefunquicksandmod.gameplay.MediumReactions.fuse(level, pos);
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.store("Medium", BlockState.CODEC, medium()); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); setMedium(input.read("Medium", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState())); }
}
