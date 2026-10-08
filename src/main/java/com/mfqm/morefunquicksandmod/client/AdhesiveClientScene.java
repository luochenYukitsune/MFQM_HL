package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.block.StickyBoardBlock;
import com.mfqm.morefunquicksandmod.entity.StuckBootsEntity;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** Actual client/network/server actions in the copied validation save, never a user's normal world. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class AdhesiveClientScene {
    private static final String[] SCREENSHOTS={"mfqm-adhesive-glue.png","mfqm-adhesive-deep.png","mfqm-adhesive-board.png","mfqm-adhesive-hand.png"};
    private record Observation(double effort,long animation,int connections,String material,double depth,
                               ItemStack main,ItemStack off,ItemStack boots,double y) {}
    private static boolean running,oldFirstPerson,oldCreativeGroundPhysics;
    private static double oldBootChance;
    private static volatile double restingY;
    private static int sample,phase,ticks,acceptedPresses,boardPressRequests;
    private static volatile boolean serverReady,probing;
    private static volatile Observation observation;
    private static Observation baseline,accepted;
    private static net.minecraft.client.player.ClientInput previousInput;
    private static double jumpStartY,maxJumpY;
    private static final KeyEvent F=new KeyEvent(GLFW.GLFW_KEY_F,0,0);

    public static void start(Minecraft game) {
        if(!Boolean.getBoolean("mfqm.clientChecks") || !Boolean.getBoolean("mfqm.textureChecks") || running)return;
        require(game.getSingleplayerServer()!=null && game.player!=null,"requires copied singleplayer save");
        running=true;sample=0;acceptedPresses=0;
        oldFirstPerson=ModConfig.CLIENT.forceFirstPerson.get();oldBootChance=ModConfig.SERVER.bootLossChance.get();
        oldCreativeGroundPhysics=ModConfig.SERVER.creativeGroundPhysics.get();
        // Deterministic equipment validation; no file save and both values are restored at completion.
        ModConfig.CLIENT.forceFirstPerson.set(false);ModConfig.SERVER.bootLossChance.set(0.0);
        execute(game,player->{
            var level=player.level();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            for(var old:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(34,249,-8,67,263,9),
                    entity->entity.getTags().contains("mfqm_adhesive_scene")))old.discard();
            for(int x=34;x<=66;x++)for(int z=-8;z<=8;z++) {
                var floor=new BlockPos(x,252,z);level.getChunk(floor);
                for(int dy=1;dy<=9;dy++)level.setBlock(floor.above(dy),Blocks.AIR.defaultBlockState(),2);
                level.setBlock(floor,((x+z)%2==0?Blocks.SMOOTH_STONE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);
            }
            for(int center:new int[]{43,51})for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                boolean edge=Math.abs(dx)==3 || Math.abs(dz)==3;
                int bottom=center==43?252:250;
                var foot=new BlockPos(center+dx,bottom,dz);
                level.setBlock(foot,Blocks.SMOOTH_STONE.defaultBlockState(),2);
                for(int y=bottom+1;y<=253;y++)level.setBlock(new BlockPos(center+dx,y,dz),
                        edge?Blocks.SMOOTH_STONE.defaultBlockState():ModBlocks.byId("glue").defaultBlockState(),2);
            }
            for(int x=59;x<=61;x++)for(int z=-1;z<=1;z++)level.setBlock(new BlockPos(x,253,z),StickyBoardBlock.coatedState(7),3);
            var cow=EntityType.COW.create(level,EntitySpawnReason.TRIGGERED);
            require(cow!=null,"cow creation");cow.setNoAi(true);cow.setPersistenceRequired();cow.setPos(61.25,253.0625,.7);
            cow.setYRot(180);cow.yBodyRot=180;cow.addTag("mfqm_adhesive_scene");require(level.addFreshEntity(cow),"cow insertion");
            var pig=EntityType.PIG.create(level,EntitySpawnReason.TRIGGERED);
            require(pig!=null,"boots donor creation");pig.setPos(61.5,253.0625,-.5);
            var boots=fixtureBoots();pig.setItemSlot(EquipmentSlot.FEET,boots);
            require(StuckBootsEntity.tryLeaveBehind(pig,new BlockPos(61,253,-1)),"actual board boots transfer");pig.discard();
            var retainedBoots=level.getEntitiesOfClass(StuckBootsEntity.class,new net.minecraft.world.phys.AABB(61,253,-1,62,254,0));
            require(retainedBoots.size()==1,"one visible retained boots entity");
            for(var retained:retainedBoots) {
                require(ItemStack.matches(boots,retained.stack()),"retained boot item components");retained.addTag("mfqm_adhesive_scene");
            }
            MFQM.LOGGER.info("MFQM_ADHESIVE_FIXTURE_BUILT survival=true gluePools=2 boardTiles=9 cow=true retainedBootComponents=true");
        });
        reset(game);
    }

    private static ItemStack fixtureBoots() {
        var boots=new ItemStack(Items.DIAMOND_BOOTS);boots.setDamageValue(17);
        boots.set(DataComponents.CUSTOM_NAME,Component.literal("MFQM validation boots"));return boots;
    }
    private static void reset(Minecraft game) {
        phase=0;ticks=0;serverReady=false;observation=null;baseline=null;accepted=null;
        execute(game,player->{
            player.setGameMode(GameType.SURVIVAL);player.getAbilities().flying=false;
            player.setHealth(player.getMaxHealth());player.getFoodData().setFoodLevel(20);
            player.setItemSlot(EquipmentSlot.FEET,fixtureBoots());
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.PAPER));
            player.teleportTo(player.level(),36.5,253,.5,java.util.Set.of(),0,0,false);
            player.setDeltaMovement(Vec3.ZERO);serverReady=true;
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!running)return;
        var game=Minecraft.getInstance();
        if(game.player==null || game.level==null)return;
        try {
            if(++ticks>600)throw new IllegalStateException("scene timeout sample="+sample+" phase="+phase);
            switch(phase) {
                case 0->{
                    // Dry ground ends the previous real trapping episode instead of editing its effort/animation.
                    if(!serverReady || ticks<65)return;
                    serverReady=false;phase=1;ticks=0;
                    game.options.setCameraType(sample==3?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);
                    game.options.hideGui=sample!=3; // Vanilla hides first-person hands together with the HUD.
                    execute(game,player->{
                        double x=sample==0 || sample==3?43.2:sample==1?51.2:59.7;
                        double y=sample==1?252.75:sample==2?253.0625:253.75;
                        if(sample==3){player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);}
                        player.teleportTo(player.level(),x,y,.5,java.util.Set.of(),0,sample==3?38:0,false);
                        restingY=y;
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;
                    });
                }
                case 1->{
                    if(!serverReady || ticks<30)return;
                    // Move within the same medium after anchors formed: geometry now has real nonzero stretch.
                    phase=2;ticks=0;observation=null;
                    execute(game,player->{
                        if(sample!=2)require(Math.abs(player.getY()-restingY)<.04,"actual resting glue position changed without struggle: start="+restingY+" current="+player.getY());
                        player.teleportTo(player.level(),player.getX()+.65,player.getY(),player.getZ(),java.util.Set.of(),0,sample==3?38:0,false);player.setDeltaMovement(Vec3.ZERO);observation=observe(player);
                    });
                }
                case 2->{
                    if(observation==null || ticks<5)return;
                    baseline=observation;
                    require(baseline.connections()>0,"real anchors present sample="+sample);
                    require(baseline.material().equals(sample==2?"sticky_board":"glue"),"real contact sample="+sample+" was="+baseline.material());
                    require(!baseline.boots().isEmpty(),"standing retains boots");
                    phase=3;ticks=0;observation=null;press();
                }
                case 3->{
                    probe(game);
                    if(observation==null || observation.animation()<=baseline.animation())return;
                    accepted=observation;
                    require(accepted.effort()>baseline.effort(),"F payload changes server effort");
                    require(ItemStack.matches(baseline.main(),accepted.main()) && ItemStack.matches(baseline.off(),accepted.off()),"F does not swap hands");
                    require(ItemStack.matches(baseline.boots(),accepted.boots()),"zero-probability boot roll preserves components");
                    acceptedPresses++;phase=4;ticks=0;
                    MFQM.LOGGER.info("MFQM_ADHESIVE_REAL_INPUT sample={} acceptedF={} effort={} connections={} depth={} material={} handsPreserved=true",sample,acceptedPresses,accepted.effort(),accepted.connections(),accepted.depth(),accepted.material());
                }
                case 4->{
                    var state=QuicksandPhysics.state(game.player);
                    long elapsed=game.level.getGameTime()-state.struggleAnimationTick;
                    if(state.struggleAnimationTick==accepted.animation() && elapsed>=4 && elapsed<=8) {
                        phase=5;ticks=0;
                        Screenshot.grab(game.gameDirectory,SCREENSHOTS[sample],game.getMainRenderTarget(),1,message->game.execute(()->{
                            MFQM.LOGGER.info("MFQM_ADHESIVE_SCENE_SAVED sample={} {}",sample,message.getString());
                            // Repeated OS events use the same public input entry point. They must not create requests.
                            for(int n=0;n<8;n++)MfqmClient.key(new InputEvent.Key(F,GLFW.GLFW_REPEAT));
                            phase=6;ticks=0;observation=null;
                        }));
                    } else if(ticks>30) {
                        // A slow renderer may miss the first action. Repeat a real short tap after the cooldown.
                        baseline=accepted;accepted=null;observation=null;phase=3;ticks=0;press();
                    }
                }
                case 5->{} // screenshot completion callback advances the scene
                case 6->{
                    if(ticks<25)return;probe(game);
                    if(observation==null)return;
                    require(observation.animation()==accepted.animation(),"OS REPEAT produces no extra accepted animation");
                    require(observation.effort()==accepted.effort(),"OS REPEAT produces no extra server effort");
                    require(ItemStack.matches(accepted.main(),observation.main()) && ItemStack.matches(accepted.off(),observation.off()),"hands still preserved after repeats");
                    MFQM.LOGGER.info("MFQM_ADHESIVE_REPEAT_REJECTED sample={} effort={} animation={}",sample,observation.effort(),observation.animation());
                    if(++sample==SCREENSHOTS.length) {
                        // A real sustained jump goes through LocalPlayer.applyInput and vanilla
                        // fluid travel, rather than calling the server physics routine directly.
                        phase=10;ticks=0;serverReady=false;
                        execute(game,player->{player.teleportTo(player.level(),36.5,253,.5,java.util.Set.of(),0,0,false);
                            player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                    } else reset(game);
                }
                case 10->{
                    if(!serverReady || ticks<65)return;
                    phase=11;ticks=0;serverReady=false;
                    execute(game,player->{player.teleportTo(player.level(),51.2,252.75,.5,java.util.Set.of(),0,0,false);
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                }
                case 11->{
                    if(!serverReady || ticks<30)return;
                    observation=null;probe(game);phase=12;ticks=0;
                }
                case 12->{
                    if(observation==null)return;
                    require(observation.material().equals("glue"),"sustained-jump probe starts inside deep glue");
                    baseline=observation;jumpStartY=baseline.y();maxJumpY=game.player.getY();
                    previousInput=game.player.input;
                    game.player.input=heldJumpInput();
                    phase=13;ticks=0;observation=null;
                }
                case 13->{
                    maxJumpY=Math.max(maxJumpY,game.player.getY());probe(game);
                    if(ticks<60 || observation==null)return;
                    MFQM.LOGGER.info("MFQM_ADHESIVE_JUMP_OBSERVED startY={} serverY={} maxClientY={} effortBefore={} effortAfter={}",
                            jumpStartY,observation.y(),maxJumpY,baseline.effort(),observation.effort());
                    require(maxJumpY>jumpStartY+.01 && maxJumpY<=jumpStartY+.30 && observation.y()<=jumpStartY+.04,
                            "holding space must not float out of glue: start="+jumpStartY+" server="+observation.y()+" maxClient="+maxJumpY);
                    require(observation.effort()>baseline.effort(),"suppressed physical jump retains real struggle intent on the server");
                    restoreInput(game);
                    phase=14;ticks=0;serverReady=false;
                    execute(game,player->{ModConfig.SERVER.creativeGroundPhysics.set(false);player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;
                        player.teleportTo(player.level(),51.2,252.75,.5,java.util.Set.of(),0,0,false);
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                }
                case 14->{
                    if(!serverReady || ticks<30 || !game.player.isCreative())return;
                    observation=null;probe(game);phase=15;ticks=0;
                }
                case 15->{
                    if(observation==null)return;
                    baseline=observation;jumpStartY=baseline.y();maxJumpY=game.player.getY();
                    previousInput=game.player.input;game.player.input=heldJumpInput();phase=16;ticks=0;observation=null;
                }
                case 16->{
                    maxJumpY=Math.max(maxJumpY,game.player.getY());probe(game);
                    if(ticks<20 || observation==null)return;
                    require(maxJumpY>jumpStartY+.1,"creative glue immunity preserves real physical jumping");
                    require(observation.connections()==0 && observation.effort()==0,"creative jumping does not create glue restraint or struggle cost");
                    MFQM.LOGGER.info("MFQM_ADHESIVE_CREATIVE_JUMP_PRESERVED startY={} maxClientY={} connections={} effort={}",
                            jumpStartY,maxJumpY,observation.connections(),observation.effort());
                    restoreInput(game);
                    phase=17;ticks=0;serverReady=false;
                    execute(game,player->{ModConfig.SERVER.creativeGroundPhysics.set(oldCreativeGroundPhysics);player.setGameMode(GameType.SURVIVAL);player.getAbilities().flying=false;
                        player.teleportTo(player.level(),36.5,253,.5,java.util.Set.of(),0,0,false);
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                }
                case 17->{
                    if(!serverReady || ticks<65)return;
                    phase=18;ticks=0;serverReady=false;
                    execute(game,player->{
                        // The photographed cow remains part of the earlier rendered scene.
                        // Its normal self-rescue also consumes coating, so this later single-
                        // player boundary test must give it an independent supported board.
                        var independentBoard=new BlockPos(65,253,4);
                        player.level().setBlock(independentBoard.below(),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                        player.level().setBlock(independentBoard,StickyBoardBlock.coatedState(7),3);
                        for(var cow:player.level().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,
                                new net.minecraft.world.phys.AABB(58,252,-2,63,256,3),other->other.getTags().contains("mfqm_adhesive_scene"))) {
                            cow.setPos(65.5,253.0625,4.5);cow.setDeltaMovement(Vec3.ZERO);cow.setOnGround(true);
                        }
                        player.level().setBlock(new BlockPos(60,253,0),StickyBoardBlock.coatedState(7),3);
                        player.teleportTo(player.level(),60.5,253.0625,.5,java.util.Set.of(),0,0,false);
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                }
                case 18->{
                    if(!serverReady || ticks<30)return;
                    observation=null;probe(game);phase=19;ticks=0;
                }
                case 19->{
                    if(observation==null)return;
                    require(observation.material().equals("sticky_board"),"board sustained-jump probe starts on a fresh coated board");
                    baseline=observation;jumpStartY=baseline.y();maxJumpY=game.player.getY();
                    previousInput=game.player.input;game.player.input=heldJumpInput();phase=20;ticks=0;observation=null;
                }
                case 20->{
                    maxJumpY=Math.max(maxJumpY,game.player.getY());probe(game);
                    if(ticks<40 || observation==null)return;
                    MFQM.LOGGER.info("MFQM_ADHESIVE_BOARD_JUMP_OBSERVED startY={} serverY={} maxClientY={} effortBefore={} effortAfter={}",
                            jumpStartY,observation.y(),maxJumpY,baseline.effort(),observation.effort());
                    require(maxJumpY>jumpStartY+.1 && maxJumpY<=jumpStartY+.30 && observation.y()<=jumpStartY+.04,
                            "fresh board allows a finite short hop before weakening: start="+jumpStartY+" server="+observation.y()+" maxClient="+maxJumpY);
                    require(observation.effort()>baseline.effort(),"suppressed board jump preserves raw struggle intent");
                    restoreInput(game);
                    phase=21;ticks=0;observation=null;boardPressRequests=0;
                }
                case 21->{
                    if(ticks%13==1){press();boardPressRequests++;}probe(game);
                    if(ticks%100==0) {
                        int requests=boardPressRequests,elapsed=ticks;
                        execute(game,player->{var state=QuicksandPhysics.state(player);
                            var block=player.level().getBlockState(new BlockPos(60,253,0));
                            MFQM.LOGGER.info("MFQM_ADHESIVE_BOARD_WEAKENING_PROGRESS clientTicks={} rawF={} effort={} material={} charge={} anchors={} food={} y={} episodeBoard={} previousCharge={} lastAccepted={} serverTime={}",
                                    elapsed,requests,state.struggle.effort(),state.material,
                                    com.mfqm.morefunquicksandmod.gameplay.AdhesionController.charge(block),state.anchors.size(),player.getFoodData().getFoodLevel(),player.getY(),
                                    state.episodeBoard,state.previousBoardCharge,state.struggle.lastAcceptedTick(),player.level().getGameTime());});
                        execute(game,player->{var state=QuicksandPhysics.state(player);
                            if(!StickyBoardBlock.isCoated(player.level().getBlockState(new BlockPos(60,253,0))) && state.struggle.effort()<34) {
                                var cowStates=player.level().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,
                                        new net.minecraft.world.phys.AABB(58,252,-2,63,256,3),cow->cow.getTags().contains("mfqm_adhesive_scene"))
                                        .stream().map(cow->{var other=QuicksandPhysics.state(cow);return "cowBoard="+other.episodeBoard+",cowEffort="+other.struggle.effort()+",consumedSteps="+other.consumedBoardSteps;}).toList();
                                throw new IllegalStateException("sharedFixtureWear: board depleted before isolated player reached 34 effort; playerEffort="+state.struggle.effort()+" rawF="+requests+" "+cowStates);
                            }
                        });
                    }
                    if(observation==null || observation.effort()<34)return;
                    require(observation.connections()==0,"actual F weakening releases all board anchors");
                    require(StickyBoardBlock.isCoated(game.level.getBlockState(new BlockPos(60,253,0))),"released-board regression retains some coating");
                    phase=22;ticks=0;
                }
                case 22->{
                    if(ticks<10)return;
                    observation=null;probe(game);phase=23;ticks=0;
                }
                case 23->{
                    if(observation==null)return;
                    jumpStartY=observation.y();maxJumpY=game.player.getY();
                    previousInput=game.player.input;game.player.input=heldJumpInput();phase=24;ticks=0;observation=null;
                }
                case 24->{
                    maxJumpY=Math.max(maxJumpY,game.player.getY());probe(game);
                    if(ticks<25 || observation==null)return;
                    require(maxJumpY>jumpStartY+.2,"weakening permits normal physical jumps on a still-coated board");
                    require(observation.effort()>=34 && observation.connections()==0,
                            "landing on the same weakened coating keeps the released episode instead of trapping again");
                    MFQM.LOGGER.info("MFQM_ADHESIVE_RELEASED_BOARD_JUMP_PRESERVED startY={} maxClientY={} effort={} connections={}",
                            jumpStartY,maxJumpY,observation.effort(),observation.connections());
                    restoreInput(game);phase=25;ticks=0;serverReady=false;
                    execute(game,player->{player.teleportTo(player.level(),36.5,253,.5,java.util.Set.of(),0,0,false);
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                }
                case 25->{
                    if(!serverReady || ticks<65)return;
                    phase=26;ticks=0;serverReady=false;
                    execute(game,player->{player.level().setBlock(new BlockPos(60,253,0),StickyBoardBlock.coatedState(0),3);
                        player.teleportTo(player.level(),60.5,253.0625,.5,java.util.Set.of(),0,0,false);
                        player.setDeltaMovement(Vec3.ZERO);serverReady=true;});
                }
                case 26->{
                    if(!serverReady || ticks<15)return;
                    jumpStartY=game.player.getY();maxJumpY=jumpStartY;
                    previousInput=game.player.input;game.player.input=heldJumpInput();phase=27;ticks=0;observation=null;
                }
                case 27->{
                    maxJumpY=Math.max(maxJumpY,game.player.getY());probe(game);
                    if(ticks<25 || observation==null)return;
                    require(maxJumpY>jumpStartY+.2 && observation.connections()==0 && observation.effort()==0,
                            "depleted board permits normal jumping without starting another trapping episode");
                    MFQM.LOGGER.info("MFQM_ADHESIVE_DEPLETED_BOARD_JUMP_PRESERVED startY={} maxClientY={} effort={} connections={}",
                            jumpStartY,maxJumpY,observation.effort(),observation.connections());
                    restoreInput(game);
                    MFQM.LOGGER.info("MFQM_ADHESIVE_SCENE_COMPLETE screenshots=4 acceptedF={} realNetwork=true survival=true repeatRejected=true sustainedJump=true creativeJump=true boardJump=true releasedBoardJump=true depletedBoardJump=true",acceptedPresses);
                    finish(game);
                }
                default->throw new IllegalStateException("invalid scene phase");
            }
        } catch(Throwable failure){fail(game,failure);}
    }
    private static void press(){MfqmClient.key(new InputEvent.Key(F,GLFW.GLFW_PRESS));MfqmClient.key(new InputEvent.Key(F,GLFW.GLFW_RELEASE));}
    private static void probe(Minecraft game) {
        if(probing)return;probing=true;
        execute(game,player->{try{observation=observe(player);}finally{probing=false;}});
    }
    private static Observation observe(ServerPlayer player) {
        var state=QuicksandPhysics.state(player);
        return new Observation(state.struggle.effort(),state.struggleAnimationTick,state.adhesiveConnections,state.material,state.depth,
                player.getMainHandItem().copy(),player.getOffhandItem().copy(),player.getItemBySlot(EquipmentSlot.FEET).copy(),player.getY());
    }
    private static void execute(Minecraft game,java.util.function.Consumer<ServerPlayer> action) {
        var server=game.getSingleplayerServer();var id=game.player.getUUID();
        require(server!=null,"integrated server available");
        server.execute(()->{try{var player=server.getPlayerList().getPlayer(id);require(player!=null,"fixture player available");action.accept(player);}
            catch(Throwable failure){game.execute(()->fail(game,failure));}});
    }
    private static void fail(Minecraft game,Throwable failure){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED adhesive scene: "+failure.getMessage(),failure);finish(game);}
    private static void finish(Minecraft game){
        restoreInput(game);
        if(running){running=false;ModConfig.CLIENT.forceFirstPerson.set(oldFirstPerson);ModConfig.SERVER.bootLossChance.set(oldBootChance);ModConfig.SERVER.creativeGroundPhysics.set(oldCreativeGroundPhysics);}
        game.stop();
    }
    private static void restoreInput(Minecraft game){if(previousInput!=null && game.player!=null){game.player.input=previousInput;previousInput=null;}}
    private static net.minecraft.client.player.ClientInput heldJumpInput(){return new net.minecraft.client.player.ClientInput(){
        @Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(false,false,false,false,true,false,false);}
    };}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Adhesive client scene failed: "+message);}
    private AdhesiveClientScene(){}
}
