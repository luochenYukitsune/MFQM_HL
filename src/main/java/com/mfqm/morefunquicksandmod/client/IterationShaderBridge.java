package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Iris reads IdMap after constructing ProgramSource. Adapt getters at compile time, not constructors. */
public final class IterationShaderBridge {
    private static volatile IterationFluidCompatibility.Selection current=new IterationFluidCompatibility.Selection(IterationFluidCompatibility.Profile.NONE,0,null);
    private static volatile boolean vertexApplied,fragmentApplied;
    public static String properties(Path shaders,String name,String original) {
        if(!"block.properties".equals(name))return original;
        current=new IterationFluidCompatibility.Selection(IterationFluidCompatibility.Profile.NONE,0,original);
        vertexApplied=false;fragmentApplied=false;
        if(original==null)return null;
        try {
            Path folder=shaders.resolve("Lib/Programs/Gbuffers");
            Path vertex=folder.resolve("Water_VS.glsl"),fragment=folder.resolve("Water_FS.glsl");
            if(!Files.isRegularFile(vertex) || !Files.isRegularFile(fragment))return original;
            var selection=IterationFluidCompatibility.select(original,Files.readString(vertex),Files.readString(fragment));
            if(selection.enabled())for(var contract:IterationShaderContracts.expected(selection.profile()).entrySet()) {
                Path file=shaders.resolve(contract.getKey());
                if(!Files.isRegularFile(file) || !IterationShaderContracts.matches(contract.getValue(),Files.readString(file))) {
                    MFQM.LOGGER.warn("MFQM Iteration material contract changed at {}; keeping original shader",contract.getKey());return original;
                }
            }
            current=selection;
            if(selection.enabled())MFQM.LOGGER.info("MFQM_ITERATION_FLUID_COMPAT profile={} materialId={} scope=stickyFluids",selection.profile(),selection.id());
            return selection.properties();
        }catch(IOException | RuntimeException error){MFQM.LOGGER.warn("MFQM Iteration fluid signature could not be read; keeping original shader",error);return original;}
    }
    public static String vertex(String name,String source){String result=IterationFluidCompatibility.vertex(current,name,source);if(!result.equals(source))vertexApplied=true;return result;}
    public static String fragment(String name,String source){String result=IterationFluidCompatibility.fragment(current,name,source);if(!result.equals(source))fragmentApplied=true;return result;}
    public static boolean enabled(){return current.enabled();}
    public static boolean applied(){return vertexApplied && (current.profile()!=IterationFluidCompatibility.Profile.RP || fragmentApplied);}
    public static int materialId(){return current.id();}
    private IterationShaderBridge(){}
}
