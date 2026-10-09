package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Texture alpha survives shader packs that discard vertex alpha. Each tile has a uniform alpha. */
final class TranslucentGeometry {
    static final Identifier TEXTURE=Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"textures/entity/translucent_alpha.png");
    static void quad(VertexConsumer vertices,PoseStack.Pose matrix,CoatingVoxels.Quad q,int color,int light,double textureOpacity) {
        int tint=CoatingAppearance.tint(q.color(),color,textureOpacity);
        int alpha=tint>>>24;
        if(alpha==0)return;
        float u0=((alpha&15)*4+1.25F)/64,u1=((alpha&15)*4+2.75F)/64;
        float v0=((alpha>>>4)*4+1.25F)/64,v1=((alpha>>>4)*4+2.75F)/64;
        int opaque=tint|0xff000000;
        vertex(vertices,matrix,q.a(),q.normal(),opaque,light,u0,v0);
        vertex(vertices,matrix,q.b(),q.normal(),opaque,light,u1,v0);
        vertex(vertices,matrix,q.c(),q.normal(),opaque,light,u1,v1);
        vertex(vertices,matrix,q.d(),q.normal(),opaque,light,u0,v1);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose matrix,CoatingVoxels.Vec p,CoatingVoxels.Vec n,int color,int light,float u,float v) {
        out.addVertex(matrix,(float)p.x(),(float)p.y(),(float)p.z()).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,(float)n.x(),(float)n.y(),(float)n.z());
    }
    static CoatingVoxels.Quad modelPixels(CoatingVoxels.Quad q) {
        return new CoatingVoxels.Quad(q.a().scale(1./16),q.b().scale(1./16),q.c().scale(1./16),q.d().scale(1./16),q.normal(),q.color());
    }
    private TranslucentGeometry(){}
}
