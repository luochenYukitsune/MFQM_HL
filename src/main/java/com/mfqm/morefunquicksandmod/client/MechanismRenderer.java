package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.blockentity.MechanismBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pulsing flesh organs and writhing larvae surfaces, replacing three legacy TESRs. */
public final class MechanismRenderer implements BlockEntityRenderer<MechanismBlockEntity,MechanismRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        String kind;
        float time, amplitude;
        int faces;
        int variant;
    }
    public MechanismRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(MechanismBlockEntity entity,State state,float tick,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breakProgress){
        BlockEntityRenderer.super.extractRenderState(entity,state,tick,camera,breakProgress);
        state.kind=entity.kind;state.time=entity.getLevel()==null?0:entity.getLevel().getGameTime()+tick;
        state.faces=0;state.amplitude=0.25F;state.variant=0;
        for(var property:state.blockState.getProperties()) if(property.getName().equals("variant") && property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty p)state.variant=state.blockState.getValue(p);
        if(entity.getLevel()!=null){
            for(Direction face:Direction.values()) if(!entity.getLevel().getBlockState(entity.getBlockPos().relative(face)).isSolidRender())state.faces|=1<<face.ordinal();
            if(!entity.getLevel().getEntitiesOfClass(LivingEntity.class,new AABB(entity.getBlockPos()).inflate(1.5)).isEmpty())state.amplitude=0.3F;
        }
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        int light=state.lightCoords;
        Identifier texture=Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"textures/blocks/"+(state.kind.equals("larvae")?"larvae":"meat10")+".png");
        for(Direction face:Direction.values()){
            if((state.faces & 1<<face.ordinal())==0)continue;
            if(state.kind.equals("blossom") && state.variant>=6 && state.variant<=9){
                Direction requested=switch(state.variant){case 6->Direction.NORTH;case 7->Direction.EAST;case 8->Direction.SOUTH;default->Direction.WEST;};
                if(face!=requested)continue;
            }
            pose.pushPose();pose.translate(.5,.5,.5);
            rotate(pose,face);pose.translate(0,0,.501);
            if(state.kind.equals("larvae")){
                float wave=(float)Math.sin(state.time*.12)*.018F;
                var normal=new Vec3(-wave,wave,1).normalize();
                collector.submitCustomGeometry(pose,RenderTypes.entityCutoutNoCull(texture),(matrix,vertices)->{
                    vertex(vertices,matrix,-.5F,-.5F,0,0,1,light,normal);
                    vertex(vertices,matrix,.5F,-.5F,wave,1,1,light,normal);
                    vertex(vertices,matrix,.5F,.5F,0,1,0,light,normal);
                    vertex(vertices,matrix,-.5F,.5F,-wave,0,0,light,normal);
                });
            }else{
                int count=state.kind.equals("blossom")?6:3;
                for(int i=0;i<count;i++){
                    float px=(i%3-1)*.27F,py=count==6?(i<3?.23F:-.23F):(i-1)*.21F;
                    float pulse=(float)Math.sin(state.time*.09+i*Math.PI*.5);
                    float size=.11F+pulse*.014F;
                    float protrusion=state.amplitude*(1+pulse*.25F);
                    collector.submitCustomGeometry(pose,RenderTypes.entityCutoutNoCull(texture),(matrix,vertices)->box(vertices,matrix,px-size,py-size,0,px+size,py+size,protrusion,light));
                }
            }
            pose.popPose();
        }
    }
    private static void rotate(PoseStack pose,Direction face){
        switch(face){case NORTH->pose.mulPose(Axis.YP.rotationDegrees(180));case WEST->pose.mulPose(Axis.YP.rotationDegrees(-90));case EAST->pose.mulPose(Axis.YP.rotationDegrees(90));case UP->pose.mulPose(Axis.XP.rotationDegrees(-90));case DOWN->pose.mulPose(Axis.XP.rotationDegrees(90));default->{}}
    }
    private static void box(VertexConsumer vertices,PoseStack.Pose matrix,float x0,float y0,float z0,float x1,float y1,float z1,int light){
        quad(vertices,matrix,new float[]{x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1},light);
        quad(vertices,matrix,new float[]{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0},light);
        quad(vertices,matrix,new float[]{x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1},light);
        quad(vertices,matrix,new float[]{x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0},light);
        quad(vertices,matrix,new float[]{x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1},light);
    }
    private static void quad(VertexConsumer vertices,PoseStack.Pose matrix,float[] coordinates,int light){
        var a=new Vec3(coordinates[0],coordinates[1],coordinates[2]);
        var b=new Vec3(coordinates[3],coordinates[4],coordinates[5]);var c=new Vec3(coordinates[6],coordinates[7],coordinates[8]);
        var normal=b.subtract(a).cross(c.subtract(a)).normalize();
        for(int i=0;i<4;i++)vertex(vertices,matrix,coordinates[i*3],coordinates[i*3+1],coordinates[i*3+2],i==1||i==2?1:0,i<2?1:0,light,normal);
    }
    private static void vertex(VertexConsumer vertices,PoseStack.Pose matrix,float x,float y,float z,float u,float v,int light,Vec3 normal){
        vertices.addVertex(matrix,x,y,z).setColor(-1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,(float)normal.x,(float)normal.y,(float)normal.z);
    }
}
