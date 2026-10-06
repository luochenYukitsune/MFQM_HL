package com.mfqm.morefunquicksandmod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** The original overlays are 64x32; a modern 64x64 player model would sample the wrong UVs. */
public final class LegacyCoatingModel extends HumanoidModel<AvatarRenderState> {
    public LegacyCoatingModel(boolean slim){super(layer(slim).bakeRoot(),RenderTypes::entityTranslucent);hat.visible=false;}
    private static LayerDefinition layer(boolean slim){
        var mesh=HumanoidModel.createMesh(new CubeDeformation(0.012F),0);
        mesh.getRoot().getChild("head").addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        if(slim){
            mesh.getRoot().addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(40,16).addBox(-2,-2,-2,3,12,4,new CubeDeformation(.012F)),PartPose.offset(-5,2,0));
            mesh.getRoot().addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(40,16).mirror().addBox(-1,-2,-2,3,12,4,new CubeDeformation(.012F)),PartPose.offset(5,2,0));
        }
        return LayerDefinition.create(mesh,64,32);
    }
}
