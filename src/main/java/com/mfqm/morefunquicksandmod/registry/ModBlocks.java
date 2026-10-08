package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.mfqm.morefunquicksandmod.block.LegacyStateBlock;
import com.mfqm.morefunquicksandmod.block.MechanismBlock;
import com.mfqm.morefunquicksandmod.fluid.SinkingLiquidBlock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MFQM.MOD_ID);
    private static final Map<String, DeferredBlock<? extends Block>> ENTRIES = new LinkedHashMap<>();
    static {
        for (String id : new String[]{"mud", "bog", "soft_snow", "dry_quicksand", "soft_quicksand", "morass",
                "wet_peat", "peat", "brown_clay", "wax", "quicksand", "sandstone_trap", "jungle_quicksand",
                "liquid_mire", "stable_liquid_mire", "sinky_liquid", "sinking_slime", "mucus", "mire", "moor",
                "hardened_clay", "sinking_clay", "tangleroot_moss", "dense_web", "tar", "larvae", "corrupted_sand",
                "swallowing_flesh", "acid", "slurry", "gas", "soft_gravel", "honey", "solid_honey", "honeycomb",
                "liquid_chocolate", "chocolate", "sinking_rug", "lure", "blossom", "blossom_slab", "vore_hole",
                "meat_wall", "meat_hole", "wax_wood", "custom_lily_pad", "moor_grass", "tendrils", "leaves_pile", "glue"}) {
            if (ModFluids.entries().containsKey(id)) {
                ENTRIES.put(id, BLOCKS.registerBlock(id, p -> new SinkingLiquidBlock(id, ModFluids.source(id), p),
                        () -> BlockBehaviour.Properties.of().replaceable().noCollision().noOcclusion()
                                .strength(100).randomTicks().sound(SoundType.EMPTY)));
            } else if (id.equals("lure") || id.equals("blossom") || id.equals("meat_wall") || id.equals("larvae")) {
                ENTRIES.put(id, BLOCKS.registerBlock(id, p -> new MechanismBlock(id, p), () -> properties(id)));
            } else {
                ENTRIES.put(id, BLOCKS.registerBlock(id, p -> new LegacyStateBlock(id, p), () -> properties(id)));
            }
        }
        ENTRIES.put("sticky_board", BLOCKS.registerBlock("sticky_board",
                com.mfqm.morefunquicksandmod.block.StickyBoardBlock::new,
                () -> BlockBehaviour.Properties.of().strength(.4f).noOcclusion().sound(SoundType.WOOD)));
    }

    private static BlockBehaviour.Properties properties(String id) {
        var properties = BlockBehaviour.Properties.of().strength(.5f).randomTicks().noOcclusion()
                .sound(id.contains("sand") || id.equals("soft_gravel") ? SoundType.SAND : SoundType.MUD);
        if (id.equals("hardened_clay") || id.equals("sandstone_trap")) properties.strength(1.5f).sound(SoundType.STONE);
        if (id.equals("wax_wood")) properties.strength(1.3f).sound(SoundType.WOOD);
        if (id.equals("mud") || id.equals("morass")) properties.dynamicShape();
        if (id.equals("gas") || id.equals("vore_hole") || id.equals("meat_hole")) properties.replaceable().noCollision().noLootTable();
        if (id.equals("moor_grass") || id.equals("tendrils") || id.equals("leaves_pile") || id.equals("custom_lily_pad")) {
            properties.replaceable().sound(SoundType.GRASS);
        }
        return properties;
    }

    public static Block byId(String id) {
        var holder = ENTRIES.get(id);
        if (holder == null) throw new IllegalArgumentException("Unknown MFQM block: " + id);
        return holder.get();
    }
    public static Map<String, DeferredBlock<? extends Block>> entries() { return Collections.unmodifiableMap(ENTRIES); }
    private ModBlocks() {}
}
