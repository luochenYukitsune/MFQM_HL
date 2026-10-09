package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Vec;
import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Quad;
import java.util.ArrayList;
import java.util.List;

/** Cosmetic wet-film geometry in world blocks, independent of entities and physics. */
public final class WetAdhesiveStyle {
    public record Frame(Vec center,Vec up,Vec right,Vec front,double halfWidth,double halfDepth,double calfHeight) {
        public Frame(Vec center,Vec up,Vec right,Vec front,double halfWidth,double halfDepth) {
            this(center,up,right,front,halfWidth,halfDepth,.28);
        }
    }
    public record Root(Vec point,double radius) {}
    public static final double THICKNESS=.003;
    private static final double[][] EDGE={{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1},{1,0}};
    public static double height(double depth) {return Double.isFinite(depth)?.08+.20*Math.clamp((depth-.15)/.85,0,1):0;}
    public static double width(double length,int variation) {return (.010+.006*CompactStrandStyle.variation(Integer.toUnsignedLong(variation)^0x8EBC6AF09C88C6E3L))/Math.sqrt(1+Math.max(0,length-.35)*1.2);}
    public static double width(double length,int variation,String material) {
        return width(length,variation)*("glue".equals(material) || "sticky_board".equals(material)?3:1);
    }
    public static double taper(double t) {double x=2*Math.clamp(t,0,1)-1;return .88+.12*x*x;}
    public static double sag(double length) {return Math.min(Math.max(0,length)*.008,.002/(1+Math.max(0,length)*3));}
    public static double contraction(double progress){double remaining=1-Math.clamp(progress,0,1);return remaining*remaining;}
    public static Vec endpoint(Frame f,double depth,long seed) {
        return endpoint(f,depth,seed,f.center.subtract(f.up));
    }
    /** Connect the surface facing this root, rather than selecting a random side of the leg. */
    public static Vec endpoint(Frame f,double depth,long seed,Vec root) {
        Vec towards=root.subtract(f.center);double x=towards.dot(f.right),z=towards.dot(f.front);
        if(Math.abs(x)+Math.abs(z)<1e-8){x=0;z=1;}
        double halfWidth=f.halfWidth+THICKNESS,halfDepth=f.halfDepth+THICKNESS;
        double scale=1/Math.max(Math.abs(x)/halfWidth,Math.abs(z)/halfDepth);
        x*=scale;z*=scale;
        double offset=2*CompactStrandStyle.variation(seed^0x589965CC75374CC3L)-1;
        // Move along the continuous rectangle perimeter. Switching the random
        // offset axis at a corner would teleport attachments when the leg turns.
        double position;
        if(Math.abs(x)/halfWidth>=Math.abs(z)/halfDepth)
            position=x>=0?2*halfWidth+z+halfDepth:4*halfWidth+3*halfDepth-z;
        else position=z>=0?3*halfWidth+2*halfDepth-x:x+halfWidth;
        double perimeter=4*(halfWidth+halfDepth);
        position=(position+offset*Math.min(f.halfWidth,f.halfDepth)*.55+perimeter)%perimeter;
        if(position<=2*halfWidth){x=position-halfWidth;z=-halfDepth;}
        else if(position<=2*halfWidth+2*halfDepth){x=halfWidth;z=position-2*halfWidth-halfDepth;}
        else if(position<=4*halfWidth+2*halfDepth){x=3*halfWidth+2*halfDepth-position;z=halfDepth;}
        else{x=-halfWidth;z=4*halfWidth+3*halfDepth-position;}
        return f.center.add(f.right.scale(x)).add(f.front.scale(z))
                .add(f.up.scale(attachmentHeight(f,depth,seed)));
    }
    /** Stable minority reaches the sampled calf midpoint; shallow film coverage stays at the ankle. */
    public static double attachmentHeight(Frame f,double depth,long seed) {
        double calf=Math.max(0,f.calfHeight),variation=CompactStrandStyle.variation(seed^0x9E3779B97F4A7C15L);
        if(CompactStrandStyle.variation(seed^0xD1B54A32D192ED03L)<.25)return calf*(.8+.2*variation);
        return Math.min(height(depth)*(.25+.5*variation),calf);
    }
    /** Local cell coordinates; a sphere around the full start cap stays below every fluid corner. */
    public static Root submergedRoot(Vec desired,double lowestHeight,double requestedRadius) {
        if(!Double.isFinite(lowestHeight+requestedRadius+desired.length()) || lowestHeight<=0 || requestedRadius<=0)return null;
        double radius=Math.min(requestedRadius,lowestHeight*.2),margin=.004;
        double low=radius+margin,high=lowestHeight-radius-margin;
        if(low>=high)return null;
        double inset=Math.max(.035,radius+.006);
        // Lower the medium end, not the skin end. The whole cap still fits in shallow flowing cells and boards.
        return new Root(new Vec(Math.clamp(desired.x(),inset,1-inset),Math.clamp(Math.min(desired.y(),.06),low,high),Math.clamp(desired.z(),inset,1-inset)),radius);
    }
    /** Independent roots share a contact cell, never a branching stem or an exposed cap. */
    public static Root submergedRoot(Vec desired,double lowestHeight,double requestedRadius,long seed,int index,int count) {
        if(count<1 || count>CompactStrandStyle.MAX_DENSITY || index<0 || index>=count)
            throw new IllegalArgumentException("Invalid strand density");
        var root=submergedRoot(desired,lowestHeight,requestedRadius);if(root==null || index==0)return root;
        double spread=.09,inset=Math.max(.035,root.radius+.006);
        double x=Math.clamp(root.point.x(),inset+spread,1-inset-spread),z=Math.clamp(root.point.z(),inset+spread,1-inset-spread);
        double distance=spread*Math.sqrt(CompactStrandStyle.variation(seed^((index+1L)*0xD6E8FEB86659FD93L))),angle=CompactStrandStyle.angle(seed,index);
        return new Root(new Vec(x+distance*Math.cos(angle),root.point.y(),z+distance*Math.sin(angle)),root.radius);
    }
    public static List<Quad> film(Frame f,double depth,long seed) {
        double height=height(depth);if(height==0)return List.of();var quads=new ArrayList<Quad>();
        for(int i=0;i<8;i++) {
            int j=(i+1)%8;
            Vec a=edge(f,i,-.025,THICKNESS),b=edge(f,j,-.025,THICKNESS),c=edge(f,j,height*.45,THICKNESS),d=edge(f,i,height*.45,THICKNESS);
            var normal=f.right.scale(EDGE[i][0]+EDGE[j][0]).add(f.front.scale(EDGE[i][1]+EDGE[j][1])).unit();
            add(quads,a,b,c,d,normal,0x38ffffff);
            Vec topJ=edge(f,j,height*edgeHeight(seed,j),THICKNESS),topI=edge(f,i,height*edgeHeight(seed,i),THICKNESS);
            add(quads,d,c,topJ,topI,normal,0x24ffffff);
            Vec innerA=edge(f,i,-.025,0),innerB=edge(f,j,-.025,0),innerC=edge(f,j,height*.45,0),innerD=edge(f,i,height*.45,0);
            Vec innerTopI=edge(f,i,height*edgeHeight(seed,i),0),innerTopJ=edge(f,j,height*edgeHeight(seed,j),0);
            add(quads,innerA,innerB,innerC,innerD,normal.scale(-1),0x18ffffff);
            add(quads,innerD,innerC,innerTopJ,innerTopI,normal.scale(-1),0x18ffffff);
            add(quads,innerTopI,innerTopJ,topJ,topI,f.up,0x32ffffff);
            add(quads,innerA,innerB,b,a,f.up.scale(-1),0x30ffffff);
        }
        // Narrow highlights are translucent and lit normally, never emissive.
        for(int side:new int[]{-1,1})for(int sign:new int[]{-1,1}) {
            var start=f.center.add(f.front.scale(side*(f.halfDepth+THICKNESS+.001))).add(f.right.scale(sign*f.halfWidth*.55));
            var across=f.right.scale(.002);var rise=f.up.scale(height*.65);
            add(quads,start.subtract(across),start.add(across),start.add(across).add(rise),start.subtract(across).add(rise),f.front.scale(side),0x16ffffff);
        }
        return List.copyOf(quads);
    }
    /** Closed flattened hexagonal strip: real thickness, without a round rope silhouette. */
    public static List<Quad> tube(Vec start,Vec end,int segments,double startRadius,double endRadius) {
        return tubeShape(start,end,segments,startRadius,endRadius,.30,0,1);
    }
    public static List<Quad> tube(Vec start,Vec end,int segments,double startRadius,double endRadius,long seed) {
        double thickness=.23+.15*CompactStrandStyle.variation(seed^0xDB4F0B9175AE2165L);
        double bend=(CompactStrandStyle.variation(seed^0xBBE0563303A4615FL)-.5)*Math.min(.004,Math.min(startRadius,endRadius)*.18);
        double droop=.6+.8*CompactStrandStyle.variation(seed^0xA0F2EC75A1FE1575L);
        return tubeShape(start,end,segments,startRadius,endRadius,thickness,bend,droop);
    }
    private static List<Quad> tubeShape(Vec start,Vec end,int segments,double startRadius,double endRadius,double thickness,double bend,double droop) {
        Vec delta=end.subtract(start);double length=delta.length();
        if(!Double.isFinite(length) || length<1e-7)return List.of();
        if(segments<1 || segments>6 || !Double.isFinite(startRadius+endRadius) || startRadius<=0 || endRadius<=0)
            throw new IllegalArgumentException("Invalid adhesive tube dimensions");
        Vec axis=delta.unit(),right=new Vec(-axis.z(),0,axis.x());
        right=right.length()<1e-8?new Vec(1,0,0):right.unit();Vec up=axis.cross(right).unit();
        var rings=new Vec[segments+1][6];var centers=new Vec[segments+1];double sag=sag(length)*droop;
        for(int segment=0;segment<=segments;segment++) {
            double t=segment/(double)segments,radius=(startRadius+(endRadius-startRadius)*t)*taper(t);
            double curve=Math.sin(Math.PI*t);
            centers[segment]=start.add(delta.scale(t)).add(new Vec(0,-sag*curve,0)).add(right.scale(bend*curve));
            for(int side=0;side<6;side++) {
                double angle=side*Math.PI/3;
                rings[segment][side]=centers[segment].add(right.scale(radius*Math.cos(angle))).add(up.scale(radius*thickness*Math.sin(angle)));
            }
        }
        var quads=new ArrayList<Quad>(segments*6+12);
        for(int segment=0;segment<segments;segment++)for(int side=0;side<6;side++) {
            int next=(side+1)%6;Vec a=rings[segment][side],b=rings[segment+1][side],c=rings[segment+1][next],d=rings[segment][next];
            Vec outward=a.subtract(centers[segment]).add(d.subtract(centers[segment])).unit();
            add(quads,a,b,c,d,outward,side==0?0xefffffff:0xd8ffffff);
        }
        for(int side=0;side<6;side++) {
            int next=(side+1)%6;
            add(quads,start,rings[0][side],rings[0][next],start,axis.scale(-1),0xd8ffffff);
            add(quads,end,rings[segments][side],rings[segments][next],end,axis,0xd8ffffff);
        }
        return List.copyOf(quads);
    }
    private static double edgeHeight(long seed,int i){return .82+.06*Math.floorMod(seed+i*17,4);}
    private static Vec edge(Frame f,int i,double y,double extra) {return f.center.add(f.right.scale(EDGE[i][0]*(f.halfWidth+extra))).add(f.front.scale(EDGE[i][1]*(f.halfDepth+extra))).add(f.up.scale(y));}
    private static void add(List<Quad> list,Vec a,Vec b,Vec c,Vec d,Vec normal,int color) {
        Vec actual=b.subtract(a).cross(c.subtract(a)).unit();
        if(actual.dot(normal)<0)list.add(new Quad(d,c,b,a,actual.scale(-1),color));else list.add(new Quad(a,b,c,d,actual,color));
    }
    private WetAdhesiveStyle(){}
}
