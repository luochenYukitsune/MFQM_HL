package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.gameplay.AdhesiveSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

/** Cosmetic roots respect the sloping mesh, while server contact coordinates remain unchanged. */
final class RenderedAdhesiveSurface {
    record Root(Vec3 point,double radius) {}
    static Root root(Level level,BlockPos cell,String material,Vec3 desired,double radius) {
        return root(cell,minimumHeight(level,cell,material),desired,radius,0,0,1);
    }
    /** Reuse the same sampled surface for all independent strands at this contact. */
    static Root root(BlockPos cell,double height,Vec3 desired,double radius,long seed,int index,int count) {
        if(height<=0)return null;
        var local=new CoatingVoxels.Vec(desired.x-cell.getX(),desired.y-cell.getY(),desired.z-cell.getZ());
        var root=WetAdhesiveStyle.submergedRoot(local,height,radius,seed,index,count);if(root==null)return null;
        return new Root(new Vec3(cell.getX()+root.point().x(),cell.getY()+root.point().y(),cell.getZ()+root.point().z()),root.radius());
    }
    /** Every point of the native top quad is at or above its lowest corner. */
    static double minimumHeight(Level level,BlockPos cell,String material) {
        double height=AdhesiveSurface.height(level,cell,material);if(height<=0)return 0;
        var fluid=level.getFluidState(cell);if(fluid.isEmpty())return height;
        Fluid type=fluid.getType();double center=sample(level,cell,type);if(center>=1)return .999;
        double north=sample(level,cell.north(),type),south=sample(level,cell.south(),type);
        double east=sample(level,cell.east(),type),west=sample(level,cell.west(),type);
        double minimum=1;
        for(int z:new int[]{-1,1})for(int x:new int[]{-1,1}) {
            double a=z<0?north:south,b=x<0?west:east;
            double diagonal=a>0 || b>0?sample(level,cell.offset(x,0,z),type):-1;
            minimum=Math.min(minimum,corner(center,a,b,diagonal));
        }
        // Vanilla lowers the exposed top by .001; keep another .001 safety margin.
        return Math.max(0,minimum-.002);
    }
    private static double sample(Level level,BlockPos cell,Fluid type) {
        if(!level.hasChunkAt(cell))return 0;
        var state=level.getBlockState(cell);var fluid=state.getFluidState();
        if(type.isSame(fluid.getType()))return type.isSame(level.getFluidState(cell.above()).getType())?1:fluid.getOwnHeight();
        return state.isSolid()?-1:0;
    }
    // Same corner weighting as the native LiquidBlockRenderer, with conservative rounding inset above.
    private static double corner(double center,double a,double b,double diagonal) {
        if(a>=1 || b>=1 || diagonal>=1)return 1;
        double sum=0,weight=0;
        for(double value:new double[]{center,a,b,diagonal})if(value>=0) {
            double w=value>=.8?10:1;sum+=value*w;weight+=w;
        }
        return weight>0?sum/weight:0;
    }
    private RenderedAdhesiveSurface(){}
}
