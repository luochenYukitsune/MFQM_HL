package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Cached pixel surfaces, submitted with immutable geometry and a snapshot of each native part's pose. */
public final class GlueCoatingRenderer {
    private record Key(Identifier texture,String part,boolean slim,float padding,float thickness){}
    private record Geometry(ModelPart.Cube cube,CoatingVoxels.Mesh mesh,java.util.List<CoatingVoxels.Quad> worldQuads){}
    private static final Map<Key,Geometry> CACHE=new LinkedHashMap<>(32,.75F,true);
    private static final Set<Identifier> FAILED=new java.util.HashSet<>();
    private record MaskImage(int width,int height,int[] colors) {
        int sample(double u,double v) {
            int x0=(int)Math.floor((u-.5)*width/64.),y0=(int)Math.floor((v-.5)*height/32.);
            int x1=(int)Math.ceil((u+.5)*width/64.),y1=(int)Math.ceil((v+.5)*height/32.);
            long a=0,r=0,g=0,b=0;int count=0;
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
                int c=colors[Math.clamp(y,0,height-1)*width+Math.clamp(x,0,width-1)],alpha=c>>>24;
                a+=alpha;r+=(c>>16&255)*alpha;g+=(c>>8&255)*alpha;b+=(c&255)*alpha;count++;
            }
            return a==0 || count==0?0:(int)(a/count)<<24|(int)(r/a)<<16|(int)(g/a)<<8|(int)(b/a);
        }
    }
    private static final Map<Identifier,MaskImage> MASKS=new LinkedHashMap<>(32,.75F,true);
    private static long detailedSubmissions,flatSubmissions,armSubmissions;
    private static final Map<String,Long> FAMILIES=new java.util.HashMap<>();
    public static void clear(){CACHE.clear();MASKS.clear();FAILED.clear();FlatCoatingTextures.clear();NativeSkinSurface.clear();}
    public static int cachedMeshes(){return CACHE.size();}
    static long detailedSubmissions(){return detailedSubmissions;}
    static long flatSubmissions(){return flatSubmissions;}
    static long armSubmissions(){return armSubmissions;}
    static long submissions(String family){return FAMILIES.getOrDefault(family,0L);}

    public static void submit(ModelPart part,String name,boolean slim,MuddyPlayerLayer.Coating coat,
                              PoseStack pose,SubmitNodeCollector collector,int light,boolean firstPerson,boolean detailed) {
        submit(part,name,slim,coat,pose,collector,light,firstPerson,detailed,null);
    }
    public static void submit(ModelPart part,String name,boolean slim,MuddyPlayerLayer.Coating coat,
                              PoseStack pose,SubmitNodeCollector collector,int light,boolean firstPerson,boolean detailed,ModelPart outer) {
        submit(part,name,slim,coat,pose,collector,light,firstPerson,detailed,outer,null);
    }
    public static void submit(ModelPart part,String name,boolean slim,MuddyPlayerLayer.Coating coat,
                              PoseStack pose,SubmitNodeCollector collector,int light,boolean firstPerson,boolean detailed,ModelPart outer,Identifier skin) {
        if(!part.visible || part.skipDraw)return;
        var settings=ModConfig.CLIENT.visuals(CoatingAppearance.family(coat.material()));
        double opacity=CoatingAppearance.materialOpacity(coat.material())*ModConfig.CLIENT.coatingOpacity.get()*settings.opacity().get();
        if(opacity<=0)return;
        float thickness=(float)(Math.round(ModConfig.CLIENT.coatingThickness.get()*settings.thickness().get()*16)/16.);
        // Base skin and visible outer pixels get separate, surface-following films.
        // Never inflate a whole body part to the largest clothing voxel radius.
        float padding=.012F;
        boolean volume=ModConfig.CLIENT.glueCoating3d.get() && detailed && thickness>0;
        var geometry=geometry(coat.texture(),name,slim,padding,Math.max(.0625F,thickness));if(geometry==null)return;
        if(geometry.mesh().pixels()==0)return;
        pose.pushPose();part.translateAndRotate(pose);
        int tint=coat.color();
        if(volume) {
            detailedSubmissions++;
            FAMILIES.merge(CoatingAppearance.family(coat.material()),1L,Long::sum);
            if(firstPerson)armSubmissions++;
            var quads=geometry.worldQuads();
            collector.order(1).submitCustomGeometry(pose,RenderTypes.entityTranslucent(TranslucentGeometry.TEXTURE),(matrix,vertices)->{
                for(var q:quads)TranslucentGeometry.quad(vertices,matrix,q,tint,light,opacity);
            });
        } else {
            flatSubmissions++;
            var texture=FlatCoatingTextures.texture(coat.texture(),opacity*(tint>>>24)/255.);
            if(texture!=null) {
                var cube=geometry.cube();int opaque=tint|0xff000000;
                collector.order(1).submitCustomGeometry(pose,RenderTypes.entityTranslucent(texture),
                        (matrix,vertices)->cube.compile(matrix,vertices,light,OverlayTexture.NO_OVERLAY,opaque));
            }
        }
        var mask=MASKS.get(coat.texture());
        if(mask!=null && outer!=null) {
            var surface=NativeSkinSurface.surface(outer,skin,firstPerson,name,slim,volume);
            double ox=switch(name){case "head"->32;case "body"->16;case "left_arm"->48;case "right_arm"->40;default->0;};
            double oy=switch(name){case "head"->0;case "left_arm","left_leg"->48;default->32;};
            double lu=name.endsWith("arm")?40:name.equals("body")?16:0,lv=name.equals("head")?0:16;
            if(surface.flat() && !volume && !surface.faces().isEmpty()) {
                var texture=FlatCoatingTextures.outerTexture(coat.texture(),skin,name,opacity*(tint>>>24)/255.,
                        (x,y)->mask.sample(x+.5-ox+lu,y+.5-oy+lv));
                if(texture!=null)collector.order(1).submitCustomGeometry(pose,RenderTypes.entityTranslucent(texture),(matrix,vertices)->{
                    for(var face:surface.faces())TranslucentGeometry.texturedQuad(vertices,matrix,SkinSurfaceContact.lift(face.quad(),.012/16.),face.uv(),tint,light);
                });
            } else {
            var quads=new ArrayList<CoatingVoxels.Quad>();
            for(var face:surface.faces()) {
                int color=mask.sample(face.u()*64-ox+lu,face.v()*64-oy+lv);
                if((color>>>24)<32)continue;
                var q=face.quad();double relief=(.012+(volume?.006*thickness:0))/16.;
                q=new CoatingVoxels.Quad(q.a(),q.b(),q.c(),q.d(),q.normal(),color);
                quads.add(SkinSurfaceContact.lift(q,relief));
            }
            var immutable=java.util.List.copyOf(quads);
            collector.order(1).submitCustomGeometry(pose,RenderTypes.entityTranslucent(TranslucentGeometry.TEXTURE),(matrix,vertices)->{
                for(var q:immutable)TranslucentGeometry.quad(vertices,matrix,q,tint,light,opacity);
            });
            }
        }
        pose.popPose();
    }
    static CoatingVoxels.Mesh mesh(Identifier texture,String part,boolean slim,boolean firstPerson) {
        var geometry=geometry(texture,part,slim,.012F,1);
        return geometry==null?new CoatingVoxels.Mesh(java.util.List.of(),0):geometry.mesh();
    }
    private static Geometry geometry(Identifier texture,String part,boolean slim,float padding,float thickness) {
        var key=new Key(texture,part,slim,padding,thickness);var existing=CACHE.get(key);if(existing!=null)return existing;
        if(FAILED.contains(texture))return null;
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture).open();var image=NativeImage.read(stream)) {
            if(image.getWidth()<64 || image.getHeight()<32)throw new IOException("Coating UV image is too small");
            int[] colors=new int[image.getWidth()*image.getHeight()];
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)colors[y*image.getWidth()+x]=image.getPixel(x,y);
            MASKS.put(texture,new MaskImage(image.getWidth(),image.getHeight(),colors));
            if(MASKS.size()>96)MASKS.remove(MASKS.keySet().iterator().next());
            var cube=cube(part,slim,padding);var faces=new ArrayList<CoatingVoxels.Face>();
            for(var polygon:cube.polygons) {
                var vertices=polygon.vertices();
                float minU=1,minV=1,maxU=0,maxV=0;
                for(var v:vertices){minU=Math.min(minU,v.u());maxU=Math.max(maxU,v.u());minV=Math.min(minV,v.v());maxV=Math.max(maxV,v.v());}
                var origin=corner(vertices,minU,minV);var right=corner(vertices,maxU,minV);var bottom=corner(vertices,minU,maxV);
                var normal=polygon.normal();
                faces.add(new CoatingVoxels.Face(point(origin),point(right).subtract(point(origin)),point(bottom).subtract(point(origin)),
                        new CoatingVoxels.Vec(normal.x(),normal.y(),normal.z()),Math.round(minU*64),Math.round(minV*32),
                        Math.round((maxU-minU)*64),Math.round((maxV-minV)*32)));
            }
            // Average a complete native UV pixel, keeping the 128x64 refreshed mask
            // faithful to its original 64x32 coverage rather than sampling one corner.
            var mesh=CoatingVoxels.build(faces,(u,v)->sample(image,u,v),thickness);
            var result=new Geometry(cube,mesh,mesh.quads().stream().map(TranslucentGeometry::modelPixels).toList());CACHE.put(key,result);
            if(CACHE.size()>96)CACHE.remove(CACHE.keySet().iterator().next());
            return result;
        } catch(IOException | RuntimeException e) {
            if(FAILED.add(texture))MFQM.LOGGER.warn("Unable to build material coating {}",texture,e);
            return null;
        }
    }
    private static int sample(NativeImage image,double u,double v) {
        int x0=(int)Math.floor((u-.5)*image.getWidth()/64.),y0=(int)Math.floor((v-.5)*image.getHeight()/32.);
        int x1=(int)Math.ceil((u+.5)*image.getWidth()/64.),y1=(int)Math.ceil((v+.5)*image.getHeight()/32.);
        long a=0,r=0,g=0,b=0;int count=0;
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
            int c=image.getPixel(Math.clamp(x,0,image.getWidth()-1),Math.clamp(y,0,image.getHeight()-1)),alpha=c>>>24;
            a+=alpha;r+=(c>>16&255)*alpha;g+=(c>>8&255)*alpha;b+=(c&255)*alpha;count++;
        }
        if(a==0)return 0;
        return (int)(a/count)<<24|(int)(r/a)<<16|(int)(g/a)<<8|(int)(b/a);
    }
    private static ModelPart.Vertex corner(ModelPart.Vertex[] vertices,float u,float v) {
        return Arrays.stream(vertices).min(java.util.Comparator.comparingDouble(p->Math.abs(p.u()-u)+Math.abs(p.v()-v))).orElseThrow();
    }
    private static CoatingVoxels.Vec point(ModelPart.Vertex v){return new CoatingVoxels.Vec(v.x(),v.y(),v.z());}
    private static ModelPart.Cube cube(String part,boolean slim,float p) {
        int u=0,v=16;float x=-2,y=0,z=-2,w=4,h=12,d=4;boolean mirror=part.startsWith("left");
        switch(part) {
            case "head"->{u=0;v=0;x=-4;y=-8;z=-4;w=8;h=8;d=8;}
            case "body"->{u=16;x=-4;w=8;}
            case "left_arm","right_arm"->{u=40;x=mirror?-1:slim?-2:-3;y=-2;w=slim?3:4;}
            case "left_leg","right_leg"->{}
            default->throw new IllegalArgumentException("Unknown coating part: "+part);
        }
        return new ModelPart.Cube(u,v,x,y,z,w,h,d,p,p,p,mirror,64,32,Set.of(Direction.values()));
    }
    private GlueCoatingRenderer(){}
}
