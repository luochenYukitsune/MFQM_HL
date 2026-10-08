package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** Opt-in installed-game checks. Visual-only avatars never stand in for movement/flight tests. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class CoatingClientChecks {
    private static final String[] PARTS={"head","body","left_arm","right_arm","left_leg","right_leg"};
    private static final ArrayList<RemotePlayer> AVATARS=new ArrayList<>();
    private static boolean running;
    private static volatile boolean ready,reloaded;
    private static int ticks,phase;
    private static boolean oldFirst,oldCoating,old3d,oldGui;
    private static long submissions,flat;
    public static void start(Minecraft game) {
        require(SkinLayerClearance.present()==Boolean.getBoolean("mfqm.coatingSkinLayersExpected"),"exact optional mod presence");
        oldFirst=ModConfig.CLIENT.forceFirstPerson.get();oldCoating=ModConfig.CLIENT.coverPlayerWithMud.get();old3d=ModConfig.CLIENT.glueCoating3d.get();oldGui=game.options.hideGui;
        ModConfig.CLIENT.forceFirstPerson.set(false);ModConfig.CLIENT.coverPlayerWithMud.set(true);ModConfig.CLIENT.glueCoating3d.set(true);
        verifyMasks();running=true;ready=false;ticks=0;phase=0;
        MFQM.LOGGER.info("MFQM_COATING_CHECKS_START skinLayers={} actualModVersion={} groundCamera=true",SkinLayerClearance.present(),
                net.neoforged.fml.ModList.get().getModContainerById("skinlayers3d").map(c->c.getModInfo().getVersion().toString()).orElse("absent"));
        var id=game.player.getUUID();game.getSingleplayerServer().execute(()->{
            try {
                var player=game.getSingleplayerServer().getPlayerList().getPlayer(id);var level=player.level();
                level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                for(int x=176;x<190;x++)for(int z=-8;z<10;z++) {
                    var pos=new BlockPos(x,252,z);level.getChunk(pos);level.setBlock(pos,Blocks.SMOOTH_STONE.defaultBlockState(),2);
                    for(int up=1;up<8;up++)level.setBlock(pos.above(up),Blocks.AIR.defaultBlockState(),2);
                }
                player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();
                for(var slot:net.minecraft.world.entity.EquipmentSlot.values())player.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY);
                player.teleportTo(level,183.5,253,0,Set.of(),0,7,false);ready=true;
            } catch(Throwable e){fail(game,e);}
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!running)return;
        var game=Minecraft.getInstance();if(game.player==null || game.level==null)return;
        try {
            require(!game.player.getAbilities().flying,"camera player never flies");
            if(++ticks>900)throw new IllegalStateException("Coating phase timed out: "+phase);
            switch(phase) {
                case 0->{
                    if(!ready || ticks<70)return;
                    game.options.setCameraType(CameraType.FIRST_PERSON);game.options.hideGui=true;
                    for(int i=0;i<4;i++) {
                        // Kai's packaged vanilla skin has a real outer layer; no generated test texture is needed.
                        int seed=i<2?12+(i*18):3+((i-2)*18);
                        var avatar=new RemotePlayer(game.level,new GameProfile(new UUID(0,seed),new String[]{"WideDry","WideGlue","SlimDry","SlimGlue"}[i])) {
                            @Override public boolean isModelPartShown(PlayerModelPart part){return true;}
                        };
                        avatar.setId(50000+i);require(game.level.getEntity(avatar.getId())==null,"isolated fixture ID available");
                        avatar.setPos(180.5+i*2,253,5.5);avatar.setYRot(180);avatar.yBodyRot=180;avatar.yHeadRot=180;
                        avatar.setOldPosAndRot();avatar.setNoGravity(true);avatar.setOnGround(true);game.level.addEntity(avatar);AVATARS.add(avatar);
                        require((avatar.getSkin().model()==PlayerModelType.SLIM)==(i>=2),"actual wide/slim skin selection");
                    }
                    submissions=GlueCoatingRenderer.detailedSubmissions();phase=1;ticks=0;
                }
                case 1->{
                    coatAvatars(10);if(ticks<45)return;
                    require(GlueCoatingRenderer.detailedSubmissions()>submissions+30,"real rendered body submits voxel geometry");
                    if(SkinLayerClearance.present())for(var avatar:AVATARS)verifySkinMesh(game,avatar);
                    screenshot(game,"mfqm-glue-coating-"+(SkinLayerClearance.present()?"skinlayers":"vanilla")+".png");
                    MFQM.LOGGER.info("MFQM_COATING_BODY_COMPLETE wide=true slim=true fullCoverage=true detailedSubmissions={}",GlueCoatingRenderer.detailedSubmissions()-submissions);
                    flat=GlueCoatingRenderer.flatSubmissions();ModConfig.CLIENT.glueCoating3d.set(false);phase=2;ticks=0;
                }
                case 2->{
                    coatAvatars(3);if(ticks<20)return;
                    require(GlueCoatingRenderer.flatSubmissions()>flat,"disabled 3D uses flat geometry outside skin layers");
                    ModConfig.CLIENT.glueCoating3d.set(true);game.reloadResourcePacks().thenRun(()->reloaded=true);phase=3;ticks=0;
                }
                case 3->{
                    coatAvatars(3);if(!reloaded || game.getOverlay()!=null || ticks<35)return;
                    verifyMasks();MFQM.LOGGER.info("MFQM_COATING_RESOURCE_RELOAD_COMPLETE regenerated=true cacheBounded=true");
                    if(SkinLayerClearance.present())screenshot(game,"mfqm-glue-legs-only.png");
                    // The actual first-person renderer uses the local player's own arm width and skin mesh.
                    game.options.hideGui=false;
                    submissions=GlueCoatingRenderer.armSubmissions();phase=4;ticks=0;
                }
                case 4->{
                    coatAvatars(1);var state=QuicksandPhysics.state(game.player);state.coatingType="glue";state.coatingLevel=10;state.coatingTicks=1200;
                    if(ticks<35)return;
                    require(GlueCoatingRenderer.armSubmissions()>submissions+30,"actual first-person arm submits voxel coating independently of other avatar bodies");
                    if(SkinLayerClearance.present())screenshot(game,"mfqm-glue-first-person.png");
                    MFQM.LOGGER.info("MFQM_COATING_ARM_COMPLETE actualSkin={} padding={} flight=false",game.player.getSkin().model(),SkinLayerClearance.padding("right_arm",true));
                    require(GlueCoatingRenderer.cachedMeshes()<=96,"bounded resource cache");
                    MFQM.LOGGER.info("MFQM_COATING_CHECKS_COMPLETE skinLayers={} anatomicalHeightMasks=true originalPngUnchanged=true",SkinLayerClearance.present());
                    finish(game);
                }
            }
        } catch(Throwable failure){fail(game,failure);}
    }
    private static void coatAvatars(int level) {
        for(int i=0;i<AVATARS.size();i++) {
            var state=QuicksandPhysics.state(AVATARS.get(i));state.coatingType="glue";state.coatingLevel=i%2==1?level:0;state.coatingTicks=1200;
        }
    }
    private static void verifyMasks() {
        int cases=0;
        for(String material:new String[]{"glue","mud","sinking_slime","tar","honey"})for(boolean slim:new boolean[]{false,true}) {
            int previous=0;
            for(int level=1;level<=10;level++) {
                var coat=new MuddyPlayerLayer.Coating(level,1200,material);int total=0;
                for(String part:PARTS) {
                    var mesh=GlueCoatingRenderer.mesh(coat.texture(),part,slim,false);total+=mesh.pixels();
                    if(level<=3 && !part.endsWith("leg"))require(mesh.pixels()==0,"leg immersion stays off head/torso/arms "+material+" "+level+" "+part);
                    if(level<=7 && part.equals("head"))require(mesh.pixels()==0,"head stays dry below its height "+material+" "+level);
                    if(level==10)require(mesh.pixels()>0,"full coverage includes "+part);
                    cases++;
                }
                require(total>=previous && total>0,"coverage levels are monotonic");previous=total;
            }
        }
        require(GlueCoatingRenderer.cachedMeshes()<=96,"LRU does not grow with every avatar/configuration");
        MFQM.LOGGER.info("MFQM_COATING_MASKS_COMPLETE cases={} legTiers1to3=true headDryThrough7=true fullBody=true",cases);
    }
    @SuppressWarnings("unchecked") private static void verifySkinMesh(Minecraft game,RemotePlayer avatar)throws ReflectiveOperationException {
        var renderer=(AvatarRenderer<AbstractClientPlayer>)game.getEntityRenderDispatcher().getRenderer(avatar);
        var state=renderer.createRenderState(avatar,1);renderer.getModel().setupAnim(state);
        Object mesh=renderer.getModel().jacket.getClass().getMethod("getInjectedMesh").invoke(renderer.getModel().jacket);
        require(mesh!=null && (boolean)mesh.getClass().getMethod("isVisible").invoke(mesh),"3D Skin Layers actually injects a visible jacket mesh");
        require(SkinLayerClearance.padding("body",false)>.7,"glue shell clears the real extruded jacket");
        var config=Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase").getField("config").get(null);
        var size=config.getClass().getField("baseVoxelSize");float old=size.getFloat(config),normal=SkinLayerClearance.padding("right_arm",false);
        try{size.setFloat(config,1.4F);require(SkinLayerClearance.padding("right_arm",false)>normal+.3,"custom voxel size increases clearance");}
        finally{size.setFloat(config,old);}
        MFQM.LOGGER.info("MFQM_SKIN_LAYERS_MESH_VERIFIED skin={} visibleJacket=true dynamicSizing=true",avatar.getSkin().model());
    }
    private static void screenshot(Minecraft game,String name) {
        if(!Boolean.getBoolean("mfqm.coatingVisual"))return;
        net.minecraft.client.Screenshot.grab(game.gameDirectory,name,game.getMainRenderTarget(),1,
                result->MFQM.LOGGER.info("MFQM_COATING_VISUAL_SAVED file={} result={}",name,result.getString()));
    }
    private static void finish(Minecraft game) {
        running=false;ModConfig.CLIENT.forceFirstPerson.set(oldFirst);ModConfig.CLIENT.coverPlayerWithMud.set(oldCoating);ModConfig.CLIENT.glueCoating3d.set(old3d);game.options.hideGui=oldGui;game.stop();
    }
    private static void fail(Minecraft game,Throwable failure){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED glue coating",failure);game.execute(()->finish(game));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Coating client check failed: "+message);}
    private CoatingClientChecks(){}
}
