package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.entity.BlobEntity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class BlobRenderState extends LivingEntityRenderState {
    public float squish;
    public float swallowDepth;
    public BlobEntity.Kind kind = BlobEntity.Kind.VORE;
}
