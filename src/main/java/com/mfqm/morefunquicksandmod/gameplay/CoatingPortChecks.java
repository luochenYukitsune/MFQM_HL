package com.mfqm.morefunquicksandmod.gameplay;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import java.util.LinkedHashMap;
import java.util.List;

/** Explicit isolated-server checks of real player contact and persistent residue. */
public final class CoatingPortChecks {
    public static List<String> verify(ServerLevel level,BlockPos at) {
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MFQMCoatingProbe");
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,profile);
        player.getAbilities().invulnerable=true;
        var state=QuicksandPhysics.state(player);
        int cases=0;
        try {
            for(int up=-1;up<=5;up++)saved.put(at.above(up),level.getBlockState(at.above(up)));
            level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
            for(String medium:List.of("glue","mud","tar","honey")) {
                for(int up=0;up<3;up++)level.setBlock(at.above(up),ModBlocks.byId(medium).defaultBlockState(),2);
                for(int up=3;up<=5;up++)level.setBlock(at.above(up),Blocks.AIR.defaultBlockState(),2);
                var top=at.above(2);var block=level.getBlockState(top);var fluid=block.getFluidState();
                double surface=top.getY()+(fluid.isEmpty()?1:fluid.getHeight(level,top));
                for(Pose pose:List.of(Pose.STANDING,Pose.CROUCHING)) {
                    player.setPose(pose);player.refreshDimensions();
                    double[] depths={.08,.4,.65,.73,1.3,1.5,1.82};int[] tiers={1,2,3,4,7,8,10};
                    for(int i=0;i<depths.length;i++) {
                        AdhesionController.prepare(player,level,null,true);
                        state.coatingLevel=0;state.coatingTicks=0;state.coatingType="";state.lastTick=Long.MIN_VALUE;
                        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        player.setPos(at.getX()+.5,surface-depths[i],at.getZ()+.5);
                        var contact=QuicksandPhysics.findContact(player,level);
                        require(contact!=null && Math.abs(contact.depth()-depths[i])<.00001,"actual surface depth "+medium+" "+pose);
                        QuicksandPhysics.tick(player,level);
                        require(state.coatingLevel==tiers[i] && state.coatingType.equals(medium),"height tier "+medium+" "+pose+" depth="+depths[i]+" level="+state.coatingLevel);
                        cases++;
                    }
                    player.setPos(at.getX()+.5,surface-.4,at.getZ()+.5);state.lastTick=Long.MIN_VALUE;
                    QuicksandPhysics.tick(player,level);
                    require(state.coatingLevel==10,"returning to shallow contact retains actual deep residue");
                    player.setPos(at.getX()+.5,surface+.1,at.getZ()+.5);state.lastTick=Long.MIN_VALUE;
                    int remaining=state.coatingTicks;QuicksandPhysics.tick(player,level);
                    require(state.coatingLevel==10 && state.coatingTicks==remaining-1,"leaving the medium starts residue expiry instead of erasing it");
                }
            }
            require(cases==56,"all standing/crouching depth cases ran");
            return List.of("56 real standing/crouching player coating depths follow stable anatomical tiers",
                    "deep residue survives shallower contact without fabricating new upper-body coverage",
                    "dry exit retains residue and advances its existing expiry timer");
        } finally {
            AdhesionController.prepare(player,level,null,true);player.discard();
            saved.forEach((pos,block)->level.setBlock(pos,block,3));
        }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("MFQM coating verification failed: "+message);}
    private CoatingPortChecks(){}
}
