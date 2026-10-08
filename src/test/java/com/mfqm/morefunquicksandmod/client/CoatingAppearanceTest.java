package com.mfqm.morefunquicksandmod.client;

public final class CoatingAppearanceTest {
    public static void main(String[] args) {
        check(CoatingAppearance.tint(0x80ffffff,0xff191413,1)==0x80191413,"tar retains its tint in white-vertex voxel rendering");
        check(CoatingAppearance.tint(0x80ffffff,0xffffffff,.5)==0x40ffffff,"global/material opacity multiplies source alpha");
        check(CoatingAppearance.tint(0x80ffffff,0x80ffffff,1)==0x40ffffff,"historical fade preserves RGB and opacity");
        check(CoatingAppearance.tint(0x80ffffff,0xffffffff,0)>>>24==0,"zero intensity hides residue");
        check(CoatingAppearance.family("sinking_slime").equals("slime"),"slime variants share their visual controls");
        check(CoatingAppearance.family("sticky_board").equals("glue"),"board residue uses glue controls");
        check(CoatingAppearance.family("bog").equals("mud"),"non adhesive residue still has adjustable 3D appearance");
        check(CoatingAppearance.surfacePadding(false,false,1)==.02,"hidden outer layer must not leave a floating shell");
        check(CoatingAppearance.surfacePadding(false,true,0)==.27,"flat clothing needs only a small separation beyond its quarter-pixel shell");
        check(CoatingAppearance.surfacePadding(true,true,0)==.52,"hat coating follows the half-pixel hat shell");
        check(CoatingAppearance.surfacePadding(false,true,.875)==.895,"active voxel clothing receives no extra thick safety gap");
        check(CoatingAppearance.surfacePadding(false,true,1.5)>CoatingAppearance.surfacePadding(false,true,.875),"custom skin voxel sizes still receive adequate clearance");
        System.out.println("CoatingAppearance: 12 behavioral checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
