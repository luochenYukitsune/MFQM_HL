package com.mfqm.morefunquicksandmod.blockentity;

import com.mfqm.morefunquicksandmod.block.LegacyStateBlock;
import com.mfqm.morefunquicksandmod.gameplay.MediumReactions;
import com.mfqm.morefunquicksandmod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public final class MechanismBlockEntity extends BlockEntity {
    public final String kind;
    private int phase;
    public MechanismBlockEntity(String kind, BlockPos pos, BlockState state) {
        super(ModBlockEntities.byId(kind), pos, state);
        this.kind = kind;
    }
    public int phase() { return phase; }
    public static void tick(Level world, BlockPos pos, BlockState state, MechanismBlockEntity entity) {
        if (!(world instanceof ServerLevel level)) return;
        if (entity.kind.equals("lure")) {
            if (level.getGameTime() % 48 != 0 || level.random.nextInt(5) != 0) return;
            for (var mob : level.getEntitiesOfClass(PathfinderMob.class, new AABB(pos).inflate(64, 16, 64), LivingEntity::isAlive)) {
                double distance = mob.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
                if (distance > 16 && level.random.nextInt((int) (Math.sqrt(distance) / 5) + 20) == 0) mob.getNavigation().moveTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 1);
            }
        } else if (level.getGameTime() % 10 == 0) {
            int nearby = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(1.5), LivingEntity::isAlive).size();
            entity.phase = (entity.phase + (nearby > 0 ? 3 : 1)) % 40;
            entity.setChanged();
        }
    }
    @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); output.putInt("phase", phase); }
    @Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); phase = Math.clamp(input.getIntOr("phase", 0), 0, 39); }
}
