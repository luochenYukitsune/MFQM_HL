package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import net.neoforged.fml.ModList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.model.geom.ModelPart;
import java.util.HashMap;
import java.util.Map;

/** Optional, read-only configuration bridge. No third-party classes enter our linkage or server API. */
public final class SkinLayerClearance {
    private static boolean resolved,failed;
    private static Field config;
    private static Method injectedMesh,visibleMesh;
    private static final Map<String,Field> FIELDS=new HashMap<>();
    public static boolean present(){return ModList.get().isLoaded("skinlayers3d");}
    public static float padding(String part,boolean firstPerson,ModelPart outer) {
        if(outer!=null && (!outer.visible || !active(outer)))return part.equals("head")?.65F:.35F;
        return padding(part,firstPerson);
    }
    private static boolean active(ModelPart outer) {
        if(!present() || failed)return false;
        try {
            if(injectedMesh==null) {
                injectedMesh=ModelPart.class.getMethod("getInjectedMesh");
                visibleMesh=Class.forName("dev.tr7zw.skinlayers.api.Mesh").getMethod("isVisible");
            }
            Object mesh=injectedMesh.invoke(outer);
            return mesh!=null && (boolean)visibleMesh.invoke(mesh);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError e) {
            unavailable(e);return false;
        }
    }
    public static float padding(String part,boolean firstPerson) {
        float vanilla=part.equals("head")?.65F:.35F;
        if(!present() || failed)return vanilla;
        try {
            if(!resolved) {
                config=Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase").getField("config");resolved=true;
            }
            Object settings=config.get(null);if(settings==null)return vanilla;
            String flag=switch(part){case "head"->"enableHat";case "body"->"enableJacket";
                case "left_arm"->"enableLeftSleeve";case "right_arm"->"enableRightSleeve";
                case "left_leg"->"enableLeftPants";default->"enableRightPants";};
            if(!(boolean)field(settings,flag).get(settings))return vanilla;
            float base=number(settings,firstPerson?"firstPersonPixelScaling":"baseVoxelSize");
            float size=part.equals("head")?number(settings,"headVoxelSize"):base;
            float half=part.equals("head") || part.equals("body")?4:2;
            float width=part.equals("body")?number(settings,"bodyVoxelWidthSize"):size;
            // One extruded voxel plus scale growth of the underlying part. Head
            // uses a larger allowance for the skin mod's separate pivot offset.
            return Math.max(vanilla,Math.max((size-1)*2+size*.5F,(width-1)*half+size*.5F)+(part.equals("head")?.2F:.1F));
        } catch(ReflectiveOperationException | RuntimeException | LinkageError e) {
            unavailable(e);
            return vanilla;
        }
    }
    private static void unavailable(Throwable e){failed=true;MFQM.LOGGER.warn("Optional 3D Skin Layers sizing is unavailable; using vanilla coating clearance",e);}
    private static Field field(Object settings,String name)throws ReflectiveOperationException {
        var field=FIELDS.get(name);
        if(field==null){field=settings.getClass().getField(name);FIELDS.put(name,field);}
        return field;
    }
    private static float number(Object settings,String name)throws ReflectiveOperationException {
        float number=((Number)field(settings,name).get(settings)).floatValue();
        if(!Float.isFinite(number) || number<.5F || number>4)throw new IllegalArgumentException("Invalid skin voxel size: "+name);
        return number;
    }
    private SkinLayerClearance(){}
}
