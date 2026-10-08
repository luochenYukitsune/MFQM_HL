package com.mfqm.morefunquicksandmod.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Real server checks, invoked only in the explicitly enabled isolated validation world. */
public final class StickyBoardPortChecks {
    public static List<String> verify(ServerLevel level, BlockPos base) {
        var board = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("mfqm", "sticky_board"));
        if (board == Blocks.AIR) throw new IllegalStateException("Sticky board must be registered");
        require(board instanceof StickyBoardBlock, "Sticky board uses its dedicated block");
        var passed = new ArrayList<String>();
        var saved = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (var pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(3, 2, 2))) {
            require(level.getBlockEntity(pos) == null, "Board fixture must contain no block entity");
            saved.put(pos.immutable(), level.getBlockState(pos));
        }
        var player = new FakePlayer(level, new GameProfile(UUID.fromString("29dad746-c084-4696-b1e6-1a7af0c0a711"), "[MFQM Board]"));
        player.setPos(base.getX() + .5, base.getY() + 1, base.getZ() + .5);
        var hit = new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false);
        try {
            for (var pos : saved.keySet()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(base, board.defaultBlockState(), 3);
            var state = level.getBlockState(base);
            require(state.getValue(StickyBoardBlock.CHARGE) == 0 && !StickyBoardBlock.isCoated(state), "Fresh empty board has no adhesion");
            require(state.getCollisionShape(level, base, CollisionContext.empty()).max(Direction.Axis.Y) == 1.0 / 16,
                    "Board collision is exactly 1/16 block tall");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.byId("glue_bucket")));
            require(state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit).consumesAction(), "Glue bucket coats board through actual block use");
            require(level.getBlockState(base).getValue(StickyBoardBlock.CHARGE) == 7 && StickyBoardBlock.isCoated(level.getBlockState(base)), "Coating becomes full and adhesive");
            require(player.getMainHandItem().is(Items.BUCKET) && player.getMainHandItem().getCount() == 1, "Coating transfers exactly one bucket container");
            passed.add("board: exact thin collision, empty/nonempty adhesion, real right-click and single bucket return");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.byId("glue_bucket")));
            level.getBlockState(base).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            require(player.getMainHandItem().is(ModItems.byId("glue_bucket")), "Already full board never wastes another bucket");
            require(StickyBoardBlock.consumeCoating(level, base, 3) == 4, "Server coating consumption subtracts charges");
            require(StickyBoardBlock.consumeCoating(level, base, -7) == 4 && StickyBoardBlock.consumeCoating(level, base, 0) == 4,
                    "Nonpositive consumption preserves existing coating");
            player.getAbilities().mayBuild = false;
            require(!level.getBlockState(base).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit).consumesAction(), "Restricted player cannot recoat board");
            require(level.getBlockState(base).getValue(StickyBoardBlock.CHARGE) == 4 && player.getMainHandItem().is(ModItems.byId("glue_bucket")), "Denied coating leaves board and bucket unchanged");
            player.getAbilities().mayBuild = true;
            player.getAbilities().instabuild = true;
            level.getBlockState(base).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            require(level.getBlockState(base).getValue(StickyBoardBlock.CHARGE) == 7 && player.getMainHandItem().is(ModItems.byId("glue_bucket")), "Creative coating preserves held bucket");
            player.getAbilities().instabuild = false;
            require(StickyBoardBlock.consumeCoating(level, base, 3) == 4, "Consumption works after creative refill");
            var drops = Block.getDrops(level.getBlockState(base), level, base, null, player, ItemStack.EMPTY);
            require(drops.size() == 1 && drops.getFirst().is(ModItems.byId("sticky_board")), "Actual board loot returns one reusable base");
            require(Integer.valueOf(4).equals(drops.getFirst().getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(StickyBoardBlock.CHARGE)), "Actual loot copies remaining coating state");
            level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst());
            var supportHit = new BlockHitResult(Vec3.atCenterOf(base.below()).add(0, .5, 0), Direction.UP, base.below(), false);
            require(player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, supportHit)).consumesAction(), "Recovered board can actually be placed again");
            require(level.getBlockState(base).getValue(StickyBoardBlock.CHARGE) == 4 && player.getMainHandItem().isEmpty(), "Replaced board retains coating without duplicating its base");
            require(StickyBoardBlock.consumeCoating(level, base, Integer.MAX_VALUE) == 0 && !StickyBoardBlock.isCoated(level.getBlockState(base)), "Spent coating stops adhesion while retaining base");
            require(StickyBoardBlock.consumeCoating(level, base, -1) == 0, "Negative consumption cannot create free glue");
            passed.add("board: full/creative refill, charge consumption, real loot and re-placement preserve coating without duplication");

            level.setBlock(base.east().below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(base.east(), StickyBoardBlock.coatedState(7), 3);
            level.setBlock(base.south(), ModBlocks.byId("glue").defaultBlockState(), 3);
            require(level.getBlockState(base).is(board) && level.getBlockState(base.east()).is(board), "Adjacent glue and board do not invalidate supported boards");
            require(!board.defaultBlockState().canSurvive(level, base.east(2)), "Floating board placement is rejected");
            level.setBlock(base.below(), Blocks.AIR.defaultBlockState(), 3);
            require(level.getBlockState(base).isAir(), "Support removal breaks board through neighbor updates");
            require(level.getBlockState(base.east()).is(board), "Removing one support leaves neighboring board intact");
            passed.add("board: adjacent boards/glue retain support, floating placement rejected and removed support breaks only affected board");
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 2, List.of(
                    new ItemStack(Items.PAPER), new ItemStack(Items.PAPER), new ItemStack(Items.PAPER),
                    new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS), new ItemStack(Items.SPRUCE_PLANKS)));
            var recipe = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                    .orElseThrow(() -> new IllegalStateException("Empty board recipe accepts mixed planks"));
            var result = recipe.value().assemble(input, level.registryAccess());
            require(result.is(ModItems.byId("sticky_board")) && result.getCount() == 3, "Three paper and tagged planks produce three empty boards");
            require(result.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(board.defaultBlockState()).getValue(StickyBoardBlock.CHARGE) == 0, "Recipe boards start empty");
            passed.add("board: actual tagged mixed-wood recipe produces three empty bases");
        } finally {
            for (var entity : level.getEntities(player, new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(base.offset(-2, -2, -2)), Vec3.atLowerCornerOf(base.offset(5, 4, 4)))))
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().is(ModItems.byId("sticky_board"))) entity.discard();
            saved.forEach((pos, state) -> level.setBlock(pos, state, 2));
            player.discard();
        }
        return List.copyOf(passed);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private StickyBoardPortChecks() {}
}
