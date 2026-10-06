package com.mfqm.morefunquicksandmod.block;

import com.mfqm.morefunquicksandmod.gameplay.MediumReactions;
import com.mfqm.morefunquicksandmod.gameplay.SinkingMaterial;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The 1.7 metadata remains explicit state, including internal/non-creative mechanism stages. */
public class LegacyStateBlock extends Block {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 15);
    public final String legacyId;
    public LegacyStateBlock(String id, BlockBehaviour.Properties properties) {
        super(properties);
        legacyId = id;
        registerDefaultState(stateDefinition.any().setValue(VARIANT, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(VARIANT); }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int variant = state.getValue(VARIANT);
        return switch (legacyId) {
            case "mud" -> switch (mudDepth(state, level, pos)) {
                case 0 -> Block.box(0, 0, 0, 16, 11.2, 16);
                case 1 -> Block.box(0, 0, 0, 16, 8, 16);
                case 2 -> Block.box(0, 0, 0, 16, 4, 16);
                default -> Shapes.empty();
            };
            case "wax" -> variant < 3 ? Block.box(0, 0, 0, 16, variant == 0 ? 15.84 : variant == 1 ? 14.4 : 12, 16) : Shapes.empty();
            case "morass" -> variant == 0 && MediumReactions.nearSolidEdge(level, pos)
                    ? Block.box(0, 0, 0, 16, 8.16, 16) : Shapes.empty();
            case "soft_gravel" -> variant == 0 ? Block.box(0, 0, 0, 16, 12.8, 16) : Shapes.empty();
            case "tangleroot_moss" -> Shapes.empty();
            case "custom_lily_pad" -> Block.box(1, 0, 1, 15, 1.5, 15);
            case "solid_honey" -> Block.box(.4, .4, .4, 15.6, 15.6, 15.6);
            case "moor_grass", "tendrils", "leaves_pile", "gas", "vore_hole", "meat_hole" -> Shapes.empty();
            case "blossom_slab" -> variant == 0 ? Block.box(0, 8, 0, 16, 16, 16) : Block.box(0, 0, 0, 16, 8, 16);
            case "blossom", "meat_wall" -> Block.box(0, 0, 0, 16, 14, 16);
            default -> SinkingMaterial.byId(legacyId) != null ? Shapes.empty() : Shapes.block();
        };
    }
    private static int mudDepth(BlockState state, BlockGetter level, BlockPos pos) {
        int depth = Math.min(state.getValue(VARIANT), 3);
        var above = level.getBlockState(pos.above());
        if (!above.getFluidState().isEmpty() || above.is(state.getBlock()) && !level.getFluidState(pos.above(2)).isEmpty()) depth = 3;
        else if (depth < 3) {
            int surround = 0;
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if ((x != 0 || z != 0) && level.getBlockState(pos.offset(x, 0, z)).is(state.getBlock())) surround++;
            if (surround == 8) {
                int radius = 0;
                for (var direction : Direction.Plane.HORIZONTAL) if (level.getBlockState(pos.relative(direction, 2)).is(state.getBlock())) radius++;
                int natural = radius == 4 ? 2 : 1;
                if (radius == 4) {
                    radius = 0;
                    for (var direction : Direction.Plane.HORIZONTAL) if (level.getBlockState(pos.relative(direction, 3)).is(state.getBlock())) radius++;
                    if (radius == 4) natural = 3;
                }
                depth = Math.max(depth, natural);
            }
        }
        if (depth > 0 && above.getFluidState().isEmpty()) {
            for (var direction : Direction.Plane.HORIZONTAL) {
                var neighbor = level.getBlockState(pos.relative(direction));
                if (!neighbor.is(state.getBlock()) && !neighbor.blocksMotion()) return 0;
            }
        }
        if (above.is(state.getBlock()) && above.getValue(VARIANT) < 3) return 0;
        return depth;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (legacyId) {
            case "leaves_pile" -> Block.box(0, 0, 0, 16, .25, 16);
            case "custom_lily_pad" -> Block.box(1, 0, 1, 15, 1.5, 15);
            case "moor_grass" -> Block.box(2, 0, 2, 14, state.getValue(VARIANT) < 3 ? 14 : 6, 14);
            case "tendrils" -> Block.box(2, 0, 2, 14, 14, 14);
            case "blossom_slab", "meat_wall", "blossom" -> getCollisionShape(state, level, pos, context);
            case "tangleroot_moss" -> Block.box(0, mossBottom(state) * 16, 0, 16, mossTop(state) * 16, 16);
            default -> Shapes.block();
        };
    }
    public static double mossBottom(BlockState state) { return switch (state.getValue(VARIANT)) { case 0, 2, 3, 5, 6 -> -.8; default -> 0; }; }
    public static double mossTop(BlockState state) { return switch (state.getValue(VARIANT)) { case 0, 2, 6 -> 0; case 1, 3, 5 -> .2; default -> 1; }; }
    @Override protected RenderShape getRenderShape(BlockState state) {
        return legacyId.equals("vore_hole") || legacyId.equals("meat_hole") ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return SinkingMaterial.byId(legacyId) == null && super.isPathfindable(state, type);
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var below = level.getBlockState(pos.below());
        return switch (legacyId) {
            case "moor_grass" -> !below.isAir() && below.getFluidState().isEmpty();
            case "tendrils" -> below.is(ModBlocks.byId("meat_wall")) || below.is(ModBlocks.byId("swallowing_flesh"));
            case "custom_lily_pad" -> !below.getFluidState().isEmpty() && below.getFluidState().isSource()
                    && (below.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || below.is(ModBlocks.byId("bog")) || below.is(ModBlocks.byId("stable_liquid_mire")));
            case "leaves_pile", "lure" -> !below.isAir();
            default -> true;
        };
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) level.scheduleTick(pos, this, legacyId.equals("gas") ? 10 : 20);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moved) {
        if (level instanceof ServerLevel server) {
            if (!state.canSurvive(level, pos)) { level.destroyBlock(pos, true); return; }
            if (legacyId.equals("sandstone_trap") && level.hasNeighborSignal(pos)) MediumReactions.triggerTrap(server, pos);
            if (legacyId.equals("vore_hole") || legacyId.equals("meat_hole")) MediumReactions.validateHole(legacyId, server, pos);
            server.scheduleTick(pos, this, 10);
        }
    }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MediumReactions.tick(legacyId, state, level, pos, random);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MediumReactions.tick(legacyId, state, level, pos, random);
        if (level.getBlockState(pos).is(this) && (legacyId.equals("gas") || legacyId.equals("vore_hole") || legacyId.equals("meat_hole"))) {
            level.scheduleTick(pos, this, legacyId.equals("gas") ? 10 : 20);
        }
        if (level.getBlockState(pos).is(this) && (legacyId.equals("meat_wall") && state.getValue(VARIANT) == 10 || legacyId.equals("blossom") && state.getValue(VARIANT) == 11)) level.scheduleTick(pos, this, 100);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int variant = state.getValue(VARIANT);
        if (legacyId.equals("moor_grass") && variant == 5) {
            if (!level.isClientSide()) {
                popResource(level, pos, new ItemStack(ModItems.byId("cranberry"), 2 + level.random.nextInt(4)));
                level.setBlock(pos, state.setValue(VARIANT, 4), 3);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.SHEARS) && (legacyId.equals("leaves_pile") || legacyId.equals("tendrils") || legacyId.equals("moor_grass"))) {
            if (!level.isClientSide()) {
                Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, stack);
                level.removeBlock(pos, false);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity entity, ItemStack tool) {
        if (legacyId.equals("sinking_clay") && state.getValue(VARIANT) < 7) {
            if (!level.isClientSide()) level.setBlock(pos, state.setValue(VARIANT, 7), 3);
            return;
        }
        super.playerDestroy(level, player, pos, state, entity, tool);
        if (!(level instanceof ServerLevel server)) return;
        boolean silk = EnchantmentHelper.getItemEnchantmentLevel(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), tool) > 0;
        if (!silk && legacyId.equals("mire")) level.setBlock(pos, ModBlocks.byId("liquid_mire").defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 4), 3);
        if (!silk && legacyId.equals("wet_peat")) level.setBlock(pos, ModBlocks.byId("stable_liquid_mire").defaultBlockState(), 3);
        if (legacyId.equals("honeycomb")) {
            if (state.getValue(VARIANT) == 0 && server.random.nextBoolean()) {
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(com.mfqm.morefunquicksandmod.MFQM.MOD_ID, "bee"));
                var bee = type.create(server, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                if (bee != null) { bee.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); server.addFreshEntity(bee); }
            }
            if (!silk && state.getValue(VARIANT) == 2) level.setBlock(pos, ModBlocks.byId("honey").defaultBlockState(), 3);
        }
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (legacyId.equals("wax_wood") && placer != null) {
            int facing = net.minecraft.util.Mth.floor(placer.getYRot() * 4 / 360f + 2.5) & 3;
            level.setBlock(pos, state.setValue(VARIANT, facing + (state.getValue(VARIANT) >= 4 ? 4 : 0)), 3);
        }
    }
}
