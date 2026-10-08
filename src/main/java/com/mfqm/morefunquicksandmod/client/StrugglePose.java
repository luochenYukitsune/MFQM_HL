package com.mfqm.morefunquicksandmod.client;

/** Pure animation math shared by body poses, camera and foot tether endpoints. */
public final class StrugglePose {
    public record Frame(double amplitude,double rightLeg,double leftLeg,double bodyYaw,double armPitch,double cameraPitch) {}
    public record Point(double x,double y,double z) {}
    public static Frame sample(double elapsed,double depth,int side) {
        if(!Double.isFinite(elapsed) || elapsed<0 || elapsed>=12)return new Frame(0,0,0,0,0,0);
        double amplitude=Math.sin(Math.PI*elapsed/12);
        double deep=Double.isFinite(depth)?Math.clamp((depth-.35)/.85,0,1):0;
        double leg=-amplitude*(.95-.65*deep);
        return new Frame(amplitude,side==0?leg:0,side==1?leg:0,
                (side==0?1:-1)*.32*deep*amplitude,-amplitude*(.25+.9*deep),
                .4*Math.sin(Math.PI*elapsed/6)*amplitude);
    }
    public static Frame walking(double position,double speed) {
        if(!Double.isFinite(position) || !Double.isFinite(speed))return sample(-1,0,0);
        double amplitude=1.4*Math.clamp(speed,0,1);
        return new Frame(0,Math.cos(position*.6662)*amplitude,Math.cos(position*.6662+Math.PI)*amplitude,0,0,0);
    }
    public static Point foot(Frame frame,int side,double yaw,double width) {
        double angle=side==0?frame.rightLeg:frame.leftLeg;
        double lateral=(side==0?-1:1)*Math.clamp(Double.isFinite(width)?width:.6,.1,2)*.21;
        double lift=.75*(1-Math.cos(angle));
        double forward=-.75*Math.sin(angle);
        double radians=Math.toRadians(Double.isFinite(yaw)?yaw:0);
        return new Point(lateral*Math.cos(radians)-forward*Math.sin(radians),.05+lift,
                lateral*Math.sin(radians)+forward*Math.cos(radians));
    }
    public static double width(String material,double distance) {
        boolean thick=java.util.Set.of("mud","bog","morass","mire","moor","wet_peat","brown_clay","sinking_clay","slurry").contains(material==null?"":material);
        double stretch=Double.isFinite(distance)?Math.max(0,distance):0;
        return (thick?.065:.038)/(1+.65*stretch);
    }
    private StrugglePose() {}
}
