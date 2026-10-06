package com.mfqm.morefunquicksandmod.tags;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public final class MfqmTags {
    // Block tags
    public static final TagKey<Block> SINKING_BLOCKS = BlockTags.create(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "sinking_blocks"));
    public static final TagKey<Block> STICKY_BLOCKS = BlockTags.create(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "sticky_blocks"));
    public static final TagKey<Block> GAS_BLOCKS = BlockTags.create(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "gas_blocks"));
    public static final TagKey<Block> FERTILIZABLE = BlockTags.create(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "fertilizable"));

    // Fluid tags
    public static final TagKey<Fluid> QUICKSAND_FLUIDS = FluidTags.create(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "quicksand_fluids"));

    // Biome tags
    public static final TagKey<Biome> SPAWNS_MUD = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "spawns_mud"));
    public static final TagKey<Biome> SPAWNS_TAR = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "spawns_tar"));
    public static final TagKey<Biome> SPAWNS_QS_DESERT = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "spawns_quicksand_desert"));
    public static final TagKey<Biome> SPAWNS_QS_JUNGLE = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "spawns_quicksand_jungle"));
    public static final TagKey<Biome> MUDDY_BLOB_SPAWNABLE = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "muddy_blob_spawnable"));
    public static final TagKey<Biome> SAND_BLOB_SPAWNABLE = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "sand_blob_spawnable"));
    public static final TagKey<Biome> TAR_SLIME_SPAWNABLE = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "tar_slime_spawnable"));

    private MfqmTags() {}
}
