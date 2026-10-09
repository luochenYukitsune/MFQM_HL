package com.mfqm.morefunquicksandmod.client;
import java.util.List;
public final class SurfaceBubbleStyleTest {
    public static void main(String[] args) {
        int checks=0;
        for(int seed=0;seed<32;seed++)for(int tick=0;tick<=30;tick++) {
            var mesh=SurfaceBubbleStyle.mesh(tick/30.,seed);
            if(mesh.size()!=8)throw new AssertionError("small bubble is a dome, never a square block-texture billboard");checks++;
            for(var q:mesh)for(var p:List.of(q.a(),q.b(),q.c(),q.d())) {
                if(Math.abs(p.x())>.056 || Math.abs(p.z())>.056 || p.y()<0 || p.y()>.035)throw new AssertionError("bubble must remain smaller than 0.112 blocks");checks++;
            }
            for(var q:mesh) {
                if(q.normal().y()<=0 || Math.abs(q.normal().length()-1)>1e-8
                        || q.b().subtract(q.a()).cross(q.c().subtract(q.a())).dot(q.normal())<=0)
                    throw new AssertionError("dome faces need outward unit normals and matching winding");checks++;
            }
            if(!mesh.equals(SurfaceBubbleStyle.mesh(tick/30.,seed)))throw new AssertionError("surface bubble does not jitter");checks++;
        }
        if(!SurfaceBubbleStyle.mesh(Double.NaN,0).isEmpty())throw new AssertionError("invalid progress must not create huge/NaN vertices");
        System.out.println("SurfaceBubbleStyle: "+(checks+1)+" checks passed");
    }
}
