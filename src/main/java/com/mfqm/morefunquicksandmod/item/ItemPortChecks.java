package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Server integration assertions. Invoked only by the explicitly enabled development validation runner. */
public final class ItemPortChecks {
    private static final UUID PROFILE = UUID.fromString("9038041a-76c6-4fdd-b605-5a7b403db8ed");

    public static List<String> verify(ServerLevel level, BlockPos base) {
        List<String> passed = new ArrayList<>();
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 4, 5))) {
            BlockPos copy = pos.immutable();
            require(level.getBlockEntity(copy) == null, "Item test volume must not contain block entities");
            saved.put(copy, level.getBlockState(copy));
        }
        FakePlayer player = new FakePlayer(level, new GameProfile(PROFILE, "[MFQM Checks]"));
        player.setPos(base.getX() + .5, base.getY() + 1, base.getZ() + .5);
        player.setYRot(0); player.setXRot(20);
        try {
            for (BlockPos pos : saved.keySet()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
            verifyDrinks(level, player, passed);
            verifyGear(passed);
            verifyGun(level, player, base.offset(0, 1, 3), passed);
            verifyBuckets(level, player, base.offset(0, 1, 3), passed);
            verifyFertilizer(level, player, base.offset(1, 1, 2), passed);
        } finally {
            for (var entity : level.getEntities(player, player.getBoundingBox().inflate(16))) {
                if (entity instanceof Projectile projectile && projectile.getOwner() == player) entity.discard();
            }
            saved.forEach((pos, state) -> level.setBlock(pos, state, 2));
            player.discard();
        }
        return List.copyOf(passed);
    }

    private static void verifyDrinks(ServerLevel level, FakePlayer player, List<String> passed) {
        ItemStack mire = new ItemStack(ModItems.byId("bottle_of_mire"));
        ItemStack result = mire.finishUsingItem(level, player);
        require(result.is(Items.GLASS_BOTTLE), "Mire drink must return its bottle");
        require(player.getEffect(MobEffects.POISON) != null && player.getEffect(MobEffects.POISON).getAmplifier() == 2, "Mire drink must apply Poison III");
        require(player.getEffect(MobEffects.HUNGER) != null && player.getEffect(MobEffects.HUNGER).getDuration() == 1200, "Mire hunger must last 1200 ticks");
        player.removeAllEffects();
        result = new ItemStack(ModItems.byId("sinking_potion")).finishUsingItem(level, player);
        require(result.is(Items.GLASS_BOTTLE), "Sinking drink must return its bottle");
        require(player.getEffect(MobEffects.WITHER) != null && player.getEffect(MobEffects.WITHER).getAmplifier() == 2, "Sinking drink must apply Wither III");
        require(player.getEffect(MobEffects.HUNGER) != null && player.getEffect(MobEffects.HUNGER).getDuration() == 2400, "Sinking hunger must last 2400 ticks");
        player.removeAllEffects();
        player.getFoodData().setFoodLevel(10);
        result = new ItemStack(ModItems.byId("hot_chocolate")).finishUsingItem(level, player);
        require(result.is(Items.GLASS_BOTTLE) && player.getFoodData().getFoodLevel() == 18, "Hot chocolate restores 8 food and returns a bottle");
        require(player.hasEffect(MobEffects.REGENERATION), "Hot chocolate restores regeneration");
        require(ModItems.byId("chocolate_donut").components().get(DataComponents.FOOD).nutrition() == 8, "Original chocolate donut nutrition is 8");
        require(ModItems.byId("cranberry").components().get(DataComponents.CONSUMABLE).consumeTicks() == 8, "Cranberries take 8 ticks to eat");
        passed.add("items: three drinks, original effect durations, bottle returns, food values");
    }

    private static void verifyGear(List<String> passed) {
        require(ModBlocks.byId("brown_clay").asItem() == ModItems.byId("brown_clay"), "Metadata items cannot overwrite the canonical block item");
        for (String id : new String[]{"vore_slime", "muddy_blob", "sand_blob", "tar_slime", "bee"}) {
            var data = ModItems.byId(id + "_spawn_egg").components().get(DataComponents.ENTITY_DATA);
            require(data != null && data.type() == com.mfqm.morefunquicksandmod.registry.ModEntities.byId(id), "Spawn egg must point at the intended entity: " + id);
        }
        Map<String, EquipmentSlot> slots = Map.of("gas_mask", EquipmentSlot.HEAD, "life_jacket", EquipmentSlot.CHEST,
                "wading_boots", EquipmentSlot.FEET, "tall_leather_boots", EquipmentSlot.FEET, "slimy_tall_leather_boots", EquipmentSlot.FEET);
        slots.forEach((id, slot) -> {
            var gear = ModItems.byId(id).components().get(DataComponents.EQUIPPABLE);
            require(gear != null && gear.slot() == slot && !gear.damageOnHurt() && gear.assetId().isPresent(), "Equipment slot and original unprotective durability: " + id);
        });
        passed.add("items: five equippable armor assets and slots");
    }

    private static void verifyGun(ServerLevel level, FakePlayer player, BlockPos target, List<String> passed) {
        ItemStack gun = new ItemStack(ModItems.byId("liquid_gun"));
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        require(LiquidGunItem.shots(gun) == 32, "New liquid guns carry 32 water shots");
        require(gun.getItem().releaseUsing(gun, level, player, 71980), "Liquid gun release must fire");
        require(LiquidGunItem.shots(gun) == 31, "One gun shot consumes one charge");
        CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> tag.putInt("mfqm_shots", 1));
        player.setShiftKeyDown(true);
        level.setBlock(target, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 2);
        require(!gun.getItem().use(level, player, InteractionHand.MAIN_HAND).consumesAction(), "Flowing water cannot refill the gun");
        require(LiquidGunItem.shots(gun) == 1 && !level.getBlockState(target).isAir(), "Rejected collection preserves ammo and world");
        level.setBlock(target, Blocks.WATER.defaultBlockState(), 2);
        require(gun.getItem().use(level, player, InteractionHand.MAIN_HAND).consumesAction(), "Source water must refill the gun");
        require(LiquidGunItem.shots(gun) == 32 && level.getBlockState(target).isAir(), "Source collection resets 32 shots and consumes the source");
        player.setShiftKeyDown(false);
        passed.add("items: liquid gun fires, decrements ammo, rejects flowing fluid, samples and removes source");
    }

    private static void verifyBuckets(ServerLevel level, FakePlayer player, BlockPos target, List<String> passed) {
        for (String block : new String[]{"acid", "stable_liquid_mire", "brown_clay"}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            BlockState state = ModBlocks.byId(block).defaultBlockState();
            if (block.equals("brown_clay")) state = state.setValue(LegacyBlockItem.VARIANT, 4);
            level.setBlock(target, state, 2);
            var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, target,
                    new BlockHitResult(Vec3.atCenterOf(target), Direction.NORTH, target, false));
            NeoForge.EVENT_BUS.post(event);
            String bucket = block.equals("acid") ? "acid_bucket" : block.equals("brown_clay") ? "mineral_clay_bucket" : "mire_bucket";
            require(event.isCanceled() && player.getMainHandItem().is(ModItems.byId(bucket)) && level.getBlockState(target).isAir(), "Special bucket pickup: " + block);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        level.setBlock(target, ModBlocks.byId("acid").defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 2);
        var flowing = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, target,
                new BlockHitResult(Vec3.atCenterOf(target), Direction.NORTH, target, false));
        NeoForge.EVENT_BUS.post(flowing);
        require(!flowing.isCanceled() && player.getMainHandItem().is(Items.BUCKET), "Bucket pickup rejects flowing acid");
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.byId("mineral_clay_bucket")));
        require(player.getMainHandItem().getItem().use(level, player, InteractionHand.MAIN_HAND).consumesAction(), "Mineral clay bucket places its content");
        BlockState placed = level.getBlockState(target.north());
        require(placed.is(ModBlocks.byId("brown_clay")) && placed.getValue(LegacyBlockItem.VARIANT) == 4 && player.getMainHandItem().is(Items.BUCKET), "Mineral clay placement keeps variant and returns the empty bucket");
        passed.add("items: acid, stable mire, mineral clay collection; flowing source refusal; variant-preserving bucket placement");
    }

    private static void verifyFertilizer(ServerLevel level, FakePlayer player, BlockPos target, List<String> passed) {
        level.setBlock(target.below(), Blocks.FARMLAND.defaultBlockState(), 2);
        level.setBlock(target, Blocks.WHEAT.defaultBlockState(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.byId("fertilizer"), 2));
        UseOnContext use = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
        require(player.getMainHandItem().getItem().useOn(use).consumesAction(), "Fertilizer accepts growing wheat");
        require(level.getBlockState(target).getValue(CropBlock.AGE) >= 4 && player.getMainHandItem().getCount() == 1, "One fertilizer runs two growth attempts and consumes one item");
        passed.add("items: fertilizer double growth with one-item consumption");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private ItemPortChecks() {}
}
