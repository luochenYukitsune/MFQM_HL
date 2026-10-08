package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Captures synchronized server actions once per render state; all rendering uses immutable frames. */
public final class StruggleClient {
    public static final ContextKey<StrugglePose.Frame> POSE=new ContextKey<>(Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"struggle_pose"));
    public static StrugglePose.Frame snapshot(Entity entity,float partialTick) {
        var state=QuicksandPhysics.state(entity);
        double elapsed=state.struggleAnimationTick==Long.MIN_VALUE?-1:
                (double)entity.level().getGameTime()-state.struggleAnimationTick+partialTick;
        return StrugglePose.sample(elapsed,state.depth,state.struggleSide);
    }
    public static void capture(Entity entity,AvatarRenderState state) {
        var frame=ModConfig.CLIENT.struggleAnimation.get()?snapshot(entity,state.partialTick):StrugglePose.sample(-1,0,0);
        // Preserve rescue, bows, consumables and other real use animations.
        if(state.isUsingItem || state.isFallFlying || state.isPassenger || state.isSpectator)frame=StrugglePose.sample(-1,0,0);
        state.setRenderData(POSE,frame);
        if(frame.amplitude()>0) {
            state.rightArmPose=StruggleEnumParams.STRUGGLE.getValue();
            state.leftArmPose=HumanoidModel.ArmPose.EMPTY;
            state.swimAmount=0; // Vanilla swim interpolation would overwrite the custom legs and arms.
        }
    }
    public static void apply(HumanoidModel<?> model,HumanoidRenderState state) {
        var frame=state.getRenderData(POSE);
        if(frame==null || frame.amplitude()<=0)return;
        model.rightLeg.xRot=(float)frame.rightLeg();model.leftLeg.xRot=(float)frame.leftLeg();
        model.body.yRot=(float)frame.bodyYaw();
        model.rightArm.xRot=(float)frame.armPitch();model.leftArm.xRot=(float)frame.armPitch();
        model.rightArm.zRot=(float)(-.18*frame.amplitude());model.leftArm.zRot=(float)(.18*frame.amplitude());
    }
    public static void hand(RenderHandEvent event) {
        var player=Minecraft.getInstance().player;
        if(player==null || !ModConfig.CLIENT.struggleAnimation.get() || player.isUsingItem() || player.isInvisible())return;
        // Vanilla's two-handed map already submits both arms during the main-hand event.
        if(event.getHand()==InteractionHand.OFF_HAND && event.getItemStack().isEmpty()
                && player.getMainHandItem().getItem() instanceof net.minecraft.world.item.MapItem)return;
        var frame=snapshot(player,event.getPartialTick());
        if(frame.amplitude()<=0)return;
        if(!event.getItemStack().isEmpty()) {
            var heldPose=event.getPoseStack();heldPose.pushPose();
            try {
                heldPose.translate(0,-.045*frame.amplitude(),-.07*frame.amplitude());
                heldPose.mulPose(Axis.XP.rotationDegrees((float)(-8*frame.amplitude())));
                Minecraft.getInstance().gameRenderer.itemInHandRenderer.renderArmWithItem(player,event.getPartialTick(),event.getInterpolatedPitch(),
                        event.getHand(),event.getSwingProgress(),event.getItemStack(),event.getEquipProgress(),heldPose,event.getSubmitNodeCollector(),event.getPackedLight());
            } finally {heldPose.popPose();}
            event.setCanceled(true);return;
        }
        // RenderHand fires outside vanilla's per-hand push/pop. Submit an empty arm in our own
        // scope rather than leaving transforms on the shared main/off-hand pose stack.
        var arm=event.getHand()==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
        float sign=arm==HumanoidArm.RIGHT?1:-1;
        var pose=event.getPoseStack();pose.pushPose();
        pose.translate(sign*.64,-.6-event.getEquipProgress()*.6-.045*frame.amplitude(),-.72-.07*frame.amplitude());
        pose.mulPose(Axis.XP.rotationDegrees((float)(-8*frame.amplitude())));
        pose.mulPose(Axis.YP.rotationDegrees(sign*45));
        pose.translate(-sign,3.6,3.5);
        pose.mulPose(Axis.ZP.rotationDegrees(sign*120));pose.mulPose(Axis.XP.rotationDegrees(200));
        pose.mulPose(Axis.YP.rotationDegrees(-sign*135));pose.translate(sign*5.6,0,0);
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        var texture=player.getSkin().body().texturePath();
        if(arm==HumanoidArm.RIGHT)renderer.renderRightHand(pose,event.getSubmitNodeCollector(),event.getPackedLight(),texture,player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE),player);
        else renderer.renderLeftHand(pose,event.getSubmitNodeCollector(),event.getPackedLight(),texture,player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE),player);
        pose.popPose();event.setCanceled(true);
    }
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var minecraft=Minecraft.getInstance();
        if(minecraft.player==null || minecraft.screen!=null || !minecraft.options.getCameraType().isFirstPerson()
                || !ModConfig.CLIENT.struggleCamera.get())return;
        var frame=snapshot(minecraft.player,(float)event.getPartialTick());
        event.setPitch(event.getPitch()+(float)frame.cameraPitch());
    }
    private StruggleClient() {}
}
