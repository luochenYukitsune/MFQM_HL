package com.mfqm.morefunquicksandmod.block;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Reusable supported floor board; only the coating is consumed. */
public final class StickyBoardBlock extends Block {
    public static final MapCodec<StickyBoardBlock> CODEC = simpleCodec(StickyBoardBlock::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 7);
    public static final BooleanProperty NORTH=BlockStateProperties.NORTH,EAST=BlockStateProperties.EAST,
            SOUTH=BlockStateProperties.SOUTH,WEST=BlockStateProperties.WEST;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public StickyBoardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0).setValue(NORTH,false).setValue(EAST,false)
                .setValue(SOUTH,false).setValue(WEST,false));
    }
    @Override public MapCodec<StickyBoardBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(CHARGE,NORTH,EAST,SOUTH,WEST); }
    private static BooleanProperty connection(Direction direction) {
        return switch(direction) {
            case NORTH -> NORTH;case EAST -> EAST;case SOUTH -> SOUTH;case WEST -> WEST;
            default -> throw new IllegalArgumentException("Board connections must be horizontal");
        };
    }
    private BlockState connected(BlockState state,BlockGetter level,BlockPos pos) {
        for(var direction:Direction.Plane.HORIZONTAL) {
            var neighbor=pos.relative(direction);
            if(level instanceof LevelReader reader && !reader.hasChunkAt(neighbor))continue;
            state=state.setValue(connection(direction),level.getBlockState(neighbor).is(this));
        }
        return state;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(defaultBlockState(),context.getLevel(),context.getClickedPos());
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,
            Direction direction,BlockPos neighborPos,BlockState neighbor,RandomSource random) {
        return direction.getAxis().isHorizontal()?state.setValue(connection(direction),neighbor.is(this))
                :super.updateShape(state,level,ticks,pos,direction,neighborPos,neighbor,random);
    }
    public void refreshConnections(Level level,BlockPos pos) {
        if(!(level instanceof ServerLevel))return;
        var current=level.getBlockState(pos);if(!current.is(this))return;
        var updated=connected(current,level,pos);if(updated!=current)level.setBlock(pos,updated,Block.UPDATE_ALL);
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved) {
        refreshConnections(level,pos);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {
        var result=state;
        for(var direction:Direction.Plane.HORIZONTAL)
            result=result.setValue(connection(rotation.rotate(direction)),state.getValue(connection(direction)));
        return result;
    }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        return switch(mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH,state.getValue(SOUTH)).setValue(SOUTH,state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST,state.getValue(WEST)).setValue(WEST,state.getValue(EAST));
            default -> state;
        };
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
        if (level instanceof ServerLevel) {
            var current=level.getBlockState(pos);if(!current.is(this))return;
            if(!current.canSurvive(level,pos))level.destroyBlock(pos,true);
            else refreshConnections(level,pos);
        }
    }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return coatedStack(state.getValue(CHARGE));
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.byId("glue_bucket"))) return InteractionResult.TRY_WITH_EMPTY_HAND;
        // Consume the interaction on a full board so the bucket cannot instead spill beside it.
        if (state.getValue(CHARGE) == 7) return InteractionResult.SUCCESS;
        if (!player.mayBuild() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, hit.getDirection(), stack)) return InteractionResult.FAIL;
        if (level instanceof ServerLevel server && server.setBlock(pos, state.setValue(CHARGE, 7), 3)) {
            if (!player.getAbilities().instabuild)
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET), false));
        }
        return InteractionResult.SUCCESS;
    }
    public static boolean isCoated(BlockState state) {
        return state.getBlock() instanceof StickyBoardBlock && state.getValue(CHARGE) > 0;
    }
    public static BlockState coatedState(int charge) {
        return ModBlocks.byId("sticky_board").defaultBlockState().setValue(CHARGE, Math.clamp(charge, 0, 7));
    }
    public static ItemStack coatedStack(int charge) {
        ItemStack stack = new ItemStack(ModItems.byId("sticky_board"));
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CHARGE, Math.clamp(charge, 0, 7)));
        return stack;
    }
    /** Returns remaining charge; nonpositive requests cannot refill, and unloaded positions are untouched. */
    public static int consumeCoating(ServerLevel level, BlockPos pos, int amount) {
        if (!level.hasChunkAt(pos)) return 0;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StickyBoardBlock)) return 0;
        int current = state.getValue(CHARGE);
        int remaining = Math.max(0, current - Math.max(0, amount));
        if (remaining != current && !level.setBlock(pos, state.setValue(CHARGE, remaining), 3)) return current;
        return remaining;
    }
}
