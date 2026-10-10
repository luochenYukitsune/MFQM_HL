package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Vec;
import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Quad;
import java.util.List;

/** Surface queries in a single coordinate system, never random points inside a volume. */
public final class SkinSurfaceContact {
    public record Hit(Vec point,Vec normal) {}
    public static Hit project(Vec origin,Vec direction,List<Quad> surfaces) {
        if(!Double.isFinite(direction.length()) || direction.length()<1e-8)return null;
        Vec ray=direction.unit();Hit result=null;double farthest=0;
        for(var q:surfaces) {
            double facing=q.normal().dot(ray);if(facing<1e-7)continue;
            double distance=((q.a().x()-origin.x())*q.normal().x()+(q.a().y()-origin.y())*q.normal().y()+(q.a().z()-origin.z())*q.normal().z())/facing;
            if(distance<0 || distance<farthest)continue;
            double px=origin.x()+ray.x()*distance,py=origin.y()+ray.y()*distance,pz=origin.z()+ray.z()*distance;
            if(inside(px,py,pz,q.a(),q.b(),q.c()) || inside(px,py,pz,q.a(),q.c(),q.d())) {
                farthest=distance;result=new Hit(new Vec(px,py,pz),q.normal().unit());
            }
        }
        return result;
    }
    private static boolean inside(double px,double py,double pz,Vec a,Vec b,Vec c) {
        // Thousands of strand rays share these surfaces; avoid temporary vectors per triangle.
        double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
        double wx=px-a.x(),wy=py-a.y(),wz=pz-a.z();
        double uu=ux*ux+uy*uy+uz*uz,uv=ux*vx+uy*vy+uz*vz,vv=vx*vx+vy*vy+vz*vz,wu=wx*ux+wy*uy+wz*uz,wv=wx*vx+wy*vy+wz*vz,denominator=uu*vv-uv*uv;
        if(denominator<1e-16)return false;
        double x=(wu*vv-wv*uv)/denominator,y=(wv*uu-wu*uv)/denominator;
        return x>=-1e-6 && y>=-1e-6 && x+y<=1+1e-6;
    }
    public static Quad lift(Quad q,double distance) {
        Vec offset=q.normal().scale(distance);
        return new Quad(q.a().add(offset),q.b().add(offset),q.c().add(offset),q.d().add(offset),q.normal(),q.color());
    }
    /** Project a near-body vertex out of the selected attachment face, preserving its tangent coordinates. */
    public static Vec outside(Vec point,Hit skin) {
        double intrusion=point.subtract(skin.point()).dot(skin.normal());
        return intrusion>=0?point:point.subtract(skin.normal().scale(intrusion));
    }
    private SkinSurfaceContact() {}
}
