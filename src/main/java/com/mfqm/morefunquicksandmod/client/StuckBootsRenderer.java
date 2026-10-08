package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.entity.StuckBootsEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws the synchronized real equipment model, including its component-driven appearance and glint. */
public final class StuckBootsRenderer extends EntityRenderer<StuckBootsEntity,StuckBootsRenderer.State> {
    public static final class State extends EntityRenderState { public final ItemStackRenderState item=new ItemStackRenderState(); }
    private final ItemModelResolver items;
    public StuckBootsRenderer(EntityRendererProvider.Context context) { super(context);items=context.getItemModelResolver();shadowRadius=.2F; }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(StuckBootsEntity entity,State state,float tick) {
        super.extractRenderState(entity,state,tick);
        items.updateForNonLiving(state.item,entity.stack(),ItemDisplayContext.GROUND,entity);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(state.item.isEmpty())return;
        pose.pushPose();pose.translate(0,.04,0);pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(.8F,.8F,.8F);
        state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);
        pose.popPose();super.submit(state,pose,collector,camera);
    }
}
