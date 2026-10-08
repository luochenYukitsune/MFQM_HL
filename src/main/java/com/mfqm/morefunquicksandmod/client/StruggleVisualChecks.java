package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.MFQM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import com.mfqm.morefunquicksandmod.registry.ModEntities;

/** Real client/model checks, invoked only from the isolated port-checks save. */
public final class StruggleVisualChecks {
    public static void run(Minecraft game) {
        int cases=0;
        for(boolean slim:new boolean[]{false,true}) {
            var player=new PlayerModel(game.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
            var coating=new LegacyCoatingModel(slim);
            for(double depth:new double[]{.1,1.5})for(int side=0;side<2;side++) {
                var state=new AvatarRenderState();state.ageScale=1;state.scale=1;
                var frame=StrugglePose.sample(6,depth,side);
                state.setRenderData(StruggleClient.POSE,frame);
                state.rightArmPose=StruggleEnumParams.STRUGGLE.getValue();state.leftArmPose=HumanoidModel.ArmPose.EMPTY;
                player.setupAnim(state);coating.setupAnim(state);
                require(Math.abs(player.rightLeg.xRot-frame.rightLeg())<1e-5,"deferred right leg action");
                require(Math.abs(player.leftLeg.xRot-frame.leftLeg())<1e-5,"deferred left leg action");
                require(Math.abs(player.body.yRot-frame.bodyYaw())<1e-5,"deep body twist");
                require(Math.abs(player.rightLeg.xRot-coating.rightLeg.xRot)<1e-6,"coating right leg alignment");
                require(Math.abs(player.leftArm.xRot-coating.leftArm.xRot)<1e-6,"coating arm alignment");
                state.rightArmPose=HumanoidModel.ArmPose.EMPTY;state.setRenderData(StruggleClient.POSE,StrugglePose.sample(-1,0,0));
                player.setupAnim(state);coating.setupAnim(state);
                require(Math.abs(player.rightLeg.xRot)<1e-6 && Math.abs(coating.rightLeg.xRot)<1e-6,"finished action resets reused model");
                cases++;
            }
        }
        var tether=ModEntities.ADHESIVE_TETHER.get().create(game.level,EntitySpawnReason.TRIGGERED);
        var renderer=(AdhesiveTetherRenderer)game.getEntityRenderDispatcher().getRenderer(tether);
        var sampler=renderer.feet();
        for(boolean slim:new boolean[]{false,true})for(int side=0;side<2;side++) {
            var standing=new AvatarRenderState();standing.ageScale=1;standing.scale=1;
            standing.setRenderData(StruggleClient.POSE,StrugglePose.sample(6,.1,side));
            standing.rightArmPose=StruggleEnumParams.STRUGGLE.getValue();
            var before=sampler.avatar(standing,side,slim);
            standing.isCrouching=true;standing.boundingBoxHeight=1.5F;
            var crouched=sampler.avatar(standing,side,slim);
            require(crouched.ankle().z-before.ankle().z<-.23,"crouch includes actual leg local Z offset");
            require(Math.abs(crouched.ankle().y-before.ankle().y+.125)<1e-5,"crouch includes renderer translation");
            require(Math.abs(crouched.ankle().distanceTo(crouched.shin())-before.ankle().distanceTo(before.shin()))<1e-5,"crouch bounding box does not shorten the leg");
            cases++;
        }
        for(String kind:new String[]{"cow","pig","sheep"})for(int side=0;side<2;side++) {
            LivingEntityRenderState state=animalState(kind,false);
            state.scale=1;state.ageScale=1;
            var foot=sampler.animal(kind,state,side);
            require(Math.abs(foot.ankle().z-(side==0?5./16:-7./16))<1e-5,"quadruped front/back feet use model positions");
            require(Math.abs(foot.ankle().x-(side==0?-1:1)*(kind.equals("cow")?4.:3.)/16)<1e-5,"quadruped feet use actual model lateral spacing");
            require(foot.ankle().y>=0 && foot.ankle().y<.04,"stationary quadruped ankle stays at ground");
            state.walkAnimationSpeed=.3F;
            var walking=sampler.animal(kind,state,side);
            // Vanilla QuadrupedModel gives right_front_leg and left_hind_leg the same
            // cos(phase + PI) diagonal gait, both swinging toward +Z at phase zero.
            require(walking.ankle().z-foot.ankle().z>.05,"quadruped diagonal walking phase kind="+kind+" side="+side+" rest="+foot.ankle()+" walking="+walking.ankle());
            require(walking.ankle().y<.12,"quadruped has walking ankle lift without a virtual player struggle");
            cases++;
        }
        for(String kind:new String[]{"cow","pig","sheep","cat","wolf","rabbit","chicken"})for(int side=0;side<2;side++) {
            var adult=animalState(kind,false);var young=animalState(kind,true);
            var full=sampler.animal(kind,adult,side);var small=sampler.animal(kind,young,side);
            finite(full,kind+" adult");finite(small,kind+" baby");
            require(Math.abs(small.ankle().x)<Math.abs(full.ankle().x)*.8,kind+" baby has baked lateral scaling");
            require(small.ankle().distanceTo(small.shin())<full.ankle().distanceTo(full.shin())*.85,kind+" baby uses shorter baked leg");
            // Sampling a different age and action must not leak into the next standing frame.
            var repeated=sampler.animal(kind,adult,side);
            require(repeated.ankle().distanceTo(full.ankle())<1e-6,kind+" private model resets between ages");
            for(boolean baby:new boolean[]{false,true}) {
                var state=animalState(kind,baby);var rest=sampler.animal(kind,state,side);
                if(state instanceof WolfRenderState wolf) {
                    wolf.isSitting=true;
                    require(sampler.animal(kind,state,side).ankle().distanceTo(rest.ankle())>.04,"wolf sitting foot follows folded model leg");
                } else if(state instanceof CatRenderState cat) {
                    cat.isSitting=true;
                    require(sampler.animal(kind,state,side).ankle().distanceTo(rest.ankle())>.04,"cat sitting foot follows leg translation/rotation");
                    cat.isSitting=false;cat.lieDownAmount=1;cat.lieDownAmountTail=1;
                    var lying=sampler.animal(kind,state,side);
                    require(lying.ankle().distanceTo(rest.ankle())>.1,"cat lying foot includes renderer roll and leg pose");
                    cat.isLyingOnTopOfSleepingPlayer=true;
                    require(Math.abs(sampler.animal(kind,state,side).ankle().y-lying.ankle().y-.15)<1e-5,"cat sleeping-player offset follows rolled renderer transform");
                } else if(state instanceof RabbitRenderState rabbit) {
                    rabbit.jumpCompletion=.5F;
                    require(sampler.animal(kind,state,side).ankle().distanceTo(rest.ankle())>.025,"rabbit jumping includes haunch and nested hind-foot transforms");
                } else {
                    state.walkAnimationSpeed=.3F;
                    require(sampler.animal(kind,state,side).ankle().distanceTo(rest.ankle())>.015,kind+" baby/adult walking follows actual model");
                }
                cases++;
            }
        }
        for(var type:new EntityType<?>[]{EntityType.COW,EntityType.PIG,EntityType.SHEEP,EntityType.CAT,EntityType.WOLF,EntityType.RABBIT,EntityType.CHICKEN})for(boolean baby:new boolean[]{false,true}) {
            var entity=(AgeableMob)type.create(game.level,EntitySpawnReason.TRIGGERED);
            require(entity!=null,"client can create supported animal");entity.setAge(baby?-24000:0);
            if(entity instanceof net.minecraft.world.entity.animal.feline.Cat cat)cat.setInSittingPose(true);
            if(entity instanceof net.minecraft.world.entity.animal.wolf.Wolf wolf)wolf.setInSittingPose(true);
            // Goes through the sampler's real private renderer.extractRenderState path.
            for(int side=0;side<2;side++)finite(sampler.sample(entity,side,.5F),"real entity "+type+" baby="+baby);
            cases++;
        }
        MFQM.LOGGER.info("MFQM_STRUGGLE_VISUAL_CHECKS_COMPLETE modelCases={}",cases);
    }
    private static LivingEntityRenderState animalState(String kind,boolean baby) {
        LivingEntityRenderState state=switch(kind) {
            case "cow"->new CowRenderState();case "pig"->new PigRenderState();case "sheep"->new SheepRenderState();case "cat"->new CatRenderState();
            case "wolf"->new WolfRenderState();case "rabbit"->new RabbitRenderState();case "chicken"->new ChickenRenderState();
            default->new LivingEntityRenderState();
        };
        state.scale=1;state.isBaby=baby;state.ageScale=baby?.5F:1;return state;
    }
    private static void finite(AdhesiveFeetSampler.Foot foot,String message) {
        require(Double.isFinite(foot.ankle().lengthSqr()) && Double.isFinite(foot.shin().lengthSqr()),message+" finite endpoints");
    }
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException("Struggle visual validation failed: "+message); }
    private StruggleVisualChecks() {}
}
