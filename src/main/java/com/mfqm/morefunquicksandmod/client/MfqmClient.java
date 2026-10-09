package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import com.mfqm.morefunquicksandmod.registry.ModParticles;
import com.mfqm.morefunquicksandmod.registry.ModBlockEntities;
import com.mfqm.morefunquicksandmod.entity.ConnectorEntity;
import com.google.common.reflect.TypeToken;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/** Dist-gated client entry point. No client classes are referenced by the common mod constructor. */
@EventBusSubscriber(modid=MFQM.MOD_ID,value=Dist.CLIENT)
public final class MfqmClient {
    public static final ModelLayerLocation BLOB_MODEL=new ModelLayerLocation(id("blob"),"main");
    public static final ModelLayerLocation BEE_MODEL=new ModelLayerLocation(id("bee"),"main");
    private static final KeyMapping.Category CONTROLS=new KeyMapping.Category(id("controls"));
    private static final KeyMapping REEL=new KeyMapping("key.mfqm.reel_in",org.lwjgl.glfw.GLFW.GLFW_KEY_R,CONTROLS);
    private static final KeyMapping STRUGGLE=new KeyMapping("key.mfqm.struggle",org.lwjgl.glfw.GLFW.GLFW_KEY_F,CONTROLS);
    private static final KeyMapping RELEASE=new KeyMapping("key.mfqm.release",org.lwjgl.glfw.GLFW.GLFW_KEY_X,CONTROLS);
    private static boolean previousJump,previousSneak,previousMoving,pendingStruggle;
    private static int inputTicks;
    private static boolean trappingHintShown;
    private static final LegacyCoatingModel WIDE_COATING=new LegacyCoatingModel(false);
    private static final LegacyCoatingModel SLIM_COATING=new LegacyCoatingModel(true);
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        net.neoforged.fml.ModList.get().getModContainerById(MFQM.MOD_ID).ifPresent(container ->
            container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mod,parent) -> new net.neoforged.neoforge.client.gui.ConfigurationScreen(mod,parent)));
    }
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.registerCategory(CONTROLS);event.register(REEL);event.register(STRUGGLE);event.register(RELEASE);}
    @SubscribeEvent public static void models(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(BLOB_MODEL,LegacyBlobModel::layer);event.registerLayerDefinition(BEE_MODEL,LegacyBeeModel::layer);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(ModEntities.VORE_SLIME.get(),BlobRenderer::new);event.registerEntityRenderer(ModEntities.MUDDY_BLOB.get(),BlobRenderer::new);
        event.registerEntityRenderer(ModEntities.SAND_BLOB.get(),BlobRenderer::new);event.registerEntityRenderer(ModEntities.TAR_SLIME.get(),BlobRenderer::new);
        event.registerEntityRenderer(ModEntities.BEE.get(),MfqmBeeRenderer::new);
        event.registerEntityRenderer(ModEntities.TENTACLES.get(),HelperRenderer::new);event.registerEntityRenderer(ModEntities.MUD_TENTACLES.get(),HelperRenderer::new);
        event.registerEntityRenderer(ModEntities.BUBBLE.get(),HelperRenderer::new);event.registerEntityRenderer(ModEntities.TAR_TREADS.get(),HelperRenderer::new);
        event.registerEntityRenderer(ModEntities.SLIME_HOLE.get(),HelperRenderer::new);event.registerEntityRenderer(ModEntities.LONG_STICK.get(),HelperRenderer::new);
        event.registerEntityRenderer(ModEntities.ROPE.get(),HelperRenderer::new);event.registerEntityRenderer(ModEntities.HOOK.get(),HelperRenderer::new);event.registerEntityRenderer(ModEntities.RESCUE.get(),HelperRenderer::new);
        event.registerEntityRenderer(ModEntities.SINKING_POTION.get(),ThrownItemRenderer::new);event.registerEntityRenderer(ModEntities.LIQUID_BALL.get(),ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.ADHESIVE_TETHER.get(),AdhesiveTetherRenderer::new);
        event.registerEntityRenderer(ModEntities.STUCK_BOOTS.get(),StuckBootsRenderer::new);
        for(String type:new String[]{"blossom","larvae","meat_wall"})event.registerBlockEntityRenderer(ModBlockEntities.byId(type),MechanismRenderer::new);
    }
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event){ event.registerSpriteSet(ModParticles.MUD_BUBBLE.get(),MudBubbleParticle.Provider::new); }
    @SubscribeEvent public static void coatingReload(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) {
        event.addListener(id("glue_coating"),new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(net.minecraft.server.packs.resources.ResourceManager resources,net.minecraft.util.profiling.ProfilerFiller profiler){return null;}
            @Override protected void apply(Void unused,net.minecraft.server.packs.resources.ResourceManager resources,net.minecraft.util.profiling.ProfilerFiller profiler){GlueCoatingRenderer.clear();}
        });
    }
    @SubscribeEvent public static void playerLayers(EntityRenderersEvent.AddLayers event){
        for(var skin:event.getSkins()){
            var renderer=event.getPlayerRenderer(skin);if(renderer!=null)renderer.addLayer(new MuddyPlayerLayer(renderer,skin==net.minecraft.world.entity.player.PlayerModelType.SLIM));
        }
    }
    @SubscribeEvent public static void renderState(RegisterRenderStateModifiersEvent event){
        event.registerEntityModifier(new TypeToken<AvatarRenderer<?>>() {},(Entity entity,AvatarRenderState state)-> {
            state.setRenderData(MuddyPlayerLayer.COATING,MuddyPlayerLayer.Coating.snapshot(QuicksandPhysics.state(entity)));
            StruggleClient.capture(entity,state);
            FirstPersonCompatibility.capture(entity,state);
        });
    }
    @SubscribeEvent public static void struggleHand(RenderHandEvent event){StruggleClient.hand(event);}
    @SubscribeEvent public static void struggleCamera(ViewportEvent.ComputeCameraAngles event){StruggleClient.camera(event);}
    @SubscribeEvent public static void renderFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event){FirstPersonCompatibility.beginFrame();FirstPersonTetherProof.beginFrame();FlatCoatingTextures.beginFrame();}
    @SubscribeEvent public static void adhesiveTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        var item=event.getItemStack().getItem();
        String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(MFQM.MOD_ID))return;
        String[] hints=switch(id) {
            case "glue_bucket" -> new String[]{"tooltip.mfqm.glue"};
            case "sticky_board" -> new String[]{"tooltip.mfqm.sticky_board"};
            case "gas_mask" -> new String[]{"itemGasMask.instruction"};
            case "life_jacket" -> new String[]{"tooltip.mfqm.life_jacket"};
            case "wading_boots" -> new String[]{"tooltip.mfqm.wading_boots"};
            case "sinking_potion" -> new String[]{"itemSinkingPotion.Text"};
            case "splash_sinking_potion" -> new String[]{"itemSPotion.Text"};
            case "hot_chocolate" -> new String[]{"itemHotChocolate.Text"};
            case "broken_grappling_hook" -> new String[]{"itemGrapplingHookBRK.instruction1","itemGrapplingHookBRK.instruction2"};
            default -> new String[0];
        };
        for(String hint:hints)event.getToolTip().add(net.minecraft.network.chat.Component.translatable(hint).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @SubscribeEvent public static void arm(RenderArmEvent event){
        if(!ModConfig.CLIENT.coverPlayerWithMud.get())return;
        var coat=MuddyPlayerLayer.Coating.snapshot(QuicksandPhysics.state(event.getPlayer()));
        if(coat.level()<=0 || coat.ticks()<=50)return;
        var model=event.getPlayer().getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM?SLIM_COATING:WIDE_COATING;
        var arm=event.getArm()==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        arm.resetPose();arm.visible=true;arm.zRot=event.getArm()==HumanoidArm.RIGHT?0.1F:-0.1F;
        boolean slim=event.getPlayer().getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM;
        GlueCoatingRenderer.submit(arm,event.getArm()==HumanoidArm.RIGHT?"right_arm":"left_arm",slim,coat,
                event.getPoseStack(),event.getSubmitNodeCollector(),event.getPackedLight(),true,true);
    }
    @SubscribeEvent public static void beforeTick(ClientTickEvent.Pre event){
        var game=Minecraft.getInstance();
        if(game.player!=null && game.screen==null && !com.mfqm.morefunquicksandmod.gameplay.AdhesionController.exempt(game.player)
                && (!QuicksandPhysics.state(game.player).material.isEmpty() || QuicksandPhysics.state(game.player).adhesiveConnections>0)
                && STRUGGLE.getKey().equals(game.options.keySwapOffhand.getKey())) {
            while(game.options.keySwapOffhand.consumeClick()) {} // F is reserved for struggle only while trapped.
        }
    }
    @SubscribeEvent public static void key(net.neoforged.neoforge.client.event.InputEvent.Key event){
        recordPress(com.mojang.blaze3d.platform.InputConstants.getKey(event.getKeyEvent()),event.getAction());
    }
    @SubscribeEvent public static void mouse(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Post event){
        recordPress(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(event.getButton()),event.getAction());
    }
    private static void recordPress(com.mojang.blaze3d.platform.InputConstants.Key key,int action){
        var game=Minecraft.getInstance();
        if(game.screen==null && game.player!=null && action==org.lwjgl.glfw.GLFW.GLFW_PRESS && STRUGGLE.isActiveAndMatches(key))pendingStruggle=true;
    }
    static boolean takeStrugglePress(){boolean press=pendingStruggle;pendingStruggle=false;return press;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft minecraft=Minecraft.getInstance();if(minecraft.player==null){inputTicks=0;previousJump=false;previousSneak=false;previousMoving=false;pendingStruggle=false;trappingHintShown=false;return;}
        var trappedState=QuicksandPhysics.state(minecraft.player);
        boolean trapped=!com.mfqm.morefunquicksandmod.gameplay.AdhesionController.exempt(minecraft.player)
                && (!trappedState.material.isEmpty() || trappedState.adhesiveConnections>0);
        if(trapped && !trappingHintShown)minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.mfqm.trapped",STRUGGLE.getTranslatedKeyMessage()),true);
        trappingHintShown=trapped;
        boolean jump=minecraft.screen==null && minecraft.player.input.keyPresses.jump();
        boolean sneak=minecraft.screen==null && minecraft.player.input.keyPresses.shift();
        var keys=minecraft.player.input.keyPresses;
        boolean moving=minecraft.screen==null && (keys.forward() || keys.backward() || keys.left() || keys.right());
        if(inputTicks++%5==0 || jump!=previousJump || sneak!=previousSneak || moving!=previousMoving){ClientNetworking.sendInput(jump,sneak,moving);previousJump=jump;previousSneak=sneak;previousMoving=moving;}
        while(STRUGGLE.consumeClick()) {} // Raw PRESS events retain short taps and exclude operating-system REPEAT.
        boolean struggle=takeStrugglePress();
        if(minecraft.screen==null && struggle)ClientNetworking.sendStruggle();
        int control=RELEASE.consumeClick()?2:REEL.isDown()?0:-1;
        if(minecraft.screen==null && control>=0)for(ConnectorEntity connector:minecraft.level.getEntitiesOfClass(ConnectorEntity.class,minecraft.player.getBoundingBox().inflate(52))){
            if(connector.visibleOwner()==minecraft.player){ClientNetworking.sendControl(connector.getId(),control);break;}
        }
        if(QuicksandPhysics.state(minecraft.player).eyesCovered && ModConfig.CLIENT.forceFirstPerson.get()) minecraft.options.setCameraType(CameraType.FIRST_PERSON);
    }
    @SubscribeEvent public static void obstruction(RenderGuiEvent.Pre event){
        Minecraft minecraft=Minecraft.getInstance();if(minecraft.player==null || !minecraft.options.getCameraType().isFirstPerson())return;
        var state=QuicksandPhysics.state(minecraft.player);var gui=event.getGuiGraphics();
        if(state.eyesCovered && ModConfig.CLIENT.quicksandOpacity.get()){
            var block=QuicksandPhysics.immersedEyeBlock(minecraft.player);
            var sprite=minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(block,minecraft.level,minecraft.player.blockPosition());
            var name=sprite.contents().name();var texture=Identifier.fromNamespaceAndPath(name.getNamespace(),"textures/"+name.getPath()+".png");
            gui.blit(RenderPipelines.GUI_TEXTURED,texture,0,0,0,0,gui.guiWidth(),gui.guiHeight(),16,16,16,16,0xe0303030);
        }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        Minecraft minecraft=Minecraft.getInstance();if(minecraft.player==null || minecraft.options.hideGui)return;
        var state=QuicksandPhysics.state(minecraft.player);var gui=event.getGuiGraphics();
        boolean mire=com.mfqm.morefunquicksandmod.registry.ModFluids.isWaterMire(state.material);
        if(ModConfig.CLIENT.customAirHud.get() && (state.air<300 || mire && minecraft.player.getAirSupply()<300) && !minecraft.player.hasInfiniteMaterials()){
            int air=Mth.clamp(Math.min(state.air,minecraft.player.getAirSupply()),0,300);int filled=(air+29)/30;
            int x=gui.guiWidth()/2+91,y=gui.guiHeight()-49;
            for(int i=0;i<10;i++)gui.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.withDefaultNamespace(i<filled?"hud/air":"hud/air_bursting"),x-i*8-9,y,9,9);
        }
    }
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath(MFQM.MOD_ID,path);}
    private MfqmClient(){}
}
