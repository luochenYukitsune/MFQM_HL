package com.mfqm.morefunquicksandmod.client;

import java.util.ArrayList;
import java.util.List;

/** Pure, deterministic extrusion of legacy skin UV pixels. Coordinates are model pixels. */
public final class CoatingVoxels {
    public record Vec(double x,double y,double z) {
        public Vec add(Vec b){return new Vec(x+b.x,y+b.y,z+b.z);}
        public Vec subtract(Vec b){return new Vec(x-b.x,y-b.y,z-b.z);}
        public Vec scale(double n){return new Vec(x*n,y*n,z*n);}
        public double dot(Vec b){return x*b.x+y*b.y+z*b.z;}
        public Vec cross(Vec b){return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        public double length(){return Math.sqrt(dot(this));}
        public Vec unit(){return scale(1/length());}
    }
    public record Face(Vec origin,Vec across,Vec down,Vec normal,int u,int v,int width,int height) {}
    @FunctionalInterface public interface Mask { int color(double u,double v); }
    public record Quad(Vec a,Vec b,Vec c,Vec d,Vec normal,int color) {}
    public record Mesh(List<Quad> quads,int pixels) {
        public Mesh { quads=List.copyOf(quads); }
    }
    public static Mesh build(List<Face> faces,Mask mask) {
        var quads=new ArrayList<Quad>();int pixels=0;
        for(var f:faces) {
            if(f.width<1 || f.height<1 || f.width>64 || f.height>64)throw new IllegalArgumentException("Invalid UV face");
            var u=f.across.scale(1./f.width);var v=f.down.scale(1./f.height);
            double[][] heights=new double[f.height][f.width];int[][] colors=new int[f.height][f.width];
            for(int y=0;y<f.height;y++)for(int x=0;x<f.width;x++) {
                int color=mask.color(f.u+x+.5,f.v+y+.5),alpha=color>>>24;
                if(alpha<32)continue;
                pixels++;
                // The mask owns opacity; do not turn a thin translucent patch
                // into dense white paint when switching to voxel geometry.
                colors[y][x]=color;
                heights[y][x]=.12+.04*((f.u+x)*37+(f.v+y)*17&3);
            }
            for(int y=0;y<f.height;y++)for(int x=0;x<f.width;x++) {
                double h=heights[y][x];if(h==0)continue;
                var base=f.origin.add(u.scale(x)).add(v.scale(y));var top=base.add(f.normal.scale(h));
                int color=colors[y][x];
                add(quads,top,top.add(u),top.add(u).add(v),top.add(v),f.normal,color);
                wall(quads,base,base.add(v),f.normal,u.unit().scale(-1),h,neighbor(heights,x-1,y),color);
                wall(quads,base.add(u),base.add(u).add(v),f.normal,u.unit(),h,neighbor(heights,x+1,y),color);
                wall(quads,base,base.add(u),f.normal,v.unit().scale(-1),h,neighbor(heights,x,y-1),color);
                wall(quads,base.add(v),base.add(v).add(u),f.normal,v.unit(),h,neighbor(heights,x,y+1),color);
            }
        }
        return new Mesh(quads,pixels);
    }
    private static double neighbor(double[][] heights,int x,int y) {
        return x<0 || y<0 || y>=heights.length || x>=heights[0].length?.02:Math.max(.02,heights[y][x]);
    }
    private static void wall(List<Quad> quads,Vec a,Vec b,Vec outward,Vec normal,double high,double low,int color) {
        if(high<=low+.0001)return;
        add(quads,a.add(outward.scale(low)),b.add(outward.scale(low)),b.add(outward.scale(high)),a.add(outward.scale(high)),normal,color);
    }
    private static void add(List<Quad> quads,Vec a,Vec b,Vec c,Vec d,Vec normal,int color) {
        if(b.subtract(a).cross(c.subtract(a)).dot(normal)<0)quads.add(new Quad(d,c,b,a,normal,color));
        else quads.add(new Quad(a,b,c,d,normal,color));
    }
    private CoatingVoxels(){}
}
