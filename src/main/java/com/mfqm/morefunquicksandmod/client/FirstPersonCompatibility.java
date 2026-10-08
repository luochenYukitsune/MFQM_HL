package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.neoforged.fml.ModList;
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Optional read-only bridge. The per-state flag survives 1.21.11's deferred render submission. */
public final class FirstPersonCompatibility {
    private static boolean resolved,failed;
    private static Class<?> stateAccess;
    private static Method camera;
    private static AdhesiveFeetSampler sampler;
    private record Feet(java.lang.ref.WeakReference<Entity> entity,AdhesiveFeetSampler.Foot right,AdhesiveFeetSampler.Foot left){}
    private static Feet feet;
    static void sampler(AdhesiveFeetSampler value){sampler=value;feet=null;}
    static void beginFrame(){feet=null;}
    static boolean hasCameraFeet(){return feet!=null;}
    static void capture(Entity entity,AvatarRenderState state) {
        if(sampler==null || !(entity instanceof net.minecraft.client.player.AbstractClientPlayer player) || !camera(state))return;
        boolean slim=player.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM;
        var origin=new Vec3(state.x,state.y,state.z);
        // FirstPersonModel restores the temporary entity position after extracting this state.
        // Preserve its actual model-space pose AND shifted world origin while both are available.
        var right=sampler.avatar(state,0,slim);var left=sampler.avatar(state,1,slim);
        feet=new Feet(new java.lang.ref.WeakReference<>(entity),right.translated(origin),left.translated(origin));
    }
    static AdhesiveFeetSampler.Foot localFeet(Entity entity,int side,float partialTick) {
        var game=net.minecraft.client.Minecraft.getInstance();
        if(feet==null || feet.entity().get()!=entity || game.getCameraEntity()!=entity || !game.options.getCameraType().isFirstPerson())return null;
        var world=side==0?feet.right():feet.left();var origin=entity.getPosition(partialTick);
        return world.translated(origin.scale(-1));
    }
    public static boolean present(){return ModList.get().isLoaded("firstperson");}
    public static boolean camera(AvatarRenderState state) {
        if(!present() || failed)return false;
        try {
            if(!resolved) {
                stateAccess=Class.forName("dev.tr7zw.firstperson.access.LivingEntityRenderStateAccess");
                camera=stateAccess.getMethod("isCameraEntity");resolved=true;
            }
            return stateAccess.isInstance(state) && (boolean)camera.invoke(state);
        } catch(ReflectiveOperationException | LinkageError e) {
            failed=true;MFQM.LOGGER.warn("FirstPersonModel state API unavailable; native model visibility remains respected",e);return false;
        }
    }
    private FirstPersonCompatibility(){}
}
