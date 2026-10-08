package com.mfqm.morefunquicksandmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Opt-in proof from the actual submitted native leg matrices, independent of the sampler. */
final class FirstPersonTetherProof {
    private static final boolean ENABLED=Boolean.getBoolean("mfqm.firstPersonExpected");
    private static final Vec3[] nativeFeet=new Vec3[2];
    private static long samples;
    private static double maxError;
    static void beginFrame(){nativeFeet[0]=null;nativeFeet[1]=null;}
    static void body(PoseStack pose,PlayerModel model,AvatarRenderState state) {
        if(!ENABLED || !FirstPersonCompatibility.camera(state))return;
        for(int side=0;side<2;side++) {
            pose.pushPose();(side==0?model.rightLeg:model.leftLeg).translateAndRotate(pose);
            nativeFeet[side]=point(pose,new Vec3(0,11.5/16,0));pose.popPose();
        }
    }
    static void tether(PoseStack pose,Vec3 foot,int side,boolean local) {
        if(!ENABLED || !local || nativeFeet[side]==null)return;
        samples++;maxError=Math.max(maxError,point(pose,foot).distanceTo(nativeFeet[side]));
    }
    static void reset(){samples=0;maxError=0;}
    static long samples(){return samples;}
    static double error(){return maxError;}
    private static Vec3 point(PoseStack pose,Vec3 point) {
        var p=pose.last().pose().transformPosition(new Vector3f((float)point.x,(float)point.y,(float)point.z));
        return new Vec3(p.x,p.y,p.z);
    }
    private FirstPersonTetherProof(){}
}
