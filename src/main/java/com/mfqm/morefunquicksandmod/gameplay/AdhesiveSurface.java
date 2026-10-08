package com.mfqm.morefunquicksandmod.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Both logical sides use the same occupied cell and root inset, including flowing layers. */
public final class AdhesiveSurface {
    public record Contact(BlockPos block,Vec3 point){}
    public static double height(Level level,BlockPos block,String material) {
        if(AdhesiveRules.profileFor(material)==null || !level.hasChunkAt(block))return 0;
        var state=level.getBlockState(block);double height;
        if(material.equals("sticky_board")) {
            if(!com.mfqm.morefunquicksandmod.block.StickyBoardBlock.isCoated(state))return 0;
            height=.0625;
        } else {
            if(!QuicksandPhysics.id(state).equals(material))return 0;
            height=state.getFluidState().isEmpty()?1:state.getFluidState().getHeight(level,block);
        }
        return Double.isFinite(height) && height>0?height:0;
    }
    public static Vec3 root(Level level,BlockPos block,String material,Vec3 desired) {
        double height=height(level,block,material);if(height<=0)return null;
        var point=AdhesiveContact.root(desired.x,desired.y,desired.z,block.getX(),block.getY(),block.getZ(),height,AdhesiveContact.ROOT_MARGIN);
        return new Vec3(point.x(),point.y(),point.z());
    }
    public static double rootWidth(Level level,BlockPos block,String material,Vec3 root) {
        return .75*Math.max(0,Math.min(root.y-block.getY(),block.getY()+height(level,block,material)-root.y));
    }
    public static Contact find(Level level,Vec3 foot,String material) {
        Contact best=null;double nearest=Double.POSITIVE_INFINITY;
        for(var cursor:BlockPos.betweenClosed(BlockPos.containing(foot.add(-.18,-.2,-.18)),BlockPos.containing(foot.add(.18,.05,.18)))) {
            var point=root(level,cursor,material,foot);
            // A nearby occupied cell must actually touch the ankle/sole, not hover over it.
            if(point==null || Math.abs(point.y-foot.y)>.21)continue;
            double distance=point.distanceToSqr(foot);
            if(distance<nearest){nearest=distance;best=new Contact(cursor.immutable(),point);}
        }
        return best;
    }
    private AdhesiveSurface(){}
}
