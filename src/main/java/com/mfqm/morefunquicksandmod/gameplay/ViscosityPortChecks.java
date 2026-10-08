package com.mfqm.morefunquicksandmod.gameplay;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Opt-in server checks for the movement contract and synchronized prediction costs. */
public final class ViscosityPortChecks {
    private ViscosityPortChecks() {}
    public static List<String> verify(ServerLevel level,BlockPos origin) {
        var passed=new ArrayList<String>();
        var original=new SinkingState();
        original.cachedLoad=2.25;original.rescueTicks=6;original.rescueSequence=7;
        original.totalStruggleSink=.075;original.adhesiveForce=new Vec3(.01,.002,-.015);
        original.contactTicks=18;original.externalMotionTicks=4;original.externalMotionSequence=9;
        original.preparedTick=100;original.nativeTravelTick=101;
        original.adhesiveOrigin=new Vec3(12.5,64,-.5);original.adhesiveStrength=.42;original.adhesiveMaterial="sticky_board";
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
        SinkingState decoded;
        try {SinkingState.STREAM_CODEC.encode(buffer,original);decoded=SinkingState.STREAM_CODEC.decode(buffer);}
        finally {buffer.release();}
        require(decoded.cachedLoad==2.25 && decoded.rescueTicks==6 && decoded.rescueSequence==7,"load and rescue snapshot round trip");
        require(decoded.totalStruggleSink==.075 && decoded.adhesiveForce.equals(original.adhesiveForce),"sink total and bounded pull round trip");
        require(decoded.contactTicks==18 && decoded.externalMotionTicks==4 && decoded.externalMotionSequence==9,"contact and external impulse round trip");
        require(decoded.preparedTick==Long.MIN_VALUE && decoded.nativeTravelTick==Long.MIN_VALUE,"native execution clocks are not synchronized");
        require(original.adhesiveOrigin.equals(decoded.adhesiveOrigin) && decoded.adhesiveStrength==.42 && decoded.adhesiveMaterial.equals("sticky_board"),"fixed origin, strength and material survive replacement snapshots");
        passed.add("motion codec preserves load, rescue, cumulative sink, pull and trusted impulse while execution clocks remain local");
        var ledger=new SinkingMotion.SinkLedger(decoded.totalStruggleSink);
        require(ledger.consume(decoded.totalStruggleSink)==0,"first packet cannot replay history");
        require(Math.abs(ledger.consume(.1)-.025)<1e-9 && ledger.consume(.1)==0,"replacement attachment adds a press only once");
        require(ledger.consume(0)==0 && Math.abs(ledger.consume(.025)-.025)<1e-9,"dimension reset consumes only new costs");
        passed.add("prediction ledger consumes a synchronized sinking cost once across replacement snapshots and dimension resets");

        var saved=new LinkedHashMap<BlockPos,BlockState>();
        var data=(ServerLevelData)level.getLevelData();long clock=level.getGameTime();
        var pig=EntityType.PIG.create(level,EntitySpawnReason.TRIGGERED);
        require(pig!=null,"create viscosity probe");
        try {
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=2;y++) {
                var at=origin.offset(x,y,z);saved.put(at,level.getBlockState(at));
                level.setBlock(at,y<0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            pig.setNoAi(true);pig.setPos(origin.getX()+.5,origin.getY()+.1,origin.getZ()+.5);
            level.setBlock(origin,ModBlocks.byId("quicksand").defaultBlockState(),2);
            var state=QuicksandPhysics.state(pig);
            pig.setDeltaMovement(.2,0,.2);data.setGameTime(clock+400);
            QuicksandPhysics.tick(pig,level);
            require(pig.getDeltaMovement().horizontalDistanceSqr()==0 && pig.getDeltaMovement().y<0,"direct caller has authoritative fallback");
            int air=state.air;Vec3 once=pig.getDeltaMovement();QuicksandPhysics.tick(pig,level);
            require(state.air==air && pig.getDeltaMovement().equals(once),"duplicate direct fallback cannot apply motion or air twice");
            passed.add("direct server physics callers retain one motion and one air update without native travel");

            data.setGameTime(clock+401);pig.setDeltaMovement(Vec3.ZERO);
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));
            double before=pig.getZ();pig.travel(new Vec3(0,0,1));
            require(pig.getZ()>before && pig.getZ()-before<.02,"native solid sinking travel limits actual input displacement");
            require(state.nativeTravelTick==level.getGameTime(),"native travel records its server tick");
            Vec3 nativeVelocity=pig.getDeltaMovement();QuicksandPhysics.tick(pig,level);
            require(pig.getDeltaMovement().equals(nativeVelocity),"Post cannot damp native travel again");
            passed.add("actual native solid travel limits input before displacement and Post does not apply it twice");

            data.setGameTime(clock+402);
            level.setBlock(origin,ModBlocks.byId("tar").defaultBlockState().setValue(LiquidBlock.LEVEL,7),2);
            pig.setPos(origin.getX()+.5,origin.getY()+.025,origin.getZ()+.5);pig.setDeltaMovement(Vec3.ZERO);
            var contact=QuicksandPhysics.findContact(pig,level);
            require(contact!=null && contact.material()==SinkingMaterial.TAR && contact.depth()<.12,"thin flowing tar detects feet independently of eyes");
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));
            before=pig.getZ();pig.travel(new Vec3(0,0,1));
            double step=pig.getZ()-before;
            double ordinaryAcceleration=.02*pig.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)/.1;
            require(step>0 && step<ordinaryAcceleration,"thin flowing tar permits a step below this mob's ordinary acceleration: "+step+" < "+ordinaryAcceleration);
            passed.add("actual one-ninth flowing tar constrains foot input without eye immersion");
        } finally {
            for(var visual:level.getEntitiesOfClass(com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity.class,new net.minecraft.world.phys.AABB(origin).inflate(6)))if(visual.target()==pig)visual.discard();
            pig.discard();saved.forEach((at,state)->level.setBlock(at,state,3));data.setGameTime(clock);
        }
        return List.copyOf(passed);
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException("MFQM viscosity server check failed: "+message);}
}
