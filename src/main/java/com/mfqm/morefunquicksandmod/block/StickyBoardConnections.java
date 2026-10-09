package com.mfqm.morefunquicksandmod.block;

import com.mfqm.morefunquicksandmod.MFQM;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Upgrade old saved boards after FULL; never request chunks from the load callback. */
@EventBusSubscriber(modid=MFQM.MOD_ID)
public final class StickyBoardConnections {
    private record Pending(ResourceKey<Level> dimension,ChunkPos pos){}
    private static final ConcurrentLinkedQueue<Pending> PENDING=new ConcurrentLinkedQueue<>();
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        for(var section:event.getChunk().getSections())
            if(section.maybeHas(state->state.getBlock() instanceof StickyBoardBlock)) {
                PENDING.add(new Pending(level.dimension(),event.getChunk().getPos()));return;
            }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(int i=0;i<4;i++) {
            var next=PENDING.poll();if(next==null)return;
            var level=event.getServer().getLevel(next.dimension());if(level==null)continue;
            var chunk=level.getChunkSource().getChunkNow(next.pos().x,next.pos().z);
            if(chunk!=null)refresh(level,chunk);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){PENDING.clear();}
    public static void refresh(ServerLevel level,LevelChunk chunk) {
        var sections=chunk.getSections();var pos=new BlockPos.MutableBlockPos();
        for(int i=0;i<sections.length;i++) {
            var section=sections[i];if(!section.maybeHas(state->state.getBlock() instanceof StickyBoardBlock))continue;
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
                if(!(section.getBlockState(x,y,z).getBlock() instanceof StickyBoardBlock board))continue;
                pos.set(chunk.getPos().getMinBlockX()+x,chunk.getMinY()+i*16+y,chunk.getPos().getMinBlockZ()+z);
                board.refreshConnections(level,pos);
                // Repair the already-loaded side of a chunk boundary as well.
                for(var direction:Direction.Plane.HORIZONTAL) {
                    var neighbor=pos.relative(direction);if(!level.hasChunkAt(neighbor))continue;
                    if(level.getBlockState(neighbor).is(board))board.refreshConnections(level,neighbor);
                }
            }
        }
    }
    private StickyBoardConnections(){}
}
