package com.mfqm.morefunquicksandmod.gameplay;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.block.LegacyStateBlock;
import com.mfqm.morefunquicksandmod.fluid.SinkingLiquidBlock;
import com.mfqm.morefunquicksandmod.entity.BlobEntity;
import com.mfqm.morefunquicksandmod.registry.ModAttachmentTypes;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModDamageTypes;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Server authority for physical contact, oxygen, equipment, coating, and validated rescue. */
@EventBusSubscriber(modid = MFQM.MOD_ID)
public final class QuicksandPhysics {
    private static final Identifier DRAG_ID = Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "quicksand_drag");
    private static final Identifier GLUE_GRAVITY_ID = Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "glue_gravity");
    private static final TagKey<Item> WADING_BOOTS = itemTag("wading_boots");
    private static final TagKey<Item> LIFE_JACKETS = itemTag("life_jackets");
    private static final TagKey<Item> GAS_MASKS = itemTag("gas_masks");
    private static final TagKey<Item> HEAVY_EQUIPMENT = itemTag("heavy_equipment");
    /** Client-only bookkeeping survives replacement of synchronized attachments. */
    private static final WeakHashMap<LivingEntity, Prediction> CLIENT_PREDICTIONS = new WeakHashMap<>();
    private static final class Prediction {
        final WeakReference<net.minecraft.world.level.Level> level;
        long clock, rescueSequence, rescueUntil, externalSequence, externalUntil;
        final SinkingMotion.SinkLedger sink;
        float previousYaw;
        long travelTick=Long.MIN_VALUE;
        Vec3 velocity=Vec3.ZERO,remainder=Vec3.ZERO;
        String travelMaterial="";
        Vec3 bindingOrigin;
        String bindingMaterial="";
        AdhesiveMotion.Jump jump=new AdhesiveMotion.Jump();
        String material = "";
        Prediction(LivingEntity living, SinkingState state) {
            level=new WeakReference<>(living.level());clock=living.level().getGameTime();sink=new SinkingMotion.SinkLedger(state.totalStruggleSink);
            rescueSequence=state.rescueSequence;rescueUntil=clock+state.rescueTicks;
            externalSequence=state.externalMotionSequence;externalUntil=clock+state.externalMotionTicks;
            previousYaw=living.getYRot();
        }
        void observe(SinkingState state,long tick) {
            if(state.rescueSequence!=rescueSequence) {
                rescueSequence=state.rescueSequence;rescueUntil=tick+state.rescueTicks;
            }
            if(state.externalMotionSequence!=externalSequence) {
                externalSequence=state.externalMotionSequence;externalUntil=tick+state.externalMotionTicks;
            }
            clock=tick;
        }
        double consumeSink(SinkingState state) {
            return sink.consume(state.totalStruggleSink);
        }
    }
    private QuicksandPhysics() {}

    private static TagKey<Item> itemTag(String id) { return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, id)); }
    public static SinkingState state(Entity entity) { return entity.getData(ModAttachmentTypes.SINKING_STATE.get()); }
    public static boolean isSinking(Entity entity) { return !state(entity).material.isEmpty(); }
    public static void setInput(ServerPlayer player, boolean jump, boolean sneak) {
        setInput(player,jump,sneak,false);
    }
    public static void setInput(ServerPlayer player, boolean jump, boolean sneak, boolean moving) {
        var state = state(player);
        state.jumpInput = jump;
        state.sneakInput = sneak;
        state.movingInput = moving;
        state.inputTick = player.level().getGameTime();
    }
    public static void coat(Entity entity, String kind, int level, int ticks) {
        var state = state(entity);
        int next = Math.clamp(level, 0, 10);
        if (next >= state.coatingLevel || state.coatingTicks < 40) {
            state.coatingType = kind;
            state.coatingLevel = next;
        }
        state.coatingTicks = Math.max(state.coatingTicks, Math.max(0, ticks));
        if (!entity.level().isClientSide()) entity.syncData(ModAttachmentTypes.SINKING_STATE.get());
    }

    /** Connectors call this after resolving their server-owned target and anchor. */
    public static void rescue(Entity target, Vec3 anchor, double strength) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive() || !Double.isFinite(strength)) return;
        Vec3 direction = anchor.subtract(target.position());
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() > 128 * 128) return;
        if (target.getVehicle() instanceof BlobEntity blob) { blob.rescuePassenger(target, Math.clamp(strength, 0, 1)); return; }
        if (direction.lengthSqr() < .0001) direction = new Vec3(0, 1, 0);
        double amount = Math.clamp(strength, .005, .25);
        Vec3 pull = direction.normalize().scale(amount);
        var motion = target.getDeltaMovement();
        target.setDeltaMovement(motion.x * .7 + pull.x, Math.max(motion.y, Math.max(.035, pull.y)), motion.z * .7 + pull.z);
        target.hurtMarked = true;
        target.fallDistance = 0;
        var state=state(target);state.rescueTicks = 6;state.rescueSequence++;
        // Send the preservation window before the corresponding velocity packet.
        target.syncData(ModAttachmentTypes.SINKING_STATE.get());
    }
    public static void applyRescue(Entity target, double lift) { rescue(target, target.position().add(0, 2, 0), lift); }

    private static void externalImpulse(LivingEntity living) {
        if(!(living.level() instanceof ServerLevel) || AdhesionController.exempt(living) || findContact(living,living.level())==null)return;
        var state=state(living);state.externalMotionTicks=4;state.externalMotionSequence++;
        clearGlueGravity(living);
        living.syncData(ModAttachmentTypes.SINKING_STATE.get());
    }
    @SubscribeEvent public static void knockedBack(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event) {
        if(!event.isCanceled() && event.getStrength()>0)externalImpulse(event.getEntity());
    }
    @SubscribeEvent public static void explosionImpulse(net.neoforged.neoforge.event.level.ExplosionKnockbackEvent event) {
        if(event.getAffectedEntity() instanceof LivingEntity living && event.getKnockbackVelocity().lengthSqr()>0)externalImpulse(living);
    }

    /** Vanilla client prediction must obey the same resting glue depth as the server. */
    public static boolean isHeldByGlue(LivingEntity living) {
        if(!living.isAlive() || AdhesionController.exempt(living))return false;
        var contact=findContact(living,living.level());
        return contact!=null && contact.material()==SinkingMaterial.GLUE && !materialImmune(living,contact.material());
    }
    /** Consult actual contact first so depleted, replaced and re-coated boards cannot use stale release data. */
    public static boolean isJumpHeld(LivingEntity living) {
        if(!living.isAlive() || AdhesionController.exempt(living))return false;
        var contact=findContact(living,living.level());
        if(contact!=null && !living.isPassenger() && !materialImmune(living,contact.material))return true;
        var board=AdhesionController.boardUnder(living,living.level());
        if(board==null)return false;
        var state=state(living);
        int actualCharge=AdhesionController.charge(living.level().getBlockState(board));
        boolean sameReleasedCoating=state.boardReleased && board.equals(state.episodeBoard)
                && actualCharge<=state.previousBoardCharge;
        return !sameReleasedCoating;
    }
    @SubscribeEvent public static void beforeEntityTick(EntityTickEvent.Pre event) {
        if(!(event.getEntity() instanceof LivingEntity living))return;
        if(living.level() instanceof ServerLevel level && living.isAlive())prepare(living,level);
        else if(living.level().isClientSide() && living.canSimulateMovement()) {
            var state=state(living);long tick=living.level().getGameTime();
            var prediction=CLIENT_PREDICTIONS.get(living);
            if(prediction==null || prediction.level.get()!=living.level() || tick<prediction.clock) {
                prediction=new Prediction(living,state);CLIENT_PREDICTIONS.put(living,prediction);
            }
            prediction.observe(state,tick);
            if(AdhesionController.exempt(living) || findContact(living,living.level())==null
                    && AdhesionController.boardUnder(living,living.level())==null && state.adhesiveConnections==0) {
                prediction.sink.discard(state.totalStruggleSink);prediction.material="";
                prediction.travelTick=Long.MIN_VALUE;prediction.velocity=Vec3.ZERO;prediction.remainder=Vec3.ZERO;prediction.travelMaterial="";
                prediction.bindingOrigin=null;prediction.bindingMaterial="";prediction.jump=new AdhesiveMotion.Jump();
            }
        }
        var gravity=living.getAttribute(Attributes.GRAVITY);
        if(gravity==null)return;
        var state=state(living);
        var prediction=living.level().isClientSide()?CLIENT_PREDICTIONS.get(living):null;
        boolean external=prediction==null?state.externalMotionTicks>0:living.level().getGameTime()<prediction.externalUntil;
        boolean held=isHeldByGlue(living) && !external;
        if(held && !gravity.hasModifier(GLUE_GRAVITY_ID)) {
            gravity.addTransientModifier(new AttributeModifier(GLUE_GRAVITY_ID,-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if(!held)gravity.removeModifier(GLUE_GRAVITY_ID);
    }
    public static void clearGlueGravity(LivingEntity living) {
        var gravity=living.getAttribute(Attributes.GRAVITY);if(gravity!=null)gravity.removeModifier(GLUE_GRAVITY_ID);
    }

    @SubscribeEvent public static void changedDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            AdhesionController.changedDimension(player,player.level().getServer().getLevel(event.getFrom()),player.level());
            removeDrag(player);
            player.syncData(ModAttachmentTypes.SINKING_STATE.get());
        }
    }

    @SubscribeEvent public static void onTick(EntityTickEvent.Post event) {
        var entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity)) return;
        tick(entity, level);
    }

    /** Resolve server intent before aiStep/travel, once, including direct developer tick callers. */
    private static void prepare(LivingEntity living,ServerLevel level) {
        var state=state(living);long time=level.getGameTime();
        if(state.physicsDimension!=null && !state.physicsDimension.equals(level.dimension())) {
            AdhesionController.changedDimension(living,level.getServer().getLevel(state.physicsDimension),level);
            removeDrag(living);
        }
        state.physicsDimension=level.dimension();
        if(state.preparedTick==time)return;
        state.preparedTick=time;
        var contact=AdhesionController.exempt(living)?null:findContact(living,level);
        String previous=state.material;
        boolean previousEyes=state.eyesCovered;
        state.material=contact==null?"":contact.material.id;
        state.depth=contact==null?0:contact.depth;state.eyesCovered=contact!=null && contact.eyesCovered;
        if(state.rescueTicks>0)state.rescueTicks--;
        if(state.externalMotionTicks>0)state.externalMotionTicks--;
        if(contact!=null && (time%32==0 || state.contactTicks==0))state.cachedLoad=inventoryWeight(living);
        AdhesionController.prepare(living,level,contact,contact!=null && materialImmune(living,contact.material));
        if(state.struggleResult!=null)state.totalStruggleSink+=state.struggleResult.sinkDelta();
        if(!previous.equals(state.material) || previousEyes!=state.eyesCovered || state.struggleResult!=null
                && (state.struggleResult.acceptedPress() || state.struggleResult.sinkDelta()>=.01)) {
            living.syncData(ModAttachmentTypes.SINKING_STATE.get());
        }
    }

    /** One call per entity and world tick, independent of how many sinking blocks overlap the body. */
    public static void tick(Entity entity, ServerLevel level) {
        var state = state(entity);
        // Detect direct level changes by other mods as well, before the same-clock early return.
        if(state.physicsDimension!=null && !state.physicsDimension.equals(level.dimension())) {
            if(entity instanceof LivingEntity living) {
                AdhesionController.changedDimension(living,level.getServer().getLevel(state.physicsDimension),level);
                removeDrag(living);
                entity.syncData(ModAttachmentTypes.SINKING_STATE.get());
            } else state.lastTick=Long.MIN_VALUE;
        }
        state.physicsDimension=level.dimension();
        long time = level.getGameTime();
        if (state.lastTick == time) return;
        state.lastTick = time;
        if(entity instanceof LivingEntity living)prepare(living,level);
        boolean previousEye = state.eyesCovered;
        String previousMaterial = state.material;
        var contact = findContact(entity, level);
        if (entity instanceof LivingEntity living && AdhesionController.exempt(living)) contact = null;
        state.material = contact == null ? "" : contact.material.id;
        state.depth = contact == null ? 0 : contact.depth;
        state.eyesCovered = contact != null && contact.eyesCovered;
        state.contactTicks = contact == null ? 0 : state.contactTicks + 1;

        if (entity instanceof LivingEntity living) {
            boolean immune = contact != null && materialImmune(living, contact.material);
            // Preparation ran before native travel; a board has no SinkingMaterial contact.
            if(contact==null && AdhesionController.boardUnder(living,level)!=null) {state.material="sticky_board";state.depth=.08;}
            boolean buffered = living instanceof Player ? ModConfig.SERVER.realisticSuffocation.get() : ModConfig.SERVER.realisticMobSuffocation.get();
            boolean spendsAir = contact != null && contact.airImmersion && !immune;
            state.air = SinkingMotion.nextAir(state.air, spendsAir);
            if (spendsAir && (!buffered || state.air < 0) && time % 16 == 0 && !invulnerablePlayer(living)) {
                damage(living, level, ModDamageTypes.QUICKSAND_SUFFOCATION, Math.max(living.getMaxHealth() * .1f, 2));
            }
            if (contact != null && !immune) {
                if(state.nativeTravelTick!=time)applyMotion(living, contact, state, level);
            }
            else removeDrag(living);
            environmentalContact(living, level);
            if (contact != null && !immune) {
                updateCoating(living, contact, state);
                contactDamage(living, contact, state, level);
                terrainContact(living, contact, state, level);
                visualEntities(living, contact, state, level);
            }
            washCoating(living, contact, state, time);
            AdhesionController.finish(living,level);
        } else if (contact != null) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x * .6, Math.max(-.055, motion.y * .2 - .012), motion.z * .6);
            entity.fallDistance = 0;
            if (contact.material == SinkingMaterial.ACID || contact.material == SinkingMaterial.LARVAE || contact.material == SinkingMaterial.SWALLOWING_FLESH)
                if (state.contactTicks > 200) entity.discard();
        }
        state.previousYaw = entity.getYRot();
        state.previousY = entity.getY();
        if (entity instanceof LivingEntity && (time % 5 == 0 || previousEye != state.eyesCovered || !previousMaterial.equals(state.material))) {
            entity.syncData(ModAttachmentTypes.SINKING_STATE.get());
        }
    }

    public record Contact(SinkingMaterial material, BlockPos pos, BlockState block, double surface, double depth, boolean eyesCovered, boolean airImmersion) {}
    public static Contact findContact(Entity entity, net.minecraft.world.level.Level level) {
        AABB box = entity.getBoundingBox().deflate(.001);
        var eyeState = immersedEyeBlock(entity);
        var eyeMaterial = SinkingMaterial.byId(id(eyeState));
        boolean eyeCovered = eyeMaterial != null;
        Contact best = null;
        int count = 0;
        for (var cursor : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY) + 1, Mth.floor(box.maxZ))) {
            if (++count > 256) break;
            if (!level.hasChunkAt(cursor)) continue;
            var block = level.getBlockState(cursor);
            String id = id(block);
            var material = SinkingMaterial.byId(id);
            if (material == null) continue;
            double bottom = cursor.getY() + solidBottom(block);
            double localSurface = surface(level, cursor, block);
            if (box.minY >= localSurface || box.maxY <= bottom) continue;
            var top = cursor.immutable();
            // Find the actual connected surface. Full cubes below a fluid/other medium are immersed too.
            for (int up = 0; up < 8 && top.getY() < level.getMaxY(); up++) {
                var next = top.above();
                if (!level.hasChunkAt(next) || SinkingMaterial.byId(id(level.getBlockState(next))) != material
                        || next.getY() + solidBottom(level.getBlockState(next)) > surface(level, top, level.getBlockState(top)) + .015) break;
                top = next;
            }
            var topState = level.getBlockState(top);
            double surface = surface(level, top, topState);
            double depth = Math.max(0, surface - entity.getY());
            if (best == null || depth > best.depth) best = new Contact(material, cursor.immutable(), block, surface, depth, eyeCovered, eyeCovered && eyeMaterial.usesAir);
        }
        return best;
    }
    public static String id(BlockState state) {
        if (state.getBlock() instanceof LegacyStateBlock block) return block.legacyId;
        if (state.getBlock() instanceof SinkingLiquidBlock block) return block.legacyId;
        return "";
    }
    private static double solidHeight(BlockState state) {
        String id = id(state);
        if (id.equals("tangleroot_moss")) return LegacyStateBlock.mossTop(state);
        return 1;
    }
    private static double solidBottom(BlockState state) {
        return id(state).equals("tangleroot_moss") ? LegacyStateBlock.mossBottom(state) : 0;
    }
    private static double surface(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return pos.getY() + (state.getFluidState().isEmpty() ? solidHeight(state) : state.getFluidState().getHeight(level, pos));
    }
    /** Hanging moss occupies part of the cell below its anchor, on both logical sides. */
    public static BlockState immersedEyeBlock(Entity entity) {
        BlockPos eyes = BlockPos.containing(entity.getEyePosition());
        for (int up = 0; up <= 1; up++) {
            BlockPos at = eyes.above(up);
            var block = entity.level().getBlockState(at);
            if (SinkingMaterial.byId(id(block)) != null && entity.getEyeY() >= at.getY() + solidBottom(block)
                    && entity.getEyeY() < surface(entity.level(), at, block) - .015) return block;
        }
        return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    private record Motion(SinkingMotion.Result result,double sink,boolean boots) {}
    private static Motion motion(LivingEntity living,Contact contact,SinkingState state,double extraSink,Vec3 input) {
        var level=living.level();
        Vec3 movement = living.position().subtract(new Vec3(living.xo, living.yo, living.zo));
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        // Before move(), this tick's displacement is still zero. Include the small,
        // viscosity-limited intended step rather than treating raw W as a full block.
        if(input!=null)horizontal=Math.max(horizontal,input.horizontalDistance()*.02*contact.material.motion.shallowMobility());
        if (Math.abs(Mth.wrapDegrees(living.getYRot() - state.previousYaw)) > 10) horizontal += .045;
        boolean freshInput = !level.isClientSide() && state.inputTick != Long.MIN_VALUE && level.getGameTime() - state.inputTick >= 0 && level.getGameTime() - state.inputTick < 10;
        boolean jump = freshInput ? state.jumpInput : living.isJumping() || movement.y > .15;
        boolean sneak = freshInput ? state.sneakInput : living.isShiftKeyDown();
        boolean boots = contact.material != SinkingMaterial.GLUE && hasWadingBoots(living);
        if (contact.material == SinkingMaterial.MIRE && MediumReactions.variant(contact.block) >= 6) boots = false;
        boolean jacket = contact.material != SinkingMaterial.GLUE && hasLifeJacket(living);
        var resistance = LegacyResistance.forMaterial(contact.material.id, MediumReactions.variant(contact.block));
        var result = SinkingMotion.calculate(contact.material.motion, resistance, living instanceof Player ? 1.62 : 1.5,
                contact.depth, horizontal, jump, sneak, state.cachedLoad, boots, jacket);
        double sink = result.downwardSpeed();
        sink+=extraSink;
        // Glue has no passive suction, load sink or flotation. Its accepted struggle delta is authoritative.
        if(contact.material==SinkingMaterial.GLUE)sink=extraSink;
        // A still dry surface supports a shallow body; movement and jumping progressively rupture it.
        boolean crust = (contact.material == SinkingMaterial.MIRE || contact.material == SinkingMaterial.SINKING_CLAY)
                && MediumReactions.variant(contact.block) < 4 && contact.depth < .35;
        if (crust && state.cachedLoad < .5 && !jump) sink = contact.depth > .12 ? -.01 : .002;
        boolean stick = living instanceof Player player && player.isUsingItem()
                && itemId(player.getUseItem()).equals("long_stick") && player.getFoodData().getFoodLevel() > 6;
        if (stick && contact.depth < 2.5) sink -= .022;
        return new Motion(result,sink,boots);
    }

    /** Called at LivingEntity.travel HEAD on both sides, before any water/lava displacement. */
    public static boolean travel(LivingEntity living,Vec3 input) {
        if(!living.isAlive() || living.isPassenger() || !living.canSimulateMovement() || AdhesionController.exempt(living))return false;
        var contact=findContact(living,living.level());
        var state=state(living);
        var board=contact==null?AdhesionController.boardUnder(living,living.level()):null;
        boolean releasedBoard=board!=null && state.boardReleased && board.equals(state.episodeBoard)
                && AdhesionController.charge(living.level().getBlockState(board))<=state.previousBoardCharge;
        String binding=contact!=null && AdhesionController.profile(contact.material.id)!=null?contact.material.id:
                board!=null && !releasedBoard?"sticky_board":state.adhesiveConnections>0?state.adhesiveMaterial:"";
        if(!ModConfig.SERVER.adhesiveBonds.get())binding="";
        if(binding.equals("tar") && !ModConfig.SERVER.tarTreads.get())binding="";
        if(binding.equals("sticky_board") && board==null && (state.episodeBoard==null
                || !living.level().hasChunkAt(state.episodeBoard)
                || !com.mfqm.morefunquicksandmod.block.StickyBoardBlock.isCoated(living.level().getBlockState(state.episodeBoard))))binding="";
        if(contact==null && binding.isEmpty() || contact!=null && materialImmune(living,contact.material))return false;
        // Connection.tickPlayer calls ServerPlayer.doTick after the world's Post
        // event, then restores its position. It must not simulate or damp a second
        // time: this player receives one server fallback plus local prediction.
        if(living instanceof ServerPlayer)return true;
        long tick=living.level().getGameTime();
        boolean client=living.level().isClientSide();
        double extra=0;boolean rescued=state.rescueTicks>0,external=state.externalMotionTicks>0;
        Prediction prediction=null;
        if(client) {
            prediction=CLIENT_PREDICTIONS.get(living);
            if(prediction==null || prediction.level.get()!=living.level() || tick<prediction.clock) {
                prediction=new Prediction(living,state);CLIENT_PREDICTIONS.put(living,prediction);
            }
            prediction.observe(state,tick);
            String material=contact==null?binding:contact.material.id;
            if(!prediction.material.isEmpty() && !prediction.material.equals(material))prediction.sink.discard(state.totalStruggleSink);
            prediction.material=material;
            state.previousYaw=prediction.previousYaw;prediction.previousYaw=living.getYRot();
            extra=prediction.consumeSink(state);rescued=tick<prediction.rescueUntil;
            external=tick<prediction.externalUntil;
        } else {
            if(living.level() instanceof ServerLevel level)prepare(living,level);
            if(state.nativeTravelTick!=tick && state.struggleResult!=null)extra=state.struggleResult.sinkDelta();
        }
        Vec3 bindingOrigin=null;
        if(!binding.isEmpty()) {
            bindingOrigin=state.adhesiveOrigin;
            if(client) {
                if(!prediction.bindingMaterial.equals(binding)) {
                    prediction.bindingMaterial=binding;prediction.bindingOrigin=living.position();prediction.jump=new AdhesiveMotion.Jump();
                }
                if(bindingOrigin!=null)prediction.bindingOrigin=bindingOrigin;
                bindingOrigin=prediction.bindingOrigin;
            } else if(bindingOrigin==null)bindingOrigin=living.position();
        }
        String travelMaterial=contact==null?binding:contact.material.id;
        long previousTravel=client?prediction.travelTick:state.nativeTravelTick;
        String previousTravelMaterial=client?prediction.travelMaterial:state.travelMaterial;
        Vec3 previousVelocity=client?prediction.velocity:state.lowSpeedVelocity;
        Vec3 remainder=client?prediction.remainder:state.motionRemainder;
        boolean continuing=previousTravel!=Long.MIN_VALUE && tick-previousTravel==1 && travelMaterial.equals(previousTravelMaterial);
        if(!continuing)remainder=Vec3.ZERO;
        var motion=contact==null?new Motion(new SinkingMotion.Result(0,1,.55),0,false):motion(living,contact,state,extra,input);
        Vec3 velocity=living.getDeltaMovement();
        // aiStep discards velocity below .003 before calling travel. Restore only
        // our own consecutive, sub-threshold viscous momentum; real impulse windows
        // always use the newly received authoritative velocity instead.
        if(continuing && !rescued && !external) {
            if(living instanceof Player) {
                if(velocity.horizontalDistanceSqr()==0 && previousVelocity.horizontalDistanceSqr()<9e-6)
                    velocity=new Vec3(previousVelocity.x,velocity.y,previousVelocity.z);
            } else {
                velocity=new Vec3(velocity.x==0 && Math.abs(previousVelocity.x)<.003?previousVelocity.x:velocity.x,
                        velocity.y,velocity.z==0 && Math.abs(previousVelocity.z)<.003?previousVelocity.z:velocity.z);
            }
        }
        double retention=rescued || external?.7:motion.result.momentumRetention();
        if(!binding.isEmpty() && !rescued && !external)retention=.35;
        double vertical=-motion.sink;
        if(rescued)vertical=Math.max(velocity.y,Math.max(.025,vertical));
        else if(external)vertical=velocity.y-living.getGravity();
        else if(contact!=null && contact.material!=SinkingMaterial.GLUE && velocity.y<-.15 && state.contactTicks<4)vertical=Math.max(-.12,velocity.y*.35);
        if(!binding.isEmpty() && !rescued && !external) {
            var jump=client?prediction.jump:state.adhesiveJump;
            // World time is periodically corrected by the server. A hop follows
            // this entity's consecutive simulation ticks, so a clock correction
            // cannot count a held key as a new press or restart its ascent.
            var hop=jump.step(living.tickCount,living.isJumping(),.26/(1+(contact==null?0:contact.depth)*.2));
            if(contact==null)vertical=hop.active()?hop.delta():velocity.y-living.getGravity();
            else if(hop.active())vertical=hop.delta()-Math.max(0,extra);
        }
        // rescue()/externalImpulse() send their preservation window before the
        // matching velocity packet on the ordered connection. Treating every
        // positive velocity as a pull would turn a short knockback into flight.
        var force=state.adhesiveConnections>0 && state.adhesiveMaterial.equals(binding)?state.adhesiveForce:Vec3.ZERO;
        // A finite hop owns its vertical displacement; adhesive pull constrains
        // the feet horizontally rather than stretching the arc into a hover.
        if(!binding.isEmpty())force=new Vec3(force.x,0,force.z);
        // Dampen existing drift once before this tick's displacement. Resetting it
        // in Post lets ordinary water acceleration move first and bypass viscosity.
        living.setDeltaMovement(velocity.x*retention,vertical,velocity.z*retention);
        double inputScale=binding.isEmpty()?motion.result.horizontalScale():AdhesiveMotion.inputScale(binding,contact==null?0:contact.depth,state.adhesiveStrength);
        float acceleration=(float)(.02*movementSpeedWithoutDrag(living)/.1*inputScale);
        living.moveRelative(acceleration,new Vec3(input.x,0,input.z));
        living.setDeltaMovement(living.getDeltaMovement().add(force));
        Vec3 requested=living.getDeltaMovement().add(remainder.x,0,remainder.z);
        if(bindingOrigin!=null && !rescued && !external) {
            double range=AdhesiveMotion.radius(AdhesionController.activityRadius(binding),AdhesionController.profile(binding).maxDistance(),state.adhesiveStrength);
            var allowed=AdhesiveMotion.limit(point(bindingOrigin),point(living.position()),point(requested),range);
            Vec3 bounded=new Vec3(allowed.x(),allowed.y(),allowed.z());
            if(bounded.subtract(requested).horizontalDistanceSqr()>1e-14) {
                remainder=Vec3.ZERO;var speed=living.getDeltaMovement();living.setDeltaMovement(bounded.x,speed.y,bounded.z);
            }
            requested=bounded;
        }
        // Entity.move skips an entire tiny step when its sinking Y collides with
        // the pool floor. Batch only the sub-pixel horizontal remainder, preserving
        // the model's mean speed and vanilla collision/step handling.
        if(requested.horizontalDistanceSqr()>0 && requested.horizontalDistanceSqr()<1.6e-7) {
            remainder=new Vec3(requested.x,0,requested.z);
            living.move(MoverType.SELF,new Vec3(0,requested.y,0));
        } else {
            remainder=Vec3.ZERO;living.move(MoverType.SELF,requested);
        }
        // Glue costs are displacements, not persistent velocities to replay while resting.
        if(contact!=null && contact.material==SinkingMaterial.GLUE && !rescued && !external) {
            var after=living.getDeltaMovement();living.setDeltaMovement(after.x,0,after.z);
        }
        living.fallDistance=0;
        if(contact!=null && motion.boots && contact.depth<contact.material.motion.bootLimit())living.setOnGround(true);
        if(client) {
            prediction.travelTick=tick;prediction.travelMaterial=travelMaterial;
            prediction.velocity=living.getDeltaMovement();prediction.remainder=remainder;
        } else {
            state.nativeTravelTick=tick;state.travelMaterial=travelMaterial;
            state.lowSpeedVelocity=living.getDeltaMovement();state.motionRemainder=remainder;
        }
        return true;
    }
    private static AdhesiveMotion.Point point(Vec3 value){return new AdhesiveMotion.Point(value.x,value.y,value.z);}

    /** Ordinary air/water travel continues after leaving the edge; surviving bonds still pull. */
    public static void predictOutsidePull(LivingEntity living) {
        if(!living.level().isClientSide() || !living.canSimulateMovement() || living.isPassenger()
                || AdhesionController.exempt(living) || findContact(living,living.level())!=null)return;
        var state=state(living);
        if(state.adhesiveConnections>0 && state.adhesiveForce.lengthSqr()>0) {
            living.setDeltaMovement(living.getDeltaMovement().add(state.adhesiveForce));
        }
    }

    private static double movementSpeedWithoutDrag(LivingEntity living) {
        var speed=living.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed==null)return .1;
        var drag=speed.getModifier(DRAG_ID);
        return speed.getValue()/(drag==null?1:Math.max(.005,1+drag.amount()));
    }

    private static void applyMotion(LivingEntity living, Contact contact, SinkingState state, ServerLevel level) {
        if (AdhesionController.exempt(living)) { removeDrag(living); return; }
        var calculated=motion(living,contact,state,state.struggleResult==null?0:state.struggleResult.sinkDelta(),null);
        var result=calculated.result;
        double sink=state.rescueTicks>0?-.025:calculated.sink;
        Vec3 velocity = living.getDeltaMovement();
        double vertical = -sink;
        if (contact.material!=SinkingMaterial.GLUE && velocity.y < -.15 && state.contactTicks < 4) vertical = Math.max(-.12, velocity.y * .35);
        if (state.rescueTicks > 0) vertical = Math.max(velocity.y, vertical);
        else if(state.externalMotionTicks>0)vertical=velocity.y-living.getGravity();
        // Native travel controls input acceleration; this fallback only damps momentum.
        // Rescue pulls must retain horizontal movement even in media that normally stop drift.
        double retention = state.rescueTicks > 0 || state.externalMotionTicks>0 ? .7 : result.momentumRetention();
        living.setDeltaMovement(velocity.x * retention, vertical, velocity.z * retention);
        living.fallDistance = 0;
        if (calculated.boots && contact.depth < contact.material.motion.bootLimit()) living.setOnGround(true);
        // LocalPlayer predicts ordinary viscosity and consumes synchronized costs.
        // Sending our zero/input-less server delta every tick would erase its input
        // and replay the already synchronized adhesive force. Genuine pulls/damage
        // mark their own velocity packets.
        if (!(living instanceof ServerPlayer)) living.hurtMarked = true;
        removeDrag(living);
    }
    private static void removeDrag(LivingEntity living) {
        var speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(DRAG_ID);
    }
    private static boolean invulnerablePlayer(LivingEntity living) {
        return living instanceof Player player && (player.isCreative() || player.isSpectator());
    }
    private static boolean materialImmune(LivingEntity living, SinkingMaterial material) {
        if (material == SinkingMaterial.DENSE_WEB && living instanceof net.minecraft.world.entity.monster.spider.Spider) return true;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        if (!id.getNamespace().equals(MFQM.MOD_ID)) return false;
        return switch (id.getPath()) {
            case "sand_blob" -> material == SinkingMaterial.QUICKSAND || material == SinkingMaterial.DRY_QUICKSAND || material == SinkingMaterial.SOFT_QUICKSAND;
            case "muddy_blob" -> material == SinkingMaterial.MUD || material == SinkingMaterial.MIRE || material == SinkingMaterial.MOOR || material == SinkingMaterial.BOG || material == SinkingMaterial.LIQUID_MIRE || material == SinkingMaterial.STABLE_LIQUID_MIRE || material == SinkingMaterial.SINKY_LIQUID;
            case "tar_slime" -> material == SinkingMaterial.TAR;
            case "vore_slime" -> material == SinkingMaterial.SINKING_SLIME || material == SinkingMaterial.MUCUS || material == SinkingMaterial.SWALLOWING_FLESH;
            default -> false;
        };
    }
    public static boolean hasWadingBoots(LivingEntity entity) {
        var boots = entity.getItemBySlot(EquipmentSlot.FEET);
        String id = itemId(boots);
        return boots.is(WADING_BOOTS) || id.equals("wading_boots");
    }
    public static boolean hasLifeJacket(LivingEntity entity) {
        var chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return chest.is(LIFE_JACKETS) || itemId(chest).equals("life_jacket");
    }
    public static boolean breathProtected(LivingEntity entity) {
        var helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(GAS_MASKS) || itemId(helmet).equals("gas_mask");
    }
    private static String itemId(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(MFQM.MOD_ID) ? id.getPath() : "";
    }
    private static double inventoryWeight(LivingEntity entity) {
        double load = 0;
        if (ModConfig.SERVER.realisticArmor.get()) for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var item = entity.getItemBySlot(slot);
            if (item.isEmpty() || itemId(item).equals("wading_boots") || itemId(item).equals("life_jacket")) continue;
            var id = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
            load += item.is(HEAVY_EQUIPMENT) ? .7 : id.contains("netherite") || id.contains("iron") || id.contains("gold") ? .45 : .15;
        }
        if (ModConfig.SERVER.weightCalc.get() && entity instanceof Player player) {
            for (int slot = 0; slot < 36; slot++) {
                var item = player.getInventory().getItem(slot);
                if (item.isEmpty()) continue;
                double coefficient = item.getItem() instanceof BlockItem ? .012 : item.isDamageableItem() ? .009 : .003;
                if (item.is(HEAVY_EQUIPMENT)) coefficient *= 3;
                load += item.getCount() * coefficient;
            }
        }
        if (ModConfig.SERVER.realisticBoots.get()) {
            var boots = entity.getItemBySlot(EquipmentSlot.FEET);
            if (!boots.isEmpty() && !hasWadingBoots(entity)) load += .12;
        }
        return Math.min(4, load);
    }

    private static void updateCoating(LivingEntity living, Contact contact, SinkingState state) {
        if (!(living instanceof Player) || contact.material == SinkingMaterial.ACID || contact.material == SinkingMaterial.DENSE_WEB || contact.material == SinkingMaterial.SOFT_SNOW || contact.material == SinkingMaterial.SINKING_RUG) return;
        // Crouching changes the collision height, not the skin's leg/torso UV.
        int level = SinkingMotion.coatingLevel(contact.depth, living.getDimensions(net.minecraft.world.entity.Pose.STANDING).height());
        if (level >= state.coatingLevel || state.coatingTicks < 40) {
            state.coatingLevel = level;
            state.coatingType = contact.material.id;
        }
        state.coatingTicks = Math.max(state.coatingTicks, 1200 + level * 120);
    }
    private static void washCoating(LivingEntity living, Contact contact, SinkingState state, long time) {
        if (state.coatingLevel <= 0) return;
        boolean mire = contact != null && (contact.material == SinkingMaterial.LIQUID_MIRE || contact.material == SinkingMaterial.STABLE_LIQUID_MIRE);
        if (living.isInWaterOrRain() && !mire) {
            state.coatingTicks = Math.max(0, state.coatingTicks - 40);
            if (time % 16 == 0) state.coatingLevel--;
        } else if (contact == null) {
            if (state.coatingTicks > 0) state.coatingTicks--;
            else if (time % 40 == 0) state.coatingLevel--;
        }
        if (state.coatingLevel <= 0) { state.coatingType = ""; state.coatingTicks = 0; }
        if (contact == null && state.coatingType.equals("tar") && ModConfig.SERVER.tarTreads.get()) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 0, false, false));
        }
    }

    private static void contactDamage(LivingEntity living, Contact contact, SinkingState state, ServerLevel level) {
        if (invulnerablePlayer(living)) return;
        long tick = level.getGameTime();
        switch (contact.material) {
            case ACID -> { if (tick % 10 == 0) damage(living, level, ModDamageTypes.ACID_DISSOLVE, 2); }
            case TAR -> { if (ModConfig.SERVER.hotTar.get() && tick % 20 == 0) damage(living, level, ModDamageTypes.TAR_BURN, 1); }
            case MUCUS, LARVAE -> { if (tick % 20 == 0 && contact.depth > .3) damage(living, level, ModDamageTypes.FLESH_CONSUMPTION, 1); }
            case VORE_HOLE, MEAT_HOLE -> {
                living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                if (state.contactTicks > 60 && tick % 16 == 0) damage(living, level, ModDamageTypes.FLESH_CONSUMPTION, 2);
            }
            case SWALLOWING_FLESH, CORRUPTED_SAND -> { if (contact.eyesCovered && state.air < 0 && tick % 16 == 0) damage(living, level, ModDamageTypes.FLESH_CONSUMPTION, 1); }
            case LIQUID_MIRE, STABLE_LIQUID_MIRE -> {
                if (contact.eyesCovered && living.canBreatheUnderwater() && tick % 16 == 0) damage(living, level, ModDamageTypes.QUICKSAND_SUFFOCATION, 2);
            }
            case SINKY_LIQUID -> { if (tick % 40 == 0) living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0)); }
            case SLURRY -> {
                if (ModConfig.SERVER.gasSlurry.get() && !breathProtected(living) && tick % 40 == 0) {
                    living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
                    living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1));
                    if (contact.eyesCovered) { living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0)); living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0)); }
                }
            }
            case BROWN_CLAY -> {
                if (MediumReactions.variant(contact.block) >= 4 && contact.depth > .5 && tick % 40 == 0) {
                    living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
                    living.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0));
                }
            }
            default -> {}
        }
    }
    private static void damage(LivingEntity living, ServerLevel level, ResourceKey<DamageType> type, float amount) {
        var source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type));
        living.hurtServer(level, source, amount * ModConfig.SERVER.damageMultiplier.get().floatValue());
    }
    private static void terrainContact(LivingEntity living, Contact contact, SinkingState state, ServerLevel level) {
        int variant = MediumReactions.variant(contact.block);
        if ((contact.material == SinkingMaterial.MIRE || contact.material == SinkingMaterial.SINKING_CLAY) && variant < 4 && state.contactTicks % 10 == 0) {
            double moving = living.position().distanceToSqr(living.xo, living.yo, living.zo);
            if (moving > .001 || state.cachedLoad > .5 || living.isJumping()) MediumReactions.soften(level, contact.pos, contact.material.id, Math.min(7, variant + 1));
        }
        if (contact.material == SinkingMaterial.MORASS && state.contactTicks > 20 && variant >= 2) level.setBlock(contact.pos, MediumReactions.block("mire", 6), 3);
        if (contact.material == SinkingMaterial.SOFT_GRAVEL) {
            Vec3 velocity = living.getDeltaMovement();
            for (var direction : Direction.Plane.HORIZONTAL) if (level.isEmptyBlock(contact.pos.relative(direction)) && living.position().distanceToSqr(Vec3.atCenterOf(contact.pos.relative(direction))) < 1) {
                living.setDeltaMovement(velocity.add(direction.getStepX() * .045, -.015, direction.getStepZ() * .045));
                break;
            }
        }
    }
    private static void environmentalContact(LivingEntity living, ServerLevel level) {
        AABB box = living.getBoundingBox().deflate(.001);
        for (var cursor : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var block = level.getBlockState(cursor);
            String id = id(block);
            if (id.equals("gas") && ModConfig.SERVER.gasSlurry.get() && !breathProtected(living) && !invulnerablePlayer(living)) {
                int concentration = MediumReactions.variant(block);
                if (level.getGameTime() % 80 == concentration * 5) {
                    living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 1));
                    living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
                    if (concentration >= 4) living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
                    if (concentration >= 6) living.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 1));
                    if (concentration >= 8) living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    if (concentration >= 10) living.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                    if (concentration >= 12) living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                    if (concentration >= 14) living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                }
                break;
            } else if (id.equals("tendrils")) {
                living.setDeltaMovement(living.getDeltaMovement().multiply(.12, .2, .12));
            } else if (id.equals("blossom_slab") && !hasWadingBoots(living)) {
                Vec3 velocity = living.getDeltaMovement();
                living.setDeltaMovement(switch (MediumReactions.variant(block)) {
                    case 1 -> new Vec3(velocity.x, velocity.y, -.15);
                    case 2 -> new Vec3(.15, velocity.y, velocity.z);
                    case 3 -> new Vec3(velocity.x, velocity.y, .15);
                    case 4 -> new Vec3(-.15, velocity.y, velocity.z);
                    default -> velocity;
                });
            }
        }
    }
    private static void visualEntities(LivingEntity living, Contact contact, SinkingState state, ServerLevel level) {
        long time = level.getGameTime();
        if (ModConfig.SERVER.bubblesOnSurface.get() && time % 64 == 0 && level.random.nextInt(3) == 0) {
            ModEntities.spawnEffect(level, "bubble", new Vec3(living.getX(), contact.surface, living.getZ()), contact.block, living, 30);
        }
        // Foot membranes and strands are generated by the bounded adhesion controller.
        if (contact.material == SinkingMaterial.SINKING_SLIME && time % 48 == 0 && contact.depth > .3) {
            ModEntities.spawnEffect(level, "slime_hole", new Vec3(living.getX(), contact.surface, living.getZ()), contact.block, living, 48);
        }
        if (living instanceof Player && time % 80 == 0 && contact.depth > .5 && !invulnerablePlayer(living)) {
            boolean flesh = com.mfqm.morefunquicksandmod.entity.TentacleEntity.acceptsPit(contact.material.id, false);
            boolean mud = com.mfqm.morefunquicksandmod.entity.TentacleEntity.acceptsPit(contact.material.id, true);
            if (level.random.nextInt(flesh ? 18 : 30) == 0 && (flesh && ModConfig.SERVER.tentaclesInFlesh.get() || mud && ModConfig.SERVER.mudTentacles.get())) {
                ModEntities.spawnTentacle(living, BlockPos.containing(living.getX(), contact.surface - .05, living.getZ()), !flesh);
            }
        }
    }
    @SubscribeEvent public static void handRescue(PlayerInteractEvent.EntityInteract event) {
        if (!ModConfig.SERVER.enableHandRescue.get() || !event.getItemStack().isEmpty()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)) return;
        if (!(target instanceof Player) && !player.isShiftKeyDown()) return;
        if (player.distanceToSqr(target) > 6.25 || !player.hasLineOfSight(target)) return;
        if (ModEntities.startHandRescue(player, target)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
