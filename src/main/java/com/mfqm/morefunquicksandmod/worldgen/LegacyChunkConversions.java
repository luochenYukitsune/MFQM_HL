package com.mfqm.morefunquicksandmod.worldgen;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.registry.ModBlocks;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Queue newly generated chunks; Load fires before FULL and cannot safely modify the level. */
public final class LegacyChunkConversions {
    private record Pending(ResourceKey<Level> dimension, ChunkPos pos) {}
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    public static void register(IEventBus bus) {
        bus.addListener(LegacyChunkConversions::loaded);
        bus.addListener(LegacyChunkConversions::tick);
        bus.addListener((ServerStoppedEvent event) -> PENDING.clear());
    }
    private static void loaded(ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel level)
            PENDING.add(new Pending(level.dimension(), event.getChunk().getPos()));
    }
    private static void tick(ServerTickEvent.Post event) {
        for (int count = 0; count < 2; count++) {
            Pending next = PENDING.poll();
            if (next == null) return;
            ServerLevel level = event.getServer().getLevel(next.dimension());
            if (level == null || level.getChunkSource().getChunkNow(next.pos().x, next.pos().z) == null) continue;
            convert(level, next.pos());
        }
    }
    public static void convert(ServerLevel level, ChunkPos chunk) {
        boolean temple = level.dimension().equals(Level.OVERWORLD) && ModConfig.SERVER.genTempleQuicksand.get();
        boolean bop = ModConfig.COMMON.biomesOPlenty.get() && net.neoforged.fml.ModList.get().isLoaded("biomesoplenty");
        boolean betweenlands = ModConfig.COMMON.betweenlands.get() && net.neoforged.fml.ModList.get().isLoaded("thebetweenlands");
        if (!temple && !bop && !betweenlands) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        var loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (loaded == null) return;
        var sections = loaded.getSections();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> {
                if (temple && state.is(Blocks.TNT)) return true;
                var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                return bop && id.getNamespace().equals("biomesoplenty") || betweenlands && id.getNamespace().equals("thebetweenlands");
            })) continue;
            int baseY = loaded.getMinY() + index * 16;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int dy = 0; dy < 16; dy++) {
                int y = baseY + dy;
                if (y <= level.getMinY() + 1 || y >= level.getMaxY() - 1) continue;
                pos.set(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
                var state = section.getBlockState(x, dy, z);
                if (temple && state.is(Blocks.TNT) && level.getBlockState(pos.above()).is(Blocks.SANDSTONE)) {
                    level.setBlock(pos, LegacyStructures.state("quicksand", 0), 2);
                    level.setBlock(pos.above(), LegacyStructures.state("sandstone_trap", 0), 2);
                    level.setBlock(pos.below(), LegacyStructures.state("quicksand", 0), 2);
                    level.setBlock(pos.below(2), Blocks.SANDSTONE.defaultBlockState(), 2);
                } else if (!state.hasBlockEntity()) {
                    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String replacement = null;
                    if (bop && id.getNamespace().equals("biomesoplenty")) replacement = switch (id.getPath()) { case "mud" -> "mud"; case "honey" -> "honey"; default -> null; };
                    if (betweenlands && id.getNamespace().equals("thebetweenlands")) replacement = switch (id.getPath()) { case "mud" -> "mud"; case "tar" -> "tar"; case "sludge" -> "slurry"; default -> null; };
                    if (replacement != null) level.setBlock(pos, ModBlocks.byId(replacement).defaultBlockState(), 2);
                }
            }
        }
    }
    private LegacyChunkConversions() {}
}
