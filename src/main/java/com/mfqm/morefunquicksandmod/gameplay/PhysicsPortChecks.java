package com.mfqm.morefunquicksandmod.gameplay;

import com.mfqm.morefunquicksandmod.block.LegacyStateBlock;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModFluids;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit developer smoke checks; never run automatically in ordinary worlds. */
public final class PhysicsPortChecks {
    private PhysicsPortChecks() {}
    public static List<String> verify(ServerLevel level, BlockPos origin) {
        var passed = new ArrayList<String>();
        var legacyBlocks = java.util.Set.of("mud", "bog", "soft_snow", "dry_quicksand", "soft_quicksand", "morass",
                "wet_peat", "peat", "brown_clay", "wax", "quicksand", "sandstone_trap", "jungle_quicksand",
                "liquid_mire", "stable_liquid_mire", "sinky_liquid", "sinking_slime", "mucus", "mire", "moor",
                "hardened_clay", "sinking_clay", "tangleroot_moss", "dense_web", "tar", "larvae", "corrupted_sand",
                "swallowing_flesh", "acid", "slurry", "gas", "soft_gravel", "honey", "solid_honey", "honeycomb",
                "liquid_chocolate", "chocolate", "sinking_rug", "lure", "blossom", "blossom_slab", "vore_hole",
                "meat_wall", "meat_hole", "wax_wood", "custom_lily_pad", "moor_grass", "tendrils", "leaves_pile");
        var legacyFluids = java.util.Set.of("bog", "dry_quicksand", "jungle_quicksand", "liquid_mire", "stable_liquid_mire",
                "sinky_liquid", "sinking_slime", "mucus", "tar", "acid", "slurry", "honey", "liquid_chocolate");
        require(legacyBlocks.size() == 49 && ModBlocks.entries().keySet().containsAll(legacyBlocks), "49 legacy blocks preserved");
        require(legacyFluids.size() == 13 && ModFluids.entries().keySet().containsAll(legacyFluids), "13 legacy fluids preserved");
        require(ModBlocks.entries().size() == 51 && ModBlocks.entries().containsKey("glue") && ModBlocks.entries().containsKey("sticky_board"), "49 legacy blocks plus glue and sticky board");
        require(ModFluids.entries().size() == 14 && ModFluids.entries().containsKey("glue"), "13 legacy fluids plus glue");
        for (var block : ModBlocks.entries().entrySet()) require(block.getValue().get() != Blocks.AIR, "registered block " + block.getKey());
        passed.add("49 original block identities are registered");
        for (var entry : ModFluids.entries().values()) {
            require(entry.source().get().defaultFluidState().isSource(), entry.id() + " source");
            require(!entry.flowing().get().defaultFluidState().isSource(), entry.id() + " flowing");
            require(entry.source().get().isSame(entry.flowing().get()), entry.id() + " source/flowing pairing");
            require(entry.source().get().getFluidType() == entry.type().get(), entry.id() + " type pairing");
            require(ModBlocks.byId(entry.id()) instanceof LiquidBlock, entry.id() + " actual liquid block");
            require(entry.type().get().canDrownIn(null) == ModFluids.isWaterMire(entry.id()), entry.id() + " selects exactly one drowning route");
        }
        require(ModFluids.source("sinky_liquid").getBucket() == Items.AIR, "sinky fluid has no invented bucket");
        passed.add("13 legacy and one glue source/flowing/type/block pairs, with legacy bucket policy");
        var sand = SinkingMaterial.QUICKSAND.motion;
        var quiet = SinkingMotion.calculate(sand, .6, 0, false, false, 0, false, false);
        var struggle = SinkingMotion.calculate(sand, .6, .2, true, false, 0, false, false);
        var heavy = SinkingMotion.calculate(sand, .6, 0, false, false, 2, false, false);
        require(struggle.downwardSpeed() > quiet.downwardSpeed(), "struggle creates suction");
        require(heavy.downwardSpeed() > quiet.downwardSpeed(), "load creates additional sinking");
        require(SinkingMotion.calculate(sand, .2, 0, false, false, 0, true, false).downwardSpeed() <= 0, "shallow boot support");
        require(SinkingMotion.calculate(sand, 1.6, 0, false, false, 0, true, false).downwardSpeed() > 0, "deep boot support ends");
        passed.add("movement/jump/load/depth/boots parameter matrix");
        require(MediumReactions.fusionTarget(Blocks.SAND.defaultBlockState()).is(ModBlocks.byId("quicksand")), "sand fusion");
        require(MediumReactions.fusionTarget(Blocks.FARMLAND.defaultBlockState()).is(ModBlocks.byId("stable_liquid_mire")), "farmland fusion");
        require(MediumReactions.fusionTarget(Blocks.MOSSY_COBBLESTONE.defaultBlockState()).is(ModBlocks.byId("sinking_slime")), "mossy cobble fusion");
        require(MediumReactions.fusionTarget(Blocks.HAY_BLOCK.defaultBlockState()).is(ModBlocks.byId("wet_peat")), "hay fusion");
        require(MediumReactions.fusionTarget(Blocks.SANDSTONE.defaultBlockState()).is(ModBlocks.byId("dry_quicksand")), "sandstone fusion");
        require(MediumReactions.fusionTarget(Blocks.OBSIDIAN.defaultBlockState()).is(ModBlocks.byId("tar")), "obsidian fusion");
        require(MediumReactions.fusionTarget(Blocks.CLAY.defaultBlockState()).is(ModBlocks.byId("sinking_clay")), "clay fusion");
        require(MediumReactions.fusionTarget(Blocks.JUNGLE_LOG.defaultBlockState()).is(ModBlocks.byId("wax")), "wood fusion");
        require(MediumReactions.fusionTarget(Blocks.BEDROCK.defaultBlockState()) == null, "bedrock protected from fusion");
        passed.add("MCP-mapped vanilla fusion cases and bedrock protection");

        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        var pig = EntityType.PIG.create(level, EntitySpawnReason.TRIGGERED);
        require(pig != null, "create probe living entity");
        try {
            for (int up = 0; up < 4; up++) {
                var at = origin.above(up);
                require(level.hasChunkAt(at), "probe area loaded");
                saved.put(at, level.getBlockState(at));
                level.setBlock(at, ModBlocks.byId("quicksand").defaultBlockState(), 2);
            }
            pig.setPos(origin.getX() + .5, origin.getY() + .2, origin.getZ() + .5);
            var state = QuicksandPhysics.state(pig);
            require(QuicksandPhysics.findContact(pig, level) != null, "physical body contact");
            pig.setDeltaMovement(.2, 0, .2);
            QuicksandPhysics.tick(pig, level);
            require(state.material.equals("quicksand") && state.eyesCovered, "body and eyes inside actual medium");
            require(state.air == 299, "one oxygen decrement");
            int spent = state.air;
            QuicksandPhysics.tick(pig, level);
            require(state.air == spent, "same-tick overlapping contacts cannot double-spend air");
            require(pig.getDeltaMovement().y < 0, "living entity receives actual sinking velocity");
            require(pig.getDeltaMovement().horizontalDistanceSqr() == 0, "legacy sand stops momentum");
            var speed = pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            require(speed != null && Math.abs(speed.getValue()-speed.getBaseValue())<1e-7,
                    "sinking constrains actual input without modifying the FOV-driving speed attribute");
            state.lastTick = Long.MIN_VALUE;
            QuicksandPhysics.rescue(pig, pig.position().add(2, 2, 0), .1);
            QuicksandPhysics.tick(pig, level);
            require(pig.getDeltaMovement().x > 0 && pig.getDeltaMovement().y > 0, "rescue preserves horizontal and vertical pull in sticky media");
            passed.add("actual stacked body/eye contact, sinking motion, and one air spend per tick");
            for (String mire : List.of("liquid_mire", "stable_liquid_mire")) {
                for (int up = 0; up < 4; up++) level.setBlock(origin.above(up), ModBlocks.byId(mire).defaultBlockState(), 2);
                pig.setAirSupply(300);
                pig.baseTick();
                require(pig.getAirSupply() == 299, mire + " vanilla breath decreases for normal living entities");
                state.lastTick = Long.MIN_VALUE;
                QuicksandPhysics.tick(pig, level);
                require(pig.getAirSupply() == 299 && state.air == 300, mire + " has no duplicate custom air spend");
                float health = pig.getHealth();
                pig.setAirSupply(-19);
                pig.invulnerableTime = 0;
                pig.baseTick();
                require(pig.getHealth() < health && pig.getAirSupply() == 0, mire + " exhausted air causes drowning damage");
            }
            pig.setHealth(pig.getMaxHealth());
            pig.setAirSupply(300);
            for (int up = 0; up < 4; up++) level.setBlock(origin.above(up), ModBlocks.byId("quicksand").defaultBlockState(), 2);
            state.air = spent;
            passed.add("both liquid mires consume vanilla air once and drown ordinary living entities");
            pig.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.byId("wading_boots")));
            require(QuicksandPhysics.hasWadingBoots(pig), "boots equipment lookup");
            pig.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.byId("life_jacket")));
            require(QuicksandPhysics.hasLifeJacket(pig), "life jacket equipment lookup");
            pig.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.byId("gas_mask")));
            require(QuicksandPhysics.breathProtected(pig), "gas mask equipment lookup");
            passed.add("wading boots/life jacket/gas mask equipment are consumed by gameplay");
            QuicksandPhysics.coat(pig, "mud", 8, 600);
            require(state.coatingLevel == 8 && state.coatingTicks >= 600 && state.coatingType.equals("mud"), "coating update");
            QuicksandPhysics.applyRescue(pig, .08);
            require(state.rescueTicks > 0 && pig.getDeltaMovement().y > 0, "rescue lifts trapped entity");
            pig.setPos(origin.getX() + 3, origin.getY() + 4, origin.getZ() + .5);
            state.lastTick = Long.MIN_VALUE;
            QuicksandPhysics.tick(pig, level);
            require(state.air == Math.min(300, spent + 5) && state.material.isEmpty(), "surfacing recovers air and clears contact");
            passed.add("persistent coating, rescue lift, and clear-air recovery");
            for (int up = 0; up < 4; up++) level.setBlock(origin.above(up), Blocks.AIR.defaultBlockState(), 2);
            BlockPos anchor = origin.above();
            double eyeHeight = pig.getEyeY() - pig.getY();
            for (int variant = 0; variant <= 6; variant++) {
                var moss = MediumReactions.block("tangleroot_moss", variant);
                level.setBlock(anchor, moss, 2);
                double bottom = anchor.getY() + LegacyStateBlock.mossBottom(moss);
                double top = anchor.getY() + LegacyStateBlock.mossTop(moss);
                pig.setPos(origin.getX() + .5, (bottom + top) / 2 - eyeHeight, origin.getZ() + .5);
                var contact = QuicksandPhysics.findContact(pig, level);
                require(contact != null && contact.material() == SinkingMaterial.TANGLEROOT_MOSS && contact.eyesCovered(), "moss actual occupied bounds " + variant);
                require(Math.abs(contact.surface() - top) < .00001, "moss actual state surface " + variant);
                pig.setPos(origin.getX() + .5, top + .01, origin.getZ() + .5);
                require(QuicksandPhysics.findContact(pig, level) == null, "moss clear above actual surface " + variant);
            }
            level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 2);
            passed.add("seven moss states use hanging bounds, eye immersion, and their actual surface");
            require(ModBlocks.byId("peat").defaultBlockState().getCollisionShape(level, origin).isEmpty() == false, "ordinary peat is solid");
            require(ModBlocks.byId("wax").defaultBlockState().setValue(LegacyStateBlock.VARIANT, 4).getCollisionShape(level, origin).isEmpty(), "softened wax loses shell");
            require(ModBlocks.byId("blossom_slab").defaultBlockState().getCollisionShape(level, origin).min(net.minecraft.core.Direction.Axis.Y) == .5, "blossom slab original upper-half state");
            passed.add("ordinary peat, softened wax, and upper blossom slab collision semantics");
            var east = origin.east();
            var south = origin.south();
            saved.put(east, level.getBlockState(east));
            saved.put(south, level.getBlockState(south));
            level.setBlock(east, Blocks.WATER.defaultBlockState(), 2);
            level.setBlock(origin, MediumReactions.block("mire", 1), 2);
            MediumReactions.tick("mire", level.getBlockState(origin), level, origin, level.random);
            require(MediumReactions.variant(level.getBlockState(origin)) >= 4, "wet mire softens");
            level.setBlock(east, Blocks.LAVA.defaultBlockState(), 2);
            level.setBlock(origin, MediumReactions.block("sinking_clay", 6), 2);
            MediumReactions.tick("sinking_clay", level.getBlockState(origin), level, origin, level.random);
            require(level.getBlockState(origin).is(ModBlocks.byId("hardened_clay")), "hot sinking clay hardens");
            level.setBlock(origin, MediumReactions.block("wax", 2), 2);
            MediumReactions.tick("wax", level.getBlockState(origin), level, origin, level.random);
            require(MediumReactions.variant(level.getBlockState(origin)) >= 3, "heat softens wax past shell support");
            level.setBlock(origin, MediumReactions.block("gas", 0), 2);
            MediumReactions.tick("gas", level.getBlockState(origin), level, origin, level.random);
            require(level.isEmptyBlock(origin), "empty gas dissipates");
            level.setBlock(east, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(south, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(origin, MediumReactions.block("meat_hole", 6), 2);
            MediumReactions.validateHole("meat_hole", level, origin);
            require(level.isEmptyBlock(origin), "orphan swallowing mouth deletes itself");
            level.setBlock(south, MediumReactions.block("meat_wall", 6), 2);
            MediumReactions.tick("meat_wall", level.getBlockState(south), level, south, level.random);
            require(level.getBlockState(origin).is(ModBlocks.byId("meat_hole")), "directional wall creates matching mouth");
            require(level.getBlockEntity(south) instanceof com.mfqm.morefunquicksandmod.blockentity.MechanismBlockEntity, "meat wall has registered behavior block entity");
            passed.add("wetness/heat/crust/gas/orphan-mouth/directional-mouth/block-entity lifecycles");
            return List.copyOf(passed);
        } finally {
            if (pig != null) pig.discard();
            for (var entry : saved.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }
    private static void require(boolean result, String message) {
        if (!result) throw new IllegalStateException("MFQM physics verification failed: " + message);
    }
}
