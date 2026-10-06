package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.ModConfig;
import com.mfqm.morefunquicksandmod.gameplay.SinkingState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;

public final class MuddyPlayerLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    private final LegacyCoatingModel model;
    public static final ContextKey<Coating> COATING = new ContextKey<>(Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"coating"));
    public record Coating(int level,int ticks,String material) {
        public static Coating snapshot(SinkingState state){return new Coating(state.coatingLevel,state.coatingTicks,state.coatingType);}
        public Identifier texture() {
            boolean slimy=java.util.Set.of("sinking_slime","sinky_liquid","swallowing_flesh","meat","mucus","tar","honey","wax","larvae").contains(material);
            return Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"textures/entity/mudoverlays/"+(slimy?"slimeoverlay":"mudoverlay")+Mth.clamp(level-1,0,9)+".png");
        }
        public int color() {
            int rgb=switch(material){case "tar"->0x191413;case "sinking_slime","mucus"->0x8bac43;case "honey","wax"->0xd59926;case "swallowing_flesh","meat"->0x99403d;case "quicksand","soft_quicksand"->0xbcb28d;default->0x665243;};
            return (Mth.clamp((int)(255*Math.min(1,ticks/1000F)),0,255)<<24)|rgb;
        }
    }
    public MuddyPlayerLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent,boolean slim){super(parent);model=new LegacyCoatingModel(slim);}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch){
        Coating coat=state.getRenderData(COATING);
        if(coat==null || coat.level<=0 || coat.ticks<=50 || state.isInvisible || state.isSpectator || !ModConfig.CLIENT.coverPlayerWithMud.get())return;
        pose.pushPose();pose.scale(1.002F,1.002F,1.002F);
        collector.order(1).submitModel(model,state,pose,RenderTypes.entityTranslucent(coat.texture()),light,OverlayTexture.NO_OVERLAY,coat.color(),null,state.outlineColor,null);
        pose.popPose();
    }
}
