package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.blockentity.MechanismBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MFQM.MOD_ID);
    private static final Map<String, DeferredHolder<BlockEntityType<?>, BlockEntityType<MechanismBlockEntity>>> TYPES = new LinkedHashMap<>();
    static {
        for (String id : new String[]{"lure", "blossom", "larvae", "meat_wall"}) {
            TYPES.put(id, BLOCK_ENTITIES.register(id, () -> new BlockEntityType<>((pos, state) -> new MechanismBlockEntity(id, pos, state), ModBlocks.byId(id))));
        }
    }
    public static BlockEntityType<MechanismBlockEntity> byId(String id) { return TYPES.get(id).get(); }
    private ModBlockEntities() {}
}
