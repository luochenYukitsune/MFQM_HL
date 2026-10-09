package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Keeps the six-face distant/flat LOD while baking fade/intensity into texture alpha. */
final class FlatCoatingTextures {
    private record Key(Identifier source,int intensity){}
    private static final class Entry {
        final Identifier texture;
        long used;
        Entry(Identifier texture){this.texture=texture;used=frame;}
    }
    private static final LinkedHashMap<Key,Entry> CACHE=new LinkedHashMap<>(32,.75F,true);
    private static long frame;
    static void beginFrame() {
        frame++;
        if(CACHE.size()<=64)return;
        // Never release a texture retained by a current or immediately preceding deferred draw.
        var iterator=CACHE.entrySet().iterator();
        while(iterator.hasNext() && CACHE.size()>64) {
            var entry=iterator.next().getValue();
            if(entry.used>=frame-1)continue;
            Minecraft.getInstance().getTextureManager().release(entry.texture);iterator.remove();
        }
    }
    static void clear(){for(var entry:CACHE.values())Minecraft.getInstance().getTextureManager().release(entry.texture);CACHE.clear();}
    static Identifier texture(Identifier source,double opacity) {
        int intensity=(int)Math.round(Math.clamp(opacity,0,8)*128);
        var key=new Key(source,intensity);var old=CACHE.get(key);
        if(old!=null){old.used=frame;return old.texture;}
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(source).open()) {
            var image=NativeImage.read(stream);double factor=intensity/128.;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                int pixel=image.getPixel(x,y),alpha=(int)Math.clamp(Math.round((pixel>>>24)*factor),0,255);
                image.setPixel(x,y,pixel&0xffffff|alpha<<24);
            }
            // RenderTypes memoizes by Identifier permanently: reuse an ID after eviction/reload.
            var id=Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"runtime/flat_coating/"+source.getNamespace()+"/"+source.getPath()+"/"+intensity);
            try{Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(()->"MFQM flat coating alpha",image));}
            catch(RuntimeException error){image.close();throw error;}
            CACHE.put(key,new Entry(id));return id;
        }catch(IOException | RuntimeException error){MFQM.LOGGER.warn("Unable to bake flat coating alpha {}",source,error);return null;}
    }
    private FlatCoatingTextures(){}
}
