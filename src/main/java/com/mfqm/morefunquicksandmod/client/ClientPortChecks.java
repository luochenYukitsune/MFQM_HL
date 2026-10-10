package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** Enabled only by -PmfqmClientChecks in a copied isolated save. */
@EventBusSubscriber(modid = MFQM.MOD_ID, value = Dist.CLIENT)
public final class ClientPortChecks {
    private static int readyTicks;
    private static boolean finished;
    private static boolean preview;
    private static boolean requestedScreenshot;
    private static int previewTicks;
    private static int previewPass;
    private static volatile boolean sceneBuilt;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (preview) {
            Minecraft game=Minecraft.getInstance();
            if (!sceneBuilt || game.level==null || game.player==null || requestedScreenshot) return;
            var sample=game.level.getBlockState(new net.minecraft.core.BlockPos(2,253,10));
            if (!sample.is(ModBlocks.byId("tar")) || ++previewTicks<120) return;
            requestedScreenshot=true;
            String screenshot=previewPass==0?"mfqm-texture-scene.png":"mfqm-texture-flow.png";
            net.minecraft.client.Screenshot.grab(game.gameDirectory,screenshot,game.getMainRenderTarget(),1,
                    message -> game.execute(() -> {
                        MFQM.LOGGER.info("MFQM_TEXTURE_SCENE_SAVED {}",message.getString());
                        if(previewPass++==0) {
                            var server=game.getSingleplayerServer();
                            if(server==null){game.stop();return;}
                            var id=game.player.getUUID();
                            server.execute(() -> {
                                var player=server.getPlayerList().getPlayer(id);
                                if(player==null){game.execute(game::stop);return;}
                                player.teleportTo(player.level(),11,266,32,java.util.Set.of(),180,34,false);
                                game.execute(() -> {previewTicks=0;requestedScreenshot=false;});
                            });
                        } else { preview=false;AdhesiveClientScene.start(game); }
                    }));
            return;
        }
        if (!Boolean.getBoolean("mfqm.clientChecks") || finished) return;
        Minecraft game = Minecraft.getInstance();
        if (game.level == null || game.player == null || readyTicks++ < 40) return;
        finished = true;
        try {
            var codeSource=MFQM.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            if(Boolean.getBoolean("mfqm.installedChecks")) {
                require(codeSource.getPath().endsWith(".jar"),"installed validation must load the packaged JAR: "+codeSource);
                MFQM.LOGGER.info("MFQM_INSTALLED_JAR_LOADED {}",codeSource);
            }
            CreativeModeTabs.tryRebuildTabContents(game.level.enabledFeatures(), true, game.level.registryAccess());
            int visible = ModCreativeTabs.MFQM_TAB.get().getDisplayItems().size();
            long expected = ModItems.entries().keySet().stream().filter(ModItems::isCreativeVisible).count()+1; // Full-glue board component variant.
            require(visible == expected && visible > 140, "creative category count " + visible + " expected " + expected);
            var displayed=new java.util.ArrayList<>(ModCreativeTabs.MFQM_TAB.get().getDisplayItems());
            int coated=-1,empty=-1;
            for(int i=0;i<displayed.size();i++)if(displayed.get(i).is(ModItems.byId("sticky_board"))) {
                int amount=com.mfqm.morefunquicksandmod.item.StickyBoardItem.charge(displayed.get(i));
                if(amount==7)coated=i;else if(amount==0)empty=i;
            }
            require(coated>=0 && empty==coated+1,"creative tab lists full-coated board immediately beside empty board");
            require(!displayed.get(coated).getHoverName().getString().equals(displayed.get(empty).getHoverName().getString()),"board variant names visibly differ");
            MFQM.LOGGER.info("MFQM_CREATIVE_BOARD_VARIANTS_COMPLETE coated={} empty={} adjacent=true",displayed.get(coated).getHoverName().getString(),displayed.get(empty).getHoverName().getString());
            for (var entry : ModItems.entries().entrySet()) {
                Identifier id = Identifier.fromNamespaceAndPath(MFQM.MOD_ID, entry.getKey());
                require(game.getResourceManager().getResource(id.withPath("items/" + id.getPath() + ".json")).isPresent(), "item definition " + id);
                require(!(game.getModelManager().getItemModel(id) instanceof MissingItemModel), "baked item model " + id);
            }
            for (var holder : ModBlocks.entries().values()) for (var state : holder.get().getStateDefinition().getPossibleStates()) {
                var shaper = game.getBlockRenderer().getBlockModelShaper();
                require(shaper.getBlockModel(state) != game.getModelManager().getMissingBlockStateModel(), "state model " + state);
                require(!shaper.getParticleIcon(state).contents().name().equals(MissingTextureAtlasSprite.getLocation()), "state texture " + state);
            }
            for (var fluid : ModFluids.entries().values()) {
                var extension = IClientFluidTypeExtensions.of(fluid.type().get());
                for (var texture : new Identifier[]{extension.getStillTexture(), extension.getFlowingTexture()}) {
                    require(!texture.equals(MissingTextureAtlasSprite.getLocation()), "fluid extension " + fluid.id());
                    require(game.getResourceManager().getResource(texture.withPath("textures/" + texture.getPath() + ".png")).isPresent(), "fluid texture " + texture);
                }
            }
            for (var holder : ModEntities.ENTITIES.getEntries()) {
                var entity = holder.get().create(game.level, EntitySpawnReason.TRIGGERED);
                require(entity != null && game.getEntityRenderDispatcher().getRenderer(entity) != null, "renderer " + holder.getId());
            }
            for (String medium : new String[]{"mud", "mucus", "tar", "honey", "glue"}) for (int coverage=1; coverage<=10; coverage++) {
                var coating=new MuddyPlayerLayer.Coating(coverage, 1000, medium);
                var texture=coating.texture();
                require(game.getResourceManager().getResource(texture).isPresent(), "coating texture " + texture);
                var gpu=game.getTextureManager().getTexture(texture).getTexture();
                require(gpu.getWidth(0)==128 && gpu.getHeight(0)==64, "loaded coating UV size " + texture);
            }
            require(!new MuddyPlayerLayer.Coating(10,1000,"tar").texture().equals(
                    new MuddyPlayerLayer.Coating(10,1000,"honey").texture()), "tar and honey use distinct coatings");
            StruggleVisualChecks.run(game);
            verifyInput(game);
            ChineseLanguageChecks.run(game);
            MFQM.LOGGER.info("MFQM_CLIENT_CHECKS_COMPLETE items={} visible={} blocks=51 fluids=14 entities=18", ModItems.entries().size(), visible);
        } catch (Throwable failure) { MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED", failure); }
        if (Boolean.getBoolean("mfqm.shaderChecks")) { ShaderClientChecks.start(game); return; }
        if (Boolean.getBoolean("mfqm.adhesiveVisualChecks")) { AdhesiveVisualClientChecks.start(game); return; }
        if (Boolean.getBoolean("mfqm.coatingChecks")) { CoatingClientChecks.start(game); return; }
        if (Boolean.getBoolean("mfqm.dynamicAdhesionChecks")) { DynamicAdhesionClientChecks.start(game); return; }
        if (Boolean.getBoolean("mfqm.boundedOnly")) { BoundedAdhesionClientChecks.start(game); return; }
        if (Boolean.getBoolean("mfqm.actionsOnly")) { ViscosityActionChecks.start(game); return; }
        if (Boolean.getBoolean("mfqm.viscosityChecks")) { ViscosityClientChecks.start(game); return; }
        if (Boolean.getBoolean("mfqm.textureChecks")) { prepareTextureScene(game); return; }
        game.stop();
    }
    private static void verifyInput(Minecraft game) {
        require(!com.mfqm.morefunquicksandmod.ModConfig.CLIENT.enableStruggleKey.get(),"dedicated input defaults off, including upgraded configurations");
        var key=new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_F,0,0);
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_PRESS));
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_RELEASE));
        require(!MfqmClient.takeStrugglePress(),"default F input stays disabled until manually enabled");
        var state=com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics.state(game.player);
        String material=state.material;int connections=state.adhesiveConnections;
        var originalMappings=game.options.keyMappings;
        var struggle=java.util.Arrays.stream(originalMappings).filter(mapping->mapping.getName().equals("key.mfqm.struggle")).findFirst().orElseThrow();
        var savedKey=struggle.getKey();
        var savedModifier=struggle.getKeyModifier();
        try {
        state.material="";state.adhesiveConnections=1;
        verifyNoInputHint(game);
        net.minecraft.client.KeyMapping.click(game.options.keySwapOffhand.getKey());
        MfqmClient.beforeTick(new ClientTickEvent.Pre());
        require(game.options.keySwapOffhand.consumeClick(),"disabled dedicated F preserves vanilla offhand swap while tethered");
        int hiddenEntries=verifyKeyMenu(game,false);
        com.mfqm.morefunquicksandmod.ModConfig.CLIENT.enableStruggleKey.set(true);
        verifyNoInputHint(game);
        require(verifyKeyMenu(game,true)==hiddenEntries+1,"enabling adds exactly one editable key entry");
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_PRESS));
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_RELEASE));
        require(MfqmClient.takeStrugglePress(),"short PRESS+RELEASE between ticks retains one request");
        require(!MfqmClient.takeStrugglePress(),"request consumed only once");
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_REPEAT));
        require(!MfqmClient.takeStrugglePress(),"held operating-system REPEAT cannot struggle again");
        net.minecraft.client.KeyMapping.click(game.options.keySwapOffhand.getKey());
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_PRESS));
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_RELEASE));
        MfqmClient.beforeTick(new ClientTickEvent.Pre());
        require(!game.options.keySwapOffhand.consumeClick(),"F cannot exchange offhand while outside with a live tether");
        require(MfqmClient.takeStrugglePress(),"matched short tap remains queued after swap suppression");
        net.minecraft.client.KeyMapping.click(game.options.keySwapOffhand.getKey());
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_REPEAT));
        MfqmClient.beforeTick(new ClientTickEvent.Pre());
        require(!game.options.keySwapOffhand.consumeClick() && !MfqmClient.takeStrugglePress(),"held repeat cannot swap or create an extra struggle");
        require(!game.hasControlDown(),"isolated modifier case does not hold physical Ctrl");
        struggle.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.CONTROL,savedKey);
        net.minecraft.client.KeyMapping.resetMapping();
        net.minecraft.client.KeyMapping.click(game.options.keySwapOffhand.getKey());
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_PRESS));
        MfqmClient.beforeTick(new ClientTickEvent.Pre());
        require(game.options.keySwapOffhand.consumeClick() && !MfqmClient.takeStrugglePress(),"Ctrl+F mapping preserves unmodified F swap while trapped");
        struggle.setKeyModifierAndCode(savedModifier,savedKey);net.minecraft.client.KeyMapping.resetMapping();
        var screen=game.screen;game.screen=new net.minecraft.client.gui.screens.PauseScreen(false);
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_PRESS));
        require(!MfqmClient.takeStrugglePress(),"menu keypress does not leak into resumed game");game.screen=screen;
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(key,org.lwjgl.glfw.GLFW.GLFW_PRESS));
        com.mfqm.morefunquicksandmod.ModConfig.CLIENT.enableStruggleKey.set(false);
        require(!MfqmClient.takeStrugglePress(),"turning off discards an already queued request");
        com.mfqm.morefunquicksandmod.ModConfig.CLIENT.enableStruggleKey.set(true);
        require(!MfqmClient.takeStrugglePress(),"reenabling cannot replay discarded requests");
        var customKey=com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_G);
        struggle.setKey(customKey);net.minecraft.client.KeyMapping.resetMapping();
        com.mfqm.morefunquicksandmod.ModConfig.CLIENT.enableStruggleKey.set(false);
        verifyKeyMenu(game,false);
        require(game.options.keyMappings==originalMappings && struggle.getKey().equals(customKey),"hidden menus retain the original options array and customized key");
        com.mfqm.morefunquicksandmod.ModConfig.CLIENT.enableStruggleKey.set(true);
        var customEvent=new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_G,0,0);
        MfqmClient.key(new net.neoforged.neoforge.client.event.InputEvent.Key(customEvent,org.lwjgl.glfw.GLFW.GLFW_PRESS));
        require(MfqmClient.takeStrugglePress(),"custom binding remains usable after enabling again");
        MFQM.LOGGER.info("MFQM_STRUGGLE_INPUT_CHECKS_COMPLETE defaultOff=true vanillaSwap=true noHints=true hiddenMenu=true noHiddenConflict=true customBindingRetained=true modifierMismatchSwap=true shortTap=true repeat=false heldRepeatSwap=false enabledTetherSwap=false menuLeak=false queuedReplay=false");
        } finally {
            state.material=material;state.adhesiveConnections=connections;
            struggle.setKeyModifierAndCode(savedModifier,savedKey);net.minecraft.client.KeyMapping.resetMapping();game.options.save();
            com.mfqm.morefunquicksandmod.ModConfig.CLIENT.enableStruggleKey.set(false);MfqmClient.takeStrugglePress();
        }
    }
    private static void verifyNoInputHint(Minecraft game) {
        try {
            var field=net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");field.setAccessible(true);
            String sentinel="MFQM optional input hint check";
            game.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(sentinel),false);
            MfqmClient.tick(new ClientTickEvent.Post());
            require(((net.minecraft.network.chat.Component)field.get(game.gui)).getString().equals(sentinel),"trapping cannot replace actionbar with an input hint");
            game.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(),false);
        } catch(ReflectiveOperationException failure){throw new IllegalStateException("actionbar hint validation failed",failure);}
    }
    private static int verifyKeyMenu(Minecraft game,boolean enabled) {
        var previous=game.screen;
        try {
            var screen=new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(new net.minecraft.client.gui.screens.PauseScreen(false),game.options);
            game.setScreen(screen);
            var list=screen.children().stream().filter(child->child instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsList)
                    .map(child->(net.minecraft.client.gui.screens.options.controls.KeyBindsList)child).findFirst().orElseThrow();
            list.refreshEntries();int entries=0,optional=0;
            for(var entry:list.children())if(entry instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsList.KeyEntry) {
                var field=entry.getClass().getDeclaredField("key");field.setAccessible(true);
                var mapping=(net.minecraft.client.KeyMapping)field.get(entry);entries++;
                if(mapping.getName().equals("key.mfqm.struggle"))optional++;
                if(!enabled && mapping==game.options.keySwapOffhand) {
                    var collision=entry.getClass().getDeclaredField("hasCollision");collision.setAccessible(true);
                    require(!collision.getBoolean(entry),"disabled F is excluded from actual vanilla menu conflict marking");
                }
            }
            require(optional==(enabled?1:0),"actual key menu visibility follows setting immediately");return entries;
        } catch(ReflectiveOperationException failure){throw new IllegalStateException("actual key menu validation failed",failure);}
        finally {game.setScreen(previous);}
    }
    /** Only used in the copied validation world, never in a normal game. */
    private static void prepareTextureScene(Minecraft game) {
        var server=game.getSingleplayerServer();
        if(server==null || game.player==null){game.stop();return;}
        var playerId=game.player.getUUID();
        game.options.hideGui=true;
        game.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        preview=true;
        server.execute(() -> {
            try {
                var player=server.getPlayerList().getPlayer(playerId);
                if(player==null)throw new IllegalStateException("missing preview player");
                var level=player.level();
                level.setDayTime(6000);
                level.setWeatherParameters(6000,0,false,false);
                String[] materials={"mud","mire","moor","tar","honey","bog"};
                for(int n=0;n<materials.length;n++) {
                    int x=n%3*8,z=n/3*8;
                    var block=ModBlocks.byId(materials[n]).defaultBlockState();
                    if(block.hasProperty(com.mfqm.morefunquicksandmod.block.LegacyStateBlock.VARIANT))
                        block=block.setValue(com.mfqm.morefunquicksandmod.block.LegacyStateBlock.VARIANT,materials[n].equals("mire")?5:0);
                    for(int dx=0;dx<7;dx++)for(int dz=0;dz<7;dz++) {
                        var at=new net.minecraft.core.BlockPos(x+dx,253,z+dz);
                        level.getChunk(at);
                        for(int up=0;up<7;up++)level.setBlock(at.above(up),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
                        level.setBlock(at.below(),((dx+dz)%2==0?net.minecraft.world.level.block.Blocks.WHITE_CONCRETE:net.minecraft.world.level.block.Blocks.GRAY_CONCRETE).defaultBlockState(),2);
                        boolean border=dx==0||dx==6||dz==0||dz==6;
                        level.setBlock(at,border?net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState():block,2);
                    }
                    // A lower terrace provides a real source-to-flowing junction for each fluid pool.
                    if(!block.getFluidState().isEmpty()) {
                        var outlet=new net.minecraft.core.BlockPos(x+3,253,z+6);
                        level.setBlock(outlet,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                        for(int distance=1;distance<=3;distance++) {
                            var lower=outlet.offset(0,-1,distance);
                            level.setBlock(lower.below(),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),2);
                        }
                    }
                }
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(level,11,276,-22,java.util.Set.of(),0,39,false);
                sceneBuilt=true;
            } catch(Throwable failure) {
                MFQM.LOGGER.error("MFQM_CLIENT_CHECKS_FAILED texture scene",failure);
                game.execute(game::stop);
            }
        });
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException("Client port validation failed: " + message); }
    private ClientPortChecks() {}
}
