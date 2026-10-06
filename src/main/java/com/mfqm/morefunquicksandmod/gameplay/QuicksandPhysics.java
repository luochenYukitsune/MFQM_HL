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

/** Server authority for physical contact, oxygen, equipment, coating, and validated rescue. */
@EventBusSubscriber(modid = MFQM.MOD_ID)
public final class QuicksandPhysics {
    private static final Identifier DRAG_ID = Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "quicksand_drag");
    private static final TagKey<Item> WADING_BOOTS = itemTag("wading_boots");
    private static final TagKey<Item> LIFE_JACKETS = itemTag("life_jackets");
    private static final TagKey<Item> GAS_MASKS = itemTag("gas_masks");
    private static final TagKey<Item> HEAVY_EQUIPMENT = itemTag("heavy_equipment");
    private QuicksandPhysics() {}

    private static TagKey<Item> itemTag(String id) { return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MFQM.MOD_ID, id)); }
    public static SinkingState state(Entity entity) { return entity.getData(ModAttachmentTypes.SINKING_STATE.get()); }
    public static boolean isSinking(Entity entity) { return !state(entity).material.isEmpty(); }
    public static void setInput(ServerPlayer player, boolean jump, boolean sneak) {
        var state = state(player);
        state.jumpInput = jump;
        state.sneakInput = sneak;
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
        state(target).rescueTicks = 6;
    }
    public static void applyRescue(Entity target, double lift) { rescue(target, target.position().add(0, 2, 0), lift); }

    @SubscribeEvent public static void onTick(EntityTickEvent.Post event) {
        var entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity)) return;
        tick(entity, level);
    }

    /** One call per entity and world tick, independent of how many sinking blocks overlap the body. */
    public static void tick(Entity entity, ServerLevel level) {
        var state = state(entity);
        long time = level.getGameTime();
        if (state.lastTick == time) return;
        state.lastTick = time;
        boolean previousEye = state.eyesCovered;
        String previousMaterial = state.material;
        var contact = findContact(entity, level);
        if (entity instanceof Player player && (player.isSpectator() || player.getAbilities().flying)) contact = null;
        state.material = contact == null ? "" : contact.material.id;
        state.depth = contact == null ? 0 : contact.depth;
        state.eyesCovered = contact != null && contact.eyesCovered;
        state.contactTicks = contact == null ? 0 : state.contactTicks + 1;
        if (state.rescueTicks > 0) state.rescueTicks--;

        if (entity instanceof LivingEntity living) {
            boolean immune = contact != null && materialImmune(living, contact.material);
            boolean buffered = living instanceof Player ? ModConfig.SERVER.realisticSuffocation.get() : ModConfig.SERVER.realisticMobSuffocation.get();
            boolean spendsAir = contact != null && contact.airImmersion && !immune;
            state.air = SinkingMotion.nextAir(state.air, spendsAir);
            if (spendsAir && (!buffered || state.air < 0) && time % 16 == 0 && !invulnerablePlayer(living)) {
                damage(living, level, ModDamageTypes.QUICKSAND_SUFFOCATION, Math.max(living.getMaxHealth() * .1f, 2));
            }
            if (contact != null && !immune) applyMotion(living, contact, state, level);
            else removeDrag(living);
            environmentalContact(living, level);
            if (contact != null && !immune) {
                updateCoating(living, contact, state);
                contactDamage(living, contact, state, level);
                terrainContact(living, contact, state, level);
                visualEntities(living, contact, state, level);
            }
            washCoating(living, contact, state, time);
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
    public static Contact findContact(Entity entity, ServerLevel level) {
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
                if (!level.hasChunkAt(next) || SinkingMaterial.byId(id(level.getBlockState(next))) == null
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

    private static void applyMotion(LivingEntity living, Contact contact, SinkingState state, ServerLevel level) {
        if (invulnerablePlayer(living)) { removeDrag(living); return; }
        if (level.getGameTime() % 32 == 0 || state.contactTicks == 1) state.cachedLoad = inventoryWeight(living);
        Vec3 movement = living.position().subtract(new Vec3(living.xo, living.yo, living.zo));
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (Math.abs(Mth.wrapDegrees(living.getYRot() - state.previousYaw)) > 10) horizontal += .045;
        boolean freshInput = state.inputTick != Long.MIN_VALUE && level.getGameTime() - state.inputTick >= 0 && level.getGameTime() - state.inputTick < 10;
        boolean jump = freshInput ? state.jumpInput : living.isJumping() || movement.y > .15;
        boolean sneak = freshInput ? state.sneakInput : living.isShiftKeyDown();
        boolean boots = hasWadingBoots(living);
        if (contact.material == SinkingMaterial.MIRE && MediumReactions.variant(contact.block) >= 6) boots = false;
        boolean jacket = hasLifeJacket(living);
        var result = SinkingMotion.calculate(contact.material.motion, contact.depth, horizontal, jump, sneak, state.cachedLoad, boots, jacket);
        double sink = result.downwardSpeed();
        // A still dry surface supports a shallow body; movement and jumping progressively rupture it.
        boolean crust = (contact.material == SinkingMaterial.MIRE || contact.material == SinkingMaterial.SINKING_CLAY)
                && MediumReactions.variant(contact.block) < 4 && contact.depth < .35;
        if (crust && state.cachedLoad < .5 && !jump) sink = contact.depth > .12 ? -.01 : .002;
        boolean stick = living instanceof Player player && player.isUsingItem()
                && itemId(player.getUseItem()).equals("long_stick") && player.getFoodData().getFoodLevel() > 6;
        if (stick && contact.depth < 2.5) sink -= .022;
        if (state.rescueTicks > 0) sink = -.025;
        Vec3 velocity = living.getDeltaMovement();
        double vertical = -sink;
        if (velocity.y < -.15 && state.contactTicks < 4) vertical = Math.max(-.12, velocity.y * .35);
        if (state.rescueTicks > 0) vertical = Math.max(velocity.y, vertical);
        living.setDeltaMovement(velocity.x * result.horizontalScale(), vertical, velocity.z * result.horizontalScale());
        living.fallDistance = 0;
        if (boots && contact.depth < contact.material.motion.bootLimit()) living.setOnGround(true);
        if (living instanceof ServerPlayer) living.hurtMarked = true;
        var speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(DRAG_ID);
            speed.addTransientModifier(new AttributeModifier(DRAG_ID, result.horizontalScale() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
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
        int level = SinkingMotion.coatingLevel(contact.depth, living.getBbHeight());
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
        if (contact.material == SinkingMaterial.TAR && ModConfig.SERVER.tarTreads.get() && time % 40 == 0) {
            ModEntities.spawnEffect(level, "tar_treads", new Vec3(living.getX(), contact.surface, living.getZ()), contact.block, living, 40);
        }
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
