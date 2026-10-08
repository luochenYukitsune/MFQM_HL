package com.mfqm.morefunquicksandmod.gameplay;

/** Pure contact policy shared by server sampling, rendering and behavior checks. */
public final class AdhesiveContact {
    public static final double ROOT_MARGIN=.035;
    public static AdhesiveRules.Point root(double x,double y,double z,int bx,int by,int bz,double height,double margin) {
        double inset=Math.clamp(margin,.001,.49),vertical=Math.min(.012,height*.25);
        return new AdhesiveRules.Point(Math.clamp(x,bx+inset,bx+1-inset),
                Math.clamp(y,by+vertical,by+height-vertical),Math.clamp(z,bz+inset,bz+1-inset));
    }
    public static boolean fresh(long tick,long previous,double displacementSquared) {
        return tick>=previous && tick-previous>=3 && Double.isFinite(displacementSquared) && displacementSquared>=.0064;
    }
    public static int overflow(int current,int incoming,int limit){return Math.max(0,current+incoming-limit);}
    private AdhesiveContact(){}
}
