package com.mfqm.morefunquicksandmod.client;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Narrow, in-memory adaptation of the supplied Iteration water programs. No pack files are changed. */
public final class IterationFluidCompatibility {
    public enum Profile { NONE, T, RP }
    public record Selection(Profile profile,int id,String properties) {
        public boolean enabled(){return profile!=Profile.NONE && id>0;}
    }
    public static final List<String> MEDIA=List.of("glue","honey","tar","sinking_slime","mucus","sinky_liquid");
    private static final String MARKER="// MFQM viscous alpha compatibility";
    private static final Pattern MAIN=Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern IDS=Pattern.compile("(?m)^\\s*block\\.(\\d+)\\s*=");
    private static final Pattern BLOCK_LIGHT=Pattern.compile("\\bblockLight\\.x\\s*=\\s*0\\.0\\s*;");

    public static Selection select(String properties,String vertex,String fragment) {
        Profile profile=Profile.NONE;
        if(properties==null || properties.contains("mfqm:"))return new Selection(profile,0,properties);
        if(isTVertex(vertex) && fragment.contains("gbufferOutput0 = tex;") && fragment.contains("tex.a = materialIDs == MATID_LAND ? tex.a : 0.0;"))profile=Profile.T;
        else if(isRpVertex(vertex) && fragment.contains("framebuffer_albedo = albedo;") && fragment.contains("albedo.a = 0.0;")
                && fragment.contains("MATID_SOLID_TRANS") && BLOCK_LIGHT.matcher(fragment).results().count()==1)profile=Profile.RP;
        if(profile==Profile.NONE)return new Selection(profile,0,properties);
        var occupied=new HashSet<Integer>();var match=IDS.matcher(properties);
        while(match.find())occupied.add(Integer.parseInt(match.group(1)));
        int id=32000;while(id>30000 && occupied.contains(id))id--;
        if(id<=30000)return new Selection(Profile.NONE,0,properties);
        String names=String.join(" ",MEDIA.stream().map(s->"mfqm:"+s).toList());
        return new Selection(profile,id,properties+"\n\n# MFQM: pigment-bearing translucent fluids\nblock."+id+" = "+names+"\n");
    }

    public static String vertex(Selection selection,String name,String source) {
        if(!selection.enabled() || !"gbuffers_water".equals(name) || source.contains(MARKER))return source;
        String variable;
        if(selection.profile()==Profile.T && isTVertex(source))variable="materialIDs";
        else if(selection.profile()==Profile.RP && isRpVertex(source))variable="v_materialIDs";
        else return source;
        int end=source.lastIndexOf('}');
        if(end<0 || MAIN.matcher(source).results().count()!=1)return source;
        // Use the pack's own generic alpha paths. Keeping glass here would
        // absorb/tint the color a second time and leave white glue invisible.
        String material=selection.profile()==Profile.T?"1.0":"40.0";
        return source.substring(0,end)+"\n"+MARKER+"\nif (mc_Entity.x == "+selection.id()+".0) "+variable+" = "+material+";\n"+source.substring(end);
    }

    public static String fragment(Selection selection,String name,String source) {
        if(selection.profile()!=Profile.RP || !selection.enabled() || !"gbuffers_water".equals(name) || source.contains(MARKER))return source;
        if(!source.contains("framebuffer_albedo = albedo;") || !source.contains("v_materialIDs") || !source.contains("albedo.a = 0.0;"))return source;
        var light=BLOCK_LIGHT.matcher(source);var main=MAIN.matcher(source);
        if(light.results().count()!=1 || !main.find() || source.contains("uniform uimage2D img_depthtexS"))return source;
        String result=BLOCK_LIGHT.matcher(source).replaceFirst("blockLight.x = v_materialIDs == 40.0 ? blockLight.x : 0.0;");
        main=MAIN.matcher(result);main.find();
        // The RP composite can replace particle data with solid background
        // unless depthtexS records this fragment, just as its native particles do.
        String header=MARKER+"\nlayout(r32ui) uniform uimage2D img_depthtexS;\n";
        String depth="\nif (v_materialIDs == 40.0) imageAtomicMin(img_depthtexS, ivec2(gl_FragCoord.xy), floatBitsToUint(gl_FragCoord.z));\n";
        // Write only after the original zero-alpha/FSR discard decisions.
        result=result.substring(0,main.start())+header+result.substring(main.start());
        return result.replace("framebuffer_albedo = albedo;",depth+"framebuffer_albedo = albedo;");
    }
    private static boolean isTVertex(String s){return s!=null && s.contains("materialIDs") && !s.contains("v_materialIDs") && s.contains("mc_Entity.x == 8.0") && s.contains("gl_Position");}
    private static boolean isRpVertex(String s){return s!=null && s.contains("v_materialIDs") && s.contains("mc_Entity.x == 6000.0");}
    private IterationFluidCompatibility(){}
}
