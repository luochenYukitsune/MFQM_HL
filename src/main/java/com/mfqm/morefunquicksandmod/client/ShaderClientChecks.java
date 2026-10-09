package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.block.StickyBoardBlock;
import com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.ClientInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in two-view shader fixture in a copied world; never enabled in normal gameplay. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class ShaderClientChecks {
    private static boolean running;
    private static volatile boolean ready;
    private static int phase,ticks;
    private static ClientInput oldInput;
    private static long detailed,flat;
    public static void start(Minecraft game) {
        if(!Boolean.getBoolean("mfqm.shaderChecks"))return;
        running=true;ticks=0;phase=0;ready=false;oldInput=game.player.input;
        require(FirstPersonCompatibility.present(),"actual FirstPersonModel installed");
        ModConfig.SERVER.creativeGroundPhysics.set(true);ModConfig.CLIENT.forceFirstPerson.set(false);
        game.options.setCameraType(CameraType.THIRD_PERSON_BACK);game.options.hideGui=true;game.options.fov().set(55);
        game.player.input=input(0);
        var server=game.getSingleplayerServer();var id=game.player.getUUID();
        server.execute(()->{
            try {
                var player=server.getPlayerList().getPlayer(id);var level=player.level();
                player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();
                for(var slot:net.minecraft.world.entity.EquipmentSlot.values())player.setItemSlot(slot,ItemStack.EMPTY);
                level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                for(int x=198;x<=224;x++)for(int z=-5;z<=10;z++) {
                    var p=new BlockPos(x,252,z);level.getChunkAt(p);
                    level.setBlock(p,(x+z)%2==0?Blocks.QUARTZ_BLOCK.defaultBlockState():Blocks.GRAY_CONCRETE.defaultBlockState(),2);
                    for(int y=1;y<=5;y++)level.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);
                }
                String[] media={"glue","honey","tar","sinking_slime"};
                for(int i=0;i<media.length;i++)for(int x=0;x<4;x++)for(int z=0;z<4;z++)
                    level.setBlock(new BlockPos(202+i*5+x,253,z),ModBlocks.byId(media[i]).defaultBlockState(),3);
                for(int x=0;x<2;x++)for(int z=0;z<2;z++)level.setBlock(new BlockPos(200+x,253,4+z),StickyBoardBlock.coatedState(7),3);
                for(int i=0;i<3;i++)level.setBlock(new BlockPos(207+i*3,253,7),ModBlocks.byId(new String[]{"blossom","larvae","meat_wall"}[i]).defaultBlockState(),3);
                int n=0;
                for(var holder:new net.neoforged.neoforge.registries.DeferredHolder[]{ModEntities.VORE_SLIME,ModEntities.MUDDY_BLOB,ModEntities.SAND_BLOB,ModEntities.TAR_SLIME,ModEntities.BEE}) {
                    var entity=((net.minecraft.world.entity.EntityType<?>)holder.get()).create(level,EntitySpawnReason.COMMAND);
                    entity.setPos(207+n*2,254,5.5);entity.setNoGravity(true);
                    if(entity instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);
                    level.addFreshEntity(entity);n++;
                }
                player.teleportTo(level,203.5,253.74,2.5,Set.of(),0,0,false);player.setDeltaMovement(Vec3.ZERO);
                ready=true;
            }catch(Throwable error){game.execute(()->fail(game,error));}
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!running)return;var game=Minecraft.getInstance();if(game.player==null || game.level==null)return;
        try {
            if(++ticks>600)throw new IllegalStateException("shader scene timeout phase="+phase);
            require(!game.player.getAbilities().flying,"grounded client actor");
            switch(phase) {
                case 0->{
                    if(!ready || ticks<60)return;verifyPack();
                    if(System.getProperty("mfqm.shaderExpected","").startsWith("iteration")) {
                        require(IterationShaderBridge.enabled() && IterationShaderBridge.applied(),"Iteration pigment alpha/depth adaptation actually compiled");
                        var settings=Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
                        var current=settings.getField("INSTANCE").get(null);
                        var ids=(it.unimi.dsi.fastutil.objects.Object2IntMap<?>)settings.getMethod("getBlockStateIds").invoke(current);
                        for(String medium:IterationFluidCompatibility.MEDIA)for(var state:ModBlocks.byId(medium).getStateDefinition().getPossibleStates())
                            require(ids.getInt(state)==IterationShaderBridge.materialId(),"actual Iris source/flowing mapping for "+medium);
                        MFQM.LOGGER.info("MFQM_SHADER_POOL_PATH_VERIFIED pigment=true glassAbsorption=false sourceFlowingMapped=true");
                    }
                    if(Boolean.getBoolean("mfqm.shaderSmoke")){MFQM.LOGGER.info("MFQM_SHADER_SMOKE_COMPLETE active=true fallback=false grounded=true");finish(game);return;}
                    detailed=GlueCoatingRenderer.detailedSubmissions();game.player.input=input(1);phase=1;ticks=0;
                }
                case 1->{
                    game.player.input=input((ticks/16)%2==0?1:-1);
                    if(ticks<128)return;game.player.input=input(0);game.player.setYRot(145);game.player.setXRot(48);phase=2;ticks=0;
                }
                case 2->{
                    if(ticks<30)return;verifyPack();verifyStrands(game);
                    require(GlueCoatingRenderer.detailedSubmissions()>detailed,"3D body coating actually submitted");
                    phase=3;ticks=0;screenshot(game,"mfqm-shader-body.png",()->{
                        game.options.setCameraType(CameraType.FIRST_PERSON);game.player.setYRot(60);game.player.setXRot(72);
                        FirstPersonTetherProof.reset();phase=4;ticks=0;
                    });
                }
                case 3->{}
                case 4->{
                    if(ticks<60)return;verifyPack();
                    require(FirstPersonTetherProof.samples()>0,"actual first-person native leg and tether matrices sampled");
                    require(FirstPersonTetherProof.error()<.035,"first-person foot alignment error "+FirstPersonTetherProof.error());
                    MFQM.LOGGER.info("MFQM_SHADER_FIRSTPERSON_VERIFIED samples={} error={} shadowsSeparate=true",FirstPersonTetherProof.samples(),FirstPersonTetherProof.error());
                    phase=5;ticks=0;screenshot(game,"mfqm-shader-firstperson.png",()->{
                        flat=GlueCoatingRenderer.flatSubmissions();ModConfig.CLIENT.coatingThickness.set(0.);phase=6;ticks=0;
                    });
                }
                case 5->{}
                case 6->{
                    if(ticks<25)return;require(GlueCoatingRenderer.flatSubmissions()>flat,"flat body coating actually submitted");verifyPack();
                    game.options.fov().set(65);game.player.input=input(0);ready=false;phase=7;ticks=0;
                    var server=game.getSingleplayerServer();var id=game.player.getUUID();
                    server.execute(()->{
                        var player=server.getPlayerList().getPlayer(id);var level=player.level();
                        for(int x=210;x<=212;x++)for(int z=9;z<=11;z++)level.setBlock(new BlockPos(x,260,z),Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
                        player.teleportTo(level,211.5,261,10.5,Set.of(),180,50,false);player.setDeltaMovement(Vec3.ZERO);ready=true;
                    });
                }
                case 7->{
                    if(!ready || ticks<60)return;verifyPack();game.player.setYRot(180);game.player.setXRot(50);
                    phase=8;ticks=0;screenshot(game,"mfqm-shader-pools.png",()->{
                        MFQM.LOGGER.info("MFQM_SHADER_CHECKS_COMPLETE active=true fallback=false grounded=true calfAttachments=true rootsInside=true body3d=true flatLod=true firstPerson=true poolsCaptured=true");finish(game);
                    });
                }
                case 8->{}
                default->throw new IllegalStateException("invalid shader phase");
            }
        }catch(Throwable error){fail(game,error);}
    }
    private static void verifyPack() throws ReflectiveOperationException {
        require(ShaderCompatibility.active(),"shader pack actually in use, not vanilla fallback");
        var iris=Class.forName("net.irisshaders.iris.Iris");
        require(!(boolean)iris.getMethod("isFallback").invoke(null),"Iris is not in fallback mode");
        require(((java.util.Optional<?>)iris.getMethod("getStoredError").invoke(null)).isEmpty(),"Iris has no stored shader compile/runtime error");
        require(System.getProperty("mfqm.shaderExpected").equals(iris.getMethod("getCurrentPackName").invoke(null)),"correct shader pack loaded");
    }
    private static void verifyStrands(Minecraft game) {
        int total=0,raised=0;
        for(var helper:game.level.getEntitiesOfClass(AdhesiveTetherEntity.class,game.player.getBoundingBox().inflate(8),e->e.target()==game.player && !e.breaking())) {
            var renderer=(AdhesiveTetherRenderer)game.getEntityRenderDispatcher().getRenderer(helper);var state=renderer.createRenderState(helper,1);
            if(!state.visible)continue;var origin=new Vec3(state.x,state.y,state.z);
            for(var strand:state.filaments) {
                var root=origin.add(strand.root());var cell=BlockPos.containing(root);double y=root.y-cell.getY(),radius=strand.rootWidth();
                double surface=RenderedAdhesiveSurface.minimumHeight(game.level,cell,helper.material());
                require(y-radius>0 && y+radius<surface && y<=.061,"root remains fully inside glue near bottom");
                double height=new CoatingVoxels.Vec(strand.end().x,strand.end().y,strand.end().z).subtract(state.surface.center()).dot(state.surface.up());
                require(height<=state.surface.calfHeight()+1e-6,"body attachment stops at sampled calf midpoint");
                if(height>state.surface.calfHeight()*.79)raised++;total++;
            }
        }
        require(total>=32 && raised>0 && raised<total/2,"some but not all strands attach high: "+raised+"/"+total);
        MFQM.LOGGER.info("MFQM_SHADER_STRANDS_VERIFIED total={} raised={} rootsInside=true",total,raised);
    }
    private static ClientInput input(int direction){return new ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(direction>0,direction<0,false,false,false,false,false);moveVector=new Vec2(0,direction);}};}
    private static void screenshot(Minecraft game,String name,Runnable next){Screenshot.grab(game.gameDirectory,name,game.getMainRenderTarget(),1,message->game.execute(()->{MFQM.LOGGER.info("MFQM_SHADER_VISUAL_SAVED file={}",name);next.run();}));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void fail(Minecraft game,Throwable error){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED shader fixture",error);finish(game);}
    private static void finish(Minecraft game){if(!running)return;running=false;game.player.input=oldInput;game.stop();}
    private ShaderClientChecks(){}
}
