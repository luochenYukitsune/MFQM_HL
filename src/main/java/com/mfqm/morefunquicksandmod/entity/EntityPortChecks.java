package com.mfqm.morefunquicksandmod.entity;

import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import com.mfqm.morefunquicksandmod.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Development-only command checks actual entity callbacks and restores its small probe area. */
public final class EntityPortChecks {
    public static List<String> verify(ServerLevel level,BlockPos origin) {
        List<String> passed=new ArrayList<>();List<Entity> probes=new ArrayList<>();
        Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        Difficulty oldDifficulty=level.getDifficulty();
        AABB area=new AABB(origin).inflate(8);
        var existingItems=level.getEntitiesOfClass(ItemEntity.class,area).stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
        GameProfile profile=new GameProfile(UUID.fromString("0786af38-a8cd-4e11-8c97-c49f129d1a62"),"MFQMEntityProbe");
        ServerPlayer player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        // FakePlayer supplies a packet sink; use a normal ServerPlayer because FakePlayer forbids all mounts.
        player.connection=new FakePlayer(level,profile).connection;
        player.setPos(origin.getX()+.5,origin.getY()+.1,origin.getZ()+.5);
        try {
            require(ModEntities.ENTITIES.getEntries().size()==18,"18 registered entity types");
            for (String id : List.of("vore_slime", "muddy_blob", "sand_blob", "tar_slime", "bee", "tentacles", "mud_tentacles", "bubble", "tar_treads", "slime_hole", "long_stick", "rope", "hook", "rescue", "sinking_potion", "liquid_ball", "adhesive_tether", "stuck_boots"))
                require(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.byId(id)).getPath().equals(id), "exact entity identity " + id);
            for(var holder:ModEntities.ENTITIES.getEntries()){
                Entity entity=holder.get().create(level,EntitySpawnReason.TRIGGERED);require(entity!=null,"create "+holder.getId());probes.add(entity);
                entity.setPos(origin.getX()+.5,origin.getY()+2,origin.getZ()+.5);
                require(level.addFreshEntity(entity),"spawn "+holder.getId());
                if(entity instanceof BlobEntity blob){require(blob.getMaxHealth()==blob.kind().health,"health "+blob.kind());require(blob.getAttributeValue(Attributes.ATTACK_DAMAGE)==blob.kind().damage,"attack "+blob.kind());}
                if(entity instanceof MfqmBeeEntity bee){require(bee.getMaxHealth()==10 && bee.getAttributeValue(Attributes.ATTACK_DAMAGE)==1.25,"legacy bee attributes");}
            }
            passed.add("18 entity types create/spawn with four distinct blob and legacy bee attributes");
            for(int dy=-1;dy<=3;dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                BlockPos pos=origin.offset(dx,dy,dz);require(level.hasChunkAt(pos),"probe loaded");saved.put(pos,level.getBlockState(pos));level.setBlock(pos,dy==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            BlobEntity mud=ModEntities.MUDDY_BLOB.get().create(level,EntitySpawnReason.TRIGGERED);require(mud!=null,"mud blob");probes.add(mud);mud.setPos(player.position());
            require(player.startRiding(mud,true,true),"capture actual player passenger");mud.tick();
            require(player.getVehicle()==mud && mud.swallowDepth()>0,"captured passenger sinks");
            mud.rescuePassenger(player,1);require(player.getVehicle()==null && mud.swallowDepth()==0,"rescue releases captured passenger");
            passed.add("actual player passenger capture, synchronized swallow depth, and rescue release");
            level.getServer().setDifficulty(Difficulty.HARD,true);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WOODEN_SWORD));
            mud.hurtServer(level,level.damageSources().playerAttack(player),1);
            require(player.getMainHandItem().isEmpty() && !mud.removeWhenFarAway(10000),"weak melee weapon stolen and protected from despawn");
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());mud.addAdditionalSaveData(output);
            BlobEntity loaded=ModEntities.MUDDY_BLOB.get().create(level,EntitySpawnReason.LOAD);require(loaded!=null,"load blob");probes.add(loaded);loaded.setPos(player.position());
            loaded.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
            require(!loaded.removeWhenFarAway(10000),"stolen inventory survives Value I/O");
            loaded.invulnerableTime = 0;
            require(loaded.hurtServer(level,level.damageSources().genericKill(),1000), "actual loaded blob death");
            require(level.getEntitiesOfClass(ItemEntity.class,area).stream().anyMatch(item->!existingItems.contains(item.getUUID()) && item.getItem().is(Items.WOODEN_SWORD)),"death returns stolen original tool");
            passed.add("HARD tool theft, persistent inventory, no natural despawn, and death item return");
            var pit=ModBlocks.byId("swallowing_flesh").defaultBlockState();level.setBlock(origin,pit,2);
            TentacleEntity tentacle=ModEntities.TENTACLES.get().create(level,EntitySpawnReason.TRIGGERED);require(tentacle!=null,"tentacle");probes.add(tentacle);tentacle.setPos(Vec3.atCenterOf(origin));tentacle.bind(player);tentacle.tick();
            require(tentacle.extension()>0 && tentacle.victim()==player,"valid pit grows appendage with UUID victim");
            require(tentacle.hurtServer(level,level.damageSources().playerAttack(player),20),"appendage destructible");tentacle.tick();require(tentacle.isRemoved(),"broken appendage retracts");
            passed.add("valid-pit appendage target binding, growth, damage, and retraction");
            for (String medium : List.of("mud", "jungle_quicksand", "soft_quicksand", "swallowing_flesh")) {
                boolean isMud = !medium.equals("swallowing_flesh");
                level.setBlock(origin, ModBlocks.byId(medium).defaultBlockState(), 2);
                ModEntities.spawnTentacle(player, origin, isMud);
                var spawned = level.getEntitiesOfClass(TentacleEntity.class, area).stream().filter(entity -> entity.victim() == player && !entity.isRemoved()).findFirst().orElseThrow();
                probes.add(spawned);
                spawned.tick();
                require(!spawned.isRemoved() && spawned.extension() > 0, medium + " newly spawned tentacle survives first tick");
                player.setDeltaMovement(Vec3.ZERO);
                for (int tick = 0; tick < 10; tick++) spawned.tick();
                require(player.getDeltaMovement().y < 0, medium + " appendage actually pulls victim");
                spawned.discard();
            }
            for (String medium : List.of("mire", "moor", "quicksand", "larvae")) {
                level.setBlock(origin, ModBlocks.byId(medium).defaultBlockState(), 2);
                ModEntities.spawnTentacle(player, origin, !medium.equals("larvae"));
                require(level.getEntitiesOfClass(TentacleEntity.class, area).stream().noneMatch(entity -> entity.victim() == player && !entity.isRemoved()), medium + " rejects unsupported pit");
            }
            passed.add("all four legacy tentacle media survive spawning and pull; unsupported pits reject spawning");
            for(Direction direction:Direction.Plane.HORIZONTAL)level.setBlock(origin.relative(direction),Blocks.AIR.defaultBlockState(),2);level.setBlock(origin,Blocks.AIR.defaultBlockState(),2);
            LiquidProjectileEntity potion=ModEntities.SINKING_POTION.get().create(level,EntitySpawnReason.TRIGGERED);require(potion!=null,"potion");probes.add(potion);potion.setOwner(player);potion.setMedium(ModBlocks.byId("sinky_liquid").defaultBlockState());potion.setPos(Vec3.atCenterOf(origin));
            // Placement levels and terrain fusion have separate checks. Keep this impact probe
            // deterministic by avoiding the 30 possible fusion attempts (five cells x six sides).
            // Seed 86610 was found against LegacyRandomSource's exact nextInt(3) algorithm.
            var noFusion=net.minecraft.util.RandomSource.create(86610L);
            for(int i=0;i<30;i++)require(noFusion.nextInt(3)!=0,"deterministic potion fixture skips terrain fusion");
            level.random.setSeed(86610L);
            potion.onHit(new BlockHitResult(Vec3.atCenterOf(origin),Direction.UP,origin.below(),false));
            require(level.getBlockState(origin).is(ModBlocks.byId("sinky_liquid")),"potion impact places center");
            require(level.getBlockState(origin).getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL)==4,"potion center has four remaining quanta");
            for(Direction direction:Direction.Plane.HORIZONTAL)require(level.getBlockState(origin.relative(direction)).is(ModBlocks.byId("sinky_liquid")) && level.getBlockState(origin.relative(direction)).getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL)==7,"potion weak cardinal splash");
            require(potion.isRemoved(),"projectile consumed on impact");
            passed.add("server sinking potion impact places legacy weak splash levels and consumes projectile");
            for (BlockState fill : List.of(Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(), ModBlocks.byId("honey").defaultBlockState())) {
                for (Direction direction : Direction.Plane.HORIZONTAL) level.setBlock(origin.relative(direction), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(origin.east().below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                var ball = ModEntities.LIQUID_BALL.get().create(level, EntitySpawnReason.TRIGGERED);
                require(ball != null, "liquid ammunition probe"); probes.add(ball);
                ball.setOwner(player); ball.setMedium(fill); ball.setPos(Vec3.atCenterOf(origin));
                ball.onHit(new BlockHitResult(Vec3.atCenterOf(origin), Direction.UP, origin.below(), false));
                var splash = level.getBlockState(origin);
                require(splash.is(fill.getBlock()) && splash.getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL) == 7 && !splash.getFluidState().isSource(), "gun uses one quantum of " + fill);
                require(((net.minecraft.world.level.block.LiquidBlock) splash.getBlock()).pickupBlock(player, level, origin, splash).isEmpty(), "gun splash cannot refill buckets");
                for (int i = 0; i < 40; i++) com.mfqm.morefunquicksandmod.gameplay.MediumReactions.fuse(level, origin);
                require(level.getBlockState(origin.below()).is(Blocks.STONE) && level.getBlockState(origin.east().below()).is(Blocks.GRASS_BLOCK), "ordinary ammunition never fuses terrain");
            }
            passed.add("water/lava/honey gun impacts cannot produce bucket sources or fuse surrounding terrain");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var victim = net.minecraft.world.entity.EntityType.PIG.create(level, EntitySpawnReason.TRIGGERED);
            require(victim != null, "hand rescue victim"); probes.add(victim);
            victim.setPos(player.position().add(.8, 0, 0)); level.addFreshEntity(victim);
            for (int up = 0; up <= 2; up++) level.setBlock(origin.above(up), Blocks.AIR.defaultBlockState(), 2);
            require(ModEntities.startHandRescue(player, victim), "empty hand starts rescue");
            var rescue = level.getEntitiesOfClass(ConnectorEntity.class, area).stream().filter(entity -> entity.kind().equals("rescue") && entity.isOwnedBy(player)).findFirst().orElseThrow();
            probes.add(rescue);
            require(player.isUsingItem() && player.getUseItem().is(ModItems.byId("rescuing")), "rescue starts synchronized held-use animation");
            player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
            require(!rescue.isRemoved() && player.isUsingItem(), "using rescue item preserves active connection");
            rescue.discard();
            require(!player.isUsingItem() && player.getMainHandItem().isEmpty(), "rescue end clears held use and temporary item");
            passed.add("hand rescue starts held-use animation, survives use, and cleans up on release");
            for (String ending : List.of("victim_death", "owner_death", "owner_dimension")) {
                victim.setHealth(victim.getMaxHealth()); player.setHealth(player.getMaxHealth());
                require(ModEntities.startHandRescue(player, victim), "start rescue before " + ending);
                var active = level.getEntitiesOfClass(ConnectorEntity.class, area).stream().filter(entity -> !entity.isRemoved() && entity.kind().equals("rescue") && entity.isOwnedBy(player)).findFirst().orElseThrow();
                probes.add(active);
                if (ending.equals("victim_death")) victim.setHealth(0);
                if (ending.equals("owner_death")) player.setHealth(0);
                if (ending.equals("owner_dimension")) player.setServerLevel(java.util.Objects.requireNonNull(level.getServer().getLevel(net.minecraft.world.level.Level.NETHER)));
                try {
                    active.tick();
                    require(active.isRemoved() && !player.isUsingItem() && player.getMainHandItem().isEmpty(), ending + " clears connection, animation and temporary item");
                } finally { player.setServerLevel(level); }
            }
            player.setHealth(player.getMaxHealth()); victim.setHealth(victim.getMaxHealth());
            passed.add("rescue clears connection, held-use and temporary item on either death or owner dimension change");
            ConnectorEntity rope=ModEntities.ROPE.get().create(level,EntitySpawnReason.TRIGGERED);require(rope!=null,"rope");probes.add(rope);rope.setOwner(player);rope.setPos(player.getEyePosition());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.byId("rope")));
            float before=rope.ropeLength();rope.control(player,0);require(rope.ropeLength()<before,"owner reeling bounded length");float changed=rope.ropeLength();rope.control(player,1);require(rope.ropeLength()==changed,"duplicate same-tick control ignored");
            passed.add("server-owned connector reeling and duplicate intent rate limit");
            return List.copyOf(passed);
        }finally{
            if(player.getVehicle() instanceof BlobEntity blob)blob.releasePassenger(player);else player.stopRiding();
            for(Entity entity:probes)entity.discard();
            for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,area))if(!existingItems.contains(item.getUUID()))item.discard();
            for(var entry:saved.entrySet())level.setBlock(entry.getKey(),entry.getValue(),3);
            level.getServer().setDifficulty(oldDifficulty,true);
        }
    }
    private static void require(boolean valid,String message){if(!valid)throw new IllegalStateException("MFQM entity verification failed: "+message);}
    private EntityPortChecks(){}
}
