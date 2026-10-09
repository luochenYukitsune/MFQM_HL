package com.mfqm.morefunquicksandmod.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Fingerprints only; no shader source is distributed. Fail closed for changed material/composite contracts. */
public final class IterationShaderContracts {
    public static Map<String,String> expected(IterationFluidCompatibility.Profile profile) {
        return switch(profile) {
            case T->Map.of(
                "Lib/Programs/Gbuffers/Water_VS.glsl","b446d61a6fdfc45f867a7af1b3b7e327d3d732852b0f22d38ac31f3db9482425",
                "Lib/Programs/Gbuffers/Water_FS.glsl","174a978e0908df159eac75e7108cfff26dda597d91364dfadee7afa23ca575c7",
                "Lib/Settings.glsl","c76496a86e6c6b8ccaea02a76fbb3ac9fb903c3b914fab9626f71689bb0887e7",
                "Lib/GbufferData.glsl","a18da81a6a8d4e641a23dd017a1180ba933cfc285b7a225df270b15828099160",
                "shaders.properties","8c7e349e42cbb882c9b807b5a7b9cdb1c63a9336133ea0ab8c8c9432b46647f7",
                "composite5.fsh","18dc47ad4f09b042d7cfaadf6cfe02bd81e585be2ccafd96b0b15d9046a625ef");
            case RP->Map.of(
                "Lib/Programs/Gbuffers/Water_VS.glsl","936ce5f84df97d0ed55a72ad7cad95c3d4a465d3914c0d3477767e9e4db4c7f3",
                "Lib/Programs/Gbuffers/Water_FS.glsl","3a067a19eca369734ca63b41b5c102563a0814025fa128a9b0f990f71ab17bd4",
                "Lib/Settings.glsl","5fb78a3ec21e2e954e23a806a9148af6a82a1fd8faaea95f87b08a454da92dd2",
                "Lib/GbufferData.glsl","ee6b35c57118a702eddd2e0a942b2fedbdd20f0483c24a08993e40930cffd995",
                "shaders.properties","ba380a0df48254505cbf655d399a641069a32f9fbc28dea5171f4fae327314a3",
                "Lib/Programs/Gbuffers/Composite_Copy_CS.glsl","f0c771692dfa4f500ecedb3c38e754f488d07a9a3b0c38d11c7e393d8e712a1b",
                "Lib/Programs/Composite/Translucent_FS.glsl","7faf93cd626001ed39b64253c44c4e7d5c0840ae074ad46e69a7744ae6ec934a");
            case NONE->Map.of();
        };
    }
    public static boolean matches(String expected,String source) {
        if(expected==null || source==null)return false;
        try{return expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.replace("\r","").getBytes(StandardCharsets.UTF_8))));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private IterationShaderContracts(){}
}
