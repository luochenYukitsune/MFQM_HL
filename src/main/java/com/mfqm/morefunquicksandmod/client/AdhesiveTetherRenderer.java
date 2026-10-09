package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/** Wet foot films and independent complete volumetric strands; physics is server-owned. */
public final class AdhesiveTetherRenderer extends EntityRenderer<AdhesiveTetherEntity,AdhesiveTetherRenderer.State> {
    private final AdhesiveFeetSampler feet;
    record Filament(Vec3 root,Vec3 end,double width,double rootWidth){}
    public static final class State extends EntityRenderState {
        Vec3 foot=Vec3.ZERO,shin=Vec3.ZERO;
        int color;
        boolean visible,cuff;
        boolean local;
        int segments,side;
        java.util.List<Filament> filaments=java.util.List.of();
        java.util.List<CoatingVoxels.Quad> membrane=java.util.List.of();
        java.util.List<CoatingVoxels.Quad> strands=java.util.List.of();
        WetAdhesiveStyle.Frame surface;
    }
    public AdhesiveTetherRenderer(EntityRendererProvider.Context context) { super(context);feet=new AdhesiveFeetSampler(context);FirstPersonCompatibility.sampler(feet); }
    public AdhesiveFeetSampler feet() { return feet; }
    @Override public State createRenderState() { return new State(); }
    @Override protected boolean affectedByCulling(AdhesiveTetherEntity entity) { return false; }
    @Override public void extractRenderState(AdhesiveTetherEntity entity,State state,float tick) {
        super.extractRenderState(entity,state,tick);
        var target=entity.target();state.visible=false;
        state.local=target==net.minecraft.client.Minecraft.getInstance().getCameraEntity() && !entity.breaking();state.side=entity.side();
        if(target==null || !target.isAlive() || target.isInvisible() || target.isSpectator() || !ModConfig.CLIENT.adhesiveTethers.get()
                || entity.material().equals("tar") && !ModConfig.CLIENT.tarTreadsEffect.get())return;
        if(!AdhesiveDisplayBudget.visible(entity))return;
        var original=entity.position();
        var cell=net.minecraft.core.BlockPos.containing(original);
        double surfaceHeight=RenderedAdhesiveSurface.minimumHeight(entity.level(),cell,entity.material());
        var root=RenderedAdhesiveSurface.root(cell,surfaceHeight,original,WetAdhesiveStyle.width(0,entity.getId(),entity.material()),0,0,1);
        if(root==null)return;
        var base=root.point();
        // A surface anchor can be inside a dark block cell while the exposed leg is in daylight.
        // Sample both ends; preserve actual cave/night lighting rather than using emissive glue.
        int targetLight=net.minecraft.client.renderer.LevelRenderer.getLightColor(target.level(),net.minecraft.core.BlockPos.containing(target.getEyePosition()));
        state.lightCoords=net.minecraft.client.renderer.LightTexture.pack(
                Math.max(net.minecraft.client.renderer.LightTexture.block(state.lightCoords),net.minecraft.client.renderer.LightTexture.block(targetLight)),
                Math.max(net.minecraft.client.renderer.LightTexture.sky(state.lightCoords),net.minecraft.client.renderer.LightTexture.sky(targetLight)));
        var sampled=feet.sample(target,entity.side(),tick);
        // Do not interpolate a sinking/flowing root above the newly lowered liquid surface.
        state.x=base.x;state.y=base.y;state.z=base.z;Vec3 origin=base;
        state.foot=target.getPosition(tick).add(sampled.ankle()).subtract(origin);
        state.shin=target.getPosition(tick).add(sampled.shin()).subtract(origin);
        // Submerged length must not consume the existing visible reach above the medium.
        double opacity=CompactStrandStyle.opacity(state.foot.horizontalDistance(),target.getPosition(tick).y+sampled.ankle().y-(cell.getY()+surfaceHeight));
        if(opacity<=0)return;
        double recoil=entity.breakProgress(tick),remaining=entity.breaking()?WetAdhesiveStyle.contraction(recoil):1;
        Vec3 endpointFoot=state.foot;
        if(entity.breaking()) {
            endpointFoot=entity.breakAnkle(state.foot);state.foot=endpointFoot.scale(remaining);
            state.shin=entity.breakShin(state.shin).scale(remaining);
        }
        state.cuff=entity.cuff() && !entity.breaking();state.segments=state.distanceToCameraSq>256?3:6;
        var sampledSurface=sampled.surface();
        state.surface=new WetAdhesiveStyle.Frame(vector(endpointFoot).add(sampledSurface.center().subtract(vector(sampled.ankle()))),
                sampledSurface.up(),sampledSurface.right(),sampledSurface.front(),sampledSurface.halfWidth(),sampledSurface.halfDepth(),sampledSurface.calfHeight());
        double depth=com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics.state(target).depth;
        var membrane=new java.util.ArrayList<CoatingVoxels.Quad>();
        if(state.cuff)appendFaded(membrane,WetAdhesiveStyle.film(state.surface,depth,entity.side()),opacity);
        state.membrane=java.util.List.copyOf(membrane);
        int density=ModConfig.CLIENT.strandDensity.get();
        var filaments=new java.util.ArrayList<Filament>(density);var strands=new java.util.ArrayList<CoatingVoxels.Quad>();
        for(int i=0;i<density;i++) {
            long seed=((long)entity.getId()<<3)+i;
            var anchor=RenderedAdhesiveSurface.root(cell,surfaceHeight,original,WetAdhesiveStyle.width(0,(int)seed,entity.material()),entity.getId(),i,density);
            if(anchor==null)continue;
            var localRoot=anchor.point().subtract(origin);
            var attached=point(WetAdhesiveStyle.endpoint(state.surface,depth,seed,vector(localRoot)));
            var end=localRoot.add(attached.subtract(localRoot).scale(remaining));
            var delta=end.subtract(localRoot);double fade=CompactStrandStyle.opacity(delta.horizontalDistance(),end.y+origin.y-(cell.getY()+surfaceHeight));
            if(fade<=0)continue;
            double width=WetAdhesiveStyle.width(delta.length(),(int)seed,entity.material())*Math.max(.05,remaining);
            double rootWidth=Math.min(width,anchor.radius());
            filaments.add(new Filament(localRoot,end,width,rootWidth));
            appendFaded(strands,WetAdhesiveStyle.tube(vector(localRoot),vector(end),state.segments,rootWidth,width),fade);
        }
        state.filaments=java.util.List.copyOf(filaments);state.strands=java.util.List.copyOf(strands);
        int rgb=switch(entity.material()) {
            case "glue","sticky_board"->0xf5f4ee;
            case "tar"->0x201711;
            case "honey","wax"->0xd99b25;
            case "sinking_slime","mucus","sinky_liquid"->0x91b34a;
            default->0x70513a;
        };
        float strength=entity.strength();
        int alpha=Float.isFinite(strength)?(int)(255*Math.clamp(strength,.2F,1F)*(1-recoil)*CoatingAppearance.strandOpacity(entity.material())):0;
        state.color=alpha<<24|rgb;state.visible=alpha>0 && (!strands.isEmpty() || !membrane.isEmpty());
    }
    private static void appendFaded(java.util.List<CoatingVoxels.Quad> output,java.util.List<CoatingVoxels.Quad> mesh,double opacity) {
        for(var q:mesh)output.add(opacity>=1?q:new CoatingVoxels.Quad(q.a(),q.b(),q.c(),q.d(),q.normal(),CoatingAppearance.tint(q.color(),0xffffffff,opacity)));
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(!state.visible)return;
        FirstPersonTetherProof.tether(pose,state.foot,state.side,state.local);
        // Capture immutable values: a deferred callback must never retain the reused mutable render state.
        var strands=state.strands;var membrane=state.membrane;
        int light=state.lightCoords,color=CoatingAppearance.tint(state.color,0xffebebeb,1);
        double textureOpacity=.70;
        collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(TranslucentGeometry.TEXTURE),(matrix,vertices)->{
            for(var q:strands)TranslucentGeometry.quad(vertices,matrix,q,color,light,textureOpacity);
            for(var q:membrane)TranslucentGeometry.quad(vertices,matrix,q,color,light,textureOpacity);
        });
        super.submit(state,pose,collector,camera);
    }
    private static CoatingVoxels.Vec vector(Vec3 p){return new CoatingVoxels.Vec(p.x,p.y,p.z);}
    private static Vec3 point(CoatingVoxels.Vec p){return new Vec3(p.x(),p.y(),p.z());}
}
