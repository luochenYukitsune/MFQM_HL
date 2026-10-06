package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.FLUID, MFQM.MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MFQM.MOD_ID);
    public record Entry(String id, String bucketId, DeferredHolder<FluidType, FluidType> type,
                        DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
                        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing) {}
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    static {
        add("bog", "bog_bucket", 2500, 8500, 288, 30);
        add("dry_quicksand", "sand_bucket", 500, 1000, 318, 8);
        add("jungle_quicksand", "quicksand_bucket", 3000, 9000, 298, 35);
        add("liquid_mire", "mire_bucket", 1000, 7500, 288, 25);
        add("stable_liquid_mire", "mire_bucket", 1000, 7500, 288, 25);
        add("sinky_liquid", "", 750, 2500, 329, 12);
        add("sinking_slime", "slime_bucket", 6000, 15000, 288, 50);
        add("mucus", "mucus_bucket", 6000, 15000, 301, 50);
        add("tar", "tar_bucket", 5000, 10000, 345, 40);
        add("acid", "acid_bucket", 750, 2500, 329, 12);
        add("slurry", "slurry_bucket", 2000, 8500, 308, 30);
        add("honey", "honey_bucket", 3500, 9000, 303, 35);
        add("liquid_chocolate", "chocolate_bucket", 3500, 9000, 313, 35);
    }

    private static void add(String id, String bucket, int density, int viscosity, int temperature, int tickRate) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(id, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid." + MFQM.MOD_ID + "." + id)
                .density(density).viscosity(viscosity).temperature(temperature)
                .motionScale(0).canPushEntity(false).canSwim(false).canDrown(isWaterMire(id))
                .canExtinguish(!id.equals("tar"))) {
            @Override public boolean canDrownIn(net.minecraft.world.entity.LivingEntity entity) {
                // Legacy liquid mire used vanilla water air; other media use the sinking attachment.
                return super.canDrownIn(entity) && (entity == null || !entity.canBreatheUnderwater()
                        && !(entity instanceof com.mfqm.morefunquicksandmod.entity.BlobEntity blob
                        && blob.kind() == com.mfqm.morefunquicksandmod.entity.BlobEntity.Kind.MUD));
            }
        });
        var source = FLUIDS.register(id, () -> new BaseFlowingFluid.Source(properties(id, tickRate)));
        var flowing = FLUIDS.register("flowing_" + id, () -> new BaseFlowingFluid.Flowing(properties(id, tickRate)));
        ENTRIES.put(id, new Entry(id, bucket, type, source, flowing));
    }

    private static BaseFlowingFluid.Properties properties(String id, int ticks) {
        var entry = ENTRIES.get(id);
        return new BaseFlowingFluid.Properties(entry.type, entry.source, entry.flowing)
                .block(() -> (LiquidBlock) ModBlocks.byId(id))
                .bucket(() -> entry.bucketId.isEmpty() ? net.minecraft.world.item.Items.AIR : BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, entry.bucketId)))
                .tickRate(ticks).slopeFindDistance(2).levelDecreasePerBlock(2).explosionResistance(100);
    }

    public static FlowingFluid source(String id) { return ENTRIES.get(id).source.get(); }
    public static boolean isWaterMire(String id) { return id.equals("liquid_mire") || id.equals("stable_liquid_mire"); }
    public static Entry entry(String id) { return ENTRIES.get(id); }
    public static Map<String, Entry> entries() { return Collections.unmodifiableMap(ENTRIES); }
    private ModFluids() {}
}
