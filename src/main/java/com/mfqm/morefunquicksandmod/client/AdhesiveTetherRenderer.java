package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Three sagging strands and an ankle/shin membrane per synchronized anchor. No physical force here. */
public final class AdhesiveTetherRenderer extends EntityRenderer<AdhesiveTetherEntity,AdhesiveTetherRenderer.State> {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"textures/entity/adhesive_strand.png");
    private final AdhesiveFeetSampler feet;
    public static final class State extends EntityRenderState {
        Vec3 foot=Vec3.ZERO,shin=Vec3.ZERO;
        double width,radius;
        int color;
        boolean visible;
    }
    public AdhesiveTetherRenderer(EntityRendererProvider.Context context) { super(context);feet=new AdhesiveFeetSampler(context); }
    public AdhesiveFeetSampler feet() { return feet; }
    @Override public State createRenderState() { return new State(); }
    @Override protected boolean affectedByCulling(AdhesiveTetherEntity entity) { return false; }
    @Override public void extractRenderState(AdhesiveTetherEntity entity,State state,float tick) {
        super.extractRenderState(entity,state,tick);
        var target=entity.target();state.visible=false;
        if(target==null || !target.isAlive() || target.isInvisible() || target.isSpectator() || !ModConfig.CLIENT.adhesiveTethers.get()
                || entity.material().equals("tar") && !ModConfig.CLIENT.tarTreadsEffect.get())return;
        // A surface anchor can be inside a dark block cell while the exposed leg is in daylight.
        // Sample both ends; preserve actual cave/night lighting rather than using emissive glue.
        int targetLight=net.minecraft.client.renderer.LevelRenderer.getLightColor(target.level(),net.minecraft.core.BlockPos.containing(target.getEyePosition()));
        state.lightCoords=net.minecraft.client.renderer.LightTexture.pack(
                Math.max(net.minecraft.client.renderer.LightTexture.block(state.lightCoords),net.minecraft.client.renderer.LightTexture.block(targetLight)),
                Math.max(net.minecraft.client.renderer.LightTexture.sky(state.lightCoords),net.minecraft.client.renderer.LightTexture.sky(targetLight)));
        var sampled=feet.sample(target,entity.side(),tick);
        Vec3 origin=new Vec3(state.x,state.y,state.z);
        state.foot=target.getPosition(tick).add(sampled.ankle()).subtract(origin);
        state.shin=target.getPosition(tick).add(sampled.shin()).subtract(origin);
        double recoil=entity.breakProgress(tick);
        if(entity.breaking()) {
            double remaining=(1-recoil)*(1-recoil);
            state.foot=entity.breakAnkle(state.foot).scale(remaining);
            state.shin=entity.breakShin(state.shin).scale(remaining);
        }
        state.radius=Math.clamp(target.getBbWidth()*.23,.055,.19);
        state.width=StrugglePose.width(entity.material(),state.foot.length());
        int rgb=switch(entity.material()) {
            case "glue","sticky_board"->0xf5f4ee;
            case "tar"->0x201711;
            case "honey","wax"->0xd99b25;
            case "sinking_slime","mucus","sinky_liquid"->0x91b34a;
            default->0x70513a;
        };
        float strength=entity.strength();
        int alpha=Float.isFinite(strength)?(int)(255*Math.clamp(strength,.2F,1F)*(1-recoil)):0;
        state.color=alpha<<24|rgb;state.visible=alpha>0;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(!state.visible)return;
        // Capture immutable values: a deferred callback must never retain the reused mutable render state.
        Vec3 foot=state.foot,shin=state.shin;double width=state.width,radius=state.radius;
        int light=state.lightCoords,color=state.color;
        collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(TEXTURE),(matrix,vertices)->{
            for(int strand=0;strand<3;strand++) {
                double offset=(strand-1)*radius*.75;
                Vec3 start=new Vec3(offset,.008,(strand-1)*.03);
                Vec3 end=foot.add(offset*.4,0,0);
                Vec3 previous=start;
                for(int segment=1;segment<=8;segment++) {
                    double t=segment/8.;
                    Vec3 next=start.lerp(end,t).add(0,-Math.min(.11,foot.length()*.04)*Math.sin(Math.PI*t),0);
                    ribbon(vertices,matrix,previous,next,width,light,color);
                    previous=next;
                }
            }
            // A closed, translucent cuff follows the lifted foot and lower leg, with no torso endpoint.
            Vec3 axis=shin.subtract(foot).normalize();
            Vec3 across=axis.cross(new Vec3(1,0,0)).normalize();
            if(across.lengthSqr()<1e-8)across=new Vec3(0,0,1);
            Vec3 other=axis.cross(across).normalize();
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
                Vec3 first=across.scale(Math.cos(a)*radius).add(other.scale(Math.sin(a)*radius));
                Vec3 second=across.scale(Math.cos(b)*radius).add(other.scale(Math.sin(b)*radius));
                vertex(vertices,matrix,foot.add(first),i/8F,0,light,color);
                vertex(vertices,matrix,shin.add(first.scale(.82)),i/8F,1,light,color);
                vertex(vertices,matrix,shin.add(second.scale(.82)),(i+1)/8F,1,light,color);
                vertex(vertices,matrix,foot.add(second),(i+1)/8F,0,light,color);
            }
        });
        super.submit(state,pose,collector,camera);
    }
    private static void ribbon(VertexConsumer vertices,PoseStack.Pose matrix,Vec3 start,Vec3 end,double width,int light,int color) {
        Vec3 direction=end.subtract(start).normalize();
        Vec3 across=new Vec3(-direction.z,0,direction.x).normalize().scale(width);
        if(across.lengthSqr()<1e-8)across=new Vec3(width,0,0);
        quad(vertices,matrix,start,end,across,light,color);
        quad(vertices,matrix,start,end,direction.cross(across).normalize().scale(width),light,color);
    }
    private static void quad(VertexConsumer vertices,PoseStack.Pose matrix,Vec3 start,Vec3 end,Vec3 across,int light,int color) {
        vertex(vertices,matrix,start.subtract(across),0,0,light,color);vertex(vertices,matrix,end.subtract(across),0,1,light,color);
        vertex(vertices,matrix,end.add(across),1,1,light,color);vertex(vertices,matrix,start.add(across),1,0,light,color);
    }
    private static void vertex(VertexConsumer vertices,PoseStack.Pose matrix,Vec3 point,float u,float v,int light,int color) {
        vertices.addVertex(matrix,(float)point.x,(float)point.y,(float)point.z).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,0,1,0);
    }
}
