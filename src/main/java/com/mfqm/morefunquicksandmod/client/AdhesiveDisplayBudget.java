package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.entity.AdhesiveTetherEntity;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One bounded lookup per target per tick, independent of the cosmetic strand density. */
final class AdhesiveDisplayBudget {
    private record Selection(long tick,List<Integer> ids){}
    private static final Map<Integer,Selection> CACHE=new HashMap<>();
    private static java.lang.ref.WeakReference<Level> world=new java.lang.ref.WeakReference<>(null);
    static boolean visible(AdhesiveTetherEntity entity) {
        return density(entity)>0;
    }
    static int density(AdhesiveTetherEntity entity) {
        var level=entity.level();if(world.get()!=level){CACHE.clear();world=new java.lang.ref.WeakReference<>(level);}
        var target=entity.target();if(target==null)return 0;
        var family=CoatingAppearance.family(entity.material());
        int limit=Math.min(ModConfig.CLIENT.strandDisplayLimit.get(),ModConfig.CLIENT.visuals(family).strands().get());
        if(limit==0)return 0;
        long tick=level.getGameTime();var selection=CACHE.get(target.getId());
        if(selection==null || selection.tick()!=tick) {
            if(CACHE.size()>256)CACHE.clear();
            var ids=level.getEntitiesOfClass(AdhesiveTetherEntity.class,target.getBoundingBox().inflate(20),a->a.target()==target)
                    .stream().sorted(java.util.Comparator.comparing(AdhesiveTetherEntity::breaking)
                            .thenComparing(java.util.Comparator.comparingInt(AdhesiveTetherEntity::getId).reversed()))
                    .limit(72).map(AdhesiveTetherEntity::getId).toList();
            selection=new Selection(tick,ids);CACHE.put(target.getId(),selection);
        }
        int index=selection.ids().indexOf(entity.getId());
        // One target-wide density: different material limits may hide groups, but must
        // never give some groups a larger independent budget and exceed the total cap.
        int groups=Math.min(ModConfig.CLIENT.strandDisplayLimit.get(),selection.ids().size());
        return index>=0 && index<limit?CompactStrandStyle.density(ModConfig.CLIENT.strandDensity.get(),groups):0;
    }
    private AdhesiveDisplayBudget(){}
}
