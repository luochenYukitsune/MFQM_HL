package com.mfqm.morefunquicksandmod.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class HelperRenderState extends EntityRenderState {
    public String kind = "";
    public Identifier texture;
    public Vec3 end = Vec3.ZERO;
    public float height;
    public float progress;
}
