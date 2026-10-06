package com.mfqm.morefunquicksandmod.gameplay;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.block.LegacyStateBlock;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.ArrayDeque;
import java.util.HashSet;

/** Bounded, loaded-chunk-only environment reactions and legacy hidden block lifecycles. */
public final class MediumReactions {
    private MediumReactions() {}
    public static boolean nearSolidEdge(BlockGetter level, BlockPos pos) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var state = level.getBlockState(pos.relative(direction));
            if (!state.isAir() && !(state.getBlock() instanceof LegacyStateBlock) && state.getFluidState().isEmpty()) return true;
        }
        return false;
    }
    public static int variant(BlockState state) { return state.hasProperty(LegacyStateBlock.VARIANT) ? state.getValue(LegacyStateBlock.VARIANT) : 0; }
    public static BlockState block(String id, int variant) {
        var state = ModBlocks.byId(id).defaultBlockState();
        return state.hasProperty(LegacyStateBlock.VARIANT) ? state.setValue(LegacyStateBlock.VARIANT, Math.clamp(variant, 0, 15)) : state;
    }
    public static boolean nearFluid(ServerLevel level, BlockPos pos, net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag) {
        for (var direction : Direction.values()) if (level.getFluidState(pos.relative(direction)).is(tag)) return true;
        return false;
    }
    private static boolean hot(ServerLevel level, BlockPos pos) {
        for (var direction : Direction.values()) {
            var state = level.getBlockState(pos.relative(direction));
            if (state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return true;
        }
        return false;
    }
    public static void tick(String id, BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.hasChunkAt(pos)) return;
        int variant = variant(state);
        boolean water = nearFluid(level, pos, FluidTags.WATER);
        boolean wet = level.isRainingAt(pos.above()) || water;
        boolean hot = hot(level, pos);
        switch (id) {
            case "mud" -> {
                if (level.getBlockState(pos.below()).is(ModBlocks.byId("mud")) && !nearSolidEdge(level, pos)) {
                    if (variant < 3 && random.nextInt(4) == 0) level.setBlock(pos, block(id, variant + 1), 3);
                } else if (variant > 0 && nearSolidEdge(level, pos) && random.nextInt(8) == 0) level.setBlock(pos, block(id, variant - 1), 3);
            }
            case "wax" -> {
                int target = variant;
                if (hot || (level.getMaxLocalRawBrightness(pos.above()) > 12 && random.nextInt(3) == 0)) target++;
                if (wet || level.getBlockState(pos.above()).is(Blocks.ICE) || level.getBlockState(pos.above()).is(Blocks.SNOW)) target--;
                target = Math.clamp(target, 0, 15);
                if (target != variant) level.setBlock(pos, block(id, target), 3);
            }
            case "sinking_clay" -> {
                if (hot) level.setBlock(pos, block("hardened_clay", variant > 3 ? 1 : 0), 3);
                else if (wet && variant < 7) soften(level, pos, id, Math.max(4, variant + 1));
                else if (!wet && variant > 0 && random.nextInt(12) == 0) level.setBlock(pos, block(id, variant - 1), 3);
            }
            case "mire" -> {
                if (wet && variant < 7) soften(level, pos, id, Math.max(4, variant + 1));
                else if (!wet && variant > 0 && random.nextInt(16) == 0) level.setBlock(pos, block(id, variant - 1), 3);
            }
            case "morass" -> { if (wet && variant > 1) level.setBlock(pos, block("mire", 5), 3); }
            case "quicksand" -> { if (nearFluid(level, pos, FluidTags.LAVA)) level.setBlock(pos, block("dry_quicksand", 0), 3); }
            case "dry_quicksand" -> { if (wet) level.setBlock(pos, block("quicksand", 0), 3); }
            case "honey" -> { if (water) level.setBlock(pos, block("solid_honey", 0), 3); }
            case "solid_honey" -> {
                if (hot) level.setBlock(pos, block("honey", 0), 3);
                else if (variant > 0) {
                    if (variant > 1) for (var direction : Direction.values()) {
                        if (direction == Direction.UP) continue;
                        int distanceLimit = direction == Direction.DOWN ? 2 : 3;
                        for (int distance = 1; distance <= distanceLimit; distance++) {
                            var target = pos.relative(direction, distance);
                            if (!level.hasChunkAt(target)) continue;
                            var at = level.getBlockState(target);
                            if (at.is(ModBlocks.byId("honeycomb")) && variant(at) == 3) level.setBlock(target, block("solid_honey", variant - 1), 3);
                        }
                    }
                    level.setBlock(pos, block("honey", 0), 3);
                }
            }
            case "liquid_mire", "stable_liquid_mire", "bog", "wet_peat" -> {
                if (nearFluid(level, pos, FluidTags.LAVA)) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            }
            case "chocolate" -> { if (hot) level.setBlock(pos, block("liquid_chocolate", 0), 3); }
            case "liquid_chocolate" -> { if (water || (!hot && level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel()))) level.setBlock(pos, block("chocolate", 0), 3); }
            case "soft_snow" -> { if (hot) level.removeBlock(pos, false); }
            case "gas" -> diffuseGas(level, pos, state, random);
            case "slurry" -> {
                if (ModConfig.SERVER.gasSlurry.get() && level.isEmptyBlock(pos.above()) && random.nextInt(8) == 0)
                    level.setBlock(pos.above(), block("gas", 8 + random.nextInt(8)), 3);
            }
            case "sinky_liquid" -> fusion(level, pos, random);
            case "moor_grass" -> {
                if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
                else if (variant == 4 && random.nextInt(5) == 0) level.setBlock(pos, block(id, 5), 3);
            }
            case "tendrils", "leaves_pile", "custom_lily_pad", "lure" -> { if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true); }
            case "vore_hole", "meat_hole" -> validateHole(id, level, pos);
            case "blossom", "meat_wall" -> wallMouth(level, pos, id, variant);
            case "larvae" -> {
                if (hot) { level.removeBlock(pos, false); Block.popResource(level, pos, new ItemStack(Items.ROTTEN_FLESH)); }
                else if (random.nextInt(4) == 0) {
                    for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(.5), e -> e.getItem().has(net.minecraft.core.component.DataComponents.FOOD))) {
                        item.getItem().shrink(1);
                        if (item.getItem().isEmpty()) item.discard();
                        level.setBlock(pos, block(id, Math.min(15, variant + 1)), 3);
                        break;
                    }
                }
            }
            case "honeycomb" -> { if (variant == 3) level.removeBlock(pos, false); }
            case "brown_clay" -> {
                if (nearFluid(level, pos, FluidTags.LAVA)) level.setBlock(pos, variant > 3 ? Blocks.CLAY.defaultBlockState() : Blocks.TERRACOTTA.defaultBlockState(), 3);
                else if (random.nextInt(5) == 0 && pos.getY() > level.getMinY() && level.isEmptyBlock(pos.below())) {
                    level.setBlock(pos.below(), state, 3);
                    level.removeBlock(pos, false);
                }
            }
            case "tangleroot_moss" -> moss(level, pos, state, random);
            case "sandstone_trap" -> { if (level.hasNeighborSignal(pos)) triggerTrap(level, pos); }
            default -> {}
        }
    }

    /** Neighboring crusts weaken as a tear spreads; at most the four immediate cells change. */
    public static void soften(ServerLevel level, BlockPos pos, String id, int value) {
        level.setBlock(pos, block(id, value), 3);
        for (var direction : Direction.Plane.HORIZONTAL) {
            var neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) continue;
            var state = level.getBlockState(neighbor);
            if (state.is(ModBlocks.byId(id)) && variant(state) < 4 && level.random.nextInt(3) == 0) level.setBlock(neighbor, block(id, 3), 3);
        }
    }

    public static void triggerTrap(ServerLevel level, BlockPos origin) {
        var queue = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        queue.add(origin);
        while (!queue.isEmpty() && visited.size() < 256) {
            var pos = queue.removeFirst();
            if (!visited.add(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(ModBlocks.byId("sandstone_trap"))) continue;
            level.setBlock(pos, block("quicksand", 0), 3);
            for (var direction : Direction.values()) queue.addLast(pos.relative(direction));
        }
    }

    private static void diffuseGas(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        int concentration = variant(state);
        if (concentration <= 0 || random.nextInt(5) == 0) {
            if (concentration < 2) { level.removeBlock(pos, false); return; }
            level.setBlock(pos, block("gas", concentration - 1), 3);
        }
        if (concentration < 2) return;
        var direction = random.nextBoolean() ? Direction.DOWN : Direction.values()[random.nextInt(6)];
        var target = pos.relative(direction);
        if (level.hasChunkAt(target) && level.isEmptyBlock(target)) {
            level.setBlock(target, block("gas", Math.max(0, concentration - 2)), 3);
            level.setBlock(pos, block("gas", Math.max(0, concentration - 1)), 3);
        }
    }

    /** SinkyLiquid reacts with its surroundings while preserving structures and block entities. */
    private static void fusion(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(ModBlocks.byId("sinky_liquid"))) return;
        for (var direction : Direction.values()) {
            if (random.nextInt(3) != 0) continue;
            var target = pos.relative(direction);
            if (transform(level, target)) {
                if (random.nextInt(4) == 0 && level.getBlockState(pos).hasProperty(LiquidBlock.LEVEL)) {
                    var source = level.getBlockState(pos);
                    int value = source.getValue(LiquidBlock.LEVEL);
                    if (value < 7) level.setBlock(pos, source.setValue(LiquidBlock.LEVEL, value + 1), 3);
                }
            }
        }
    }
    public static void fuse(ServerLevel level, BlockPos pos) { fusion(level, pos, level.random); }

    /** The direct conversion contract is shared with projectiles and developer checks. */
    public static boolean transform(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        if (state.is(Blocks.BEDROCK) || state.hasBlockEntity() && !(state.is(ModBlocks.byId("meat_wall")) && variant(state) == 0)) return false;
        var converted = fusionTarget(state);
        if (converted == null) return false;
        level.setBlock(pos, converted, 3);
        var below = pos.below();
        if (below.getY() >= level.getMinY() && level.hasChunkAt(below)) {
            var ground = level.getBlockState(below);
            if (!ground.hasBlockEntity() && !ground.is(Blocks.BEDROCK) && fusionTarget(ground) != null) level.setBlock(below, converted, 3);
        }
        return true;
    }
    public static BlockState fusionTarget(BlockState state) {
        if (state.is(Blocks.BEDROCK) || state.isAir()) return null;
        if (state.is(ModBlocks.byId("meat_wall")) && variant(state) == 0) return block("swallowing_flesh", 0);
        if (state.hasBlockEntity()) return null;
        // Optional modded material bindings are supplied through tags, rather than obsolete numeric IDs.
        for (String target : new String[]{"mud", "moor", "mire", "quicksand", "soft_quicksand", "jungle_quicksand", "brown_clay", "sinking_clay", "tar", "wax", "dense_web", "bog", "slurry", "sinking_slime"}) {
            if (state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "convertible_to_" + target)))) return block(target, target.equals("mud") ? 3 : 0);
        }
        if (state.is(BlockTags.WOOL)) {
            String color = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().replace("_wool", "");
            try { return block("sinking_rug", net.minecraft.world.item.DyeColor.valueOf(color.toUpperCase(java.util.Locale.ROOT)).getId()); } catch (IllegalArgumentException ignored) { return block("sinking_rug", 0); }
        }
        if (state.is(BlockTags.SAND)) return block("quicksand", 0);
        if (state.is(Blocks.GRAVEL)) return block("soft_gravel", 1);
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) return block("soft_snow", 0);
        if (state.is(Blocks.CLAY)) return block("sinking_clay", 4);
        if (state.is(Blocks.TERRACOTTA) || state.is(BlockTags.TERRACOTTA)) return block("brown_clay", 0);
        if (state.is(ModBlocks.byId("hardened_clay"))) return block("sinking_clay", 0);
        if (state.is(Blocks.COARSE_DIRT)) return block("soft_quicksand", 0);
        if (state.is(Blocks.DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MUD)) return block("mire", 0);
        if (state.is(Blocks.GRASS_BLOCK)) return block("moor", 0);
        if (state.is(Blocks.MYCELIUM)) return block("bog", 0);
        if (state.is(Blocks.FARMLAND)) return block("stable_liquid_mire", 0);
        if (state.is(Blocks.PODZOL)) return block("wet_peat", 0);
        if (state.is(Blocks.HAY_BLOCK)) return block("wet_peat", 0);
        if (state.is(ModBlocks.byId("mud"))) return block("mud", 3);
        if (state.is(ModBlocks.byId("peat"))) return block("wet_peat", 0);
        if (state.is(Blocks.COBWEB) || state.is(BlockTags.LEAVES)) return block("dense_web", 0);
        if (state.is(Blocks.MOSS_BLOCK)) return block("tangleroot_moss", 0);
        if (state.is(Blocks.SLIME_BLOCK)) return block("sinking_slime", 0);
        if (state.is(Blocks.HONEY_BLOCK) || state.is(ModBlocks.byId("solid_honey"))) return block("honey", 0);
        if (state.is(ModBlocks.byId("chocolate"))) return block("liquid_chocolate", 0);
        if (state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) return block("corrupted_sand", 0);
        if (state.is(Blocks.MOSSY_COBBLESTONE)) return block("sinking_slime", 0);
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.COAL_BLOCK)) return block("tar", 0);
        if (state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)) return block("dry_quicksand", 0);
        if (state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)) return block("slurry", 0);
        if (state.is(Blocks.COBBLESTONE)) return block("jungle_quicksand", 0);
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.NETHERITE_BLOCK)
                || state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.EMERALD_BLOCK) || state.is(Blocks.LAPIS_BLOCK)) return block("mire", 0);
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)) return block("wax", 3);
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(Blocks.NETHER_BRICKS) || state.is(Blocks.BRICKS)) return block("tar", 0);
        if (!state.getFluidState().isEmpty() && (state.getFluidState().is(FluidTags.WATER) || state.getFluidState().is(FluidTags.LAVA))) return block("gas", 8);
        // Already transformed media must not eat one another when a splash places adjacent cells.
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("mfqm")) return null;
        if (state.canBeReplaced()) return block("gas", 3);
        return null;
    }
    private static Direction mouthDirection(String id, int variant) {
        if (id.equals("blossom") || id.equals("vore_hole")) return switch (variant) { case 6 -> Direction.NORTH; case 7 -> Direction.EAST; case 8 -> Direction.SOUTH; default -> Direction.WEST; };
        return switch (variant) { case 6 -> Direction.NORTH; case 7 -> Direction.SOUTH; case 8 -> Direction.WEST; default -> Direction.EAST; };
    }
    private static void wallMouth(ServerLevel level, BlockPos pos, String id, int variant) {
        if (variant >= 6 && variant <= 9) {
            var target = pos.relative(mouthDirection(id, variant));
            if (level.hasChunkAt(target) && level.isEmptyBlock(target)) level.setBlock(target, block(id.equals("blossom") ? "vore_hole" : "meat_hole", variant), 3);
        } else if (id.equals("meat_wall") && variant == 10 && level.random.nextInt(25) == 0) {
            ejectWaste(level, pos);
        } else if (id.equals("blossom") && variant == 11 && level.random.nextInt(16) == 0 && level.isEmptyBlock(pos.above())) {
            com.mfqm.morefunquicksandmod.worldgen.LegacyStructures.growBlossom(level, pos);
        }
    }
    public static void validateHole(String id, ServerLevel level, BlockPos pos) {
        String parent = id.equals("vore_hole") ? "blossom" : "meat_wall";
        for (int variant = 6; variant <= 9; variant++) {
            var at = pos.relative(mouthDirection(id, variant).getOpposite());
            var state = level.getBlockState(at);
            if (state.is(ModBlocks.byId(parent)) && variant(state) == variant) return;
        }
        level.removeBlock(pos, false);
    }
    public static boolean validMeatColumn(ServerLevel level, BlockPos base) {
        for (int up = 1; up <= 5; up++) {
            var at = base.above(up);
            if (!level.hasChunkAt(at) || !level.getBlockState(at).is(ModBlocks.byId("meat_wall"))) return false;
            if (up < 5) for (var direction : Direction.Plane.HORIZONTAL) if (!level.getBlockState(at.relative(direction)).is(ModBlocks.byId("meat_wall"))) return false;
        }
        return true;
    }
    public static boolean ejectWaste(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!state.is(ModBlocks.byId("meat_wall")) || variant(state) != 10 || !level.dimension().equals(net.minecraft.world.level.Level.NETHER)
                || !level.getBlockState(pos.below()).canBeReplaced() || !validMeatColumn(level, pos)) return false;
        if (!level.setBlock(pos.below(), block("slurry", 0), 3)) return false;
        dropWasteLoot(level, pos.below());
        return true;
    }
    private static void dropWasteLoot(ServerLevel level, BlockPos pos) {
        var random = level.random;
        ItemStack loot;
        if (random.nextInt(10) == 0) loot = new ItemStack(Items.BONE, 1 + random.nextInt(3));
        else if (random.nextInt(35) == 0) loot = new ItemStack(Items.ARROW, 1 + random.nextInt(3));
        else if (random.nextInt(35) == 0) loot = new ItemStack(Items.GOLD_NUGGET);
        else if (random.nextInt(45) == 0) loot = new ItemStack(Items.IRON_INGOT);
        else if (random.nextInt(45) == 0) loot = new ItemStack(Items.GOLD_INGOT);
        else if (random.nextInt(50) == 0) { loot = new ItemStack(Items.GOLDEN_SWORD); loot.setDamageValue(4 + random.nextInt(12)); }
        else if (random.nextInt(60) == 0) { loot = new ItemStack(Items.STONE_SWORD); loot.setDamageValue(4 + random.nextInt(12)); }
        else if (random.nextInt(100) == 0) loot = new ItemStack(Items.COAL);
        else if (random.nextInt(500) == 0) loot = new ItemStack(Items.WITHER_SKELETON_SKULL);
        else return;
        Block.popResource(level, pos, loot);
    }
    private static void moss(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (nearFluid(level, pos, FluidTags.LAVA) && random.nextInt(5) == 0) { level.removeBlock(pos, false); return; }
        if (pos.getY() > level.getMinY() && level.isEmptyBlock(pos.below())) {
            if (variant(state) != 0 && random.nextInt(4) == 0) {
                level.setBlock(pos.below(), block("tangleroot_moss", 0), 3);
                level.removeBlock(pos, false);
                return;
            }
        }
        var below = level.getBlockState(pos.below());
        boolean stacked = level.getBlockState(pos.above()).is(ModBlocks.byId("tangleroot_moss"));
        int value;
        if (below.isAir()) value = 0;
        else if (!below.getFluidState().isEmpty()) value = stacked ? 3 : 2;
        else if (below.is(ModBlocks.byId("tangleroot_moss"))) {
            boolean wet = switch (variant(below)) { case 2, 3, 5, 6 -> true; default -> false; };
            value = stacked ? (wet ? 5 : 4) : (wet ? 6 : 1);
        } else value = stacked ? 4 : 1;
        if (value != variant(state)) level.setBlock(pos, block("tangleroot_moss", value), 3);
    }
}
