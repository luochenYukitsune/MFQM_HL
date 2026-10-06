package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.entity.BlobEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class BlobRenderer extends MobRenderer<BlobEntity,BlobRenderState,LegacyBlobModel> {
    public BlobRenderer(EntityRendererProvider.Context context) { super(context, new LegacyBlobModel(context.bakeLayer(MfqmClient.BLOB_MODEL)),0.75F); }
    @Override public BlobRenderState createRenderState() { return new BlobRenderState(); }
    @Override public void extractRenderState(BlobEntity entity, BlobRenderState state, float tick) {
        super.extractRenderState(entity,state,tick); state.squish=Mth.lerp(tick,entity.oldSquish,entity.squish); state.kind=entity.kind(); state.swallowDepth=entity.swallowDepth();
    }
    @Override protected void scale(BlobRenderState state, PoseStack pose) {
        float squish = state.squish / 2.5F;
        float horizontal = 1F/(1F+squish);
        pose.scale(horizontal*3.75F, 2.8125F/horizontal, horizontal*3.75F);
    }
    @Override public Identifier getTextureLocation(BlobRenderState state) {
        return Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"textures/entity/" + switch(state.kind) {
            case VORE -> "voreslime"; case MUD -> "muddyblob"; case SAND -> "sandyblob"; case TAR -> "tarslime";
        } + ".png");
    }
}
