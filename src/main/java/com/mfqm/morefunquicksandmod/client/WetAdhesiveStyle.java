package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Vec;
import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Quad;
import java.util.ArrayList;
import java.util.List;

/** Cosmetic wet-film geometry in world blocks, independent of entities and physics. */
public final class WetAdhesiveStyle {
    public record Frame(Vec center,Vec up,Vec right,Vec front,double halfWidth,double halfDepth) {}
    public record Root(Vec point,double radius) {}
    public static final double THICKNESS=.003;
    private static final double[][] EDGE={{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1},{1,0}};
    public static double height(double depth) {return Double.isFinite(depth)?.08+.20*Math.clamp((depth-.15)/.85,0,1):0;}
    public static double width(double length,int variation) {return (.007+.002*Math.floorMod(variation,3))/Math.sqrt(1+Math.max(0,length-.12)*3);}
    public static double width(double length,int variation,String material) {
        return width(length,variation)*("glue".equals(material) || "sticky_board".equals(material)?3:1);
    }
    public static double taper(double t) {return .55+.45*Math.pow(Math.abs(2*Math.clamp(t,0,1)-1),1.5);}
    public static double sag(double length) {return Math.min(Math.max(0,length)*.04,.008/(1+Math.max(0,length)*3));}
    public static double contraction(double progress){double remaining=1-Math.clamp(progress,0,1);return remaining*remaining;}
    public static Vec endpoint(Frame f,double depth,long seed) {
        return endpoint(f,depth,seed,f.center.subtract(f.up));
    }
    /** Connect the surface facing this root, rather than selecting a random side of the leg. */
    public static Vec endpoint(Frame f,double depth,long seed,Vec root) {
        Vec towards=root.subtract(f.center);double x=towards.dot(f.right),z=towards.dot(f.front);
        if(Math.abs(x)+Math.abs(z)<1e-8){x=0;z=1;}
        double scale=1/Math.max(Math.abs(x)/(f.halfWidth+THICKNESS),Math.abs(z)/(f.halfDepth+THICKNESS));
        return f.center.add(f.right.scale(x*scale)).add(f.front.scale(z*scale))
                .add(f.up.scale(height(depth)*(.25+.5*CompactStrandStyle.variation(seed^0x9E3779B97F4A7C15L))));
    }
    /** Local cell coordinates; a sphere around the full start cap stays below every fluid corner. */
    public static Root submergedRoot(Vec desired,double lowestHeight,double requestedRadius) {
        if(!Double.isFinite(lowestHeight+requestedRadius+desired.length()) || lowestHeight<=0 || requestedRadius<=0)return null;
        double radius=Math.min(requestedRadius,lowestHeight*.2),margin=.004;
        double low=radius+margin,high=lowestHeight-radius-margin;
        if(low>=high)return null;
        double inset=Math.max(.035,radius+.006);
        return new Root(new Vec(Math.clamp(desired.x(),inset,1-inset),Math.clamp(desired.y(),low,high),Math.clamp(desired.z(),inset,1-inset)),radius);
    }
    /** Independent roots share a contact cell, never a branching stem or an exposed cap. */
    public static Root submergedRoot(Vec desired,double lowestHeight,double requestedRadius,long seed,int index,int count) {
        if(count<1 || count>CompactStrandStyle.MAX_DENSITY || index<0 || index>=count)
            throw new IllegalArgumentException("Invalid strand density");
        var root=submergedRoot(desired,lowestHeight,requestedRadius);if(root==null || count==1)return root;
        double spread=.09,inset=Math.max(.035,root.radius+.006);
        double x=Math.clamp(root.point.x(),inset+spread,1-inset-spread),z=Math.clamp(root.point.z(),inset+spread,1-inset-spread);
        double distance=spread*Math.sqrt((index+.5)/count),angle=CompactStrandStyle.angle(seed,index);
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
        Vec delta=end.subtract(start);double length=delta.length();
        if(!Double.isFinite(length) || length<1e-7)return List.of();
        if(segments<1 || segments>6 || !Double.isFinite(startRadius+endRadius) || startRadius<=0 || endRadius<=0)
            throw new IllegalArgumentException("Invalid adhesive tube dimensions");
        Vec axis=delta.unit(),right=new Vec(-axis.z(),0,axis.x());
        right=right.length()<1e-8?new Vec(1,0,0):right.unit();Vec up=axis.cross(right).unit();
        var rings=new Vec[segments+1][6];var centers=new Vec[segments+1];double sag=sag(length);
        for(int segment=0;segment<=segments;segment++) {
            double t=segment/(double)segments,radius=(startRadius+(endRadius-startRadius)*t)*taper(t);
            centers[segment]=start.add(delta.scale(t)).add(new Vec(0,-sag*Math.sin(Math.PI*t),0));
            for(int side=0;side<6;side++) {
                double angle=side*Math.PI/3;
                rings[segment][side]=centers[segment].add(right.scale(radius*Math.cos(angle))).add(up.scale(radius*.30*Math.sin(angle)));
            }
        }
        var quads=new ArrayList<Quad>(segments*6+12);
        for(int segment=0;segment<segments;segment++)for(int side=0;side<6;side++) {
            int next=(side+1)%6;Vec a=rings[segment][side],b=rings[segment+1][side],c=rings[segment+1][next],d=rings[segment][next];
            Vec outward=a.subtract(centers[segment]).add(d.subtract(centers[segment])).unit();
            add(quads,a,b,c,d,outward,side==0?0xd0ffffff:0xb0ffffff);
        }
        for(int side=0;side<6;side++) {
            int next=(side+1)%6;
            add(quads,start,rings[0][side],rings[0][next],start,axis.scale(-1),0xb0ffffff);
            add(quads,end,rings[segments][side],rings[segments][next],end,axis,0xb0ffffff);
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
