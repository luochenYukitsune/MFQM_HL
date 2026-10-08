package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.block.StickyBoardBlock;
import com.mfqm.morefunquicksandmod.gameplay.AdhesiveMotion;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import java.util.Set;
import java.util.function.Consumer;

/** Explicitly enabled installed-JAR regression: genuine input, never flying or repositioning each tick. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class BoundedAdhesionClientChecks {
    private record Sample(Vec3 position,Vec3 origin,double strength,double effort,int bonds,boolean released){}
    private static final BlockPos BOARD=new BlockPos(160,253,0),EMPTY=new BlockPos(164,253,0);
    private static final KeyEvent F=new KeyEvent(GLFW.GLFW_KEY_F,0,0);
    private static boolean running,pending,oldCreativeGround;
    private static volatile boolean ready;
    private static Sample sample,baseline;
    private static int phase,ticks,presses,fov;
    private static double dryFov,startY,maxY,maxDistance;
    private static ClientInput oldInput;

    public static void start(Minecraft game) {
        if(!Boolean.getBoolean("mfqm.viscosityChecks") || running)return;
        oldInput=game.player.input;oldCreativeGround=ModConfig.SERVER.creativeGroundPhysics.get();
        ModConfig.SERVER.creativeGroundPhysics.set(true);
        running=true;ready=false;pending=false;phase=0;ticks=0;presses=0;
        fov=game.options.fov().get();game.player.input=input(false,false,false);
        MFQM.LOGGER.info("MFQM_BOUNDED_ADHESION_START creativeGround=true flight=false actualPlacement=true");
        execute(game,player->{
            var level=player.level();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            for(int x=157;x<=168;x++)for(int z=-5;z<=25;z++) {
                var floor=new BlockPos(x,252,z);level.getChunk(floor);
                level.setBlock(floor,Blocks.SMOOTH_STONE.defaultBlockState(),2);
                for(int up=1;up<=6;up++)level.setBlock(floor.above(up),Blocks.AIR.defaultBlockState(),2);
            }
            player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();
            player.setHealth(player.getMaxHealth());player.getFoodData().setFoodLevel(20);player.removeAllEffects();
            for(var slot:EquipmentSlot.values())player.setItemSlot(slot,ItemStack.EMPTY);
            teleport(player,160.5,253,-3.5,0,0);ready=true;
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!running)return;
        var game=Minecraft.getInstance();if(game.player==null || game.level==null)return;
        try {
            require(!game.player.getAbilities().flying,"client never flies");
            require(game.options.fov().get()==fov,"configured FOV never changes");
            if(++ticks>720)throw new IllegalStateException("phase timeout "+phase+" sample="+sample);
            switch(phase) {
                case 0->{
                    if(!ready || ticks<70)return;
                    dryFov=game.player.getFieldOfViewModifier(false,1);ready=false;phase=1;ticks=0;
                    execute(game,player->{
                        // The same component stack exposed in the creative tab uses ordinary BlockItem placement.
                        place(player,BOARD,StickyBoardBlock.coatedStack(7));
                        place(player,EMPTY,new ItemStack(ModItems.byId("sticky_board")));
                        require(StickyBoardBlock.isCoated(player.level().getBlockState(BOARD)),"creative coated variant places full glue");
                        require(!StickyBoardBlock.isCoated(player.level().getBlockState(EMPTY)),"plain variant places an empty base");
                        teleport(player,160.5,253.0625,.5,0,0);ready=true;
                    });
                }
                case 1->{if(ready && ticks>=20)fresh(game,2);}
                case 2->{
                    if(sample==null)return;
                    baseline=sample;
                    require(sample.bonds()>0 && sample.origin()!=null,"coated creative-ground board actually traps player");
                    close(game.player.getFieldOfViewModifier(false,1),dryFov,.00001,"adhesion does not zoom FOV");
                    close(game.player.getAttributeValue(Attributes.MOVEMENT_SPEED),.1,.00001,"adhesion does not alter camera-driving speed attribute");
                    maxDistance=0;game.player.input=input(true,false,false);phase=3;ticks=0;
                }
                case 3->{
                    bound(game);if(ticks<60)return;
                    game.player.input=input(false,false,false);fresh(game,4);
                }
                case 4->{
                    if(sample==null)return;
                    double distance=sample.position().subtract(baseline.position()).horizontalDistance();
                    require(distance>.60 && distance<.95 && sample.bonds()>0,"real W reaches the enlarged activity range and remains tethered");
                    MFQM.LOGGER.info("MFQM_BOUNDED_BOARD_WALK_COMPLETE ticks=60 distance={} maxDistance={} clientFlying=false serverFlying=false unchangedFov={}",distance,maxDistance,dryFov);
                    startY=game.player.getY();maxY=startY;game.player.input=input(true,true,true);phase=5;ticks=0;
                }
                case 5->{
                    bound(game);maxY=Math.max(maxY,game.player.getY());
                    if(ticks<60)return;game.player.input=input(false,false,false);fresh(game,6);
                }
                case 6->{
                    if(sample==null)return;
                    require(maxY>startY+.1 && maxY<=startY+.30,"genuine sprint/jump creates a finite short hop");
                    close(sample.position().y,startY,.025,"holding jump does not float upward");
                    close(game.player.getY(),startY,.025,"client held jump also returns without restarting");
                    require(sample.bonds()>0,"running and jumping remain tethered");
                    MFQM.LOGGER.info("MFQM_BOUNDED_BOARD_RUN_JUMP_COMPLETE ticks=60 peakRise={} maxDistance={} serverEnd={} clientEnd={} heldJump=true",maxY-startY,maxDistance,sample.position(),game.player.position());
                    phase=7;ticks=0;sample=null;
                }
                case 7->{
                    // One real F tap every 14 ticks, with the normal server cooldown and food rules.
                    if(ticks%14==1 && presses<35){MfqmClient.key(new InputEvent.Key(F,GLFW.GLFW_PRESS));MfqmClient.key(new InputEvent.Key(F,GLFW.GLFW_RELEASE));presses++;}
                    probe(game);
                    if(sample==null || !sample.released() || sample.bonds()!=0)return;
                    require(sample.effort()>=34,"accepted struggle weakens board before release");
                    baseline=sample;game.player.input=input(true,false,false);phase=8;ticks=0;
                }
                case 8->{if(ticks>=60){game.player.input=input(false,false,false);fresh(game,9);}}
                case 9->{
                    if(sample==null)return;
                    double escape=sample.position().subtract(baseline.position()).horizontalDistance();
                    require(escape>5 && sample.bonds()==0,"weakened board permits ordinary escape");
                    MFQM.LOGGER.info("MFQM_BOUNDED_BOARD_STRUGGLE_ESCAPE_COMPLETE realF={} effort={} walkAfterRelease={} bonds={}",presses,sample.effort(),escape,sample.bonds());
                    ready=false;phase=10;ticks=0;execute(game,player->{teleport(player,164.5,253.0625,.5,0,0);ready=true;});
                }
                case 10->{
                    if(!ready || ticks<70)return;
                    require(QuicksandPhysics.state(game.player).adhesiveConnections==0,"empty board never traps");
                    close(game.player.getFieldOfViewModifier(false,1),dryFov,.00001,"dry/empty board restores baseline FOV");
                    if(!Boolean.getBoolean("mfqm.boardVisual")){complete(game);return;}
                    ready=false;phase=11;ticks=0;
                    execute(game,player->{
                        player.level().setBlock(BOARD,StickyBoardBlock.coatedState(7),3);
                        teleport(player,162.5,253,-3.8,0,26);ready=true;
                    });
                }
                case 11->{
                    if(!ready || ticks<60)return;
                    phase=12;ticks=0;
                    net.minecraft.client.Screenshot.grab(game.gameDirectory,"mfqm-board-fixed.png",game.getMainRenderTarget(),1,
                        message->game.execute(()->{MFQM.LOGGER.info("MFQM_BOARD_VISUAL_SAVED {}",message.getString());complete(game);}));
                }
                case 12->{} // one explicitly requested visual regression image
                default->throw new IllegalStateException("invalid phase "+phase);
            }
        }catch(Throwable failure){fail(game,failure);}
    }
    private static void bound(Minecraft game) {
        var state=QuicksandPhysics.state(game.player);
        require(state.adhesiveOrigin!=null && state.adhesiveConnections>0,"live fixed anchor during movement");
        double distance=game.player.position().subtract(state.adhesiveOrigin).horizontalDistance();
        maxDistance=Math.max(maxDistance,distance);
        require(distance<=AdhesiveMotion.radius(ModConfig.SERVER.boardActivityRadius.get(),com.mfqm.morefunquicksandmod.gameplay.AdhesionController.profile("sticky_board").maxDistance(),state.adhesiveStrength)+.025,"walking/sprinting cannot bypass circular range: "+distance);
    }
    private static void place(ServerPlayer player,BlockPos at,ItemStack item) {
        player.setItemInHand(InteractionHand.MAIN_HAND,item);
        var hit=new BlockHitResult(Vec3.atCenterOf(at.below()).add(0,.5,0),Direction.UP,at.below(),false);
        require(item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"normal item placement succeeds");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    private static void teleport(ServerPlayer player,double x,double y,double z,float yaw,float pitch){player.teleportTo(player.level(),x,y,z,Set.of(),yaw,pitch,false);player.setDeltaMovement(Vec3.ZERO);}
    private static ClientInput input(boolean forward,boolean jump,boolean sprint){return new ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(forward,false,false,false,jump,false,sprint);moveVector=forward?new Vec2(0,1):Vec2.ZERO;}};}
    private static void fresh(Minecraft game,int next){require(!pending,"no overlapping phase observations");sample=null;phase=next;ticks=0;probe(game);}
    private static void probe(Minecraft game) {
        if(pending)return;pending=true;
        execute(game,player->{var state=QuicksandPhysics.state(player);
            var value=new Sample(player.position(),state.adhesiveOrigin,state.adhesiveStrength,state.struggle.effort(),state.adhesiveConnections,state.boardReleased);
            game.execute(()->{sample=value;pending=false;});
        });
    }
    private static void execute(Minecraft game,Consumer<ServerPlayer> action) {
        var server=game.getSingleplayerServer();var id=game.player.getUUID();require(server!=null,"integrated server available");
        server.execute(()->{try{var player=server.getPlayerList().getPlayer(id);require(player!=null && !player.getAbilities().flying,"server never flies");action.accept(player);}
            catch(Throwable failure){game.execute(()->fail(game,failure));}});
    }
    private static void close(double actual,double expected,double tolerance,String message){require(Math.abs(actual-expected)<=tolerance,message+" expected="+expected+" actual="+actual);}
    private static void complete(Minecraft game){MFQM.LOGGER.info("MFQM_BOUNDED_ADHESION_CHECKS_COMPLETE creativeCoatedPlacement=true emptyPlacement=true unchangedFov=true walking=true running=true shortHop=true heldJumpFinite=true realFRelease=true screenshots={}",Boolean.getBoolean("mfqm.boardVisual")?1:0);finish(game);}
    private static void fail(Minecraft game,Throwable failure){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED bounded adhesion: "+failure.getMessage(),failure);finish(game);}
    private static void finish(Minecraft game){if(!running)return;running=false;pending=false;if(game.player!=null)game.player.input=oldInput;oldInput=null;ModConfig.SERVER.creativeGroundPhysics.set(oldCreativeGround);game.stop();}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Bounded adhesion check failed: "+message);}
    private BoundedAdhesionClientChecks(){}
}
