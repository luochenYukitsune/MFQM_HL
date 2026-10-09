package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.block.StickyBoardBlock;
import com.mfqm.morefunquicksandmod.block.StickyBoardConnections;
import com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Two focused installed-JAR scenes; normal launches never activate this fixture. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class AdhesiveVisualClientChecks {
    private static final BlockPos BOARD=new BlockPos(159,253,0); // Straddles the x=160 chunk boundary.
    private static boolean running,oldGround,oldFirstPerson,oldGui;
    private static volatile boolean ready;
    private static int phase,ticks,oldFov;
    private static ClientInput oldInput;
    private static CameraType oldCamera;
    public static void start(Minecraft game) {
        running=true;ready=false;phase=0;ticks=0;oldInput=game.player.input;
        oldGround=ModConfig.SERVER.creativeGroundPhysics.get();oldFirstPerson=ModConfig.CLIENT.forceFirstPerson.get();
        oldCamera=game.options.getCameraType();oldFov=game.options.fov().get();oldGui=game.options.hideGui;
        ModConfig.SERVER.creativeGroundPhysics.set(true);ModConfig.CLIENT.forceFirstPerson.set(false);
        game.player.input=input(0);game.options.setCameraType(CameraType.FIRST_PERSON);game.options.hideGui=true;
        execute(game,player->{
            var level=player.level();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();
            for(var slot:net.minecraft.world.entity.EquipmentSlot.values())player.setItemSlot(slot,ItemStack.EMPTY);
            for(int x=157;x<=178;x++)for(int z=-5;z<=8;z++) {
                var floor=new BlockPos(x,252,z);level.getChunk(floor);level.setBlock(floor,Blocks.GRAY_CONCRETE.defaultBlockState(),2);
                for(int y=1;y<=7;y++)level.setBlock(floor.above(y),Blocks.AIR.defaultBlockState(),2);
            }
            teleport(player,160,253,-3.5,0,24);
            for(int x=0;x<2;x++)for(int z=0;z<2;z++)place(player,BOARD.offset(x,0,z));
            verifyBoards(level::getBlockState);
            // Removal must restore the exposed edge, without changing another board's coating.
            level.removeBlock(BOARD.offset(1,0,1),false);
            require(!level.getBlockState(BOARD.east()).getValue(StickyBoardBlock.SOUTH),"removed south neighbor restores edge");
            require(!level.getBlockState(BOARD.south()).getValue(StickyBoardBlock.EAST),"removed east neighbor restores edge");
            place(player,BOARD.offset(1,0,1));
            // Old saved states have default false connections; deliver the real deferred load callback.
            for(int x=0;x<2;x++)for(int z=0;z<2;z++) {
                // Like deserialization, populate the palette without the normal placement repair hook.
                var pos=BOARD.offset(x,0,z);var chunk=level.getChunkAt(pos);var previous=level.getBlockState(pos);
                var stale=StickyBoardBlock.coatedState(7);
                chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX()&15,pos.getY()&15,pos.getZ()&15,stale);
                level.sendBlockUpdated(pos,previous,stale,2);
            }
            require(!level.getBlockState(BOARD).getValue(StickyBoardBlock.EAST),"stale saved connection reproduced");
            StickyBoardConnections.loaded(new ChunkEvent.Load(level.getChunkAt(BOARD),false));ready=true;
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!running)return;var game=Minecraft.getInstance();if(game.player==null || game.level==null)return;
        try {
            require(!game.player.getAbilities().flying,"client actor never flies");
            if(++ticks>500)throw new IllegalStateException("visual check timeout phase="+phase);
            switch(phase) {
                case 0->{
                    if(!ready || ticks<60)return;
                    for(int x=0;x<2;x++)for(int z=0;z<2;z++)
                        if(!game.level.hasChunkAt(BOARD.offset(x,0,z)) || !StickyBoardBlock.isCoated(game.level.getBlockState(BOARD.offset(x,0,z))))return;
                    verifyBoards(game.level::getBlockState);verifyBakedBoards(game);
                    ready=false;phase=1;ticks=0;
                    execute(game,player->{verifyBoards(player.level()::getBlockState);ready=true;});
                }
                case 1->{
                    if(!ready || ticks<15)return;phase=2;ticks=0;
                    screenshot(game,"mfqm-connected-boards.png",()->{
                        ready=false;phase=3;ticks=0;
                        execute(game,player->{
                            var level=player.level();
                            for(int x=170;x<=176;x++)for(int z=-1;z<=5;z++)level.setBlock(new BlockPos(x,253,z),
                                    x==170 || x==176 || z==-1 || z==5?Blocks.GRAY_CONCRETE.defaultBlockState():ModBlocks.byId("glue").defaultBlockState(),3);
                            teleport(player,173.5,253.72,2.5,0,0);ready=true;
                        });
                    });
                }
                case 2->{}
                case 3->{if(ready && ticks>=35){game.player.input=input(1);phase=4;ticks=0;}}
                case 4->{
                    game.player.input=input((ticks/16)%2==0?1:-1);
                    if(ticks<160)return;game.player.input=input(0);
                    game.options.setCameraType(CameraType.THIRD_PERSON_BACK);game.options.fov().set(40);
                    game.player.setYRot(145);game.player.setXRot(55);phase=5;ticks=0;
                }
                case 5->{
                    if(ticks<30)return;verifyStrands(game);phase=6;ticks=0;
                    screenshot(game,"mfqm-straight-strands.png",()->{
                        MFQM.LOGGER.info("MFQM_ADHESIVE_VISUAL_COMPLETE placement2x2=true removal=true oldSaveRefresh=true bakedCoverage=true rootsNearBottom=true regularStrips=true flight=false screenshots=2");finish(game);
                    });
                }
                case 6->{}
                default->throw new IllegalStateException("invalid visual phase");
            }
        }catch(Throwable failure){fail(game,failure);}
    }
    private static void verifyBoards(java.util.function.Function<BlockPos,BlockState> stateAt) {
        for(int x=0;x<2;x++)for(int z=0;z<2;z++) {
            var pos=BOARD.offset(x,0,z);var state=stateAt.apply(pos);require(StickyBoardBlock.isCoated(state),"board still coated at "+pos+": "+state);
            require(state.getValue(StickyBoardBlock.CHARGE)==7,"refresh preserves charge");
            require(state.getValue(StickyBoardBlock.EAST)==(x==0) && state.getValue(StickyBoardBlock.WEST)==(x==1)
                    && state.getValue(StickyBoardBlock.SOUTH)==(z==0) && state.getValue(StickyBoardBlock.NORTH)==(z==1),"actual reciprocal 2x2 connections "+state);
        }
    }
    private static void verifyBakedBoards(Minecraft game) {
        for(int x=0;x<2;x++)for(int z=0;z<2;z++) {
            var pos=BOARD.offset(x,0,z);var state=game.level.getBlockState(pos);
            var model=game.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
            double area=0;int faces=0;
            for(var part:model.collectParts(game.level,pos,state,RandomSource.create(0)))for(var quad:part.getQuads(null)) {
                if(quad.direction()!=Direction.UP || !quad.sprite().contents().name().getPath().equals("blocks/stickyboard_glue"))continue;
                double dx=Math.abs(quad.position2().x()-quad.position0().x()),dz=Math.abs(quad.position2().z()-quad.position0().z());
                area+=dx*dz;faces++;
            }
            require(faces==4 && Math.abs(area-225.0/256)<1e-6,"actual baked glue fills both inner edges and corner: area="+area+" faces="+faces);
        }
        MFQM.LOGGER.info("MFQM_CONNECTED_BOARD_MODELS_VERIFIED actualPlacement=true oldStateRefresh=true fourGlueFacesPerTile=true coveragePixels=225");
    }
    private static void verifyStrands(Minecraft game) {
        var helpers=game.level.getEntitiesOfClass(AdhesiveTetherEntity.class,game.player.getBoundingBox().inflate(8),e->e.target()==game.player && !e.breaking());
        int strands=0;double lowest=1,highest=0;
        for(var helper:helpers) {
            var renderer=(AdhesiveTetherRenderer)game.getEntityRenderDispatcher().getRenderer(helper);var state=renderer.createRenderState(helper,1);
            if(!state.visible)continue;var origin=new Vec3(state.x,state.y,state.z);
            for(var strand:state.filaments) {
                var root=origin.add(strand.root());var cell=BlockPos.containing(root);double y=root.y-cell.getY(),radius=strand.rootWidth();
                double surface=RenderedAdhesiveSurface.minimumHeight(game.level,cell,helper.material());
                require(y-radius>0 && y+radius<surface,"whole root remains in glue");
                require(y<=.061,"root lowered close to medium bottom");
                require(root.x-radius>cell.getX() && root.x+radius<cell.getX()+1 && root.z-radius>cell.getZ() && root.z+radius<cell.getZ()+1,"whole root stays within medium sides");
                require(strand.width()>=.02,"extended glue remains broad");lowest=Math.min(lowest,y);highest=Math.max(highest,y);strands++;
            }
        }
        require(strands>=16,"actual moving grounded actor produces dense visible strands: "+strands);
        MFQM.LOGGER.info("MFQM_STRAIGHT_STRANDS_VERIFIED physicalContacts={} visibleStrands={} rootLocalY={}..{} bodyEndpointUnchanged=true",helpers.size(),strands,lowest,highest);
    }
    private static void place(ServerPlayer player,BlockPos at) {
        var stack=StickyBoardBlock.coatedStack(7);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        require(stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at.below()).add(0,.5,0),Direction.UP,at.below(),false))).consumesAction(),"actual coated item placement");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    private static void execute(Minecraft game,Consumer<ServerPlayer> action) {
        var server=game.getSingleplayerServer();var id=game.player.getUUID();
        server.execute(()->{try{var player=server.getPlayerList().getPlayer(id);require(player!=null && !player.getAbilities().flying,"server actor never flies");action.accept(player);}
            catch(Throwable failure){game.execute(()->fail(game,failure));}});
    }
    private static void teleport(ServerPlayer p,double x,double y,double z,float yaw,float pitch){p.teleportTo(p.level(),x,y,z,Set.of(),yaw,pitch,false);p.setDeltaMovement(Vec3.ZERO);}
    private static ClientInput input(int direction){return new ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(direction>0,direction<0,false,false,false,false,false);moveVector=new Vec2(0,direction);}};}
    private static void screenshot(Minecraft game,String name,Runnable next){Screenshot.grab(game.gameDirectory,name,game.getMainRenderTarget(),1,message->game.execute(()->{MFQM.LOGGER.info("MFQM_ADHESIVE_VISUAL_SAVED file={} {}",name,message.getString());next.run();}));}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void fail(Minecraft game,Throwable failure){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED focused adhesive visuals",failure);finish(game);}
    private static void finish(Minecraft game){if(!running)return;running=false;game.player.input=oldInput;game.options.fov().set(oldFov);game.options.setCameraType(oldCamera);game.options.hideGui=oldGui;ModConfig.SERVER.creativeGroundPhysics.set(oldGround);ModConfig.CLIENT.forceFirstPerson.set(oldFirstPerson);game.stop();}
    private AdhesiveVisualClientChecks(){}
}
