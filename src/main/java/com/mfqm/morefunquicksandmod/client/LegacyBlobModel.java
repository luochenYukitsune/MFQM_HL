package com.mfqm.morefunquicksandmod.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Original ModelVoreSlime's four intersecting boxes and 64x32 legacy UV layout. */
public final class LegacyBlobModel extends EntityModel<BlobRenderState> {
    public LegacyBlobModel(ModelPart root) { super(root, RenderTypes::entityTranslucent); }
    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().texOffs(0,0)
                .addBox(-4,16,-4,8,8,8).addBox(-3,17,-5,6,6,10)
                .addBox(-5,17,-3,10,6,6).addBox(-3,15,-3,6,10,6), PartPose.ZERO);
        return LayerDefinition.create(mesh,64,32);
    }
}
