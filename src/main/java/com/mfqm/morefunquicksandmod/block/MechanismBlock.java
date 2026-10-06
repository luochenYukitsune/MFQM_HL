package com.mfqm.morefunquicksandmod.block;

import com.mfqm.morefunquicksandmod.blockentity.MechanismBlockEntity;
import com.mfqm.morefunquicksandmod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The four legacy tile-entity behavior classes now have valid registered block entities. */
public final class MechanismBlock extends LegacyStateBlock implements EntityBlock {
    public MechanismBlock(String id, BlockBehaviour.Properties properties) { super(id, properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanismBlockEntity(legacyId, pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.byId(legacyId)) return null;
        return (world, pos, block, entity) -> MechanismBlockEntity.tick(world, pos, block, (MechanismBlockEntity) entity);
    }
}
