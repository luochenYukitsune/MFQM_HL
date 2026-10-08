package com.mfqm.morefunquicksandmod.gameplay;

/** Root geometry and moving contact policy, independent of a game bootstrap. */
public final class AdhesiveContactTest {
    private static int checks;
    public static void main(String[] args) {
        for(double height:new double[]{.02,.0625,1./9,8./9,1})for(double x:new double[]{-.1,0,.005,.5,.995,1,1.1}) {
            var p=AdhesiveContact.root(x,2,1-x,0,0,0,height,.035);
            check(p.x()>=.035 && p.x()<=.965 && p.z()>=.035 && p.z()<=.965,"complete ribbon footprint inside material cell");
            check(p.y()>0 && p.y()<height,"root remains below thin or full fluid surface");
        }
        var a=AdhesiveContact.root(.2,.8,.4,0,0,0,.9,.035);
        var b=AdhesiveContact.root(.2,.8,.4,0,0,0,.3,.035);
        check(a.x()==b.x() && a.z()==b.z() && b.y()<.3,"falling fluid level changes only root height");
        check(!AdhesiveContact.fresh(10,10,1),"same tick never produces duplicate contacts");
        check(!AdhesiveContact.fresh(20,10,0),"standing does not replenish strands");
        check(!AdhesiveContact.fresh(11,10,.02),"motion is rate limited");
        check(AdhesiveContact.fresh(13,10,.02),"moving inside the same block adds a fresh strand");
        check(!AdhesiveContact.fresh(13,10,Double.NaN),"invalid displacement rejected");
        check(AdhesiveContact.overflow(64,2,64)==2,"FIFO removes exactly oldest two at capacity");
        check(AdhesiveContact.overflow(2,2,64)==0,"unused slots are filled without breaking");
        System.out.println("AdhesiveContact: "+checks+" behavioral checks passed");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
