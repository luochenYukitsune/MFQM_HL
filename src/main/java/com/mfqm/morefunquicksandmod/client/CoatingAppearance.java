package com.mfqm.morefunquicksandmod.client;

/** Color operations shared by flat and extruded surfaces. No game state. */
public final class CoatingAppearance {
    /** Art direction applied before user intensity settings, including existing saved configs. */
    public static double materialOpacity(String material) {
        return switch(family(material)) {
            case "glue"->1.20;
            case "honey","slime"->.78;
            case "tar"->.85;
            default->1;
        };
    }
    public static double strandOpacity(String material){return Math.min(1,materialOpacity(material));}
    /** Model pixels: clear the visible surface rather than an invisible outer shell. */
    public static double surfacePadding(boolean head,boolean outerVisible,double voxelPadding) {
        return outerVisible?Math.max(head?.5:.25,voxelPadding)+.02:.02;
    }
    public static String family(String material) {
        return switch(material) {
            case "glue","sticky_board"->"glue";
            case "tar"->"tar";
            case "honey","wax"->"honey";
            case "sinking_slime","sinky_liquid","swallowing_flesh","meat","mucus","larvae"->"slime";
            default->"mud";
        };
    }
    public static int tint(int pixel,int tint,double opacity) {
        int alpha=(int)Math.clamp((pixel>>>24)*(tint>>>24)/255.*opacity,0,255);
        return alpha<<24|((pixel>>16&255)*(tint>>16&255)/255)<<16
                |((pixel>>8&255)*(tint>>8&255)/255)<<8|(pixel&255)*(tint&255)/255;
    }
    private CoatingAppearance(){}
}
