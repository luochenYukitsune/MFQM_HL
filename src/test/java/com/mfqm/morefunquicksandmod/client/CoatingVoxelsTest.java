package com.mfqm.morefunquicksandmod.client;

import java.util.List;

public final class CoatingVoxelsTest {
    private static int checks;
    public static void main(String[] args) {
        var face=new CoatingVoxels.Face(new CoatingVoxels.Vec(-2,0,2.3),new CoatingVoxels.Vec(4,0,0),
                new CoatingVoxels.Vec(0,12,0),new CoatingVoxels.Vec(0,0,1),0,16,4,12);
        var empty=CoatingVoxels.build(List.of(face),(u,v)->0);
        check(empty.pixels()==0 && empty.quads().isEmpty(),"transparent UV never creates a body-wide shell");
        var shallow=CoatingVoxels.build(List.of(face),(u,v)->v>=26?0x78f5f4ef:0);
        check(shallow.pixels()==8,"shallow mask covers exactly two ankle rows");
        var full=CoatingVoxels.build(List.of(face),(u,v)->0x90f5f4ef);
        check(full.quads().stream().flatMap(q->List.of(q.a(),q.b(),q.c(),q.d()).stream()).allMatch(p->p.z()<=2.318001),"skin film stays within .018 model pixels instead of a plastic relief shell");
        var thin=CoatingVoxels.build(List.of(face),(u,v)->0x90f5f4ef,.5);
        check(full.quads().stream().flatMap(q->List.of(q.a(),q.b(),q.c(),q.d()).stream()).allMatch(p->p.z()<=2.420001),"default coating relief hugs the skin within 0.12 model pixels");
        check(thin.pixels()==full.pixels(),"thickness never changes anatomical coverage");
        check(thin.quads().stream().flatMap(q->List.of(q.a(),q.b(),q.c(),q.d()).stream()).allMatch(p->p.z()<=2.420001),"runtime thickness setting scales actual pixel relief");
        check(full.pixels()==48 && full.quads().size()>48 && full.quads().size()<240,"visible pixel fronts plus exposed step walls, no redundant closed cubes");
        check(CoatingVoxels.build(List.of(face),(u,v)->0x90f5f4ef).equals(full),"geometry and thickness are stable, never flicker between frames");
        for(var q:shallow.quads()) {
            int a=q.color()>>>24;
            check(a==120,"pixel coating preserves mask alpha instead of making thin glue opaque");
            check(Math.abs(q.normal().length()-1)<1e-8,"side walls use their own unit normals for 3D lighting");
            var cross=q.b().subtract(q.a()).cross(q.c().subtract(q.a()));
            check(cross.dot(q.normal())>0,"quad winding agrees with outward normal");
            for(var p:List.of(q.a(),q.b(),q.c(),q.d())) {
                check(p.x()>=-2.000001 && p.x()<=2.000001 && p.y()>=9.999999 && p.y()<=12.000001,"UV coverage remains on the ankles");
                check(p.z()>2.3 && p.z()<=2.540001,"thinner pixel film retains real relief without a thick shell");
            }
        }
        var slim=new CoatingVoxels.Face(face.origin(),new CoatingVoxels.Vec(3,0,0),face.down(),face.normal(),40,20,3,12);
        check(CoatingVoxels.build(List.of(slim),(u,v)->0x90ffffff).pixels()==36,"slim arm has three columns, no wide-arm UV spill");
        var mirrored=new CoatingVoxels.Face(new CoatingVoxels.Vec(2,0,2.3),new CoatingVoxels.Vec(-4,0,0),face.down(),face.normal(),0,16,4,12);
        var left=CoatingVoxels.build(List.of(mirrored),(u,v)->u<1?0x90ffffff:0);
        check(left.pixels()==12 && left.quads().stream().allMatch(q->q.a().x()>=1),"mirrored limb preserves UV placement");
        for(var q:left.quads())check(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).dot(q.normal())>0,"mirrored winding remains outward");
        System.out.println("CoatingVoxels: "+checks+" behavioral checks passed");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
