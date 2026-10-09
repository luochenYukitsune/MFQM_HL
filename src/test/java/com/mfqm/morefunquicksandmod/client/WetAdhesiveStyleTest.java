package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Vec;
import java.util.List;

public final class WetAdhesiveStyleTest {
    private static int checks;
    public static void main(String[] args) {
        check(WetAdhesiveStyle.height(.08)>=.08 && WetAdhesiveStyle.height(.15)<=.12,"shallow glue wraps soles and ankles");
        check(WetAdhesiveStyle.height(.8)>.2 && WetAdhesiveStyle.height(3)<=.28,"only deep contact extends to lower calf");
        check(WetAdhesiveStyle.height(Double.NaN)==0,"invalid depth cannot produce geometry");
        check(WetAdhesiveStyle.width(.8,1)<WetAdhesiveStyle.width(.1,1),"pulling a strand makes it thinner");
        check(WetAdhesiveStyle.width(.2,0)!=WetAdhesiveStyle.width(.2,2),"separate contacts have different strand widths");
        check(WetAdhesiveStyle.width(.4,1,"glue")==3*WetAdhesiveStyle.width(.4,1,"tar"),"glue pulling strips are thicker without extra branches");
        check(WetAdhesiveStyle.width(.4,1,"sticky_board")==WetAdhesiveStyle.width(.4,1,"glue"),"glue-coated boards use the same thicker glue strand");
        check(WetAdhesiveStyle.taper(.5)<WetAdhesiveStyle.taper(0),"strand middle narrows between attached ends");
        check(WetAdhesiveStyle.sag(.8)<WetAdhesiveStyle.sag(.1),"tense strands sag less than relaxed strands");
        check(WetAdhesiveStyle.contraction(0)==1 && WetAdhesiveStyle.contraction(.5)==.25 && WetAdhesiveStyle.contraction(1)==0,"breaking retracts the entire continuous strand including its raised ankle endpoint");
        for(var frame:List.of(
                new WetAdhesiveStyle.Frame(new Vec(0,0,0),new Vec(0,1,0),new Vec(1,0,0),new Vec(0,0,1),.135,.135),
                new WetAdhesiveStyle.Frame(new Vec(2,3,4),new Vec(0,1,0),new Vec(0,0,1),new Vec(-1,0,0),.135,.135))) {
            var mesh=WetAdhesiveStyle.film(frame,.1,17);
            check(mesh.size()==52,"one closed thin shell with inner wall, outer wall and solid rim per foot");
            closed(mesh.subList(0,48),"film shell has no open boundary edges");
            check(WetAdhesiveStyle.film(frame,.1,17).equals(mesh),"surface edge is stable and does not shimmer");
            for(var q:mesh) {
                check((q.color()>>>24)>0 && (q.color()>>>24)<=70,"wet film stays translucent");
                check(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).dot(q.normal())>0,"film winding agrees with actual normal");
                for(var p:List.of(q.a(),q.b(),q.c(),q.d())) {
                    var offset=p.subtract(frame.center());
                    check(Math.abs(offset.dot(frame.right()))<=frame.halfWidth()+WetAdhesiveStyle.THICKNESS+.002,"film hugs model width");
                    check(Math.abs(offset.dot(frame.front()))<=frame.halfDepth()+WetAdhesiveStyle.THICKNESS+.002,"film hugs model depth");
                    check(offset.dot(frame.up())>=-.026 && offset.dot(frame.up())<=.12,"shallow film stays at the ankle");
                }
            }
            for(int i=0;i<5;i++) {
                var p=WetAdhesiveStyle.endpoint(frame,.1,17+i).subtract(frame.center());
                check(Math.abs(Math.max(Math.abs(p.dot(frame.right()))/(frame.halfWidth()+WetAdhesiveStyle.THICKNESS),Math.abs(p.dot(frame.front()))/(frame.halfDepth()+WetAdhesiveStyle.THICKNESS))-1)<1e-6,"continuous strand ends on outer film surface, not inside the leg");
            }
            var root=frame.center().add(frame.right().scale(.4));
            var facing=WetAdhesiveStyle.endpoint(frame,.1,17,root).subtract(frame.center());
            check(facing.dot(frame.right())>0 && Math.abs(facing.dot(frame.front()))<1e-7,"foot endpoint faces the actual root rather than spraying around the opposite side");
        }
        for(var end:List.of(new Vec(.5,.3,.1),new Vec(0,.6,0),new Vec(.3,0,0))) {
            var tube=WetAdhesiveStyle.tube(new Vec(0,0,0),end,6,.02,.008);
            check(tube.size()==48,"six-sided volumetric strand has bounded faces and closed end caps");
            closed(tube,"strand has no open boundary edges or crossed ribbon geometry");
            for(var q:tube) {
                check(Math.abs(q.normal().length()-1)<1e-6,"volumetric strand uses finite unit normals");
                check(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).dot(q.normal())>0,"tube faces and caps face outwards");
            }
            double startRadius=tube.subList(0,6).stream().flatMap(q->List.of(q.a(),q.b(),q.c(),q.d()).stream())
                    .filter(p->Math.abs(p.dot(end.unit()))<1e-8).mapToDouble(Vec::length).max().orElseThrow();
            check(Math.abs(startRadius-.02)<1e-6,"strand starts with actual width regardless of face winding");
        }
        var strip=WetAdhesiveStyle.tube(new Vec(0,0,0),new Vec(.3,0,0),6,.02,.02);
        double vertical=strip.subList(0,6).stream().mapToDouble(q->Math.abs(q.a().y())).max().orElseThrow();
        check(vertical>.004 && vertical<.007,"pulling strip retains actual thickness without a round noodle cross-section");
        var submerged=WetAdhesiveStyle.submergedRoot(new Vec(.99,.9,.01),.15,.033);
        check(submerged.point().y()+submerged.radius()<.15 && submerged.point().y()-submerged.radius()>0,"entire root cap remains below the lowest rendered fluid corner");
        check(submerged.point().x()+submerged.radius()<1 && submerged.point().z()-submerged.radius()>0,"thicker root cap cannot cross cell sides");
        var deepRoot=WetAdhesiveStyle.submergedRoot(new Vec(.5,.85,.5),.89,.033);
        check(deepRoot.point().y()<.09,"medium end extends near the bottom instead of lying on the liquid surface");
        check(.95-deepRoot.point().y()>.3,"lowering the medium end gives a steeper strand without raising the fixed .95 body endpoint");
        check(WetAdhesiveStyle.taper(.5)>=.85,"regular strip avoids a pinched irregular silhouette");
        check(WetAdhesiveStyle.sag(.4)<=.002,"medium-side extension remains almost straight");
        check(WetAdhesiveStyle.width(.8,0,"glue")>=.024,"extended glue strip remains clearly visible without changing the body endpoint");
        var heights=new java.util.HashSet<Double>();
        var frame=new WetAdhesiveStyle.Frame(new Vec(0,0,0),new Vec(0,1,0),new Vec(1,0,0),new Vec(0,0,1),.135,.135);
        for(long seed=0;seed<32;seed++) {
            var endpoint=WetAdhesiveStyle.endpoint(frame,.8,seed,new Vec(.4,0,0));double h=endpoint.y();heights.add(h);
            check(h>=WetAdhesiveStyle.height(.8)*.25 && h<=WetAdhesiveStyle.height(.8)*.75,"raised endpoints remain inside the lower-calf film");
            check(endpoint.equals(WetAdhesiveStyle.endpoint(frame,.8,seed,new Vec(.4,0,0))),"random raised endpoint does not flicker between frames");
        }
        check(heights.size()>16,"body endpoints use varied stable heights rather than a narrow ring");
        for(double height:new double[]{.02,.0625,.15,.89})for(var desired:List.of(new Vec(.01,.9,.01),new Vec(.5,.9,.5),new Vec(.99,.9,.99))) {
            var roots=new java.util.HashSet<Vec>();
            for(int i=0;i<8;i++) {
                var independent=WetAdhesiveStyle.submergedRoot(desired,height,.033,17,i,8);var p=independent.point();double radius=independent.radius();roots.add(p);
                check(p.x()-radius>0 && p.x()+radius<1 && p.z()-radius>0 && p.z()+radius<1,"each dense strand root cap stays inside the medium sides");
                check(p.y()-radius>0 && p.y()+radius<height,"each dense strand root cap stays below even a thin fluid surface");
                check(independent.equals(WetAdhesiveStyle.submergedRoot(desired,height,.033,17,i,8)),"independent roots do not wander between frames");
            }
            check(roots.size()==8,"all eight strands have distinct roots even at a cell corner");
        }
        check(WetAdhesiveStyle.sag(.001)<.0001,"retracting short strands cannot curl into loose noodles");
        check(WetAdhesiveStyle.tube(new Vec(0,0,0),new Vec(0,0,0),6,.02,.008).isEmpty(),"fully retracted strands cannot create NaN geometry");
        System.out.println("WetAdhesiveStyle: "+checks+" behavioral checks passed");
    }
    private static void closed(List<CoatingVoxels.Quad> mesh,String message) {
        var edges=new java.util.HashMap<String,Integer>();
        for(var q:mesh) {
            var points=List.of(q.a(),q.b(),q.c(),q.d());
            for(int i=0;i<4;i++) {
                String a=key(points.get(i)),b=key(points.get((i+1)%4));if(a.equals(b))continue;
                String edge=a.compareTo(b)<0?a+"/"+b:b+"/"+a;
                edges.merge(edge,1,Integer::sum);
            }
        }
        check(edges.values().stream().allMatch(count->count==2),message);
    }
    private static String key(Vec point){return Math.round(point.x()*1e9)+","+Math.round(point.y()*1e9)+","+Math.round(point.z()*1e9);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
