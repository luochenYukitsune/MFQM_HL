package com.mfqm.morefunquicksandmod.client;

import net.minecraft.client.model.HumanoidModel;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Client-only enum constructor parameters; the common entry point never loads model classes. */
public final class StruggleEnumParams {
    public static final EnumProxy<HumanoidModel.ArmPose> STRUGGLE=new EnumProxy<>(HumanoidModel.ArmPose.class,
            true,true,(IArmPoseTransformer)(model,state,arm)->StruggleClient.apply(model,state));
    private StruggleEnumParams() {}
}
