package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.entity.ConnectorEntity;
import com.mfqm.morefunquicksandmod.entity.SurfaceEffectEntity;
import com.mfqm.morefunquicksandmod.entity.TentacleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Procedural legacy helper geometry submitted from snapshots, without OpenGL global state. */
public final class HelperRenderer<T extends Entity> extends EntityRenderer<T,HelperRenderState> {
    public HelperRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public HelperRenderState createRenderState() { return new HelperRenderState(); }
    @Override protected boolean affectedByCulling(T entity) { return false; }
    @Override public void extractRenderState(T entity, HelperRenderState state, float tick) {
        super.extractRenderState(entity,state,tick); state.end=Vec3.ZERO; state.height=0; state.progress=0;
        Vec3 origin=new Vec3(state.x,state.y,state.z);
        if (entity instanceof ConnectorEntity connector) {
            state.kind=connector.kind();
            Entity owner=connector.visibleOwner();
            if(owner!=null) state.end=owner.getPosition(tick).add(0,owner.getBbHeight()*0.65,0).subtract(origin);
            state.texture=texture("items/"+(state.kind.equals("hook")?"cabletex":"ropetex")+".png");
        } else if (entity instanceof TentacleEntity tentacle) {
            state.kind="tentacles";state.height=tentacle.extension();
            Entity victim=tentacle.victim();
            state.end=victim==null?new Vec3(0,state.height,0):victim.getPosition(tick).add(0,victim.getBbHeight()*0.55,0).subtract(origin);
            state.texture=texture("blocks/tentacles"+Math.min(2,(int)state.height)+(tentacle.isMud()?"_1":"")+".png");
        } else if(entity instanceof SurfaceEffectEntity effect) {
            state.kind=effect.kind();state.progress=(entity.tickCount+tick)/Math.max(1,effect.lifetime());
            var sprite=Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(effect.material(),entity.level(),entity.blockPosition());
            var id=sprite.contents().name(); state.texture=Identifier.fromNamespaceAndPath(id.getNamespace(),"textures/"+id.getPath()+".png");
            if(state.kind.equals("slime_hole"))state.texture=texture("blocks/slimehole"+Math.min(3,(int)(state.progress*4))+".png");
            if(state.kind.equals("tar_treads")) { state.texture=texture("blocks/tartread.png");Entity target=effect.target();if(target!=null)state.end=target.getPosition(tick).add(0,target.getBbHeight()*0.65,0).subtract(origin); }
        }
    }
    @Override public void submit(HelperRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(state.texture==null || state.kind.equals("long_stick") || state.kind.equals("rescue")) return;
        if(state.kind.equals("bubble") && !ModConfig.CLIENT.bubbleEffects.get())return;
        if(state.kind.equals("tar_treads") && !ModConfig.CLIENT.tarTreadsEffect.get())return;
        if(state.kind.equals("tentacles")) {
            int count=6;
            for(int i=0;i<count;i++) {
                double angle=i*Math.PI*2/count;
                Vec3 start=new Vec3(Math.cos(angle)*0.38,0.8,Math.sin(angle)*0.38);
                Vec3 end=state.end.scale(Math.min(1,state.height)).add(Math.sin(state.ageInTicks*0.08+i)*0.08,0,Math.cos(state.ageInTicks*0.08+i)*0.08);
                strip(collector,pose,state.texture,start,end,0.065F,state.lightCoords);
            }
        } else if(state.kind.equals("rope") || state.kind.equals("hook") || state.kind.equals("tar_treads")) {
            strip(collector,pose,state.texture,Vec3.ZERO,state.end,state.kind.equals("tar_treads")?0.025F:0.015F,state.lightCoords);
            if(state.kind.equals("hook")) {
                strip(collector,pose,texture("items/grapplinghooktex.png"),new Vec3(-0.12,-0.12,0),new Vec3(0.12,0.12,0),0.08F,state.lightCoords);
            }
        } else {
            float radius=state.kind.equals("bubble")?(float)(0.05+0.2*Math.sin(Math.min(1,state.progress)*Math.PI)):0.4F;
            float elevation=state.kind.equals("bubble")?radius*0.3F:0.01F;
            int light=state.lightCoords;
            collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(state.texture),(matrix,vertices)->{
                var normal=new Vec3(0,1,0);
                vertex(vertices,matrix,new Vec3(-radius,elevation,-radius),0,0,light,normal);
                vertex(vertices,matrix,new Vec3(-radius,elevation,radius),0,1,light,normal);
                vertex(vertices,matrix,new Vec3(radius,elevation,radius),1,1,light,normal);
                vertex(vertices,matrix,new Vec3(radius,elevation,-radius),1,0,light,normal);
            });
        }
        super.submit(state,pose,collector,camera);
    }
    private static Identifier texture(String path){return Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"textures/"+path);}
    private static void strip(SubmitNodeCollector collector,PoseStack pose,Identifier texture,Vec3 start,Vec3 end,float width,int light) {
        Vec3 direction=end.subtract(start);if(direction.lengthSqr()<1.0E-12)return;
        Vec3 side=new Vec3(-direction.z,0,direction.x).normalize().scale(width);
        if(side.lengthSqr()<1.0E-8)side=new Vec3(width,0,0);
        Vec3 cross=direction.normalize().cross(side).normalize().scale(width);
        Vec3 first=side, second=cross;
        collector.submitCustomGeometry(pose,RenderTypes.entityCutoutNoCull(texture),(matrix,vertices)->{
            quad(vertices,matrix,start,end,first,light);quad(vertices,matrix,start,end,second,light);
        });
    }
    private static void quad(VertexConsumer vertices,PoseStack.Pose matrix,Vec3 start,Vec3 end,Vec3 side,int light) {
        var normal=end.subtract(start).cross(side).normalize();
        vertex(vertices,matrix,start.subtract(side),0,0,light,normal);vertex(vertices,matrix,end.subtract(side),0,1,light,normal);
        vertex(vertices,matrix,end.add(side),1,1,light,normal);vertex(vertices,matrix,start.add(side),1,0,light,normal);
    }
    private static void vertex(VertexConsumer vertices,PoseStack.Pose matrix,Vec3 position,float u,float v,int light,Vec3 normal){
        vertices.addVertex(matrix,(float)position.x,(float)position.y,(float)position.z).setColor(-1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,(float)normal.x,(float)normal.y,(float)normal.z);
    }
}
