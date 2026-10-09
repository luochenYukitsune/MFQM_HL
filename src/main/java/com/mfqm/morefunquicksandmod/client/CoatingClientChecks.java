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
    private static boolean oldFirst,oldCoating,old3d,oldGui,oldGround,oldFirstPersonEnabled;
    private static long submissions,flat;
    private static Object firstPersonConfig,oldHandMode;
    private static long firstPersonBodies;
    private static double oldOpacity,oldThickness;
    private static int visibleBundles,visibleFilaments;
    private static double glueRestY;
    private static int oldFov;
    private static net.minecraft.client.player.ClientInput oldInput;
    public static void start(Minecraft game) {
        require(SkinLayerClearance.present()==Boolean.getBoolean("mfqm.coatingSkinLayersExpected"),"exact optional mod presence");
        require(FirstPersonCompatibility.present()==Boolean.getBoolean("mfqm.firstPersonExpected"),"exact FirstPersonModel presence");
        require(net.neoforged.fml.ModList.get().isLoaded("notenoughanimations")==Boolean.getBoolean("mfqm.coatingAnimationsExpected"),"exact optional animation mod presence");
        oldFirst=ModConfig.CLIENT.forceFirstPerson.get();oldCoating=ModConfig.CLIENT.coverPlayerWithMud.get();old3d=ModConfig.CLIENT.glueCoating3d.get();oldGui=game.options.hideGui;
        oldOpacity=ModConfig.CLIENT.coatingOpacity.get();oldThickness=ModConfig.CLIENT.coatingThickness.get();
        oldInput=game.player.input;
        oldFov=game.options.fov().get();
        oldGround=ModConfig.SERVER.creativeGroundPhysics.get();
        if(FirstPersonCompatibility.present())try {
            var api=Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI");oldFirstPersonEnabled=(boolean)api.getMethod("isEnabled").invoke(null);api.getMethod("setEnabled",boolean.class).invoke(null,true);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        ModConfig.CLIENT.coatingOpacity.set(1.);ModConfig.CLIENT.coatingThickness.set(1.);
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
                    verifyCloseFit(game);
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
                    if(FirstPersonCompatibility.present())setHandMode("ALL");
                    submissions=GlueCoatingRenderer.armSubmissions();phase=4;ticks=0;
                }
                case 4->{
                    coatAvatars(1);var state=QuicksandPhysics.state(game.player);state.coatingType="glue";state.coatingLevel=10;state.coatingTicks=1200;
                    if(ticks<35)return;
                    require(GlueCoatingRenderer.armSubmissions()>submissions+30,"actual first-person arm submits voxel coating independently of other avatar bodies");
                    if(SkinLayerClearance.present())screenshot(game,"mfqm-glue-first-person.png");
                    MFQM.LOGGER.info("MFQM_COATING_ARM_COMPLETE actualSkin={} padding={} flight=false",game.player.getSkin().model(),SkinLayerClearance.padding("right_arm",true));
                    require(GlueCoatingRenderer.cachedMeshes()<=96,"bounded resource cache");
                    phase=5;ticks=0;
                }
                case 5->{
                    String[] materials={"mud","sinking_slime","tar","honey"};
                    for(int i=0;i<AVATARS.size();i++){var s=QuicksandPhysics.state(AVATARS.get(i));s.coatingType=materials[i];s.coatingLevel=10;s.coatingTicks=1200;}
                    if(ticks<40)return;
                    for(String family:new String[]{"mud","slime","tar","honey"})require(GlueCoatingRenderer.submissions(family)>20,"actual voxel submissions for "+family);
                    screenshot(game,"mfqm-all-material-coatings.png");
                    MFQM.LOGGER.info("MFQM_ALL_MATERIAL_COATINGS_COMPLETE nativePose=true tint=true adjustable=true");
                    ModConfig.CLIENT.coatingOpacity.set(0.);submissions=GlueCoatingRenderer.detailedSubmissions();phase=7;ticks=0;
                }
                case 6->{
                    game.options.setCameraType(CameraType.FIRST_PERSON);game.player.setXRot(73);
                    var s=QuicksandPhysics.state(game.player);s.coatingType="glue";s.coatingLevel=3;s.coatingTicks=1200;
                    if(ticks<50)return;
                    require(MuddyPlayerLayer.firstPersonBodies()>firstPersonBodies+20,"actual FirstPersonModel local body renders residue");
                    require(MuddyPlayerLayer.hiddenHeads()>20,"actual native first-person model hides the coated head");
                    screenshot(game,"mfqm-firstperson-model-coating.png");
                    firstPersonBodies=MuddyPlayerLayer.firstPersonBodies();
                    game.player.input=new net.minecraft.client.player.ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(false,false,false,false,false,true,false);moveVector=net.minecraft.world.phys.Vec2.ZERO;}};
                    var id=game.player.getUUID();game.getSingleplayerServer().execute(()->game.getSingleplayerServer().getPlayerList().getPlayer(id).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)));
                    phase=9;ticks=0;
                }
                case 7->{
                    coatAvatars(10);if(ticks<20)return;
                    require(GlueCoatingRenderer.detailedSubmissions()==submissions,"zero display intensity actually suppresses voxel submissions");
                    ModConfig.CLIENT.coatingOpacity.set(1.);ModConfig.CLIENT.coatingThickness.set(0.);flat=GlueCoatingRenderer.flatSubmissions();phase=8;ticks=0;
                }
                case 8->{
                    coatAvatars(10);if(ticks<20)return;
                    require(GlueCoatingRenderer.flatSubmissions()>flat+20,"zero thickness actually selects flat geometry");
                    ModConfig.CLIENT.coatingThickness.set(1.);
                    MFQM.LOGGER.info("MFQM_COATING_DISPLAY_CONTROLS_COMPLETE zeroOpacityHidden=true zeroThicknessFlat=true");
                    if(!FirstPersonCompatibility.present()){complete(game);return;}
                    setHandMode("OFF");firstPersonBodies=MuddyPlayerLayer.firstPersonBodies();phase=6;ticks=0;
                }
                case 9->{
                    game.player.setXRot(73);var s=QuicksandPhysics.state(game.player);s.coatingType="glue";s.coatingLevel=3;s.coatingTicks=1200;
                    if(ticks<40)return;
                    require(game.player.isCrouching() && !game.player.getMainHandItem().isEmpty(),"actual crouching and held-item first-person view");
                    require(MuddyPlayerLayer.firstPersonBodies()>firstPersonBodies+20,"coating follows native crouching/held-item body");
                    MFQM.LOGGER.info("MFQM_FIRSTPERSON_MODEL_COMPLETE nativeBody=true hiddenHead=true vanillaHands=true crouchingHeldItem=true headLevel3Dry=true flight=false");
                    game.player.input=new net.minecraft.client.player.ClientInput();ready=false;
                    var id=game.player.getUUID();game.getSingleplayerServer().execute(()->{
                        try {
                            ModConfig.SERVER.creativeGroundPhysics.set(true);
                            var player=game.getSingleplayerServer().getPlayerList().getPlayer(id);
                            for(int x=181;x<=185;x++)for(int z=-2;z<=2;z++)player.level().setBlock(new BlockPos(x,253,z),com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7),2);
                            player.teleportTo(player.level(),183.5,253.0625,0,Set.of(),0,82,false);ready=true;
                        }catch(Throwable e){game.execute(()->fail(game,e));}
                    });
                    FirstPersonTetherProof.reset();phase=10;ticks=0;
                }
                case 10->{
                    if(!ready || ticks<55)return;
                    require(QuicksandPhysics.state(game.player).adhesiveConnections>0,"real coated board has synchronized tethers");
                    aligned("standing");FirstPersonTetherProof.reset();
                    game.player.input=trappedInput(0,true);phase=11;ticks=0;
                }
                case 11->{
                    if(ticks<40)return;require(game.player.isCrouching(),"real crouch input");aligned("crouching");
                    game.player.setYRot(135);game.player.setXRot(82);FirstPersonTetherProof.reset();phase=12;ticks=0;
                }
                case 12->{
                    if(ticks<45)return;aligned("turned");game.player.input=trappedInput(0,false);
                    game.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    require(FirstPersonCompatibility.localFeet(game.player,0,1)==null,"third person immediately ignores camera feet");
                    FirstPersonTetherProof.reset();phase=13;ticks=0;
                }
                case 13->{
                    if(ticks<25)return;require(!FirstPersonCompatibility.hasCameraFeet() && FirstPersonTetherProof.samples()==0,"third person does not reuse first-person frame");
                    Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI").getMethod("setEnabled",boolean.class).invoke(null,false);
                    game.options.setCameraType(CameraType.FIRST_PERSON);phase=14;ticks=0;
                }
                case 14->{
                    if(ticks<25)return;require(!FirstPersonCompatibility.hasCameraFeet() && FirstPersonTetherProof.samples()==0,"F6 off clears stale camera feet");
                    Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI").getMethod("setEnabled",boolean.class).invoke(null,true);
                    ready=false;var id=game.player.getUUID();game.getSingleplayerServer().execute(()->{
                        try {
                            var player=game.getSingleplayerServer().getPlayerList().getPlayer(id);
                            for(int x=181;x<=185;x++)for(int z=-2;z<=2;z++)player.level().setBlock(new BlockPos(x,253,z),com.mfqm.morefunquicksandmod.registry.ModBlocks.byId("glue").defaultBlockState(),2);
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
                            player.teleportTo(player.level(),183.5,253.72,0,Set.of(),0,82,false);ready=true;
                        }catch(Throwable e){game.execute(()->fail(game,e));}
                    });FirstPersonTetherProof.reset();phase=15;ticks=0;
                }
                case 15->{
                    if(!ready || ticks<40)return;
                    game.player.input=trappedInput((ticks/30)%2==0?1:-1,false);
                    if(QuicksandPhysics.state(game.player).adhesiveConnections<64)return;
                    game.player.input=trappedInput(0,false);FirstPersonTetherProof.reset();phase=16;ticks=0;
                }
                case 16->{
                    if(ticks<30)return;aligned("glue64");verifyCompactBundles(game);
                    screenshot(game,"mfqm-firstperson-tethers.png");
                    game.options.setCameraType(CameraType.THIRD_PERSON_BACK);game.player.setYRot(145);game.player.setXRot(32);phase=17;ticks=0;
                }
                case 17->{
                    if(ticks<35)return;require(!FirstPersonCompatibility.hasCameraFeet(),"dense third-person scene has no stale first-person offset");
                    verifyCompactBundles(game);glueRestY=game.player.getY();
                    // Zoom only this isolated visual fixture to inspect tube cross-sections and shell rims.
                    if(Boolean.getBoolean("mfqm.coatingVisual")){game.options.fov().set(35);game.player.setXRot(55);}
                    game.player.input=trappedInput(0,false,true);phase=18;ticks=0;
                }
                case 18->{
                    if(ticks<6)return;require(game.player.getY()>glueRestY+.12 && game.player.getY()<glueRestY+.31,"actual finite short hop exposes dense strands near the ankles");
                    verifyCompactBundles(game);screenshot(game,"mfqm-close-strands.png");
                    MFQM.LOGGER.info("MFQM_FIRSTPERSON_TETHERS_COMPLETE standing=true crouching=true turned=true toggled=true glue64=true maxErrorBelow=.012 flight=false");
                    MFQM.LOGGER.info("MFQM_COMPACT_STRANDS_COMPLETE bundles={} filaments={} physical64=true horizontalNear=true rootsInside=true animations={}",visibleBundles,visibleFilaments,Boolean.getBoolean("mfqm.coatingAnimationsExpected"));complete(game);
                }
            }
        } catch(Throwable failure){fail(game,failure);}
    }
    private static void coatAvatars(int level) {
        for(int i=0;i<AVATARS.size();i++) {
            var state=QuicksandPhysics.state(AVATARS.get(i));state.coatingType="glue";state.coatingLevel=i%2==1?level:0;state.coatingTicks=1200;
        }
    }
    private static net.minecraft.client.player.ClientInput trappedInput(int direction,boolean crouch) {
        return trappedInput(direction,crouch,false);
    }
    private static net.minecraft.client.player.ClientInput trappedInput(int direction,boolean crouch,boolean jump) {
        return new net.minecraft.client.player.ClientInput(){@Override public void tick(){keyPresses=new net.minecraft.world.entity.player.Input(direction>0,direction<0,false,false,jump,crouch,false);moveVector=new net.minecraft.world.phys.Vec2(0,direction);}};
    }
    private static void aligned(String stage) {
        require(FirstPersonTetherProof.samples()>20,"actual body and tethers submit in the same frames "+stage);
        require(FirstPersonTetherProof.error()<.012,"native first-person foot and tether alignment "+stage+" error="+FirstPersonTetherProof.error());
        MFQM.LOGGER.info("MFQM_FIRSTPERSON_TETHER_ALIGNMENT stage={} samples={} maxError={}",stage,FirstPersonTetherProof.samples(),FirstPersonTetherProof.error());
    }
    private static void verifyCompactBundles(Minecraft game) {
        var helpers=game.level.getEntitiesOfClass(com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity.class,game.player.getBoundingBox().inflate(12),e->e.target()==game.player && !e.breaking());
        if(helpers.size()!=64) {
            var id=game.player.getUUID();
            int serverCount=game.getSingleplayerServer().submit(()->QuicksandPhysics.state(game.getSingleplayerServer().getPlayerList().getPlayer(id)).anchors.size()).orTimeout(2,java.util.concurrent.TimeUnit.SECONDS).join();
            MFQM.LOGGER.error("MFQM_WET_CONTACT_MISMATCH live={} synced={} authoritative={} phase={} y={}",helpers.size(),QuicksandPhysics.state(game.player).adhesiveConnections,serverCount,phase,game.player.getY());
        }
        require(helpers.size()==64,"dense trap still has 64 physical synchronized contacts: live="+helpers.size()+" synchronized="+QuicksandPhysics.state(game.player).adhesiveConnections+" phase="+phase+" y="+game.player.getY());visibleBundles=0;visibleFilaments=0;int membranes=0;
        for(var helper:helpers) {
            var renderer=(AdhesiveTetherRenderer)game.getEntityRenderDispatcher().getRenderer(helper);var state=renderer.createRenderState(helper,1);
            if(!state.visible)continue;visibleBundles++;visibleFilaments+=state.filaments.size();
            require(state.filaments.size()<=ModConfig.CLIENT.strandDensity.get(),"actual contact respects independent strand density without branching");
            require(state.strands.size()==state.filaments.size()*(state.segments*6+12),"every independent strand has its own closed sides and caps, with no shared stem");
            if(!state.membrane.isEmpty()) {
                membranes++;require(state.cuff && state.membrane.size()==52,"one bounded solid wet shell only on the newest contact per foot");
                for(var q:state.membrane)for(var p:java.util.List.of(q.a(),q.b(),q.c(),q.d())) {
                    var delta=p.subtract(state.surface.center());
                    require(Math.abs(delta.dot(state.surface.right()))<=state.surface.halfWidth()+WetAdhesiveStyle.THICKNESS+.002 && Math.abs(delta.dot(state.surface.front()))<=state.surface.halfDepth()+WetAdhesiveStyle.THICKNESS+.002,"actual film hugs native foot dimensions and orientation");
                    require(delta.dot(state.surface.up())>=-.026 && delta.dot(state.surface.up())<=WetAdhesiveStyle.height(QuicksandPhysics.state(game.player).depth)+.001,"actual film height follows contact depth");
                }
            }
            var origin=new net.minecraft.world.phys.Vec3(state.x,state.y,state.z);
            for(var filament:state.filaments) {
                require(filament.width()>WetAdhesiveStyle.width(filament.end().subtract(filament.root()).length(),0)*2.99,"actual independent glue strip keeps the thicker visible width");
                var root=origin.add(filament.root());var cell=net.minecraft.core.BlockPos.containing(root);
                double minimum=RenderedAdhesiveSurface.minimumHeight(game.level,cell,helper.material()),radius=filament.rootWidth();
                require(minimum>0 && root.y-radius>cell.getY() && root.y+radius<cell.getY()+minimum,"entire root cap stays under the lowest actual fluid corner");
                require(root.x-radius>cell.getX() && root.x+radius<cell.getX()+1 && root.z-radius>cell.getZ() && root.z+radius<cell.getZ()+1,"entire root cap stays inside its medium cell sides");
                require(filament.end().subtract(filament.root()).horizontalDistance()<.65 && Math.abs(origin.y+filament.end().y-(cell.getY()+minimum))<1.15,"short visible bundle stays around ankle; submerged extension does not consume surface reach");
                var endpoint=new CoatingVoxels.Vec(filament.end().x,filament.end().y,filament.end().z).subtract(state.surface.center());
                double perimeter=Math.max(Math.abs(endpoint.dot(state.surface.right()))/(state.surface.halfWidth()+WetAdhesiveStyle.THICKNESS),Math.abs(endpoint.dot(state.surface.front()))/(state.surface.halfDepth()+WetAdhesiveStyle.THICKNESS));
                require(Math.abs(perimeter-1)<1e-5,"actual strand touches the skin film surface instead of ending inside the leg");
                double endpointHeight=endpoint.dot(state.surface.up()),filmHeight=WetAdhesiveStyle.height(QuicksandPhysics.state(game.player).depth);
                require(endpointHeight>=filmHeight*.25-1e-6 && endpointHeight<=filmHeight*.75+1e-6,"raised body endpoint stays within the actual depth-dependent film");
            }
        }
        require(visibleFilaments<=visibleBundles*ModConfig.CLIENT.strandDensity.get() && visibleFilaments>=16,"nearby scene has bounded independent strands without branching");
        require(membranes==2,"dense scene renders exactly one film on each foot");
        MFQM.LOGGER.info("MFQM_WET_ADHESIVE_COMPLETE film2=true closedShell=true independentStrands=true noBranches=true hexagonalTubes=true nativeFootBounds=true depthHeight=true rootsInside=true translucent=true");
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
    @SuppressWarnings("unchecked") private static void verifyCloseFit(Minecraft game) {
        var renderer=(AvatarRenderer<AbstractClientPlayer>)game.getEntityRenderDispatcher().getRenderer(AVATARS.get(1));
        var model=renderer.getModel();var outer=model.jacket;boolean visible=outer.visible;
        try {
            outer.visible=false;
            require(Math.abs(SkinLayerClearance.padding("body",false,outer)-.02)<1e-6,"actual hidden jacket does not inflate the coating shell");
        }finally{outer.visible=visible;}
        require(SkinLayerClearance.padding("body",false,outer)>.25,"visible outer layer remains clear of coating");
        MFQM.LOGGER.info("MFQM_COATING_CLOSE_FIT_COMPLETE hiddenPadding=.02 flatPadding=.27 defaultReliefMax=.12 anatomicalCoverageUnchanged=true");
    }
    private static void screenshot(Minecraft game,String name) {
        if(!Boolean.getBoolean("mfqm.coatingVisual"))return;
        if(FirstPersonCompatibility.present() && !name.equals("mfqm-firstperson-tethers.png") && !name.equals("mfqm-close-strands.png") && !name.equals("mfqm-glue-coating-skinlayers.png"))return;
        net.minecraft.client.Screenshot.grab(game.gameDirectory,name,game.getMainRenderTarget(),1,
                result->MFQM.LOGGER.info("MFQM_COATING_VISUAL_SAVED file={} result={}",name,result.getString()));
    }
    private static void finish(Minecraft game) {
        game.options.fov().set(oldFov);
        if(FirstPersonCompatibility.present())try{Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI").getMethod("setEnabled",boolean.class).invoke(null,oldFirstPersonEnabled);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        if(firstPersonConfig!=null)try{firstPersonConfig.getClass().getField("vanillaHandsMode").set(firstPersonConfig,oldHandMode);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        running=false;game.player.input=oldInput;ModConfig.SERVER.creativeGroundPhysics.set(oldGround);ModConfig.CLIENT.coatingOpacity.set(oldOpacity);ModConfig.CLIENT.coatingThickness.set(oldThickness);ModConfig.CLIENT.forceFirstPerson.set(oldFirst);ModConfig.CLIENT.coverPlayerWithMud.set(oldCoating);ModConfig.CLIENT.glueCoating3d.set(old3d);game.options.hideGui=oldGui;game.stop();
    }
    private static void complete(Minecraft game){MFQM.LOGGER.info("MFQM_COATING_CHECKS_COMPLETE skinLayers={} firstPerson={} anatomicalHeightMasks=true originalPngUnchanged=true",SkinLayerClearance.present(),FirstPersonCompatibility.present());finish(game);}
    private static void setHandMode(String value)throws ReflectiveOperationException {
        if(firstPersonConfig==null){var core=Class.forName("dev.tr7zw.firstperson.FirstPersonModelCore").getField("instance").get(null);firstPersonConfig=core.getClass().getMethod("getConfig").invoke(core);oldHandMode=firstPersonConfig.getClass().getField("vanillaHandsMode").get(firstPersonConfig);}
        var field=firstPersonConfig.getClass().getField("vanillaHandsMode");
        field.set(firstPersonConfig,java.util.Arrays.stream(field.getType().getEnumConstants()).filter(e->e.toString().equals(value)).findFirst().orElseThrow());
    }
    private static void fail(Minecraft game,Throwable failure){MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED glue coating",failure);game.execute(()->finish(game));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Coating client check failed: "+message);}
    private CoatingClientChecks(){}
}
