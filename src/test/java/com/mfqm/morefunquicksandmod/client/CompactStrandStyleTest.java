package com.mfqm.morefunquicksandmod.client;

import java.util.HashSet;

public final class CompactStrandStyleTest {
    private static int checks;
    public static void main(String[] args) {
        check(CompactStrandStyle.opacity(.2,.4)==1,"near-foot strands remain visible");
        check(CompactStrandStyle.opacity(.65,0)==0,"old horizontal trail vanishes near the actor");
        check(CompactStrandStyle.opacity(0,1.15)==0,"long vertical trail stays out of view");
        check(CompactStrandStyle.opacity(.55,.4)>0 && CompactStrandStyle.opacity(.55,.4)<1,"short horizontal fade prevents a hard pop");
        check(CompactStrandStyle.opacity(.2,1)>0 && CompactStrandStyle.opacity(.2,1)<1,"short vertical fade prevents a hard pop");
        check(CompactStrandStyle.opacity(Double.NaN,0)==0,"invalid endpoints cannot reach the vertex buffer");
        check(CompactStrandStyle.MAX_DENSITY==128,"64 contacts can display up to 8192 complete strands");
        check(CompactStrandStyle.density(8,2)==32,"fresh contact looks denser immediately");
        check(CompactStrandStyle.density(8,64)==8,"full default contact budget still tops out at 512");
        for(int base:new int[]{1,8,32,128})for(int groups=1;groups<=64;groups++)
            check(CompactStrandStyle.density(base,groups)*groups<=base*64,"adaptive density never exceeds the configured total budget");
        var identities=new HashSet<Long>();
        for(int contact=1;contact<=64;contact++)for(int i=0;i<128;i++)
            check(identities.add(CompactStrandStyle.seed(contact,i)),"larger density must not reuse another contact's random seed");
        for(long seed:new long[]{0,1,-1,17,Long.MAX_VALUE}) {
            var ends=new HashSet<Double>();
            for(int i=0;i<CompactStrandStyle.MAX_DENSITY;i++) {
                double angle=CompactStrandStyle.angle(seed,i);
                check(Double.isFinite(angle),"independent root direction stays finite");ends.add(angle%(Math.PI*2));
                check(angle==CompactStrandStyle.angle(seed,i),"root spread is stable across frames");
            }
            check(ends.size()==CompactStrandStyle.MAX_DENSITY,"independent strands do not stack every root on one line");
        }
        System.out.println("CompactStrandStyle: "+checks+" behavioral checks passed");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
