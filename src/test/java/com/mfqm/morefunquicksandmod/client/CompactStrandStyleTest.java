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
        check(CompactStrandStyle.MAX_DENSITY==8,"each physical contact can display eight independent continuous strands");
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
