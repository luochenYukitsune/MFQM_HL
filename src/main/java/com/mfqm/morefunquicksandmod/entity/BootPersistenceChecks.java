package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Two separate dedicated-server processes exercise the actual chunk entity storage, not only its codec. */
public final class BootPersistenceChecks {
    private static final BlockPos AT=new BlockPos(128,240,112);
    private static int ticks;
    private static boolean finished;
    public static void tick(ServerTickEvent.Post event) {
        String phase=System.getProperty("mfqm.persistencePhase","");
        if(phase.isEmpty() || finished)return;
        var level=event.getServer().overworld();
        if(++ticks==1)level.setChunkForced(AT.getX()>>4,AT.getZ()>>4,true);
        if(ticks<60)return;
        finished=true;
        try {
            var area=new net.minecraft.world.phys.AABB(AT).inflate(2);
            var boots=level.getEntitiesOfClass(StuckBootsEntity.class,area).stream().filter(e->!e.isRemoved()).toList();
            if(phase.equals("prepare")) {
                require(boots.isEmpty(),"persistence fixture starts empty");
                level.setBlock(AT,ModBlocks.byId("glue").defaultBlockState(),3);
                var pig=net.minecraft.world.entity.EntityType.PIG.create(level,net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                require(pig!=null,"persistence actor");pig.setPos(AT.getX()+.5,AT.getY()+.2,AT.getZ()+.5);
                pig.setItemSlot(EquipmentSlot.FEET,expected(level));
                require(StuckBootsEntity.tryLeaveBehind(pig,AT) && pig.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"actual persistent item transferred once");
                pig.discard();
            } else {
                require(phase.equals("recover") && boots.size()==1,"restarted region loads exactly one retained boots entity");
                var entity=boots.getFirst();require(ItemStack.matches(entity.stack(),expected(level)) && entity.anchor().equals(AT),"restart preserves name enchantment damage count and anchor");
                var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MFQMPersistenceProbe");
                var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,profile,net.minecraft.server.level.ClientInformation.createDefault());
                player.connection=new net.neoforged.neoforge.common.util.FakePlayer(level,profile).connection;
                player.setPos(entity.position().add(0,0,1));
                require(entity.recover(player,net.minecraft.world.InteractionHand.MAIN_HAND,2.5),"restart boots can actually be recovered");
                require(player.getInventory().countItem(Items.DIAMOND_BOOTS)==1 && entity.isRemoved(),"recovery transfers exactly one pair");
                level.setBlock(AT,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                level.setChunkForced(AT.getX()>>4,AT.getZ()>>4,false);
            }
            MFQM.LOGGER.info("MFQM_PERSISTENCE_CHECKS_COMPLETE phase={}",phase);
        } catch(Throwable failure){MFQM.LOGGER.error("MFQM_PERSISTENCE_CHECKS_FAILED",failure);}
    }
    private static ItemStack expected(ServerLevel level) {
        var item=new ItemStack(Items.DIAMOND_BOOTS);item.setDamageValue(73);
        item.set(DataComponents.CUSTOM_NAME,Component.literal("MFQM 持久留靴回归"));
        item.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING),3);
        return item;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private BootPersistenceChecks(){}
}
