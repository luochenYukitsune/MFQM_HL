package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Set;

/** Installed-JAR density check: real alternating W/S, no flying and no per-tick teleports. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class DynamicAdhesionClientChecks {
    private static boolean running;
    private static boolean oldCreativeGround;
    private static volatile boolean ready;
    private static int ticks,phase,oldLimit;
    private static ClientInput oldInput;
    private static net.minecraft.world.phys.Vec3 start;
    private static double maxMovement;
    public static void start(Minecraft game) {
        running=true;ready=false;ticks=0;phase=0;oldInput=game.player.input;
        oldCreativeGround=ModConfig.SERVER.creativeGroundPhysics.get();ModConfig.SERVER.creativeGroundPhysics.set(true);maxMovement=0;
        oldLimit=ModConfig.CLIENT.strandDisplayLimit.get();ModConfig.CLIENT.strandDisplayLimit.set(64);
        game.player.input=input(0);var id=game.player.getUUID();
        game.getSingleplayerServer().execute(()->{
            try {
                var player=game.getSingleplayerServer().getPlayerList().getPlayer(id);var level=player.level();
                level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                for(int x=178;x<=188;x++)for(int z=-4;z<=7;z++) {
                    var floor=new BlockPos(x,252,z);level.getChunk(floor);level.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
                    level.setBlock(floor.above(),ModBlocks.byId("glue").defaultBlockState(),2);
                    for(int up=2;up<8;up++)level.setBlock(floor.above(up),Blocks.AIR.defaultBlockState(),2);
                }
                player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();
                for(var slot:net.minecraft.world.entity.EquipmentSlot.values())player.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY);
                player.teleportTo(level,183.5,253.72,0,Set.of(),0,20,false);ready=true;
            }catch(Throwable e){game.execute(()->fail(game,e));}
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!running)return;var game=Minecraft.getInstance();if(game.player==null || game.level==null)return;
        try {
            require(!game.player.getAbilities().flying,"client never flies");
            if(++ticks>750)throw new IllegalStateException("Dynamic scene timed out "+phase);
            if(phase==0) {
                if(!ready || ticks<60)return;start=game.player.position();phase=1;ticks=0;
            } else if(phase==1) {
                // Sole contact refresh is exercised by actual native travel, not fixture repositioning.
                game.player.input=input((ticks/30)%2==0?1:-1);
                int bonds=QuicksandPhysics.state(game.player).adhesiveConnections;
                maxMovement=Math.max(maxMovement,game.player.position().distanceToSqr(start));
                if(ticks%100==0)MFQM.LOGGER.info("MFQM_DYNAMIC_PROGRESS tick={} bonds={} material={} pos={} groundPhysics={}",ticks,bonds,QuicksandPhysics.state(game.player).material,game.player.position(),ModConfig.SERVER.creativeGroundPhysics.get());
                if(bonds<64)return;
                require(maxMovement>.01,"real input moved the actor");
                game.player.input=input(0);phase=2;ticks=0;
            } else if(phase==2) {
                if(ticks<25)return;
                var helpers=helpers(game);require(helpers.size()==64,"client received all 64 live synchronized anchors");
                require(helpers.stream().filter(AdhesiveDisplayBudget::visible).count()==64,"default shows all 64 main strands");
                require(helpers.stream().filter(AdhesiveTetherEntity::cuff).count()==2,"only one membrane per foot, not 64 stacked cuffs");
                for(var helper:helpers)require(com.mfqm.morefunquicksandmod.gameplay.AdhesiveSurface.root(game.level,helper.blockPosition(),helper.material(),helper.position())!=null,"client root stays in actual adhesive medium");
                ModConfig.CLIENT.strandDisplayLimit.set(16);phase=3;ticks=0;
            } else if(phase==3) {
                if(ticks<20)return;var helpers=helpers(game);
                require(helpers.size()==64 && helpers.stream().filter(AdhesiveDisplayBudget::visible).count()==16,"display slider reduces rendering without deleting physical anchors");
                ModConfig.CLIENT.strandDisplayLimit.set(64);game.options.setCameraType(CameraType.THIRD_PERSON_BACK);game.player.setYRot(135);game.player.setXRot(27);phase=4;ticks=0;
            } else if(phase==4) {
                if(ticks<35)return;
                var id=game.player.getUUID();game.getSingleplayerServer().execute(()->{
                    var player=game.getSingleplayerServer().getPlayerList().getPlayer(id);
                    if(player.getAbilities().flying || QuicksandPhysics.state(player).anchors.size()!=64){game.execute(()->fail(game,new IllegalStateException("server density/flight mismatch")));return;}
                    game.execute(()->{
                        if(Boolean.getBoolean("mfqm.dynamicVisual"))net.minecraft.client.Screenshot.grab(game.gameDirectory,"mfqm-64-dynamic-strands.png",game.getMainRenderTarget(),1,r->MFQM.LOGGER.info("MFQM_DYNAMIC_VISUAL_SAVED {}",r.getString()));
                        MFQM.LOGGER.info("MFQM_DYNAMIC_ADHESION_COMPLETE realWS=true clientServer64=true client16Budget=true cuffs2=true rootsInside=true flight=false");finish(game);
                    });
                });phase=5;
            }
        }catch(Throwable e){fail(game,e);}
    }
    private static java.util.List<AdhesiveTetherEntity> helpers(Minecraft game){return game.level.getEntitiesOfClass(AdhesiveTetherEntity.class,game.player.getBoundingBox().inflate(12),e->e.target()==game.player && !e.breaking());}
    private static ClientInput input(int direction){return new ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(direction>0,direction<0,false,false,false,false,false);moveVector=new Vec2(0,direction);}};}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void fail(Minecraft game,Throwable failure){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED dynamic adhesion",failure);finish(game);}
    private static void finish(Minecraft game){running=false;game.player.input=oldInput;ModConfig.SERVER.creativeGroundPhysics.set(oldCreativeGround);ModConfig.CLIENT.strandDisplayLimit.set(oldLimit);game.stop();}
    private DynamicAdhesionClientChecks(){}
}
