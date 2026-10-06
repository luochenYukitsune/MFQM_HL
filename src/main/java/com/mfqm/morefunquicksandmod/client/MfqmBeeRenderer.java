package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.entity.MfqmBeeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public final class MfqmBeeRenderer extends MobRenderer<MfqmBeeEntity,LivingEntityRenderState,LegacyBeeModel> {
    public MfqmBeeRenderer(EntityRendererProvider.Context context) { super(context,new LegacyBeeModel(context.bakeLayer(MfqmClient.BEE_MODEL)),0.2375F); }
    @Override public LivingEntityRenderState createRenderState() { return new LivingEntityRenderState(); }
    @Override public Identifier getTextureLocation(LivingEntityRenderState state) { return Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"textures/entity/bee.png"); }
}
