package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.WeakHashMap;

/** Snapshot the geometry actually used by visible clothing, including optional skin voxels. */
final class NativeSkinSurface {
    record Face(CoatingVoxels.Quad quad,double u,double v,double[] uv) {
        Face { if(uv!=null)uv=uv.clone(); }
        Face(CoatingVoxels.Quad quad,double u,double v){this(quad,u,v,null);}
    }
    private record Pixels(int width,int height,int[] rgba) {
        int alpha(double u,double v){return rgba[Math.clamp((int)(v*height),0,height-1)*width+Math.clamp((int)(u*width),0,width-1)]>>>24;}
    }
    private static final LinkedHashMap<Identifier,Pixels> SKINS=new LinkedHashMap<>(32,.75F,true);
    record Surface(List<Face> faces,boolean flat){}
    private record FlatSnapshot(List<ModelPart.Cube> cubes,List<Face> raw,List<Face> pixels){}
    private static final WeakHashMap<ModelPart,LinkedHashMap<Identifier,FlatSnapshot>> FLAT=new WeakHashMap<>();
    private static final WeakHashMap<Object,Boolean> FAILED_MESHES=new WeakHashMap<>();
    private static Method getMesh,getOffset,visible,render,applyOffset;
    private static boolean apiUnavailable;
    private static long voxelSnapshots;
    static long voxelSnapshots(){return voxelSnapshots;}
    static void clear(){SKINS.clear();FLAT.clear();FAILED_MESHES.clear();apiUnavailable=false;}
    static List<Face> capture(ModelPart part,Identifier skin) {
        return capture(part,skin,false,"",false);
    }
    static List<Face> capture(ModelPart part,Identifier skin,boolean firstPerson,String name,boolean slim) {
        return surface(part,skin,firstPerson,name,slim,true).faces();
    }
    static Surface surface(ModelPart part,Identifier skin,boolean firstPerson,String name,boolean slim,boolean detailed) {
        var empty=new Surface(List.of(),false);
        if(part==null || !part.visible || part.skipDraw)return empty;
        if(SkinLayerClearance.present() && apiUnavailable)return empty;
        Object mesh=null,offset=null;
        if(SkinLayerClearance.present())try {
            if(getMesh==null) {
                var meshApi=Class.forName("dev.tr7zw.skinlayers.api.Mesh");var offsetApi=Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider");
                visible=meshApi.getMethod("isVisible");render=meshApi.getMethod("render",ModelPart.class,PoseStack.class,VertexConsumer.class,int.class,int.class,int.class);
                applyOffset=offsetApi.getMethod("applyOffset",PoseStack.class,meshApi);
                getOffset=ModelPart.class.getMethod("getOffsetProvider");getMesh=ModelPart.class.getMethod("getInjectedMesh");
            }
            mesh=getMesh.invoke(part);offset=getOffset.invoke(part);
        }catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
            apiUnavailable=true;MFQM.LOGGER.warn("Optional skin surface API unavailable; retaining only the base skin film until reload",error);
            return empty;
        }
        // A failed/hidden injected mesh must not fall back to a fabricated box.
        // A single bad mesh does not disable other players' healthy geometry.
        if(mesh!=null)try {
            if(FAILED_MESHES.containsKey(mesh) || offset==null || !(boolean)visible.invoke(mesh))return empty;
            if(firstPerson && name.endsWith("arm"))offset=Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider")
                    .getField("FIRSTPERSON_"+(name.startsWith("right")?"RIGHT":"LEFT")+"_ARM"+(slim?"_SLIM":"")).get(null);
            var buffer=new Capture();var pose=new PoseStack();applyOffset.invoke(offset,pose,mesh);
            render.invoke(mesh,part,pose,buffer,0,0,0xffffffff);voxelSnapshots++;
            return new Surface(buffer.finish(),false);
        }catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
            FAILED_MESHES.put(mesh,Boolean.TRUE);MFQM.LOGGER.warn("Unable to snapshot one skin mesh; retaining its base skin film until reload",error);
            return empty;
        }
        Pixels pixels=skin==null?null:pixels(skin);
        if(pixels==null)return empty;
        var cubes=new ArrayList<ModelPart.Cube>();
        part.visit(new PoseStack(),(pose,path,index,cube)->{if(path.isEmpty())cubes.add(cube);});
        var cache=FLAT.computeIfAbsent(part,key->new LinkedHashMap<>(4,.75F,true));
        var snapshot=cache.get(skin);
        if(snapshot==null || !snapshot.cubes().equals(cubes)) {
            var buffer=new Capture();for(var cube:cubes)cube.compile(new PoseStack().last(),buffer,0,0,0xffffffff);
            var raw=buffer.finish();snapshot=new FlatSnapshot(List.copyOf(cubes),raw,splitVisible(raw,pixels));
            cache.put(skin,snapshot);if(cache.size()>4)cache.remove(cache.keySet().iterator().next());
        }
        return new Surface(detailed?snapshot.pixels():snapshot.raw(),true);
    }
    static int skinAlpha(Identifier skin,double u,double v){var image=skin==null?null:pixels(skin);return image==null?0:image.alpha(u,v);}
    static List<Face> base(ModelPart part) {
        var capture=new Capture();part.visit(new PoseStack(),(pose,path,index,cube)->{
            if(path.isEmpty())cube.compile(new PoseStack().last(),capture,0,0,0xffffffff);
        });return capture.finish();
    }
    private static Pixels pixels(Identifier id) {
        var cached=SKINS.get(id);if(cached!=null)return cached;
        var game=Minecraft.getInstance();Pixels result=null;
        var texture=game.getTextureManager().getTexture(id);
        if(texture instanceof DynamicTexture dynamic && dynamic.getPixels()!=null)result=copy(dynamic.getPixels());
        else try(var stream=game.getResourceManager().getResourceOrThrow(id).open();var image=NativeImage.read(stream)){result=copy(image);}
        catch(java.io.IOException ignored){return null;}
        SKINS.put(id,result);if(SKINS.size()>32)SKINS.remove(SKINS.keySet().iterator().next());return result;
    }
    private static Pixels copy(NativeImage image) {
        int[] pixels=new int[image.getWidth()*image.getHeight()];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)pixels[y*image.getWidth()+x]=image.getPixel(x,y);
        return new Pixels(image.getWidth(),image.getHeight(),pixels);
    }
    private static List<Face> splitVisible(List<Face> faces,Pixels skin) {
        var result=new ArrayList<Face>();
        // Vanilla polygons use four rectangle corners. Subdivide in native skin
        // UV pixels so transparent sleeves/hat regions remain truly absent.
        for(var face:faces) {
            var q=face.quad();var raw=face.uv();if(raw==null)continue;
            int columns=Math.max(1,(int)Math.round(Math.hypot(raw[2]-raw[0],raw[3]-raw[1])*64));
            int rows=Math.max(1,(int)Math.round(Math.hypot(raw[6]-raw[0],raw[7]-raw[1])*64));
            var across=q.b().subtract(q.a());var down=q.d().subtract(q.a());
            for(int y=0;y<rows;y++)for(int x=0;x<columns;x++) {
                double tx=(x+.5)/columns,ty=(y+.5)/rows;
                double u=raw[0]+(raw[2]-raw[0])*tx+(raw[6]-raw[0])*ty,v=raw[1]+(raw[3]-raw[1])*tx+(raw[7]-raw[1])*ty;
                if(skin.alpha(u,v)<32)continue;
                var a=q.a().add(across.scale(x/(double)columns)).add(down.scale(y/(double)rows));
                var b=a.add(across.scale(1./columns));var d=a.add(down.scale(1./rows));
                result.add(new Face(new CoatingVoxels.Quad(a,b,b.add(down.scale(1./rows)),d,q.normal(),0xffffffff),u,v));
            }
        }
        return List.copyOf(result);
    }
    private static final class Capture implements VertexConsumer {
        private final ArrayList<Face> result=new ArrayList<>();
        private final CoatingVoxels.Vec[] points=new CoatingVoxels.Vec[4];
        private final double[] uv=new double[8];
        private int count;private CoatingVoxels.Vec normal;
        public VertexConsumer addVertex(float x,float y,float z){
            if(count==4)quad();points[count++]=new CoatingVoxels.Vec(x,y,z);return this;
        }
        public VertexConsumer setUv(float u,float v){uv[(count-1)*2]=u;uv[(count-1)*2+1]=v;return this;}
        public VertexConsumer setNormal(float x,float y,float z){normal=new CoatingVoxels.Vec(x,y,z);return this;}
        public VertexConsumer setColor(int c){return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setLineWidth(float width){return this;}
        private void quad(){
            var q=new CoatingVoxels.Quad(points[0],points[1],points[2],points[3],normal.unit(),0xffffffff);
            result.add(new Face(q,(uv[0]+uv[2]+uv[4]+uv[6])*.25,(uv[1]+uv[3]+uv[5]+uv[7])*.25,uv));count=0;
        }
        List<Face> finish(){if(count==4)quad();return List.copyOf(result);}
    }
    private NativeSkinSurface() {}
}
