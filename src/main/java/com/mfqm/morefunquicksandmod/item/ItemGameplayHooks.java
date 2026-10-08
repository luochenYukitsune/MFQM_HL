package com.mfqm.morefunquicksandmod.item;

import com.mfqm.morefunquicksandmod.registry.ModItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class ItemGameplayHooks {
    private static final Map<String, String> BUCKETS = Map.ofEntries(
            Map.entry("bog", "bog_bucket"), Map.entry("liquid_mire", "mire_bucket"), Map.entry("stable_liquid_mire", "mire_bucket"),
            Map.entry("jungle_quicksand", "quicksand_bucket"), Map.entry("sinking_slime", "slime_bucket"),
            Map.entry("dry_quicksand", "sand_bucket"), Map.entry("tar", "tar_bucket"), Map.entry("acid", "acid_bucket"),
            Map.entry("mucus", "mucus_bucket"), Map.entry("liquid_chocolate", "chocolate_bucket"),
            Map.entry("slurry", "slurry_bucket"), Map.entry("honey", "honey_bucket"), Map.entry("glue", "glue_bucket"));
    public static void register(IEventBus bus) {
        bus.addListener(ItemGameplayHooks::onBlock);
        bus.addListener(ItemGameplayHooks::onItem);
    }
    private static void onBlock(PlayerInteractEvent.RightClickBlock event) {
        if (collect(event.getLevel(), event.getEntity(), event.getHand(), event.getHitVec())) {
            event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        }
    }
    private static void onItem(PlayerInteractEvent.RightClickItem event) {
        if (collect(event.getLevel(), event.getEntity(), event.getHand(), Item.getPlayerPOVHitResult(event.getLevel(), event.getEntity(), ClipContext.Fluid.SOURCE_ONLY))) {
            event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        }
    }
    private static boolean collect(Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.BUCKET) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), held)) return false;
        BlockState state = level.getBlockState(pos);
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("mfqm")) return false;
        String item;
        if (id.getPath().equals("brown_clay")) {
            item = state.getValue(LegacyBlockItem.VARIANT) < 4 ? "brown_clay_bucket" : "mineral_clay_bucket";
        } else {
            item = BUCKETS.get(id.getPath());
            if (item == null || !state.hasProperty(LiquidBlock.LEVEL) || state.getValue(LiquidBlock.LEVEL) != 0) return false;
        }
        if (!level.isClientSide()) {
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) return false;
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(ModItems.byId(item))));
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1, 1);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        }
        return true;
    }
    private ItemGameplayHooks() {}
}
