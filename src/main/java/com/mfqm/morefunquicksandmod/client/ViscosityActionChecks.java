package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
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

/** Real input/network boundaries for cumulative sink and rescue prediction; no images or per-tick positioning. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class ViscosityActionChecks {
    private record Observation(Vec3 position,String material,double depth,double effort,double totalSink,
                               long animation,long rescueSequence,int rescueTicks,int connections,
                               boolean jumping,long inputTick,long time,double speed,double gravity,
                               Vec3 velocity,boolean onGround,long externalSequence,int externalTicks) {}
    private static final KeyEvent F=new KeyEvent(GLFW.GLFW_KEY_F,0,0);
    private static final double GLUE_Y=252.5;
    private static boolean running,probing,oldCreativeGround,oldHotTar,oldMudTentacles,oldFleshTentacles,oldStruggleKey;
    private static volatile boolean serverReady;
    private static Observation observation,baseline,afterStruggle,rescueBaseline,afterRescue,dryBaseline;
    private static ClientInput previousInput;
    private static int phase,ticks,acceptedPresses;
    private static long lastAcceptedAnimation;
    private static double baselineClientY,afterStruggleClientY,rescueClientY,afterRescueClientY,maxRescueClientY;
    private static Vec3 dryClientStart;
    private static Observation knockbackBaseline,afterKnockback;
    private static boolean trackingKnockback;
    private static java.util.UUID playerId;
    private static volatile boolean serverFlightViolation;
    private static double knockbackClientStartY,maxKnockbackClientY,maxKnockbackServerY;
    private static boolean rangeMeasured;
    private static Vec3 glueRangeStart;

    public static void start(Minecraft game) {
        if(!Boolean.getBoolean("mfqm.viscosityChecks") || running)return;
        require(game.player!=null && game.getSingleplayerServer()!=null,"requires isolated installed singleplayer save");
        previousInput=game.player.input;
        oldStruggleKey=ModConfig.CLIENT.enableStruggleKey.get();
        oldCreativeGround=ModConfig.SERVER.creativeGroundPhysics.get();oldHotTar=ModConfig.SERVER.hotTar.get();
        oldMudTentacles=ModConfig.SERVER.mudTentacles.get();oldFleshTentacles=ModConfig.SERVER.tentaclesInFlesh.get();
        ModConfig.SERVER.creativeGroundPhysics.set(false);ModConfig.SERVER.hotTar.set(false);
        ModConfig.SERVER.mudTentacles.set(false);ModConfig.SERVER.tentaclesInFlesh.set(false);
        ModConfig.CLIENT.enableStruggleKey.set(false);
        running=true;rangeMeasured=false;phase=0;ticks=0;acceptedPresses=0;serverReady=false;observation=null;game.player.input=input(false,false);
        playerId=game.player.getUUID();serverFlightViolation=false;
        MFQM.LOGGER.info("MFQM_VISCOSITY_ACTION_CHECKS_START screenshots=0 realF=true suspendedGlue=true rescueOnce=true");
        execute(game,player->{
            var level=player.level();
            // A long east-facing dry lane cannot run back into the pool.
            for(int x=98;x<=154;x++)for(int z=-8;z<=0;z++) {
                var floor=new BlockPos(x,252,z);level.getChunk(floor);
                level.setBlock(floor,Blocks.SMOOTH_STONE.defaultBlockState(),2);
                for(int dy=1;dy<=7;dy++)level.setBlock(floor.above(dy),Blocks.AIR.defaultBlockState(),2);
            }
            for(int x=98;x<=110;x++)for(int z=2;z<=14;z++) {
                var floor=new BlockPos(x,248,z);level.getChunk(floor);
                level.setBlock(floor,Blocks.SMOOTH_STONE.defaultBlockState(),2);
                boolean wall=x==98 || x==110 || z==2 || z==14;
                for(int y=249;y<=253;y++)level.setBlock(new BlockPos(x,y,z),
                        wall?Blocks.SMOOTH_STONE.defaultBlockState():ModBlocks.byId("glue").defaultBlockState(),2);
                for(int y=254;y<=259;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            preparePlayer(player);dryTeleport(player);serverReady=true;
        });
    }

    @SubscribeEvent public static void serverFlight(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if(running && event.getEntity() instanceof ServerPlayer player && player.getUUID().equals(playerId)
                && player.getAbilities().flying)serverFlightViolation=true;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!running)return;
        Minecraft game=Minecraft.getInstance();if(game.player==null || game.level==null)return;
        try {
            require(!game.player.getAbilities().flying,"all action cases remain on the ground or suspended by glue, never flying");
            require(!serverFlightViolation,"server action cases remain non-flying throughout every tick");
            if(++ticks>260)throw new IllegalStateException("phase timeout "+phase+" last="+observation);
            switch(phase) {
                case 0->{
                    if(!serverReady || ticks<70)return;
                    // The isolated action run builds this chunk for the first time.
                    // Server task completion does not mean its bulk block updates
                    // have reached the client's movement simulation yet.
                    for(int y=249;y<=253;y++)if(!game.level.getBlockState(new BlockPos(104,y,8)).is(ModBlocks.byId("glue")))return;
                    if(Math.abs(game.player.getY()-253)>.005 || !game.player.onGround())return;
                    MFQM.LOGGER.info("MFQM_VISCOSITY_ACTION_FIXTURE_READY ticks={} clientY={} clientVelocity={} glueColumn=true",ticks,game.player.getY(),game.player.getDeltaMovement());
                    serverReady=false;phase=1;ticks=0;observation=null;
                    execute(game,player->{player.teleportTo(player.level(),104.5,GLUE_Y,8.5,Set.of(),0,0,false);
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                }
                case 1->{if(serverReady && ticks>=15)fresh(game,2);}
                case 2->{
                    if(observation==null)return;
                    baseline=observation;baselineClientY=game.player.getY();
                    require(baseline.material().equals("glue") && baseline.depth()>1,"actual deep glue contact");
                    require(baseline.position().y-249>2 && !game.player.onGround(),"glue test body is suspended more than two blocks above floor");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_ACTION_BASELINE server={} clientY={} clientContact={} clientGravity={}",baseline,baselineClientY,QuicksandPhysics.findContact(game.player,game.level),game.player.getAttributeValue(Attributes.GRAVITY));
                    close(baseline.position().y,GLUE_Y,.005,"server has no passive glue sinking before baseline");
                    close(baselineClientY,GLUE_Y,.02,"client has no passive glue sinking before baseline");
                    require(baseline.effort()==0,"new naturally reset episode starts without effort");
                    if(!rangeMeasured) {
                        glueRangeStart=baseline.position();game.player.input=input(true,false);phase=30;ticks=0;observation=null;return;
                    }
                    phase=3;ticks=0;observation=null;
                }
                case 3->{if(ticks>=30)fresh(game,4);}
                case 30->{if(ticks>=120){game.player.input=input(false,false);phase=31;ticks=0;}}
                case 31->{if(ticks>=8)fresh(game,32);}
                case 32->{
                    if(observation==null)return;
                    double distance=observation.position().subtract(glueRangeStart).horizontalDistance();
                    require(distance>1.10 && distance<1.35 && observation.connections()>0,"deep glue allows the requested roughly 1.2-block activity range after sustained real W input");
                    close(game.player.getFieldOfViewModifier(false,1),1,.00001,"expanded glue range does not zoom FOV");
                    MFQM.LOGGER.info("MFQM_GLUE_ACTIVITY_RANGE_COMPLETE movementTicks=120 distance={} connections={} clientFlying=false serverFlying=false",distance,observation.connections());
                    rangeMeasured=true;serverReady=false;phase=33;ticks=0;observation=null;
                    execute(game,player->{dryTeleport(player);serverReady=true;});
                }
                case 33->{
                    if(!serverReady || ticks<55)return;
                    serverReady=false;phase=1;ticks=0;observation=null;
                    execute(game,player->{player.teleportTo(player.level(),104.5,GLUE_Y,8.5,Set.of(),0,0,false);player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                }
                case 4->{
                    if(observation==null)return;
                    close(observation.position().y,baseline.position().y,.005,"30-tick server resting depth");
                    close(game.player.getY(),baselineClientY,.02,"30-tick client resting depth");
                    close(observation.totalSink(),baseline.totalSink(),.000001,"rest does not create sink cost");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_GLUE_REST_COMPLETE ticks=30 floorGap={} serverY={} clientY={} depth={}",
                            observation.position().y-249,observation.position().y,game.player.getY(),observation.depth());
                    lastAcceptedAnimation=observation.animation();pressF();phase=34;ticks=0;observation=null;
                }
                case 34->{
                    if(ticks<20)return;probe(game);if(observation==null)return;
                    close(observation.effort(),baseline.effort(),.000001,"disabled F never reaches server effort");
                    close(observation.totalSink(),baseline.totalSink(),.000001,"disabled F cannot deepen glue");
                    require(observation.animation()==lastAcceptedAnimation,"disabled F cannot animate on server");
                    MFQM.LOGGER.info("MFQM_DISABLED_STRUGGLE_NETWORK_COMPLETE ticks=20 effortUnchanged=true sinkUnchanged=true animationUnchanged=true");
                    ModConfig.CLIENT.enableStruggleKey.set(true);
                    require(!MfqmClient.takeStrugglePress(),"enabling after disabled input has no queued replay");
                    pressF();phase=5;ticks=0;observation=null;
                }
                case 5->{
                    probe(game);
                    if(observation==null || observation.animation()<=lastAcceptedAnimation)return;
                    lastAcceptedAnimation=observation.animation();acceptedPresses++;
                    require(observation.effort()>=acceptedPresses,"physical F press arrived and was accepted by server");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_REAL_F_ACCEPTED count={} effort={} totalSink={} serverY={} clientY={}",
                            acceptedPresses,observation.effort(),observation.totalSink(),observation.position().y,game.player.getY());
                    phase=acceptedPresses>=5?7:6;ticks=0;observation=null;
                }
                case 6->{
                    if(ticks<16)return;
                    pressF();phase=5;ticks=0;observation=null;
                }
                case 7->{if(ticks>=15)fresh(game,8);}
                case 8->{
                    if(observation==null)return;
                    afterStruggle=observation;afterStruggleClientY=game.player.getY();
                    double serverSink=baseline.position().y-afterStruggle.position().y;
                    double clientSink=baselineClientY-afterStruggleClientY;
                    require(afterStruggle.effort()>=4 && acceptedPresses==5,"five real F presses cross glue effort threshold");
                    require(serverSink>.02 && clientSink>.02,"accepted struggle actually sinks both server and client");
                    close(serverSink,afterStruggle.totalSink()-baseline.totalSink(),.015,"server consumes each struggle sink cost once");
                    close(clientSink,serverSink,.025,"client consumes same cumulative accepted sink cost");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_GLUE_F_SINK_COMPLETE realF={} effort={} serverSink={} clientSink={} totalSinkDelta={}",
                            acceptedPresses,afterStruggle.effort(),serverSink,clientSink,afterStruggle.totalSink()-baseline.totalSink());
                    phase=9;ticks=0;observation=null;
                }
                case 9->{if(ticks>=60)fresh(game,10);}
                case 10->{
                    if(observation==null)return;
                    close(observation.position().y,afterStruggle.position().y,.005,"resting server cannot replay cumulative sink");
                    close(observation.depth(),afterStruggle.depth(),.005,"resting server depth remains stable");
                    close(game.player.getY(),afterStruggleClientY,.02,"resting client cannot replay cumulative sink snapshots");
                    close(observation.totalSink(),afterStruggle.totalSink(),.000001,"no new cumulative sink while resting");
                    close(observation.effort(),afterStruggle.effort(),.000001,"resting creates no effort");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_GLUE_NO_REPLAY_COMPLETE ticks=60 serverY={} clientY={} depth={} totalSink={}",
                            observation.position().y,game.player.getY(),observation.depth(),observation.totalSink());
                    game.player.input=input(false,true);phase=11;ticks=0;observation=null;
                }
                case 11->{if(ticks>=16)fresh(game,12);}
                case 12->{
                    if(observation==null)return;
                    rescueBaseline=observation;rescueClientY=game.player.getY();maxRescueClientY=rescueClientY;
                    require(rescueBaseline.jumping() && rescueBaseline.time()-rescueBaseline.inputTick()<10,"held real jump input reaches server");
                    phase=13;ticks=0;serverReady=false;observation=null;
                    execute(game,player->{QuicksandPhysics.rescue(player,player.position().add(0,2,0),.08);serverReady=true;});
                }
                case 13->{
                    maxRescueClientY=Math.max(maxRescueClientY,game.player.getY());
                    if(serverReady && ticks>=20)fresh(game,14);
                }
                case 14->{
                    if(observation==null)return;
                    afterRescue=observation;afterRescueClientY=game.player.getY();
                    require(afterRescue.position().y-rescueBaseline.position().y>.15 && afterRescueClientY-rescueClientY>.15,
                            "one real server rescue raises both bodies while jump input remains held");
                    require(afterRescue.rescueSequence()==rescueBaseline.rescueSequence()+1 && afterRescue.rescueTicks()==0,"one rescue expires normally");
                    require(afterRescue.material().equals("glue"),"rescue remains inside deep glue for resting-window validation");
                    close(afterRescueClientY,afterRescue.position().y,.05,"rescue client/server height agreement");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_RESCUE_COMPLETE strength=.08 calls=1 heldJump=true serverRise={} clientRise={} maxClientY={} rescueSequence={} rescueTicks={}",
                            afterRescue.position().y-rescueBaseline.position().y,afterRescueClientY-rescueClientY,maxRescueClientY,afterRescue.rescueSequence(),afterRescue.rescueTicks());
                    game.player.input=input(false,false);phase=15;ticks=0;observation=null;
                }
                case 15->{if(ticks>=60)fresh(game,16);}
                case 16->{
                    if(observation==null)return;
                    close(observation.position().y,afterRescue.position().y,.005,"server rescue window stops applying lift");
                    close(game.player.getY(),afterRescueClientY,.02,"same-sequence client snapshots cannot restart rescue window");
                    close(observation.totalSink(),afterRescue.totalSink(),.000001,"rescue rest produces no extra sink");
                    require(observation.rescueTicks()==0 && observation.rescueSequence()==afterRescue.rescueSequence(),"expired single rescue is not renewed");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_RESCUE_REST_COMPLETE ticks=60 serverY={} clientY={} sequence={} expired=true",
                            observation.position().y,game.player.getY(),observation.rescueSequence());
                    phase=17;ticks=0;serverReady=false;observation=null;
                    execute(game,player->{dryTeleport(player);serverReady=true;});
                }
                case 17->{if(serverReady && ticks>=70)fresh(game,18);}
                case 18->{
                    if(observation==null)return;
                    dryBaseline=observation;dryClientStart=game.player.position();
                    require(dryBaseline.material().isEmpty() && dryBaseline.connections()==0,"dry contact and anchors naturally clear");
                    close(dryBaseline.speed(),.1,.00001,"server walking attribute restored");
                    close(dryBaseline.gravity(),.08,.00001,"server glue gravity modifier removed");
                    close(game.player.getAttributeValue(Attributes.GRAVITY),.08,.00001,"client glue gravity modifier removed");
                    game.player.input=input(true,false);phase=19;ticks=0;observation=null;
                }
                case 19->{
                    if(ticks==60)game.player.input=input(false,false);
                    // After genuine 60-tick W input, let ordinary inertia settle
                    // before comparing an asynchronous server sample to client now.
                    if(ticks>=68)fresh(game,20);
                }
                case 20->{
                    if(observation==null)return;
                    double serverWalk=observation.position().subtract(dryBaseline.position()).horizontalDistance();
                    double clientWalk=game.player.position().subtract(dryClientStart).horizontalDistance();
                    require(serverWalk>=8 && clientWalk>=8,"leaving glue restores ordinary unassisted W walking");
                    require(observation.material().isEmpty() && observation.connections()==0,"dry lane stays outside all sticky media");
                    require(game.player.position().distanceTo(observation.position())<.5,"ordinary movement client/server agreement");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_DRY_WALK_COMPLETE ticks=60 serverWalk={} clientWalk={} serverEnd={} clientEnd={} speed={} gravity={}",
                            serverWalk,clientWalk,observation.position(),game.player.position(),observation.speed(),observation.gravity());
                    phase=21;ticks=0;serverReady=false;observation=null;
                    execute(game,player->{
                        var level=player.level();
                        for(int x=98;x<=110;x++)for(int z=2;z<=14;z++) {
                            level.setBlock(new BlockPos(x,250,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                            boolean wall=x==98 || x==110 || z==2 || z==14;
                            // A shallow pool lets the body's normal suction settle
                            // it onto the floor. Deep surface tethers can support
                            // a suspended body, which cannot receive a ground jump arc.
                            for(int y=251;y<=253;y++)level.setBlock(new BlockPos(x,y,z),
                                    wall?Blocks.SMOOTH_STONE.defaultBlockState():y==251?ModBlocks.byId("honey").defaultBlockState():Blocks.AIR.defaultBlockState(),2);
                        }
                        preparePlayer(player);player.teleportTo(level,104.5,251.05,8.5,Set.of(),0,0,false);
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;
                    });
                }
                case 21->{if(serverReady && ticks>=70)fresh(game,22);}
                case 22->{
                    if(observation==null)return;
                    knockbackBaseline=observation;knockbackClientStartY=game.player.getY();
                    MFQM.LOGGER.info("MFQM_VISCOSITY_KNOCKBACK_READY serverY={} clientY={} serverGround={} clientGround={} depth={}",
                            knockbackBaseline.position().y,game.player.getY(),knockbackBaseline.onGround(),game.player.onGround(),knockbackBaseline.depth());
                    require(knockbackBaseline.material().equals("honey") && knockbackBaseline.onGround() && game.player.onGround(),
                            "honey naturally settles onto the floor before real upward knockback");
                    close(knockbackBaseline.position().y,251,.005,"server settled naturally at honey floor");
                    maxKnockbackClientY=knockbackClientStartY;maxKnockbackServerY=knockbackBaseline.position().y;trackingKnockback=true;
                    phase=23;ticks=0;serverReady=false;observation=null;
                    execute(game,player->{
                        // The normal damage chain invokes vanilla knockback and sends the velocity to the player itself.
                        require(player.onGround(),"actual server knockback precondition");
                        var attacker=net.minecraft.world.entity.EntityType.PIG.create(player.level(),net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                        require(attacker!=null,"temporary genuine attack source");attacker.setNoAi(true);
                        attacker.setPos(player.getX()+1,player.getY(),player.getZ());
                        require(player.level().addFreshEntity(attacker),"insert actual attack source");
                        try{require(player.hurtServer(player.level(),player.damageSources().mobAttack(attacker),1),"one genuine damage event invokes knockback");}
                        finally{attacker.discard();}
                        serverReady=true;
                    });
                }
                case 23->{
                    maxKnockbackClientY=Math.max(maxKnockbackClientY,game.player.getY());
                    if(serverReady && ticks>=30) {
                        if(!probing)fresh(game,24);
                        return;
                    }
                    probe(game);
                }
                case 24->{
                    if(observation==null)return;
                    afterKnockback=observation;
                    double serverRise=maxKnockbackServerY-knockbackBaseline.position().y;
                    double clientRise=maxKnockbackClientY-knockbackClientStartY;
                    require(serverRise>.1 && clientRise>.1,"true knockback actually lifts both bodies");
                    require(serverRise<1.6 && clientRise<1.6,"brief external knockback produces a bounded arc");
                    require(afterKnockback.externalSequence()==knockbackBaseline.externalSequence()+1 && afterKnockback.externalTicks()==0,
                            "one actual knockback emits one sequence and expires its four-tick preservation window");
                    require(afterKnockback.material().equals("honey"),"external impulse remains in contact with honey");
                    require(afterKnockback.velocity().y<=.001 && game.player.getDeltaMovement().y<=.001,
                            "expired external impulse no longer supplies positive vertical velocity");
                    MFQM.LOGGER.info("MFQM_VISCOSITY_KNOCKBACK_ARC_COMPLETE ticks=30 strength=.4 attacks=1 realDamage=true clientFlying=false serverFlying=false groundedNaturally=true serverRise={} clientRise={} serverEnd={} clientEnd={} serverVelocity={} clientVelocity={} externalSequence={} externalTicks={}",
                            serverRise,clientRise,afterKnockback.position(),game.player.position(),afterKnockback.velocity(),game.player.getDeltaMovement(),
                            afterKnockback.externalSequence(),afterKnockback.externalTicks());
                    phase=25;ticks=0;observation=null;
                }
                case 25->{
                    maxKnockbackClientY=Math.max(maxKnockbackClientY,game.player.getY());
                    if(ticks>=60)fresh(game,26);
                }
                case 26->{
                    if(observation==null)return;
                    require(observation.externalSequence()==afterKnockback.externalSequence() && observation.externalTicks()==0,
                            "repeated snapshots cannot renew expired external-motion sequence");
                    require(observation.position().y<=afterKnockback.position().y+.02 && game.player.getY()<=afterKnockback.position().y+.03,
                            "sixty further idle ticks cannot continue an expired external lift");
                    require(maxKnockbackClientY-knockbackClientStartY<1.6 && observation.material().equals("honey"),
                            "external impulse never becomes continuing flight out of honey");
                    require(observation.velocity().y<=.001 && game.player.getDeltaMovement().y<=.001,"external lift remains expired on both sides");
                    trackingKnockback=false;
                    MFQM.LOGGER.info("MFQM_VISCOSITY_KNOCKBACK_EXPIRED_COMPLETE idleTicks=60 serverEnd={} clientEnd={} maxClientRise={} externalSequence={} externalTicks={}",
                            observation.position(),game.player.position(),maxKnockbackClientY-knockbackClientStartY,observation.externalSequence(),observation.externalTicks());
                    MFQM.LOGGER.info("MFQM_VISCOSITY_ACTION_CHECKS_COMPLETE suspendedRest=true realF=5 sinkOnce=true noReplay=true rescueOnce=true heldJump=true rescueExpires=true dryWalk=true externalKnockback=true externalExpires=true screenshots=0");
                    restore(game);
                    BoundedAdhesionClientChecks.start(game);
                }
                default->throw new IllegalStateException("invalid action phase "+phase);
            }
        } catch(Throwable failure){fail(game,failure);}
    }

    private static void preparePlayer(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);player.getAbilities().flying=false;player.onUpdateAbilities();
        player.setHealth(player.getMaxHealth());player.getFoodData().setFoodLevel(20);player.removeAllEffects();
        for(var slot:EquipmentSlot.values())player.setItemSlot(slot,ItemStack.EMPTY);
    }
    private static void dryTeleport(ServerPlayer player){player.teleportTo(player.level(),102.5,253,-3.5,Set.of(),-90,0,false);player.setDeltaMovement(Vec3.ZERO);}
    private static ClientInput input(boolean forward,boolean jump){return new ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(forward,false,false,false,jump,false,false);moveVector=forward?new Vec2(0,1):Vec2.ZERO;}};}
    private static void pressF(){MfqmClient.key(new InputEvent.Key(F,GLFW.GLFW_PRESS));MfqmClient.key(new InputEvent.Key(F,GLFW.GLFW_RELEASE));}
    private static void fresh(Minecraft game,int next){require(!probing,"fresh observation has no pending older task");observation=null;phase=next;ticks=0;probe(game);}
    private static void probe(Minecraft game) {
        if(probing)return;probing=true;
        execute(game,player->{var state=QuicksandPhysics.state(player);
            require(!player.getAbilities().flying,"server action cases never enable flight");
            var sample=new Observation(player.position(),state.material,state.depth,state.struggle.effort(),state.totalStruggleSink,
                    state.struggleAnimationTick,state.rescueSequence,state.rescueTicks,state.adhesiveConnections,state.jumpInput,state.inputTick,
                    player.level().getGameTime(),player.getAttributeValue(Attributes.MOVEMENT_SPEED),player.getAttributeValue(Attributes.GRAVITY),
                    player.getDeltaMovement(),player.onGround(),state.externalMotionSequence,state.externalMotionTicks);
            // Both publication and pending state are owned by the client thread; no stale cross-phase result is consumed.
            game.execute(()->{observation=sample;if(trackingKnockback)maxKnockbackServerY=Math.max(maxKnockbackServerY,sample.position().y);probing=false;});
        });
    }
    private static void execute(Minecraft game,Consumer<ServerPlayer> action) {
        var server=game.getSingleplayerServer();var id=game.player.getUUID();require(server!=null,"integrated server available");
        server.execute(()->{try{var player=server.getPlayerList().getPlayer(id);require(player!=null,"fixture player available");action.accept(player);}
            catch(Throwable failure){game.execute(()->fail(game,failure));}});
    }
    private static void close(double actual,double expected,double tolerance,String message){require(Math.abs(actual-expected)<=tolerance,message+" expected="+expected+" actual="+actual);}
    private static void fail(Minecraft game,Throwable failure){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED viscosity actions: "+failure.getMessage(),failure);finish(game);}
    private static void finish(Minecraft game) {
        restore(game);game.stop();
    }
    private static void restore(Minecraft game) {
        if(!running)return;
        running=false;probing=false;trackingKnockback=false;
        if(game.player!=null && previousInput!=null)game.player.input=previousInput;previousInput=null;
        ModConfig.SERVER.creativeGroundPhysics.set(oldCreativeGround);ModConfig.SERVER.hotTar.set(oldHotTar);
        ModConfig.SERVER.mudTentacles.set(oldMudTentacles);ModConfig.SERVER.tentaclesInFlesh.set(oldFleshTentacles);
        ModConfig.CLIENT.enableStruggleKey.set(oldStruggleKey);MfqmClient.takeStrugglePress();
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Viscosity action check failed: "+message);}
    private ViscosityActionChecks(){}
}
