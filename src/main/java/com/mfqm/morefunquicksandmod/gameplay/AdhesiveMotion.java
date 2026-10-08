package com.mfqm.morefunquicksandmod.gameplay;

/** Pure bounded movement and finite short-hop rules, independent of rendering and Minecraft. */
public final class AdhesiveMotion {
    public record Point(double x,double y,double z) {}
    public record Hop(double delta,boolean active) {}
    public static final class Jump {
        private long clock=Long.MIN_VALUE,start=Long.MIN_VALUE;
        private boolean held;
        private double offset,peak;
        public Hop step(long tick,boolean down,double height) {
            if(tick<0 || !Double.isFinite(height) || height<0 || height>.5)throw new IllegalArgumentException("Invalid hop");
            if(tick==clock)return new Hop(0,start!=Long.MIN_VALUE && tick-start<12);
            if(clock!=Long.MIN_VALUE && (tick<clock || tick-clock>1)){start=Long.MIN_VALUE;offset=0;held=false;}
            boolean edge=down && !held;held=down;clock=tick;
            if(edge && (start==Long.MIN_VALUE || tick-start>=12)){start=tick;peak=height;}
            long age=start==Long.MIN_VALUE?13:tick-start+1;
            double next=age<=12?4*peak*(age/12.)*(1-age/12.):0;
            double delta=next-offset;offset=next;
            return new Hop(delta,age<=12);
        }
    }
    public static Point limit(Point origin,Point position,Point delta,double radius) {
        if(!Double.isFinite(radius) || radius<=0)throw new IllegalArgumentException("Invalid range");
        double x=position.x()-origin.x(),z=position.z()-origin.z();
        double old=Math.hypot(x,z),next=Math.hypot(x+delta.x(),z+delta.z());
        // Rescue/teleport outside the circle is not snapped back. Only reject a
        // further increase; moving inward remains available.
        double ceiling=Math.max(radius,old);
        if(next<=ceiling)return delta;
        double scale=ceiling/next;
        return new Point((x+delta.x())*scale-x,delta.y(),(z+delta.z())*scale-z);
    }
    public static double defaultRadius(String material) {
        return switch(material) {
            case "glue" -> 1.2;
            case "honey","sinking_slime","mucus" -> 1;
            case "tar" -> .8;
            case "sticky_board" -> .7;
            default -> .6;
        };
    }
    public static double bondDistance(double configured,double fullRadius) {
        return Math.max(configured,Math.max(fullRadius+.4,Math.hypot(fullRadius+.32,.38)));
    }
    public static double restLength(double fullRadius) { return fullRadius*.95; }
    public static double radius(double fullRadius,double maxDistance,double strength) {
        return fullRadius+(maxDistance*1.25-fullRadius)*(1-Math.clamp(strength,0,1));
    }
    public static double inputScale(String material,double depth,double strength) {
        double base=switch(material){case "sticky_board"->1.5;case "glue"->.65;case "tar"->.85;case "honey"->1.1;default->.8;};
        return base/(1+Math.max(0,depth)*.6)*(1+.25*(1-Math.clamp(strength,0,1)));
    }
    private AdhesiveMotion(){}
}
