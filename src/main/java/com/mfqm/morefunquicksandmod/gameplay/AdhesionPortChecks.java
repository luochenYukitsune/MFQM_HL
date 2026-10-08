package com.mfqm.morefunquicksandmod.gameplay;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Actual physics and entity lifecycle in the isolated developer world. */
public final class AdhesionPortChecks {
    public static List<String> verify(ServerLevel level, BlockPos at) {
        var results=new ArrayList<String>();
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var pig=EntityType.PIG.create(level,EntitySpawnReason.TRIGGERED);
        if(pig==null)throw new IllegalStateException("create adhesive probe");
        var data=(ServerLevelData)level.getLevelData(); long clock=level.getGameTime();
        try {
            for(int x=-1;x<=2;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=3;y++) {
                var pos=at.offset(x,y,z);saved.put(pos,level.getBlockState(pos));
                // Leave a gap before the water so the pig's entire feet box can leave glue
                // at x=2.1 while its original foot anchors are still inside the 2.5-block limit.
                level.setBlock(pos,y<0?Blocks.STONE.defaultBlockState():y>=3?Blocks.AIR.defaultBlockState():
                        x<=0?ModBlocks.byId("glue").defaultBlockState():x==2?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            pig.setNoAi(true);pig.setPos(at.getX()+.5,at.getY()+.1,at.getZ()+.5);level.addFreshEntity(pig);
            pig.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
            var state=QuicksandPhysics.state(pig);pig.setDeltaMovement(0,-.08,0);
            QuicksandPhysics.tick(pig,level);
            require(pig.getDeltaMovement().y==0,"Still glue must cancel gravity and old equipment/load sinking");
            require(!state.anchors.isEmpty() && state.anchors.size()<=4,"real glue creates bounded foot anchors");
            results.add("actual deep glue holds still equipped actor without passive sinking");
            for(int i=1;i<=5;i++) {
                data.setGameTime(clock+i*12);state.struggleRequestTick=level.getGameTime();
                // Separate physical presses with a key-up tick, as the real client sends edges.
                state.struggle=new StruggleRules.State(state.struggle.lastProcessedTick(),state.struggle.lastAcceptedTick(),state.struggle.effort(),state.struggle.glueSinkStarted(),false,false,state.struggle.bootCheckMade());
                QuicksandPhysics.tick(pig,level);
            }
            require(state.struggle.glueSinkStarted() && pig.getDeltaMovement().y<0,"accepted repeated actions start actual glue sinking");
            data.setGameTime(clock+61);QuicksandPhysics.tick(pig,level);
            require(pig.getDeltaMovement().y==0,"stop struggling stops actual sinking");
            int air=state.air;QuicksandPhysics.tick(pig,level);require(state.air==air,"duplicate physics tick has no extra oxygen cost");
            results.add("actual repeated struggle begins sinking; rest and duplicate tick add no sinking");
            var originalAnchors=state.anchors.stream().map(AdhesionController.Anchor::visual).toList();
            data.setGameTime(clock+200);QuicksandPhysics.tick(pig,level);
            require(!state.anchors.isEmpty() && state.anchors.stream().map(AdhesionController.Anchor::visual).toList().equals(originalAnchors),
                    "long glue contact must preserve original anchors rather than expire by creation age");
            QuicksandPhysics.coat(pig,"glue",3,1200);
            int coatingTicks=state.coatingTicks;
            pig.setPos(at.getX()+2.1,at.getY()+.1,at.getZ()+.5);
            long waterTick=((clock+203+15)/16)*16;
            data.setGameTime(waterTick);pig.baseTick();
            require(pig.isInWater() && QuicksandPhysics.findContact(pig,level)==null,"water probe is outside all glue contact");
            QuicksandPhysics.tick(pig,level);
            require(state.coatingLevel==2 && state.coatingTicks<coatingTicks,"actual water contact cleans glue residue");
            require(!state.anchors.isEmpty() && state.anchors.stream().map(AdhesionController.Anchor::visual).toList().equals(originalAnchors),
                    "leaving long-lived glue contact within distance and washing residue cannot disconnect anchors");
            results.add("long-lived glue anchors survive nearby exit and water washing; water only removes residue");
            pig.setPos(at.getX()+8,at.getY()+.1,at.getZ()+.5);data.setGameTime(waterTick+1);QuicksandPhysics.tick(pig,level);
            require(state.anchors.isEmpty(),"over-distance glue anchors break");
            var breaking=level.getEntitiesOfClass(AdhesiveTetherEntity.class,pig.getBoundingBox().inflate(12)).stream().filter(e->e.target()==pig).toList();
            require(!breaking.isEmpty() && breaking.stream().allMatch(AdhesiveTetherEntity::breaking),"broken helpers retain only cosmetic recoil and no active connection");
            data.setGameTime(waterTick+8);breaking.forEach(AdhesiveTetherEntity::tick);
            require(breaking.stream().allMatch(AdhesiveTetherEntity::isRemoved),"cosmetic recoil helpers disappear after six ticks");
            results.add("actual over-distance break removes physical bonds immediately and finishes cosmetic recoil after six ticks");
        } finally {
            for(var e:level.getEntitiesOfClass(AdhesiveTetherEntity.class,new net.minecraft.world.phys.AABB(at).inflate(14)))if(e.target()==pig)e.discard();
            pig.discard();saved.forEach((pos,state)->level.setBlock(pos,state,3));data.setGameTime(clock);
        }
        results.addAll(verifyBoard(level,at.offset(0,0,5)));
        results.addAll(verifyDimension(level,at.offset(0,0,10)));
        results.addAll(verifyGlueJump(level,at.offset(0,0,15)));
        results.addAll(verifyDynamic(level,at.offset(0,0,20)));
        return results;
    }
    private static List<String> verifyDynamic(ServerLevel level,BlockPos at) {
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var data=(ServerLevelData)level.getLevelData();long clock=level.getGameTime(),base=((clock+199)/4)*4+1;
        var pig=EntityType.PIG.create(level,EntitySpawnReason.TRIGGERED);
        if(pig==null)throw new IllegalStateException("create dynamic adhesion probe");
        try {
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<4;y++) {
                var pos=at.offset(x,y,z);saved.put(pos,level.getBlockState(pos));
                level.setBlock(pos,y<0?Blocks.STONE.defaultBlockState():y==0?ModBlocks.byId("glue").defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            pig.setNoAi(true);pig.setPos(at.getX()+.35,at.getY()+.03,at.getZ()+.5);level.addFreshEntity(pig);
            data.setGameTime(base);QuicksandPhysics.tick(pig,level);var state=QuicksandPhysics.state(pig);
            require(state.anchors.size()==2,"first contact creates a sole connection for each foot");
            var oldest=state.anchors.getFirst().visual();var origin=state.adhesiveOrigin;
            for(int i=1;i<=40;i++) {
                data.setGameTime(base+i*4);pig.setPos(at.getX()+(i%2==0?.35:.65),at.getY()+.03,at.getZ()+.5);QuicksandPhysics.tick(pig,level);
                require(state.anchors.size()<=64,"active contact cap respected during motion");
                require(state.adhesiveOrigin.equals(origin),"refresh never resets horizontal origin");
            }
            require(state.anchors.size()==64,"same block motion reaches 64 live connections");
            require(state.anchors.stream().noneMatch(a->a.visual().equals(oldest)),"full contact list replaces its oldest identity");
            require(level.getEntity(oldest) instanceof AdhesiveTetherEntity e && e.breaking(),"replaced identity only retains cosmetic recoil");
            require(state.anchors.stream().map(AdhesionController.Anchor::point).distinct().count()>30,"sole spreading produces many different roots");
            var identities=state.anchors.stream().map(AdhesionController.Anchor::visual).toList();
            data.setGameTime(base+164);QuicksandPhysics.tick(pig,level);
            require(identities.equals(state.anchors.stream().map(AdhesionController.Anchor::visual).toList()),"standing does not add contacts");
            for(var anchor:state.anchors) {
                var p=anchor.point();var b=anchor.block();
                require(p.x>b.getX() && p.x<b.getX()+1 && p.z>b.getZ() && p.z<b.getZ()+1 && p.y>b.getY() && p.y<b.getY()+1,"every root inside occupied volume");
            }
            var lower=ModBlocks.byId("glue").defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,7);
            level.setBlock(at,lower,2);data.setGameTime(base+168);QuicksandPhysics.tick(pig,level);
            require(state.anchors.size()==64 && state.anchors.stream().allMatch(a->a.point().y<at.getY()+lower.getFluidState().getHeight(level,at)),"existing 64 roots follow a falling fluid level without replacement");
            require(identities.equals(state.anchors.stream().map(AdhesionController.Anchor::visual).toList()),"liquid height change preserves contact identities");
            level.setBlock(at,ModBlocks.byId("glue").defaultBlockState(),2);base+=8;
            pig.setPos(at.getX()+.35,at.getY()+3.7,at.getZ()+.5);data.setGameTime(base+168);QuicksandPhysics.tick(pig,level);
            require(!state.anchors.isEmpty(),"glue connections survive more than three vertical blocks");
            pig.setPos(at.getX()+.35,at.getY()+4.3,at.getZ()+.5);data.setGameTime(base+172);QuicksandPhysics.tick(pig,level);
            require(state.anchors.isEmpty(),"vertical excess releases connections");
            var thin=ModBlocks.byId("glue").defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,7);
            level.setBlock(at,thin,2);
            var root=AdhesiveSurface.root(level,at,"glue",new net.minecraft.world.phys.Vec3(at.getX()+1.1,at.getY()+1,at.getZ()-.1));
            require(root!=null && root.y<at.getY()+thin.getFluidState().getHeight(level,at) && root.x<at.getX()+1 && root.z>at.getZ(),"thin flowing layer clamps height and edge");
            level.setBlock(at,Blocks.AIR.defaultBlockState(),2);require(AdhesiveSurface.root(level,at,"glue",root)==null,"removed medium invalidates its root immediately");
            base+=180;
            for(String id:List.of("mud","bog","morass","mire","moor","wet_peat","brown_clay","sinking_clay","slurry","quicksand")) {
                level.setBlock(at,ModBlocks.byId(id).defaultBlockState(),2);pig.setPos(at.getX()+.5,at.getY()+.03,at.getZ()+.5);
                data.setGameTime(base+=4);state.lastTick=Long.MIN_VALUE;QuicksandPhysics.tick(pig,level);
                require(state.anchors.isEmpty() && state.material.equals(id),"non adhesive medium preserves contact physics without strands: "+id);
            }
            return List.of("dynamic adhesion: same-cell 64 roots, FIFO recoil, stationary retention, stable origin, vertical distance, thin-fluid edges and non-adhesive contact pass");
        } finally {
            for(var helper:level.getEntitiesOfClass(AdhesiveTetherEntity.class,new net.minecraft.world.phys.AABB(at).inflate(12)))if(helper.target()==pig)helper.discard();
            pig.discard();saved.forEach((pos,state)->level.setBlock(pos,state,3));data.setGameTime(clock);
        }
    }
    private static List<String> verifyGlueJump(ServerLevel level,BlockPos at) {
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var data=(ServerLevelData)level.getLevelData();long clock=level.getGameTime();
        // Keep vanilla Mob.aiStep, travel and JumpControl; omit wandering goals so
        // the regression isolates sustained jumping without teleporting each tick.
        var pig=new net.minecraft.world.entity.animal.pig.Pig(EntityType.PIG,level){
            @Override protected void registerGoals() {}
        };
        try {
            for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=3;y++) {
                var pos=at.offset(x,y,z);saved.put(pos,level.getBlockState(pos));
                level.setBlock(pos,y<0?Blocks.STONE.defaultBlockState():x<=1 && y<3?
                        ModBlocks.byId("glue").defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            pig.setPos(at.getX()+.5,at.getY()+.1,at.getZ()+.5);require(level.addFreshEntity(pig),"insert jump probe");
            double start=pig.getY();
            for(int i=0;i<20;i++) {
                data.setGameTime(clock+500+i);pig.getJumpControl().jump();
                QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));
                tickMob(pig);
                require(pig.getY()<=start+.30,"real mob fluid jump stays within the bounded short-hop height");
                QuicksandPhysics.tick(pig,level);
            }
            require(QuicksandPhysics.state(pig).struggle.effort()>0,"mob physical jump suppression preserves struggle intent");
            double beforeRescue=pig.getY();QuicksandPhysics.rescue(pig,pig.position().add(0,2,0),.15);
            data.setGameTime(clock+521);pig.getJumpControl().jump();
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);
            require(pig.getY()>beforeRescue,"suppressing fluid jump preserves actual upward rescue travel");
            QuicksandPhysics.tick(pig,level);
            pig.setPos(at.getX()+4.5,at.getY(),at.getZ()+.5);pig.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);pig.setOnGround(true);
            data.setGameTime(clock+522);pig.getJumpControl().jump();
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);
            require(pig.getY()>at.getY()+.1,"ordinary dry-ground mob jumping remains available");
            data.setGameTime(clock+523);pig.setJumping(false);QuicksandPhysics.tick(pig,level);
            // Advance genuine ticks so the preceding rescue window and dry jump
            // expire before measuring a fresh board. Moving only the world clock
            // does not consume per-entity preservation ticks.
            for(int i=524;i<540;i++) {
                data.setGameTime(clock+i);pig.setJumping(false);
                QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);QuicksandPhysics.tick(pig,level);
            }

            var board=at.offset(3,0,0);var next=at.offset(4,0,0);
            level.setBlock(board,com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7),3);
            pig.setPos(board.getX()+.5,board.getY()+.0625,board.getZ()+.5);pig.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);pig.setOnGround(true);
            double boardY=pig.getY();
            for(int i=0;i<20;i++) {
                data.setGameTime(clock+550+i);pig.getJumpControl().jump();
                QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);
                require(pig.getY()<=boardY+.30,"real mob jump on a fresh board remains a short bounded hop: tick="+i+" rise="+(pig.getY()-boardY));
                QuicksandPhysics.tick(pig,level);
            }
            require(QuicksandPhysics.state(pig).struggle.effort()>0,"board jump keeps mob struggle intent");
            long releasedClock=weakenMobBoard(pig,level,data,clock+600);
            require(com.mfqm.morefunquicksandmod.block.StickyBoardBlock.isCoated(level.getBlockState(board)),"released mob board still has coating");
            // Weakening can finish while the permitted short hop is descending.
            // Test an ordinary ground jump after genuine landing, not in midair.
            for(int i=1;i<=12;i++) {
                data.setGameTime(releasedClock+i);pig.setJumping(false);
                QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);QuicksandPhysics.tick(pig,level);
            }
            releasedClock+=12;require(pig.onGround(),"released mob naturally lands before its ordinary ground jump");
            data.setGameTime(releasedClock+2);pig.getJumpControl().jump();
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);
            require(pig.getY()>boardY+.1,"actually weakened coated board permits real mob jumping: rise="+(pig.getY()-boardY)+" ground="+pig.onGround()+" velocity="+pig.getDeltaMovement()+" held="+QuicksandPhysics.isJumpHeld(pig));QuicksandPhysics.tick(pig,level);
            for(int i=3;i<18;i++) {
                data.setGameTime(releasedClock+i);
                QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);QuicksandPhysics.tick(pig,level);
            }
            require(QuicksandPhysics.state(pig).boardReleased && pig.getY()<=boardY+.001,
                    "real landing on the same weakened board preserves release state");
            data.setGameTime(releasedClock+18);pig.getJumpControl().jump();
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);
            require(pig.getY()>boardY+.1,"released mob can jump again after actually landing on the same board");QuicksandPhysics.tick(pig,level);
            for(int i=19;i<34;i++) {
                data.setGameTime(releasedClock+i);
                QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);QuicksandPhysics.tick(pig,level);
            }
            // The release snapshot is still for charge 1. Refill before the next physics
            // preparation so this tests the first actual jump, not just the predicate.
            pig.setPos(board.getX()+.5,boardY,board.getZ()+.5);pig.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);pig.setOnGround(true);
            require(QuicksandPhysics.state(pig).boardReleased,"refill starts with an existing release snapshot");
            level.setBlock(board,com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7),3);
            data.setGameTime(releasedClock+34);pig.getJumpControl().jump();
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);
            require(pig.getY()<=boardY+.30,"refilled board immediately limits jumping before stale released state is prepared");
            QuicksandPhysics.tick(pig,level);require(!QuicksandPhysics.state(pig).boardReleased,"refill resets the real trapping episode");

            long secondReleased=weakenMobBoard(pig,level,data,releasedClock+80);
            level.setBlock(next,com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7),3);
            pig.setPos(next.getX()+.5,boardY,next.getZ()+.5);pig.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);pig.setOnGround(true);
            data.setGameTime(secondReleased+2);pig.getJumpControl().jump();
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);
            require(pig.getY()<=boardY+.30,"different board immediately limits jumping despite an old release snapshot");
            QuicksandPhysics.tick(pig,level);require(next.equals(QuicksandPhysics.state(pig).episodeBoard) && !QuicksandPhysics.state(pig).boardReleased,"new board starts its own real episode");
            for(int i=3;i<16;i++) {
                data.setGameTime(secondReleased+i);
                QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);QuicksandPhysics.tick(pig,level);
            }
            com.mfqm.morefunquicksandmod.block.StickyBoardBlock.consumeCoating(level,next,7);
            double depletedStart=pig.getY();boolean depletedGround=pig.onGround();
            data.setGameTime(secondReleased+16);pig.getJumpControl().jump();
            QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);
            require(pig.getY()>boardY+.1,"depleted board immediately permits ordinary mob jumping: start="+(depletedStart-boardY)+" startGround="+depletedGround+" rise="+(pig.getY()-boardY)+" velocity="+pig.getDeltaMovement()+" jumping="+pig.isJumping()+" held="+QuicksandPhysics.isJumpHeld(pig));
            return List.of("actual vanilla mob fluid jump cannot float out of glue; original intent, upward rescue and dry jumps remain available",
                    "actual mob coated-board jumping is restrained; real struggle releases it, refill/new board re-trap immediately, and depleted boards permit jumps");
        } finally {
            for(var helper:level.getEntitiesOfClass(AdhesiveTetherEntity.class,new net.minecraft.world.phys.AABB(at).inflate(10)))if(helper.target()==pig)helper.discard();
            pig.discard();saved.forEach((pos,block)->level.setBlock(pos,block,3));data.setGameTime(clock);
        }
    }
    /** ServerLevel.tickNonPassenger increments this counter before calling Entity.tick. */
    private static void tickMob(net.minecraft.world.entity.animal.pig.Pig pig){pig.tickCount++;pig.tick();}
    private static long weakenMobBoard(net.minecraft.world.entity.animal.pig.Pig pig,ServerLevel level,ServerLevelData data,long first) {
        for(long tick=first;tick<=first+36*80;tick++) {
            // Real mobs use the controller's normal once-per-80-tick self-rescue
            // attempt. Advance every genuine simulation tick so short hops and
            // landing finish naturally between attempts; never fabricate effort.
            data.setGameTime(tick);QuicksandPhysics.beforeEntityTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(pig));tickMob(pig);QuicksandPhysics.tick(pig,level);
            if(QuicksandPhysics.state(pig).boardReleased)return tick;
        }
        var state=QuicksandPhysics.state(pig);
        throw new IllegalStateException("actual mob self-rescue did not release board: position="+pig.position()+" effort="+state.struggle.effort()+" material="+state.episodeMaterial+" board="+state.episodeBoard);
    }
    private static List<String> verifyBoard(ServerLevel level,BlockPos at) {
        boolean previousCreativePhysics=com.mfqm.morefunquicksandmod.ModConfig.SERVER.creativeGroundPhysics.get();
        var saved=level.getBlockState(at);var support=level.getBlockState(at.below());
        var nextBoard=at.east();var nextSaved=level.getBlockState(nextBoard);var nextSupport=level.getBlockState(nextBoard.below());
        var data=(ServerLevelData)level.getLevelData();long clock=level.getGameTime();
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MFQMAdhesionProbe");
        var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=new net.neoforged.neoforge.common.util.FakePlayer(level,profile).connection;
        player.setPos(at.getX()+.5,at.getY()+.0625,at.getZ()+.5);
        var state=QuicksandPhysics.state(player);
        try {
            level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(at,com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7),2);
            player.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
            QuicksandPhysics.tick(player,level);
            require(!state.anchors.isEmpty() && state.material.equals("sticky_board"),"real coated board detects supported feet");
            require(!player.getItemBySlot(EquipmentSlot.FEET).isEmpty() && !state.struggle.bootCheckMade(),"standing on board cannot roll boot loss");
            var horse=EntityType.HORSE.create(level,EntitySpawnReason.TRIGGERED);var cow=EntityType.COW.create(level,EntitySpawnReason.TRIGGERED);
            require(horse!=null && cow!=null && !AdhesionController.boardTarget(horse) && AdhesionController.boardTarget(cow),"board tag targets exclude horse but allow cow");
            QuicksandPhysics.rescue(player,player.position().add(1,2,0),.1);
            data.setGameTime(clock+1);QuicksandPhysics.tick(player,level);
            require(player.getDeltaMovement().y>.035 && player.getDeltaMovement().x>.02,"board preserves actual rescue lift and horizontal pull");
            state.rescueTicks=0;
            for(int i=0;i<34;i++) {
                data.setGameTime(clock+2+i*12);AdhesionController.requestStruggle(player);
                data.setGameTime(clock+3+i*12);QuicksandPhysics.tick(player,level);
                if(i<33)require(!state.struggle.boardReleased(StruggleRules.Medium.BOARD),"board cannot release before required effort");
                data.setGameTime(clock+4+i*12);QuicksandPhysics.tick(player,level);
            }
            require(state.struggle.boardReleased(StruggleRules.Medium.BOARD) && state.anchors.isEmpty(),"actual 19.8-second board struggle releases all anchors");
            require(level.getBlockState(at).is(com.mfqm.morefunquicksandmod.registry.ModBlocks.byId("sticky_board")),"struggle consumes coating and preserves reusable base");
            long lastAccepted=state.struggle.lastAcceptedTick();
            level.setBlock(at,com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7),2);
            data.setGameTime(clock+401);QuicksandPhysics.tick(player,level);
            require(state.struggle.effort()==0 && !state.anchors.isEmpty() && state.struggle.lastAcceptedTick()==lastAccepted,
                    "recoating the released board starts a fresh restraint but preserves accepted-press cooldown");
            data.setGameTime(clock+402);AdhesionController.requestStruggle(player);
            data.setGameTime(clock+403);QuicksandPhysics.tick(player,level);
            require(state.struggle.effort()==0 && state.struggle.lastAcceptedTick()==lastAccepted,"recoating cannot accept an early repeat press");
            level.setBlock(nextBoard.below(),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(nextBoard,com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7),2);
            player.setPos(nextBoard.getX()+.5,nextBoard.getY()+.0625,nextBoard.getZ()+.5);
            data.setGameTime(clock+404);QuicksandPhysics.tick(player,level);
            require(nextBoard.equals(state.episodeBoard) && state.struggle.effort()==0 && state.struggle.lastAcceptedTick()==lastAccepted,
                    "another board starts its own effort episode without resetting the global press cooldown");
            data.setGameTime(clock+405);AdhesionController.requestStruggle(player);
            data.setGameTime(clock+406);QuicksandPhysics.tick(player,level);
            require(state.struggle.effort()==0 && state.struggle.lastAcceptedTick()==lastAccepted,"changing boards cannot bypass the twelve-tick press cooldown");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=false;
            com.mfqm.morefunquicksandmod.ModConfig.SERVER.creativeGroundPhysics.set(true);
            data.setGameTime(clock+410);QuicksandPhysics.tick(player,level);
            require(!state.anchors.isEmpty() && state.episodeMaterial.equals("sticky_board") && QuicksandPhysics.isJumpHeld(player),"enabled creative ground physics retains board restraint and blocks ordinary jump");
            com.mfqm.morefunquicksandmod.ModConfig.SERVER.creativeGroundPhysics.set(false);
            data.setGameTime(clock+411);QuicksandPhysics.tick(player,level);
            require(state.anchors.isEmpty() && state.episodeMaterial.isEmpty() && !QuicksandPhysics.isJumpHeld(player),"disabled creative ground physics clears board restraint");
            com.mfqm.morefunquicksandmod.ModConfig.SERVER.creativeGroundPhysics.set(true);player.getAbilities().flying=true;
            data.setGameTime(clock+412);QuicksandPhysics.tick(player,level);
            require(state.anchors.isEmpty() && state.episodeMaterial.isEmpty() && !QuicksandPhysics.isJumpHeld(player),"creative flight always clears board restraint");
            player.getAbilities().flying=false;player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            data.setGameTime(clock+413);QuicksandPhysics.tick(player,level);
            require(state.anchors.isEmpty() && !QuicksandPhysics.isJumpHeld(player),"spectator always remains immune");
            return List.of("actual supported board restrains players/cows, rejects horses and releases after 19.8 seconds; both creative-ground settings and flight/spectator immunity pass",
                    "same-board recoating and switching to another board reset restraint effort while retaining press cooldown");
        } finally {
            com.mfqm.morefunquicksandmod.ModConfig.SERVER.creativeGroundPhysics.set(previousCreativePhysics);
            for(var e:level.getEntitiesOfClass(AdhesiveTetherEntity.class,new net.minecraft.world.phys.AABB(at).inflate(12)))if(e.target()==player)e.discard();
            for(var e:level.getEntitiesOfClass(com.mfqm.morefunquicksandmod.entity.StuckBootsEntity.class,new net.minecraft.world.phys.AABB(at).inflate(2)))e.discard();
            player.discard();level.setBlock(at,saved,3);level.setBlock(at.below(),support,3);
            level.setBlock(nextBoard,nextSaved,3);level.setBlock(nextBoard.below(),nextSupport,3);data.setGameTime(clock);
        }
    }
    private static List<String> verifyDimension(ServerLevel source,BlockPos at) {
        ServerLevel destination=source.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        require(destination!=null && destination!=source,"dimension probe requires a distinct loaded Nether level");
        var sourceSaved=source.getBlockState(at);var destinationSaved=destination.getBlockState(at);
        var sourceSupport=source.getBlockState(at.below());var destinationSupport=destination.getBlockState(at.below());
        var data=(ServerLevelData)source.getLevelData();long clock=source.getGameTime();
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MFQMDimensionProbe");
        var player=new net.minecraft.server.level.ServerPlayer(source.getServer(),source,profile,net.minecraft.server.level.ClientInformation.createDefault());
        // Dimension teleport queries NeoForge channel attributes. FakePlayer's shared dummy
        // has no Netty channel; use a real listener over an isolated in-memory channel instead.
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(source.getServer(),connection,player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,io.netty.channel.ChannelFutureListener callback) {}
        };
        player.setPos(at.getX()+.5,at.getY()+.1,at.getZ()+.5);source.addNewPlayer(player);
        try {
            for(String material:List.of("glue","sticky_board")) {
                var block=material.equals("glue")?ModBlocks.byId("glue").defaultBlockState():com.mfqm.morefunquicksandmod.block.StickyBoardBlock.coatedState(7);
                source.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);destination.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
                source.setBlock(at,block,2);destination.setBlock(at,block,2);
                player.setPos(at.getX()+.5,at.getY()+(material.equals("glue")?.1:.0625),at.getZ()+.5);
                data.setGameTime(clock+12);QuicksandPhysics.tick(player,source);
                var state=QuicksandPhysics.state(player);
                require(!state.anchors.isEmpty(),"dimension probe establishes source anchors");
                data.setGameTime(clock+24);AdhesionController.requestStruggle(player);QuicksandPhysics.tick(player,source);
                require(state.struggle.effort()>0 && state.struggleAnimationTick!=Long.MIN_VALUE,"dimension probe has accepted action and animation");
                var visuals=state.anchors.stream().map(a->source.getEntity(a.visual())).toList();
                require(visuals.stream().allMatch(e->e instanceof AdhesiveTetherEntity),"dimension probe source helpers exist");
                QuicksandPhysics.setInput(player,true,true,true);AdhesionController.requestStruggle(player);
                var result=player.teleport(new net.minecraft.world.level.portal.TeleportTransition(destination,player.position(),net.minecraft.world.phys.Vec3.ZERO,0,0,
                        net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
                require(result==player && player.level()==destination && QuicksandPhysics.state(player)==state,"real dimension teleport retains the server player and attachment");
                require(state.anchors.isEmpty() && state.adhesiveConnections==0 && state.episodeMaterial.isEmpty(),"dimension transfer immediately clears old physical episode");
                require(visuals.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),"dimension transfer immediately removes helpers from the old level");
                require(state.struggle.effort()==0 && state.struggleAnimationTick==Long.MIN_VALUE && state.struggleRequestTick==Long.MIN_VALUE
                        && state.inputTick==Long.MIN_VALUE && !state.jumpInput && !state.movingInput && !state.sneakInput,"dimension transfer clears old input, animation and struggle effort");
                var speed=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                require(speed==null || !speed.hasModifier(net.minecraft.resources.Identifier.fromNamespaceAndPath("mfqm","board_drag")),"dimension transfer removes old board drag immediately");
                // The destination clock is identical: old lastTick must not suppress the first contact evaluation.
                QuicksandPhysics.tick(player,destination);
                require(state.material.equals(material) && !state.anchors.isEmpty() && state.struggle.effort()==0,"same-clock destination contact starts a clean episode with new anchors");
                require(state.anchors.stream().noneMatch(a->visuals.stream().anyMatch(e->e.getUUID().equals(a.visual()))),"new dimension cannot reuse old helper identities");
                player.teleport(new net.minecraft.world.level.portal.TeleportTransition(source,player.position(),net.minecraft.world.phys.Vec3.ZERO,0,0,
                        net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
                data.setGameTime(clock);
            }
            return List.of("actual same-coordinate glue/board dimension transfers remove old helpers, input and drag; same-clock contact starts fresh");
        } finally {
            for(ServerLevel level:List.of(source,destination))for(var e:level.getEntitiesOfClass(AdhesiveTetherEntity.class,new net.minecraft.world.phys.AABB(at).inflate(12)))if(e.target()==player)e.discard();
            player.discard();channel.finishAndReleaseAll();source.setBlock(at,sourceSaved,3);destination.setBlock(at,destinationSaved,3);
            source.setBlock(at.below(),sourceSupport,3);destination.setBlock(at.below(),destinationSupport,3);data.setGameTime(clock);
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private AdhesionPortChecks(){}
}
