package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/** Read-only optional public Iris API. Vanilla rendering does not require Iris on the classpath. */
public final class ShaderCompatibility {
    private static boolean resolved;
    private static Object api;
    private static Method shadow,active;
    private static void resolve() {
        if(resolved)return;
        resolved=true;
        if(!ModList.get().isLoaded("iris"))return;
        try {
            var type=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api=type.getMethod("getInstance").invoke(null);
            shadow=type.getMethod("isRenderingShadowPass");active=type.getMethod("isShaderPackInUse");
        }catch(ReflectiveOperationException | LinkageError error){api=null;MFQM.LOGGER.warn("Iris public API unavailable; using native rendering",error);}
    }
    private static boolean flag(boolean shadowFlag) {
        resolve();if(api==null)return false;
        try{return (boolean)(shadowFlag?shadow:active).invoke(api);}
        catch(ReflectiveOperationException | LinkageError error){api=null;MFQM.LOGGER.warn("Iris public API call failed; using native rendering",error);return false;}
    }
    public static boolean shadowPass(){return flag(true);}
    public static boolean active(){return flag(false);}
    private ShaderCompatibility(){}
}
