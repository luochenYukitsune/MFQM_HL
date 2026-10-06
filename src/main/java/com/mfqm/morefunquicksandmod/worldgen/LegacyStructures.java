package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class LegacyStructures {
    public static BlockState state(String id, int variant) {
        BlockState state = ModBlocks.byId(id).defaultBlockState();
        if (state.getBlock().getStateDefinition().getProperty("variant") instanceof IntegerProperty property) {
            state = state.setValue(property, Math.clamp(variant, 0, 15));
        }
        return state;
    }
    /** Shared by natural generation and the cactus/potion interaction. */
    public static boolean growBlossom(LevelAccessor level, BlockPos base) {
        if (base.getY() < level.getMinY() || base.getY() + 9 > level.getMaxY()) return false;
        for (int[] entry : MucusBlossomLayout.BLOCKS) {
            BlockPos pos = base.offset(entry[0], entry[1], entry[2]);
            BlockState existing = level.getBlockState(pos);
            boolean ownPlant = existing.is(ModBlocks.byId("blossom")) || existing.is(ModBlocks.byId("blossom_slab")) || existing.is(ModBlocks.byId("lure"));
            if (!level.hasChunkAt(pos) || (existing.hasBlockEntity() && !ownPlant) || existing.is(Blocks.BEDROCK)) return false;
            if (entry[1] > 2 && !existing.canBeReplaced() && !existing.is(Blocks.CACTUS) && !ownPlant) return false;
        }
        String[] names = {"blossom", "lure", "blossom_slab", "mucus"};
        for (int[] entry : MucusBlossomLayout.BLOCKS) {
            level.setBlock(base.offset(entry[0], entry[1], entry[2]), entry[3] == 4 ? Blocks.AIR.defaultBlockState() : state(names[entry[3]], entry[4]), 2);
        }
        return true;
    }
    private LegacyStructures() {}
}
