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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;

public final class MuddyPlayerLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    private final boolean slim;
    private static long firstPersonBodies,hiddenHeads;
    static long firstPersonBodies(){return firstPersonBodies;}
    static long hiddenHeads(){return hiddenHeads;}
    public static final ContextKey<Coating> COATING = new ContextKey<>(Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"coating"));
    public record Coating(int level,int ticks,String material) {
        public static Coating snapshot(SinkingState state){return new Coating(state.coatingLevel,state.coatingTicks,state.coatingType);}
        public boolean glue(){return material.equals("glue") || material.equals("sticky_board");}
        public Identifier texture() {
            boolean slimy=java.util.Set.of("sinking_slime","sinky_liquid","swallowing_flesh","meat","mucus","tar","honey","wax","larvae").contains(material);
            String family=switch(material){case "glue","sticky_board"->"glueoverlay";case "tar"->"taroverlay";case "honey","wax"->"honeyoverlay";default->slimy?"slimeoverlay":"mudoverlay";};
            return Identifier.fromNamespaceAndPath(MFQM.MOD_ID,"textures/entity/mudoverlays/"+family+"_height"+Mth.clamp(level-1,0,9)+".png");
        }
        public int color() {
            int rgb=switch(material){case "glue","sticky_board"->0xffffff;case "tar"->0x191413;case "sinking_slime","mucus"->0x8bac43;case "honey","wax"->0xd59926;case "swallowing_flesh","meat"->0x99403d;case "quicksand","soft_quicksand"->0xbcb28d;default->0x665243;};
            return (Mth.clamp((int)(255*Math.min(1,ticks/1000F)),0,255)<<24)|rgb;
        }
    }
    public MuddyPlayerLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent,boolean slim){super(parent);this.slim=slim;}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch){
        Coating coat=state.getRenderData(COATING);
        if(coat==null || coat.level<=0 || coat.ticks<=50 || state.isInvisible || state.isSpectator || !ModConfig.CLIENT.coverPlayerWithMud.get())return;
        var parent=getParentModel();boolean detailed=state.distanceToCameraSq<256;
        FirstPersonTetherProof.body(pose,parent,state);
        // The native model has already run setupAnim for this exact render state.
        // In particular, FirstPersonModel can move/hide its head and selected arms.
        if(FirstPersonCompatibility.camera(state)){firstPersonBodies++;if(!parent.head.visible)hiddenHeads++;}
        else GlueCoatingRenderer.submit(parent.head,"head",slim,coat,pose,collector,light,false,detailed,parent.hat,state.skin.body().texturePath());
        GlueCoatingRenderer.submit(parent.body,"body",slim,coat,pose,collector,light,false,detailed,parent.jacket,state.skin.body().texturePath());
        GlueCoatingRenderer.submit(parent.leftArm,"left_arm",slim,coat,pose,collector,light,false,detailed,parent.leftSleeve,state.skin.body().texturePath());
        GlueCoatingRenderer.submit(parent.rightArm,"right_arm",slim,coat,pose,collector,light,false,detailed,parent.rightSleeve,state.skin.body().texturePath());
        GlueCoatingRenderer.submit(parent.leftLeg,"left_leg",slim,coat,pose,collector,light,false,detailed,parent.leftPants,state.skin.body().texturePath());
        GlueCoatingRenderer.submit(parent.rightLeg,"right_leg",slim,coat,pose,collector,light,false,detailed,parent.rightPants,state.skin.body().texturePath());
    }
}
