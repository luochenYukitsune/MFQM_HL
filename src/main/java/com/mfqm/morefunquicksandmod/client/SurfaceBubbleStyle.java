package com.mfqm.morefunquicksandmod.client;

import java.util.ArrayList;
import java.util.List;
import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Vec;
import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Quad;

/** Tiny material-textured surface dome, in blocks, rather than a full-sized texture billboard. */
public final class SurfaceBubbleStyle {
    public static final double UV_RADIUS=.056;
    public static List<Quad> mesh(double progress,long seed) {
        if(!Double.isFinite(progress))return List.of();
        double radius=(.018+.032*Math.sin(Math.clamp(progress,0,1)*Math.PI))
                *(.9+.2*CompactStrandStyle.variation(seed));
        var top=new Vec(0,.005+radius*.4,0);
        double phase=CompactStrandStyle.variation(seed^0xC6BC279692B5CC83L)*Math.PI*2;
        var mesh=new ArrayList<Quad>(8);
        for(int i=0;i<8;i++) {
            double a=phase+i*Math.PI/4,b=phase+(i+1)*Math.PI/4;
            var first=new Vec(radius*Math.cos(a),.004,radius*Math.sin(a));
            var second=new Vec(radius*Math.cos(b),.004,radius*Math.sin(b));
            // Apex then clockwise rim gives outward/upward winding on all eight faces.
            var normal=first.subtract(top).cross(second.subtract(top)).unit();
            if(normal.y()<0)mesh.add(new Quad(top,second,first,top,normal.scale(-1),-1));
            else mesh.add(new Quad(top,first,second,top,normal,-1));
        }
        return List.copyOf(mesh);
    }
    private SurfaceBubbleStyle(){}
}
