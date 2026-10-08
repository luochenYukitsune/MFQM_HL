package com.mfqm.morefunquicksandmod.client;

/** Pure cosmetic policy. Server contact lifetimes, forces and activity ranges are unchanged. */
public final class CompactStrandStyle {
    public static final int MAX_DENSITY=8;
    public static double opacity(double horizontal,double vertical) {
        if(!Double.isFinite(horizontal) || !Double.isFinite(vertical))return 0;
        return Math.clamp((.65-horizontal)/.2,0,1)*Math.clamp((1.15-Math.abs(vertical))/.25,0,1);
    }
    public static double angle(long seed,int index) {
        if(index<0 || index>=MAX_DENSITY)throw new IllegalArgumentException("Invalid strand index");
        double phase=variation(seed)*Math.PI*2;
        return phase+index*2.399963229728653;
    }
    /** Identity-based variation: never use frame time for attachment randomness. */
    public static double variation(long seed) {
        long mixed=(seed^(seed>>>30))*0xBF58476D1CE4E5B9L;
        mixed=(mixed^(mixed>>>27))*0x94D049BB133111EBL;
        return ((mixed^(mixed>>>31))>>>11)*0x1.0p-53;
    }
    private CompactStrandStyle(){}
}
