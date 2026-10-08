package com.mfqm.morefunquicksandmod.client;

public final class StrugglePoseTest {
    private static int checks;
    private static void require(boolean condition,String reason) { checks++; if(!condition)throw new AssertionError(reason); }
    public static void main(String[] args) {
        require(StrugglePose.sample(-1,.1,0).amplitude()==0,"negative time is inactive");
        require(StrugglePose.sample(12,.1,0).amplitude()==0,"completed action is inactive");
        require(StrugglePose.sample(Double.NaN,1,0).amplitude()==0,"invalid time is inactive");
        var shallow=StrugglePose.sample(6,.1,0);
        require(shallow.rightLeg()<-.8 && shallow.leftLeg()==0,"shallow action pulls one foot up");
        var other=StrugglePose.sample(6,.1,1);
        require(other.leftLeg()==shallow.rightLeg() && other.rightLeg()==0,"alternating foot mirrors action");
        var deep=StrugglePose.sample(6,1.5,0);
        require(Math.abs(deep.bodyYaw())>.2 && deep.armPitch()<-.8,"deep action twists and braces");
        require(Math.abs(deep.rightLeg())<Math.abs(shallow.rightLeg()),"deep action reduces foot lift");
        for(int i=0;i<=120;i++) {
            var pose=StrugglePose.sample(i/10.,.5,0);
            require(Double.isFinite(pose.cameraPitch()) && Math.abs(pose.cameraPitch())<=.45,"camera remains subtle and finite");
            var right=StrugglePose.foot(pose,0,0,.6);
            var left=StrugglePose.foot(pose,1,0,.6);
            require(right.y()>=.05 && right.y()<.45 && left.y()>=.05,"foot endpoints stay below shins");
            var rotated=StrugglePose.foot(pose,0,90,.6);
            require(Math.abs(Math.hypot(right.x(),right.z())-Math.hypot(rotated.x(),rotated.z()))<1e-9,"yaw preserves endpoint radius");
        }
        var idle=StrugglePose.sample(0,0,0);
        require(idle.amplitude()==0,"initial action has no sudden jump");
        require(StrugglePose.foot(idle,0,0,.6).x()<0,"yaw zero right foot matches vanilla model coordinates");
        require(StrugglePose.sample(11.99,1,1).amplitude()<.01,"end smoothly fades");
        require(StrugglePose.width("mud",2) > StrugglePose.width("glue",2),"mud strings are thicker");
        require(StrugglePose.width("glue",2) < StrugglePose.width("glue",.2),"stretched strings narrow");
        var walk=StrugglePose.walking(0,.2);
        require(walk.rightLeg()>0 && walk.leftLeg()<0,"vanilla walking foot alternation");
        require(StrugglePose.foot(walk,0,0,.6).z()<0 && StrugglePose.foot(walk,1,0,.6).z()>0,"strings follow walking foot movement");
        require(StrugglePose.walking(Double.NaN,1).rightLeg()==0,"invalid walking input is inert");
        System.out.println("STRUGGLE_POSE_TESTS_PASSED checks="+checks);
    }
}
