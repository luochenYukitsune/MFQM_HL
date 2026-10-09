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
        int glue=CoatingAppearance.tint(0x63ffffff,0xffffffff,CoatingAppearance.materialOpacity("glue"));
        check((glue>>>24)>99 && (glue>>>24)<128,"glue becomes modestly more visible while preserving skin detail");
        check(CoatingAppearance.materialOpacity("sticky_board")==CoatingAppearance.materialOpacity("glue"),"board and glue residue share the same art direction");
        for(String material:new String[]{"honey","tar","sinking_slime"}) {
            int translucent=CoatingAppearance.tint(0xffffffff,0xffd59926,CoatingAppearance.materialOpacity(material));
            check((translucent>>>24)>128 && (translucent>>>24)<255,"adhesive coating becomes more translucent: "+material);
            check((translucent&0xffffff)==0xd59926,"transparency keeps the material's color: "+material);
        }
        check(CoatingAppearance.tint(0xffffffff,0xffffffff,CoatingAppearance.materialOpacity("glue")*0)>>>24==0,"user's zero opacity still hides stronger glue");
        check(CoatingAppearance.materialOpacity("bog")==1,"mud appearance is unchanged by adhesive transparency");
        System.out.println("CoatingAppearance: 22 behavioral checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
