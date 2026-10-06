package com.mfqm.morefunquicksandmod.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Legacy bee geometry, antennae, six legs, stinger and independently animated wings. */
public final class LegacyBeeModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart head, thorax, abdomen, leftWing, rightWing;
    public LegacyBeeModel(ModelPart root) {
        super(root); ModelPart bee=root.getChild("bee"); head=bee.getChild("head");thorax=bee.getChild("thorax");abdomen=bee.getChild("abdomen");leftWing=bee.getChild("left_wing");rightWing=bee.getChild("right_wing");
    }
    public static LayerDefinition layer() {
        MeshDefinition mesh=new MeshDefinition(); PartDefinition bee=mesh.getRoot().addOrReplaceChild("bee",CubeListBuilder.create(),PartPose.offset(-2.5F,18,-4));
        PartDefinition head=part(bee,"head",46,0,0,0,0,5,5,4,0,-2,8);
        part(head,"right_antenna",54,27,0,2,-8,1,1,4,3,-3,10);
        part(head,"left_antenna",54,27,0,2,-8,1,1,4,1,-3,10);
        part(head,"nose",54,9,0,2,-8,3,4,2,1,0,11);
        part(bee,"left_wing",24,26,-7,0,0,8,1,5,0,-1,2);
        part(bee,"right_wing",24,20,0,0,0,8,1,5,4,-1,2);
        PartDefinition thorax=part(bee,"thorax",0,0,0,0,0,5,5,8,0,0,0);
        for(int side=0;side<2;side++) for(int leg=0;leg<3;leg++)
            part(thorax,(side==0?"left":"right")+"_leg_"+leg,13,23,0,0,0,1,leg==1?3:4,1,side==0?-1:5,4,leg==0?1:leg==1?4:6);
        PartDefinition abdomen=part(bee,"abdomen",0,13,0,0,0,3,3,2,1,2,-2);
        part(abdomen,"stinger",0,18,-1,-3,2,1,1,3,2,4,-5);
        return LayerDefinition.create(mesh,64,32);
    }
    private static PartDefinition part(PartDefinition parent,String id,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz) {
        return parent.addOrReplaceChild(id,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.offset(px,py,pz));
    }
    @Override public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state); float age=state.ageInTicks;
        head.xRot=Mth.sin(age*0.1F)*0.0436F;head.zRot=Mth.cos(age*0.1F)*0.0262F;
        thorax.xRot=Mth.sin(age*0.075F)*0.0436F;thorax.zRot=Mth.cos(age*0.075F)*0.0262F;
        rightWing.yRot=Mth.cos(age*1.7F)*(float)Math.PI*0.25F;leftWing.yRot=-rightWing.yRot;
        rightWing.zRot=rightWing.yRot;leftWing.zRot=-rightWing.yRot;
        abdomen.xRot=Mth.sin(age*0.6F)*0.0436F;abdomen.zRot=Mth.cos(age*0.6F)*0.0262F;
    }
}
