package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Vec;
import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Quad;
import java.util.List;

public final class SkinSurfaceContactTest {
    private static int checks;
    public static void main(String[] args) {
        var skin=new Quad(new Vec(.125,0,-.125),new Vec(.125,0,.125),new Vec(.125,.75,.125),new Vec(.125,.75,-.125),new Vec(1,0,0),-1);
        var raised=new Quad(new Vec(.16,.2,-.04),new Vec(.16,.2,.04),new Vec(.16,.3,.04),new Vec(.16,.3,-.04),new Vec(1,0,0),-1);
        var surfaces=List.of(skin,raised);
        for(int i=0;i<1024;i++) {
            double y=CompactStrandStyle.variation(i)*.7;
            var hit=SkinSurfaceContact.project(new Vec(0,y,0),new Vec(1,0,0),surfaces);
            check(hit!=null && Math.abs(hit.point().y()-y)<1e-9,"projection preserves stable randomized leg height");
            check(Math.abs(hit.point().x()-(y>=.2 && y<=.3?.16:.125))<1e-9,"only actual occupied voxel pixels raise the surface");
            var lifted=SkinSurfaceContact.lift(raised,.012/16);
            check(Math.abs(lifted.a().x()-raised.a().x()-.012/16)<1e-9,"thin film follows the existing voxel, without replacing its silhouette");
            check(SkinSurfaceContact.outside(new Vec(.1,y,.01),hit).x()>=hit.point().x()-1e-9,"large strand cap is clipped out of the skin");
            check(SkinSurfaceContact.outside(new Vec(.2,y,.01),hit).x()==.2,"already exposed strand geometry is preserved");
        }
        check(SkinSurfaceContact.project(new Vec(0,1,0),new Vec(1,0,0),surfaces)==null,"missing skin regions are not filled with a shell");
        check(SkinSurfaceContact.project(new Vec(0,.2,0),new Vec(0,0,0),surfaces)==null,"zero direction cannot create NaN geometry");
        System.out.println("SkinSurfaceContact: "+checks+" behavioral checks passed");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
