package com.mfqm.morefunquicksandmod.gameplay;

public final class AdhesiveMotionTest {
    private static int checks;
    public static void main(String[] args) {
        var center=new AdhesiveMotion.Point(0,0,0);
        var inside=new AdhesiveMotion.Point(.2,0,.2);
        var step=new AdhesiveMotion.Point(.1,.08,.1);
        check(AdhesiveMotion.limit(center,inside,step,.6).equals(step),"inside range preserves actual movement and vertical hop");
        var clipped=AdhesiveMotion.limit(center,inside,new AdhesiveMotion.Point(2,.08,2),.6);
        check(Math.hypot(inside.x()+clipped.x(),inside.z()+clipped.z())<=.6000001,"diagonal sprint cannot bypass circular range");
        check(clipped.y()==.08,"range projection preserves vertical hop");
        var rescued=new AdhesiveMotion.Point(2,0,0);
        check(AdhesiveMotion.limit(center,rescued,new AdhesiveMotion.Point(-.1,.1,0),.6).x()==-.1,"outside rescued body may move inward without teleportation");
        check(AdhesiveMotion.limit(center,rescued,new AdhesiveMotion.Point(.1,0,0),.6).x()<=0,"outside position cannot create further outward drift");
        String[] media={"glue","honey","tar","sinking_slime","mud","sticky_board"};
        double[] ranges={1.2,1,.8,1,.6,.7};
        for(int i=0;i<media.length;i++) {
            double range=AdhesiveMotion.defaultRadius(media[i]);
            check(Math.abs(range-ranges[i])<1e-9,"requested activity radius "+media[i]);
            double distance=AdhesiveMotion.bondDistance(.8,range);
            check(distance>=range+.399999,"foot turning and hop reserve "+media[i]);
            check(AdhesiveMotion.radius(range,distance,1)==range,"full strength preserves activity "+media[i]);
            check(AdhesiveMotion.radius(range,distance,.1)>distance,"weakened bond may reach break distance "+media[i]);
            var profile=new AdhesiveRules.Profile(distance,.032,AdhesiveMotion.restLength(range));
            var relaxed=new AdhesiveRules.Bond(new AdhesiveRules.Point(0,0,0),new AdhesiveRules.Point(range*.8,0,0),profile,1);
            check(AdhesiveRules.evaluate(java.util.List.of(relaxed)).force().length()==0,"ordinary movement within slack is not pulled to original tiny range "+media[i]);
            var tight=new AdhesiveRules.Bond(relaxed.anchor(),new AdhesiveRules.Point(range,0,0),profile,1);
            check(AdhesiveRules.evaluate(java.util.List.of(tight)).force().x()<0,"taut edge still pulls inward "+media[i]);
        }
        for(double radius:new double[]{.1,.2,.6,.7,.8,1,1.2,4}) {
            check(AdhesiveMotion.bondDistance(.25,radius)>=Math.hypot(radius+.32,.38),"even a custom tiny circle reserves full foot rotation plus short-hop height");
        }
        var jump=new AdhesiveMotion.Jump();double offset=0,peak=0;
        for(int i=0;i<60;i++){var hop=jump.step(i,true,.24);offset+=hop.delta();peak=Math.max(peak,offset);check(offset>=-1e-8 && offset<=.240001,"held jump is bounded tick="+i);}
        check(peak>.20 && Math.abs(offset)<1e-8,"real short hop rises and returns; holding does not float");
        jump.step(60,false,.24);var again=jump.step(61,true,.24);check(again.delta()>0 && again.active(),"release and re-press permits another short hop");
        var teleported=jump.step(1,true,.24);check(Double.isFinite(teleported.delta()),"clock reset remains finite");
        System.out.println("AdhesiveMotion: "+checks+" behavioral checks passed");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
