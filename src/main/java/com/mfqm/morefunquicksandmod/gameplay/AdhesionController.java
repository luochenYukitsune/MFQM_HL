package com.mfqm.morefunquicksandmod.gameplay;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.UUID;

/** Owns one trapping episode and the aggregate force. Visual entities never apply drag. */
public final class AdhesionController {
    public record Anchor(BlockPos block, Vec3 point, String material, int side, long created, UUID visual) {}
    private static final Identifier BOARD_DRAG=Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"board_drag");
    private static final TagKey<EntityType<?>> ALLOWED=TagKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"sticky_board_targets"));
    private static final TagKey<EntityType<?>> EXCLUDED=TagKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"sticky_board_excluded"));
    private AdhesionController(){}

    public static void requestStruggle(ServerPlayer player) {
        var state=QuicksandPhysics.state(player);
        if(exempt(player) || state.material.isEmpty() && state.anchors.isEmpty())return;
        // A burst of packets is still just one request for this server tick.
        state.struggleRequestTick=player.level().getGameTime();
    }
    public static boolean exempt(LivingEntity entity) {
        return entity instanceof Player player && (player.isSpectator() || player.getAbilities().flying
                || player.isCreative() && !ModConfig.SERVER.creativeGroundPhysics.get());
    }
    public static boolean boardTarget(LivingEntity entity) {
        if(exempt(entity) || entity.getType().is(EXCLUDED))return false;
        return entity instanceof Player || entity.getType().is(ALLOWED) && entity.getBbWidth()<=1 && entity.getBbHeight()<=2.2;
    }
    public static int charge(net.minecraft.world.level.block.state.BlockState block) {
        for(var property:block.getProperties())if(property.getName().equals("charge") && property instanceof IntegerProperty p)return block.getValue(p);
        return 0;
    }
    public static BlockPos boardUnder(LivingEntity entity,net.minecraft.world.level.Level level) {
        if(!boardTarget(entity))return null;
        var box=entity.getBoundingBox().deflate(.02);
        for(var cursor:BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX),net.minecraft.util.Mth.floor(box.minY-.12),net.minecraft.util.Mth.floor(box.minZ),
                net.minecraft.util.Mth.floor(box.maxX),net.minecraft.util.Mth.floor(box.minY+.02),net.minecraft.util.Mth.floor(box.maxZ))) {
            if(!level.hasChunkAt(cursor))continue;
            var block=level.getBlockState(cursor);
            if(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).equals(Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"sticky_board"))
                    && charge(block)>0 && Math.abs(entity.getY()-(cursor.getY()+.0625))<.18)return cursor.immutable();
        }
        return null;
    }

    /** Before ordinary medium motion: one input evaluation, including movement intent while immobilized. */
    public static void prepare(LivingEntity entity,ServerLevel level,QuicksandPhysics.Contact contact,boolean immune) {
        var state=QuicksandPhysics.state(entity);long tick=level.getGameTime();
        state.struggleResult=null;
        if(exempt(entity) || immune) { clear(entity,level,state);return; }
        // Removed medium or exhausted glue must release before native travel,
        // including a ground jump on the very first depleted-board tick.
        boolean invalid=state.anchors.removeIf(anchor->{
            if(valid(level,anchor))return false;
            removeVisual(level,anchor);return true;
        });
        if(invalid) {
            state.adhesiveConnections=state.anchors.size();state.adhesiveForce=Vec3.ZERO;
            if(state.anchors.isEmpty()){state.adhesiveOrigin=null;state.adhesiveMaterial="";state.adhesiveJump=new AdhesiveMotion.Jump();}
        }
        var board=contact==null?boardUnder(entity,level):null;
        String material=contact!=null?contact.material().id:board!=null?"sticky_board":"";
        if(!material.isEmpty()) {
            boolean newBoard=board!=null && (!board.equals(state.episodeBoard) || charge(level.getBlockState(board))>state.previousBoardCharge
                    || state.dryTicks>0 && state.anchors.isEmpty() && !state.struggle.boardReleased(StruggleRules.Medium.BOARD));
            state.dryTicks=0;
            if(!state.episodeMaterial.equals(material) || newBoard) {
                clearAnchors(level,state);
                boolean checked=state.struggle.bootCheckMade();
                var previous=state.struggle;
                state.struggle=new StruggleRules.State(previous.lastProcessedTick(),previous.lastAcceptedTick(),0,false,previous.keyDown(),previous.jumpDown(),checked);
                state.episodeMaterial=material;state.consumedBoardSteps=0;
                state.episodeBoard=board;
            }
            if(board!=null){state.material=material;state.depth=.08;}
        } else if(++state.dryTicks>40 && state.anchors.isEmpty()) { clear(entity,level,state);return; }
        String episode=material.isEmpty()?state.episodeMaterial:material;
        if(episode.isEmpty())return;
        StruggleRules.Medium medium=medium(episode);
        boolean fresh=state.inputTick!=Long.MIN_VALUE && tick-state.inputTick>=0 && tick-state.inputTick<10;
        boolean moving=entity instanceof Player?fresh && state.movingInput:entity.getDeltaMovement().horizontalDistanceSqr()>.00001;
        boolean jumping=entity instanceof Player?fresh && state.jumpInput:entity.isJumping();
        boolean key=state.struggleRequestTick!=Long.MIN_VALUE && tick>=state.struggleRequestTick && tick-state.struggleRequestTick<=2;
        state.struggleRequestTick=Long.MIN_VALUE; // Consume once even when network tasks arrive before the next world tick.
        // Mobs attempt a slow pull once every 4 seconds. They normally need outside rescue.
        if(!(entity instanceof Player) && !material.isEmpty() && tick%80==0)key=true;
        int food=entity instanceof Player player?Math.clamp(player.getFoodData().getFoodLevel(),0,20):20;
        var result=StruggleRules.step(state.struggle,tick,medium,new StruggleRules.Input(moving,jumping,key),food);
        state.struggle=result.state();state.struggleResult=result;
        state.boardReleased=state.episodeMaterial.equals("sticky_board") && state.struggle.boardReleased(StruggleRules.Medium.BOARD);
        if(result.acceptedPress()) {
            state.struggleAnimationTick=tick;state.struggleSide^=1;
            level.playSound(null,entity.blockPosition(),net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL,net.minecraft.sounds.SoundSource.NEUTRAL,.25F,.85F);
            level.sendParticles(episode.equals("glue") || episode.equals("sticky_board")?net.minecraft.core.particles.ParticleTypes.ITEM_SNOWBALL:net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                    entity.getX(),entity.getY()+.12,entity.getZ(),3,.16,.05,.16,.005);
            if(entity instanceof Player player)player.causeFoodExhaustion((float)result.exhaustion());
            // QuicksandPhysics synchronizes after recording this tick's motion cost.
        }
        if(board!=null) {
            int steps=Math.min(7,(int)(state.struggle.effort()/5));
            if(steps>state.consumedBoardSteps) {
                com.mfqm.morefunquicksandmod.block.StickyBoardBlock.consumeCoating(level,board,steps-state.consumedBoardSteps);state.consumedBoardSteps=steps;
            }
            state.previousBoardCharge=charge(level.getBlockState(board));
            // Input resistance belongs to actual travel, not the speed attribute
            // which vanilla also uses to zoom the camera.
            removeBoardDrag(entity);
            if(!state.struggle.boardReleased(medium)) {
                if(state.coatingLevel<=1 || state.coatingTicks<40){state.coatingType="glue";state.coatingLevel=1;}
                state.coatingTicks=Math.max(state.coatingTicks,1200); // Normal physics synchronization sends this once per cadence.
            }
        } else removeBoardDrag(entity);
        if(!material.isEmpty() && ModConfig.SERVER.adhesiveBonds.get() && profile(material)!=null
                && !(material.equals("tar") && !ModConfig.SERVER.tarTreads.get())
                && !state.struggle.boardReleased(medium)) {
            BlockPos block=board!=null?board:contact.pos();
            double surface=board!=null?board.getY()+.065:Math.min(contact.surface(),entity.getY()+.1);
            // Existing anchors remain fixed; only fresh contact can create replacements.
            if(state.anchors.isEmpty()) {state.adhesiveOrigin=entity.position();state.adhesiveMaterial=material;state.adhesiveJump=new AdhesiveMotion.Jump();}
            if(state.anchors.stream().noneMatch(a->a.block().equals(block) && a.material().equals(material)))for(int side=0;side<2 && state.anchors.size()<AdhesiveRules.MAX_BONDS;side++) {
                Vec3 foot=foot(entity,side);Vec3 point=new Vec3(foot.x,surface,foot.z);
                var visual=ModEntities.ADHESIVE_TETHER.get().create(level,EntitySpawnReason.TRIGGERED);
                if(visual!=null){visual.configure(entity,point,material,side,1);if(!level.addFreshEntity(visual))visual=null;}
                state.anchors.add(new Anchor(block.immutable(),point,material,side,tick,visual==null?null:visual.getUUID()));
            }
        }
        state.adhesiveStrength=state.struggle.adhesionScale(medium);
    }
    public static StruggleRules.Medium medium(String id){return id.equals("glue")?StruggleRules.Medium.GLUE:id.equals("sticky_board")?StruggleRules.Medium.BOARD:StruggleRules.Medium.OTHER;}
    public static double activityRadius(String id) {
        return switch(id) {
            case "glue" -> ModConfig.SERVER.glueActivityRadius.get();
            case "honey" -> ModConfig.SERVER.honeyActivityRadius.get();
            case "tar" -> ModConfig.SERVER.tarActivityRadius.get();
            case "sinking_slime","mucus" -> ModConfig.SERVER.slimeActivityRadius.get();
            case "sticky_board" -> ModConfig.SERVER.boardActivityRadius.get();
            default -> ModConfig.SERVER.mudActivityRadius.get();
        };
    }
    public static AdhesiveRules.Profile profile(String id) {
        var base=AdhesiveRules.profileFor(id);if(base==null)return null;
        double distance=switch(id) {
            case "glue"->ModConfig.SERVER.glueBondDistance.get();
            case "sticky_board"->base.maxDistance();
            case "tar"->ModConfig.SERVER.tarBondDistance.get();
            case "honey"->ModConfig.SERVER.honeyBondDistance.get();
            case "sinking_slime","mucus"->ModConfig.SERVER.slimeBondDistance.get();
            default->ModConfig.SERVER.mudBondDistance.get();
        };
        double radius=activityRadius(id);
        return new AdhesiveRules.Profile(AdhesiveMotion.bondDistance(distance,radius),base.stiffness(),AdhesiveMotion.restLength(radius));
    }
    public static Vec3 foot(LivingEntity entity,int side) {
        double yaw=Math.toRadians(entity.yBodyRot),offset=side==0?-.16:.16;
        return entity.position().add(Math.cos(yaw)*offset,.12,Math.sin(yaw)*offset);
    }

    /** After medium motion: evaluate once and add a single capped force, preserving rescue. */
    public static void finish(LivingEntity entity,ServerLevel level) {
        var state=QuicksandPhysics.state(entity);long tick=level.getGameTime();
        if(exempt(entity) || !ModConfig.SERVER.adhesiveBonds.get()){clearAnchors(level,state);return;}
        var bonds=new ArrayList<AdhesiveRules.Bond>();var retained=new ArrayList<Anchor>();
        BlockPos bootAnchor=null;
        boolean snapped=false;
        for(var anchor:state.anchors) {
            boolean valid=valid(level,anchor);
            var p=profile(anchor.material());Vec3 foot=foot(entity,anchor.side());
            double strength=state.struggle.adhesionScale(medium(anchor.material()));
            var bond=new AdhesiveRules.Bond(point(anchor.point()),point(foot),p,strength,valid);
            var evaluated=AdhesiveRules.evaluate(java.util.List.of(bond));
            if(evaluated.activeBonds()>0) {
                bonds.add(bond);retained.add(anchor);
                if(anchor.visual()!=null && level.getEntity(anchor.visual()) instanceof AdhesiveTetherEntity visual)visual.configure(entity,anchor.point(),anchor.material(),anchor.side(),(float)strength);
            } else {
                if(anchor.visual()!=null && level.getEntity(anchor.visual()) instanceof AdhesiveTetherEntity visual)visual.beginBreak();
                snapped=true;
                if(valid && strength>0 && foot.distanceTo(anchor.point())>p.maxDistance() && medium(anchor.material())!=StruggleRules.Medium.OTHER)bootAnchor=anchor.block();
            }
            if(bootAnchor==null && state.struggleResult!=null && state.struggleResult.acceptedPress() && state.struggle.effort()>=4
                    && medium(anchor.material())!=StruggleRules.Medium.OTHER)bootAnchor=anchor.block();
        }
        state.anchors.clear();state.anchors.addAll(retained);
        if(snapped)level.playSound(null,entity.blockPosition(),net.minecraft.sounds.SoundEvents.SLIME_JUMP_SMALL,net.minecraft.sounds.SoundSource.NEUTRAL,.2F,1.25F);
        state.adhesiveConnections=retained.size();
        if(retained.isEmpty()){state.adhesiveOrigin=null;state.adhesiveMaterial="";}
        Vec3 force=vector(AdhesiveRules.evaluate(bonds).force());
        // Glue preserves its depth while resting. Vertical stretch cannot silently cause passive sinking.
        if(state.material.equals("glue"))force=new Vec3(force.x,0,force.z);
        if(state.rescueTicks>0)force=force.scale(.2);
        state.adhesiveForce=force;
        // Native travel applies the previous bounded force after momentum damping.
        // Server players that did not travel still need the authoritative velocity fallback.
        if(force.lengthSqr()>0 && state.nativeTravelTick!=tick){entity.setDeltaMovement(entity.getDeltaMovement().add(force));if(!(entity instanceof ServerPlayer))entity.hurtMarked=true;}
        if(bootAnchor!=null && !entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty()) {
            var decision=StruggleRules.evaluateBootLoss(state.struggle,StruggleRules.Medium.GLUE,ModConfig.SERVER.bootLossChance.get(),level.random.nextDouble());
            state.struggle=decision.state();
            if(decision.lost())com.mfqm.morefunquicksandmod.entity.StuckBootsEntity.tryLeaveBehind(entity,bootAnchor);
        }
    }
    private static AdhesiveRules.Point point(Vec3 v){return new AdhesiveRules.Point(v.x,v.y,v.z);}
    private static Vec3 vector(AdhesiveRules.Point p){return new Vec3(p.x(),p.y(),p.z());}
    private static void removeVisual(ServerLevel level,Anchor anchor){if(anchor.visual()!=null && level.getEntity(anchor.visual()) instanceof AdhesiveTetherEntity visual)visual.discard();}
    private static boolean valid(ServerLevel level,Anchor anchor) {
        if(!level.hasChunkAt(anchor.block()) || anchor.material().equals("tar") && !ModConfig.SERVER.tarTreads.get())return false;
        var block=level.getBlockState(anchor.block());
        return anchor.material().equals("sticky_board")?com.mfqm.morefunquicksandmod.block.StickyBoardBlock.isCoated(block):QuicksandPhysics.id(block).equals(anchor.material());
    }
    private static void clearAnchors(ServerLevel level,SinkingState state){state.anchors.forEach(a->removeVisual(level,a));state.anchors.clear();state.adhesiveConnections=0;state.adhesiveForce=Vec3.ZERO;state.adhesiveOrigin=null;state.adhesiveMaterial="";state.adhesiveJump=new AdhesiveMotion.Jump();}
    private static void removeBoardDrag(LivingEntity entity){var speed=entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(BOARD_DRAG);}
    /** A ServerPlayer keeps this attachment across teleport, so clear the old world's helpers explicitly. */
    public static void changedDimension(LivingEntity entity,ServerLevel previous,ServerLevel current) {
        var state=QuicksandPhysics.state(entity);
        clear(entity,previous==null?current:previous,state);
        state.physicsDimension=current.dimension();state.lastTick=Long.MIN_VALUE;
        state.preparedTick=Long.MIN_VALUE;state.nativeTravelTick=Long.MIN_VALUE;state.totalStruggleSink=0;
        state.lowSpeedVelocity=Vec3.ZERO;state.motionRemainder=Vec3.ZERO;state.travelMaterial="";
        state.material="";state.depth=0;state.eyesCovered=false;state.contactTicks=0;state.dryTicks=0;
        state.jumpInput=false;state.sneakInput=false;state.movingInput=false;
        state.inputTick=Long.MIN_VALUE;state.struggleRequestTick=Long.MIN_VALUE;
        state.struggleResult=null;state.struggleSide=0;state.rescueTicks=0;
        state.externalMotionTicks=0;
        state.previousYaw=entity.getYRot();state.previousY=entity.getY();
        QuicksandPhysics.clearGlueGravity(entity);
    }
    private static void clear(LivingEntity entity,ServerLevel level,SinkingState state) {
        clearAnchors(level,state);removeBoardDrag(entity);state.struggle=StruggleRules.State.initial();state.episodeMaterial="";
        state.consumedBoardSteps=0;state.struggleAnimationTick=Long.MIN_VALUE;
        state.episodeBoard=null;state.previousBoardCharge=0;
        state.boardReleased=false;
    }
}
