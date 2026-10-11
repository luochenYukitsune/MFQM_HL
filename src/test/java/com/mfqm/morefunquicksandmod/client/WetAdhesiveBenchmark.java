package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.client.CoatingVoxels.Vec;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Reproducible optional geometry benchmark; timings never gate correctness tests. */
public final class WetAdhesiveBenchmark {
    private static volatile double sink;
    private record Result(double milliseconds,long bytes){}
    public static void main(String[] args) {
        var allocations=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(allocations.isThreadAllocatedMemorySupported())allocations.setThreadAllocatedMemoryEnabled(true);
        System.out.println("segments,strandsPerFoot,legacyMedianMs,optimizedMedianMs,legacyBytes,optimizedBytes");
        for(int segments:new int[]{3,6}) {
            for(int i=0;i<30;i++){run(false,segments,2);run(true,segments,2);}
            double[] old=new double[9],next=new double[9];long[] oldBytes=new long[9],nextBytes=new long[9];
            for(int i=0;i<9;i++) {
                Result a,b;
                if(i%2==0){a=timed(false,segments,allocations);b=timed(true,segments,allocations);}
                else{b=timed(true,segments,allocations);a=timed(false,segments,allocations);}
                old[i]=a.milliseconds;next[i]=b.milliseconds;oldBytes[i]=a.bytes;nextBytes[i]=b.bytes;
            }
            Arrays.sort(old);Arrays.sort(next);Arrays.sort(oldBytes);Arrays.sort(nextBytes);
            System.out.printf(java.util.Locale.ROOT,"%d,256,%.6f,%.6f,%d,%d%n",segments,old[4],next[4],oldBytes[4],nextBytes[4]);
        }
        if(!Double.isFinite(sink))throw new AssertionError("invalid benchmark output");
    }
    private static Result timed(boolean optimized,int segments,com.sun.management.ThreadMXBean allocations) {
        int frames=10;long thread=Thread.currentThread().threadId();
        long before=allocations.isThreadAllocatedMemoryEnabled()?allocations.getThreadAllocatedBytes(thread):-1;
        long start=System.nanoTime();run(optimized,segments,frames);long elapsed=System.nanoTime()-start;
        long after=before<0?-1:allocations.getThreadAllocatedBytes(thread);
        return new Result(elapsed/1e6/frames,before<0?-1:(after-before)/frames);
    }
    private static void run(boolean optimized,int segments,int frames) {
        var leg=new WetAdhesiveStyle.Frame(new Vec(0,0,0),new Vec(0,1,0),new Vec(1,0,0),new Vec(0,0,1),.125,.125);
        double checksum=0;
        for(int frame=0;frame<frames;frame++)for(int i=0;i<256;i++) {
            long seed=CompactStrandStyle.seed(i,i%128);double angle=CompactStrandStyle.variation(seed)*Math.PI*2;
            var root=new Vec(.4*Math.cos(angle),-.15,.4*Math.sin(angle));var end=WetAdhesiveStyle.endpoint(leg,.6,seed,root);
            var hit=new SkinSurfaceContact.Hit(end,new Vec(end.x(),0,end.z()).unit());
            double width=.02+.025*CompactStrandStyle.variation(seed^37);
            var mesh=optimized?WetAdhesiveStyle.attachedTube(root,hit,leg,segments,width,width,seed)
                    :WetAdhesiveStyleTest.referenceAttached(root,hit,leg,segments,width,width,seed);
            checksum+=mesh.size();if(!mesh.isEmpty())checksum+=mesh.getFirst().a().x()+mesh.getFirst().normal().y();
        }
        sink=checksum;
    }
    private WetAdhesiveBenchmark(){}
}
