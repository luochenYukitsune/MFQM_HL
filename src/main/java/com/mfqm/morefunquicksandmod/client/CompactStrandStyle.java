package com.mfqm.morefunquicksandmod.client;

/** Pure cosmetic policy. Server contact lifetimes, forces and activity ranges are unchanged. */
public final class CompactStrandStyle {
    public static final int MAX_DENSITY=128;
    public static int density(int base,int groups) {
        if(base<1 || base>MAX_DENSITY || groups<1 || groups>64)throw new IllegalArgumentException("Invalid visual strand budget");
        return Math.min(Math.min(MAX_DENSITY,base*64/groups),(int)Math.round(base*Math.min(4,Math.sqrt(64./groups))));
    }
    public static long seed(int contact,int index) {
        if(index<0 || index>=MAX_DENSITY)throw new IllegalArgumentException("Invalid strand index");
        return ((long)contact<<32)^Integer.toUnsignedLong(index)^0xA0761D6478BD642FL;
    }
    public static double opacity(double horizontal,double vertical) {
        if(!Double.isFinite(horizontal) || !Double.isFinite(vertical))return 0;
        return Math.clamp((.65-horizontal)/.2,0,1)*Math.clamp((1.15-Math.abs(vertical))/.25,0,1);
    }
    public static double angle(long seed,int index) {
        if(index<0 || index>=MAX_DENSITY)throw new IllegalArgumentException("Invalid strand index");
        double phase=variation(seed)*Math.PI*2;
        return phase+index*2.399963229728653+(variation(seed^((index+1L)*0xE7037ED1A0B428DBL))-.5)*1.4;
    }
    /** Identity-based variation: never use frame time for attachment randomness. */
    public static double variation(long seed) {
        long mixed=(seed^(seed>>>30))*0xBF58476D1CE4E5B9L;
        mixed=(mixed^(mixed>>>27))*0x94D049BB133111EBL;
        return ((mixed^(mixed>>>31))>>>11)*0x1.0p-53;
    }
    private CompactStrandStyle(){}
}
