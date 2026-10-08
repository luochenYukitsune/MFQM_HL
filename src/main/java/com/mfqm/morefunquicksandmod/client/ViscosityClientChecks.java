package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in, screenshot-free measurements from a LocalPlayer loaded from mods/*.jar. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class ViscosityClientChecks {
    private record Case(String medium,String geometry,GameType mode,boolean flying) {
        String key(){return mode.getName()+"/"+(flying?"flying/":"ground/")+geometry+"/"+medium;}
        boolean thin(){return geometry.equals("flowing_thin");}
        boolean deep(){return geometry.equals("source_deep");}
        boolean jumping(){return geometry.equals("source_shallow_jump");}
    }
    private record Observation(Vec3 position,String medium,double depth,double effort,int connections,
                               boolean moving,boolean flying,long serverTime) {}
    private record Result(Case test,Vec3 clientStart,Vec3 clientEnd,Vec3 serverStart,Vec3 serverEnd,
                          double forward,double horizontal,double predictionError,double maxPredictionError,
                          String finalMaterial,double finalDepth,double finalEffort,int contactTicks) {}
    private static final String[] MEDIA={"water","glue","tar","honey","mud"};
    private static final List<Case> CASES=new ArrayList<>();
    private static final Map<String,Result> RESULTS=new LinkedHashMap<>();
    private static boolean running;
    private static boolean flightControls;
    private static volatile boolean movementWindow,expectedFlight,serverFlightViolation;
    private static java.util.UUID playerId;
    private static volatile boolean probing;
    private static boolean oldCreativeGround;
    private static boolean oldHotTar,oldMudTentacles,oldFleshTentacles;
    private static int index,phase,ticks,contactTicks,maxObservedSampleAge;
    private static volatile boolean serverReady;
    private static volatile Observation observation;
    private static ClientInput previousInput;
    private static Vec3 clientStart,serverStart;
    private static double maxPredictionError,maxClientY;

    public static void start(Minecraft game) {
        if(!Boolean.getBoolean("mfqm.viscosityChecks") || running)return;
        require(game.player!=null && game.getSingleplayerServer()!=null,"requires isolated singleplayer save");
        oldCreativeGround=ModConfig.SERVER.creativeGroundPhysics.get();
        ModConfig.SERVER.creativeGroundPhysics.set(Boolean.getBoolean("mfqm.viscosityCreativeGroundTraps"));
        // Burn knockback and randomly spawned tentacles are separate mechanics, not viscosity.
        oldHotTar=ModConfig.SERVER.hotTar.get();oldMudTentacles=ModConfig.SERVER.mudTentacles.get();
        oldFleshTentacles=ModConfig.SERVER.tentaclesInFlesh.get();
        ModConfig.SERVER.hotTar.set(false);ModConfig.SERVER.mudTentacles.set(false);ModConfig.SERVER.tentaclesInFlesh.set(false);
        CASES.clear();RESULTS.clear();
        flightControls=Boolean.getBoolean("mfqm.viscosityFlightChecks");playerId=game.player.getUUID();
        if(!flightControls) {
            for(String geometry:new String[]{"source_shallow","source_deep","flowing_thin"})for(String medium:MEDIA) {
                // Mud is a sinking solid, so inventing a flowing mud state would invalidate the comparison.
                if(geometry.equals("flowing_thin") && medium.equals("mud"))continue;
                CASES.add(new Case(medium,geometry,GameType.SURVIVAL,false));
            }
            for(String medium:new String[]{"water","glue","tar","honey"})
                CASES.add(new Case(medium,"source_shallow_jump",GameType.SURVIVAL,false));
            for(String medium:MEDIA)CASES.add(new Case(medium,"source_shallow",GameType.CREATIVE,false));
        } else {
            for(String medium:MEDIA)CASES.add(new Case(medium,"source_shallow",GameType.CREATIVE,true));
        }
        MFQM.LOGGER.info("MFQM_VISCOSITY_CHECKS_START suite={} cases={} movementTicks=60 screenshots=0 flightControls={} thinMud=unsupported hotTar=false randomTentacles=false creativeGroundTraps={}",
                flightControls?"flight-controls":"ground",CASES.size(),flightControls,Boolean.getBoolean("mfqm.viscosityCreativeGroundTraps"));
        running=true;index=0;previousInput=game.player.input;
        game.player.input=idleInput();
        prepareDryReset(game);
    }

    @SubscribeEvent public static void serverFlight(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if(movementWindow && event.getEntity() instanceof ServerPlayer player && player.getUUID().equals(playerId)
                && player.getAbilities().flying!=expectedFlight)serverFlightViolation=true;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!running)return;
        Minecraft game=Minecraft.getInstance();
        if(game.player==null || game.level==null)return;
        try {
            if(++ticks>240)throw new IllegalStateException("phase timeout case="+CASES.get(index).key()+" phase="+phase);
            switch(phase) {
                case 0->{
                    if(!serverReady || ticks<70)return; // Naturally expire the preceding medium/anchors/input episode.
                    phase=1;ticks=0;serverReady=false;buildCase(game,CASES.get(index));
                }
                case 1->{
                    if(!serverReady || ticks<15)return;
                    // Bulk fixture changes can arrive after teleport, especially
                    // during resource loading. Start only in the actual client
                    // medium, rather than measuring leftover blocks as water.
                    var test=CASES.get(index);
                    if(!test.flying()) {
                        if(test.medium().equals("water")) {if(!game.player.isInWater())return;}
                        else {var contact=QuicksandPhysics.findContact(game.player,game.level);if(contact==null || !contact.material().id.equals(test.medium()))return;}
                    }
                    observation=null;probe(game);phase=2;ticks=0;
                }
                case 2->{
                    if(observation==null)return;
                    if(Math.abs(game.level.getGameTime()-observation.serverTime())>2) {observation=null;probe(game);return;}
                    clientStart=game.player.position();serverStart=observation.position();
                    if(clientStart.distanceTo(serverStart)>.5)
                        MFQM.LOGGER.warn("MFQM_VISCOSITY_START_DIVERGENCE case={} client={} server={} error={}",
                                CASES.get(index).key(),clientStart,serverStart,clientStart.distanceTo(serverStart));
                    require(observation.flying()==CASES.get(index).flying(),"server flight state "+CASES.get(index).key());
                    Case test=CASES.get(index);
                    boolean physics=!test.flying() && (test.mode()==GameType.SURVIVAL || Boolean.getBoolean("mfqm.viscosityCreativeGroundTraps"));
                    if(!test.flying() && !test.medium().equals("water")) {
                        var contact=QuicksandPhysics.findContact(game.player,game.level);
                        require(contact!=null && contact.material().id.equals(test.medium()),"actual fixture medium "+test.key());
                        if(physics)require(observation.medium().equals(test.medium()),"active starting medium "+test.key()+" got="+observation.medium());
                        else require(observation.medium().isEmpty() && observation.connections()==0,"immune creative ground state remains clear "+test.key());
                        if(physics)require(Math.abs(game.player.getFieldOfViewModifier(false,1)-1)<.00001,
                                "stationary adhesive FOV matches ordinary unsprinted walking: "+test.key());
                    }
                    contactTicks=0;maxPredictionError=0;maxObservedSampleAge=0;maxClientY=game.player.getY();game.player.input=forwardInput(CASES.get(index).jumping());
                    expectedFlight=test.flying();serverFlightViolation=false;movementWindow=true;
                    phase=3;ticks=0;observation=null;
                }
                case 3->{
                    require(game.player.getAbilities().flying==expectedFlight,"client flight mode is fixed throughout W movement");
                    require(!serverFlightViolation,"server flight mode is fixed throughout W movement");
                    if(observation!=null)require(observation.flying()==expectedFlight,"observed server flight mode throughout W movement");
                    maxClientY=Math.max(maxClientY,game.player.getY());
                    if(QuicksandPhysics.findContact(game.player,game.level)!=null || game.player.isInWater())contactTicks++;
                    if(observation!=null) {
                        int age=(int)Math.abs(game.level.getGameTime()-observation.serverTime());
                        maxObservedSampleAge=Math.max(maxObservedSampleAge,age);
                        // Asynchronous observations older than two game ticks are not evidence of prediction divergence.
                        if(age<=2)maxPredictionError=Math.max(maxPredictionError,game.player.position().distanceTo(observation.position()));
                    }
                    probe(game);
                    if(ticks<60)return;
                    movementWindow=false;
                    game.player.input=idleInput();observation=null;probe(game);phase=4;ticks=0;
                }
                case 4->{
                    if(observation==null)return;
                    Case test=CASES.get(index);Vec3 end=game.player.position();
                    double forward=observation.position().z-serverStart.z;
                    double horizontal=Math.hypot(observation.position().x-serverStart.x,forward);
                    Result result=new Result(test,clientStart,end,serverStart,observation.position(),forward,horizontal,
                            end.distanceTo(observation.position()),maxPredictionError,observation.medium(),observation.depth(),observation.effort(),contactTicks);
                    RESULTS.put(test.key(),result);
                    if(test.jumping() && !test.medium().equals("water"))
                        require(maxClientY>clientStart.y+.01 && maxClientY<=clientStart.y+.35 && observation.position().y<=serverStart.y+.04 && end.y<=clientStart.y+.04,
                                "one bounded short hop returns instead of sustained floating: "+test.key()+" start="+clientStart.y+" max="+maxClientY+" clientEnd="+end.y+" serverEnd="+observation.position().y);
                    MFQM.LOGGER.info("MFQM_VISCOSITY_RESULT case={} ticks=60 clientFlying={} serverFlying={} clientStart={} clientEnd={} serverStart={} serverEnd={} maxClientY={} forward={} horizontal={} clientServerError={} maxRecentClientServerError={} maxObservedSampleAgeTicks={} finalMaterial={} depth={} effort={} connections={} contactTicks={} serverMoving={}",
                            test.key(),game.player.getAbilities().flying,observation.flying(),clientStart,end,serverStart,observation.position(),maxClientY,forward,horizontal,result.predictionError(),maxPredictionError,maxObservedSampleAge,
                            observation.medium(),observation.depth(),observation.effort(),observation.connections(),contactTicks,observation.moving());
                    if(!flightControls && index==4 && Boolean.getBoolean("mfqm.viscosityFailFast")) {
                        List<String> failures=expectations();
                        require(failures.isEmpty(),"shallow-source regression (fail fast): "+String.join("; ",failures));
                    }
                    if(++index<CASES.size()){prepareDryReset(game);return;}
                    verify(game);
                }
                default->throw new IllegalStateException("invalid phase");
            }
        } catch(Throwable failure){fail(game,failure);}
    }

    private static void prepareDryReset(Minecraft game) {
        phase=0;ticks=0;serverReady=false;observation=null;game.player.input=idleInput();
        execute(game,player->{
            var level=player.level();var dry=new BlockPos(77,253,-4);level.getChunk(dry);
            level.setBlock(dry.below(),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            for(int up=0;up<4;up++)level.setBlock(dry.above(up),Blocks.AIR.defaultBlockState(),3);
            player.setGameMode(GameType.SURVIVAL);player.getAbilities().flying=false;player.onUpdateAbilities();
            player.teleportTo(level,77.5,253,-3.5,Set.of(),0,0,false);player.setDeltaMovement(Vec3.ZERO);
            player.setHealth(player.getMaxHealth());player.getFoodData().setFoodLevel(20);
            for(var slot:EquipmentSlot.values())player.setItemSlot(slot,ItemStack.EMPTY);
            player.removeAllEffects();serverReady=true;
        });
    }

    private static void buildCase(Minecraft game,Case test) {
        execute(game,player->{
            var level=player.level();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            int floor=test.deep()?250:252;
            for(int x=79;x<=92;x++)for(int z=-1;z<=75;z++) {
                var at=new BlockPos(x,floor,z);level.getChunk(at);
                for(int y=250;y<=258;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                level.setBlock(at,Blocks.SMOOTH_STONE.defaultBlockState(),2);
                boolean wall=x==79 || x==92 || z==-1 || z==75;
                for(int y=floor+1;y<=253;y++) {
                    var fill=new BlockPos(x,y,z);
                    if(wall){level.setBlock(fill,Blocks.SMOOTH_STONE.defaultBlockState(),2);continue;}
                    if(!test.thin())level.setBlock(fill,test.medium().equals("water")?Blocks.WATER.defaultBlockState():ModBlocks.byId(test.medium()).defaultBlockState(),2);
                    else {
                        int amount=8-(x-80)*(test.medium().equals("water")?1:2);
                        if(amount<=0)continue;
                        var source=test.medium().equals("water")?Fluids.WATER:ModFluids.source(test.medium());
                        var state=(amount==8?source.getSource(false):source.getFlowing(amount,false)).createLegacyBlock();
                        level.setBlock(fill,state,3);
                    }
                }
            }
            double startX=test.thin()?(test.medium().equals("water")?86.5:83.5):85.5;
            double startY=floor+1.04;
            player.setGameMode(test.mode());player.getAbilities().flying=test.flying();player.onUpdateAbilities();
            player.teleportTo(level,startX,startY,4.5,Set.of(),0,0,false);player.setDeltaMovement(Vec3.ZERO);
            player.setHealth(player.getMaxHealth());player.getFoodData().setFoodLevel(20);serverReady=true;
        });
    }

    private static void verify(Minecraft game) {
        List<String> failures=expectations();
        if(!failures.isEmpty()) {
            for(String failure:failures)MFQM.LOGGER.error("MFQM_VISCOSITY_EXPECTATION_FAILED {}",failure);
            throw new IllegalStateException(String.join("; ",failures));
        }
        if(flightControls) {
            MFQM.LOGGER.info("MFQM_VISCOSITY_FLIGHT_CONTROLS_COMPLETE cases={} realInput=true packagedJar=true separateSuite=true screenshots=0",RESULTS.size());
            finish(game);return;
        }
        MFQM.LOGGER.info("MFQM_VISCOSITY_CHECKS_COMPLETE cases={} realInput=true packagedJar=true clientFlying=false serverFlying=false screenshots=0",RESULTS.size());
        restore(game);
        ViscosityActionChecks.start(game);
    }
    private static List<String> expectations() {
        List<String> failures=new ArrayList<>();
        for(Result result:RESULTS.values()) {
            Case test=result.test();Result water=RESULTS.get(new Case("water",test.geometry(),test.mode(),test.flying()).key());
            if(test.medium().equals("water")) {
                if(result.forward()<1)failures.add(test.key()+" W input did not cause ordinary movement: "+result.forward());
                if(!test.flying() && !test.jumping() && result.contactTicks()<55)failures.add(test.key()+" water benchmark was not in water for the full movement window: "+result.contactTicks());
                continue;
            }
            double ratio=result.forward()/water.forward();
            MFQM.LOGGER.info("MFQM_VISCOSITY_RATIO case={} waterForward={} mediumForward={} ratio={}",test.key(),water.forward(),result.forward(),ratio);
            if(test.flying()) {
                if(ratio<.8)failures.add(test.key()+" flight should remain free, ratio="+ratio);
            } else if(test.mode()==GameType.CREATIVE && !Boolean.getBoolean("mfqm.viscosityCreativeGroundTraps")) {
                if(ratio<.8)failures.add(test.key()+" immune creative ground movement should remain free, ratio="+ratio);
                if(!result.finalMaterial().isEmpty())failures.add(test.key()+" immune creative ground must not retain trapping state");
            } else if(test.mode()==GameType.SURVIVAL || Boolean.getBoolean("mfqm.viscosityCreativeGroundTraps")) {
                double limit=test.thin()?.5:.2;
                if(result.forward()<=.001)failures.add(test.key()+" W input must permit slow progress instead of zero displacement: "+result.forward());
                if(ratio>=limit)failures.add(test.key()+" should move distinctly slower than water; ratio="+ratio+" limit="+limit);
                if(result.predictionError()>.5)failures.add(test.key()+" final client/server divergence="+result.predictionError());
                if(result.contactTicks()<55)failures.add(test.key()+" escaped the fixture instead of measuring 60 ticks of contact: "+result.contactTicks());
            }
        }
        return failures;
    }
    private static ClientInput idleInput(){return new ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(false,false,false,false,false,false,false);moveVector=Vec2.ZERO;}};}
    private static ClientInput forwardInput(boolean jump){return new ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(true,false,false,false,jump,false,false);moveVector=new Vec2(0,1);}};}
    private static void probe(Minecraft game) {
        if(probing)return;probing=true;
        execute(game,player->{try{var state=QuicksandPhysics.state(player);observation=new Observation(player.position(),state.material,state.depth,
                state.struggle.effort(),state.adhesiveConnections,state.movingInput,player.getAbilities().flying,player.level().getGameTime());}finally{probing=false;}});
    }
    private static void execute(Minecraft game,Consumer<ServerPlayer> action) {
        var server=game.getSingleplayerServer();var id=game.player.getUUID();
        require(server!=null,"integrated server available");
        server.execute(()->{try{var player=server.getPlayerList().getPlayer(id);require(player!=null,"fixture player available");action.accept(player);}
            catch(Throwable failure){game.execute(()->fail(game,failure));}});
    }
    private static void fail(Minecraft game,Throwable failure){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED viscosity: "+failure.getMessage(),failure);finish(game);}
    private static void restore(Minecraft game){if(game.player!=null && previousInput!=null)game.player.input=previousInput;previousInput=null;
        ModConfig.SERVER.creativeGroundPhysics.set(oldCreativeGround);ModConfig.SERVER.hotTar.set(oldHotTar);
        ModConfig.SERVER.mudTentacles.set(oldMudTentacles);ModConfig.SERVER.tentaclesInFlesh.set(oldFleshTentacles);running=false;movementWindow=false;}
    private static void finish(Minecraft game){restore(game);game.stop();}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Viscosity client check failed: "+message);}
    private ViscosityClientChecks(){}
}
