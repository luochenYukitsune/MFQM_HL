package com.mfqm.morefunquicksandmod.client;

import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Run with the two supplied pack paths to verify the real source signatures too. */
public final class IterationFluidCompatibilityTest {
    private static int checks;
    private static final String T_VERTEX="flat out float materialIDs; void main(){ gl_Position = gl_Vertex; materialIDs = MATID_STAINEDGLASS; if(mc_Entity.x == 8.0){materialIDs = MATID_WATER;} }";
    private static final String T_FRAGMENT="layout(location = 2) out vec4 gbufferOutput0; void main(){ tex.a = materialIDs == MATID_LAND ? tex.a : 0.0; gbufferOutput0 = tex; }";
    private static final String RP_VERTEX="flat out float v_materialIDs; void main(){ if (mc_Entity.x == 6000.0){v_materialIDs = MATID_WATER;} else {v_materialIDs = MATID_STAINEDGLASS;} }";
    private static final String RP_FRAGMENT="layout(location = 0) out vec4 framebuffer_albedo; void main(){ bool isWater = v_materialIDs == MATID_WATER; float albedoAlpha=textureLod(tex,v_texCoord,0.0).a; if (!isWater && albedoAlpha == 1.0){materialEnc = vec2(1.0, MATID_SOLID_TRANS);} else { albedo.a = 0.0; blockLight.x = 0.0; } framebuffer_albedo = albedo; }";
    public static void main(String[] args) throws Exception {
        var t=IterationFluidCompatibility.select("block.32000 = stone\nblock.8 = water\n",T_VERTEX,T_FRAGMENT);
        check(t.enabled() && t.id()!=32000 && t.id()>0 && t.id()<32767,"allocate a free signed-short ID, never alias stone/water");
        for(String fluid:IterationFluidCompatibility.MEDIA)check(t.properties().contains("mfqm:"+fluid),"all sticky source/flowing states mapped: "+fluid);
        check(!t.properties().contains("mfqm:bog") && !t.properties().contains("minecraft:"),"only intended sticky fluids are mapped");
        String tv=IterationFluidCompatibility.vertex(t,"gbuffers_water",T_VERTEX.replace("MATID_STAINEDGLASS","7.0").replace("MATID_WATER","6.0"));
        check(tv.contains("mc_Entity.x == "+t.id()+".0") && tv.contains("materialIDs = 1.0"),"expanded ITT source uses its ordinary alpha-blend class");
        check(tv.contains("if(mc_Entity.x == 8.0){materialIDs = 6.0;}"),"vanilla water classification is preserved");
        check(IterationFluidCompatibility.fragment(t,"gbuffers_water",T_FRAGMENT).equals(T_FRAGMENT),"ITT needs no duplicate alpha blend or absorption patch");
        check(IterationFluidCompatibility.vertex(t,"gbuffers_terrain",T_VERTEX).equals(T_VERTEX),"never alter other programs");
        check(IterationFluidCompatibility.vertex(t,"gbuffers_water",tv).equals(tv),"reload/repeated source getter is idempotent");
        var rp=IterationFluidCompatibility.select("block.6000 = water\n",RP_VERTEX,RP_FRAGMENT);
        String rv=IterationFluidCompatibility.vertex(rp,"gbuffers_water",RP_VERTEX);
        String rf=IterationFluidCompatibility.fragment(rp,"gbuffers_water",RP_FRAGMENT);
        check(rv.contains("v_materialIDs = 40.0"),"ITPR uses its lit alpha composite, not glass absorption");
        check(rf.contains("imageAtomicMin(img_depthtexS") && rf.contains("floatBitsToUint(gl_FragCoord.z)"),"record fluid depth so particle composite cannot replace it with background");
        check(rf.contains("layout(r32ui) uniform uimage2D img_depthtexS"),"use the pack's existing depth image binding");
        check(rf.contains("blockLight.x = v_materialIDs == 40.0 ? blockLight.x : 0.0"),"keep MFQM block light without altering glass light");
        check(rf.contains("albedo.a = 0.0"),"ITPR mixes alpha once at final composite");
        check(IterationFluidCompatibility.fragment(rp,"gbuffers_water",rf).equals(rf),"fragment adaptation is idempotent");
        check(!IterationFluidCompatibility.select("block.1 = mfqm:glue\n",T_VERTEX,T_FRAGMENT).enabled(),"respect an explicit pack-provided MFQM mapping");
        check(!IterationFluidCompatibility.select("block.1 = stone\n","unknown",T_FRAGMENT).enabled(),"unrecognized/future water programs stay untouched");
        check(IterationFluidCompatibility.vertex(t,"gbuffers_water","unknown").equals("unknown"),"fail closed if the expanded source changes");
        check(IterationShaderContracts.expected(IterationFluidCompatibility.Profile.NONE).isEmpty(),"other packs have no material adaptation contract");
        for(String path:args)try(var zip=new ZipFile(Path.of(path).toFile())) {
            String properties=read(zip,"shaders/block.properties"),vertex=read(zip,"shaders/Lib/Programs/Gbuffers/Water_VS.glsl"),fragment=read(zip,"shaders/Lib/Programs/Gbuffers/Water_FS.glsl");
            var selected=IterationFluidCompatibility.select(properties,vertex,fragment);
            check(selected.enabled(),"recognize supplied pack: "+path);
            for(var contract:IterationShaderContracts.expected(selected.profile()).entrySet()) {
                String original=read(zip,"shaders/"+contract.getKey());
                check(IterationShaderContracts.matches(contract.getValue(),original),"recognize actual material/composite contract: "+contract.getKey());
                check(!IterationShaderContracts.matches(contract.getValue(),original+"\nchanged"),"refuse changed/future material/composite contract");
                check(IterationShaderContracts.matches(contract.getValue(),original.replace("\r","").replace("\n","\r\n")),"line endings do not change recognition");
            }
            check(!IterationFluidCompatibility.vertex(selected,"gbuffers_water",vertex).equals(vertex),"adapt real vertex program: "+path);
            if(selected.profile()==IterationFluidCompatibility.Profile.RP)check(!IterationFluidCompatibility.fragment(selected,"gbuffers_water",fragment).equals(fragment),"adapt real fragment depth/light: "+path);
        }
        System.out.println("IterationFluidCompatibility: "+checks+" behavioral checks passed");
    }
    private static String read(ZipFile zip,String name)throws Exception{return new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
