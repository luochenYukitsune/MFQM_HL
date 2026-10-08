package com.mfqm.morefunquicksandmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.animal.pig.ColdPigModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.model.animal.feline.CatModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.animal.rabbit.RabbitModel;
import net.minecraft.client.model.animal.chicken.ChickenModel;
import net.minecraft.client.model.animal.chicken.ColdChickenModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.RabbitRenderer;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Owns private baked models. Sampling never changes the renderer model queued for deferred drawing. */
public final class AdhesiveFeetSampler {
    public record Foot(Vec3 ankle,Vec3 shin,WetAdhesiveStyle.Frame surface) {
        public Foot(Vec3 ankle,Vec3 shin){this(ankle,shin,new WetAdhesiveStyle.Frame(vector(ankle),vector(shin.subtract(ankle).normalize()),new CoatingVoxels.Vec(1,0,0),new CoatingVoxels.Vec(0,0,1),.08,.08));}
        public Foot translated(Vec3 offset){return new Foot(ankle.add(offset),shin.add(offset),new WetAdhesiveStyle.Frame(surface.center().add(vector(offset)),surface.up(),surface.right(),surface.front(),surface.halfWidth(),surface.halfDepth()));}
    }
    private record Bounds(double minX,double maxX,double minZ,double maxZ){}
    private static final Map<ModelPart,Bounds> BOUNDS=new java.util.WeakHashMap<>();
    private final AvatarSampler wide,slim;
    private final Map<String,AnimalSampler> animatedAnimals=new HashMap<>();
    private record Frame(java.lang.ref.WeakReference<Entity> target,long tick,float partialTick,Foot right,Foot left){}
    private final Map<Integer,Frame> frames=new HashMap<>();
    public AdhesiveFeetSampler(EntityRendererProvider.Context context) {
        wide=new AvatarSampler(context,false);slim=new AvatarSampler(context,true);
        animatedAnimals.put("cat",new CatSampler(context));
        animatedAnimals.put("wolf",new WolfSampler(context));
        animatedAnimals.put("rabbit",new RabbitSampler(context));
        animatedAnimals.put("chicken",new ChickenSampler(context));
        animatedAnimals.put("cow",new CowSampler(context));
        animatedAnimals.put("pig",new PigSampler(context));
        animatedAnimals.put("sheep",new SheepSampler(context));
    }
    public Foot sample(Entity target,int side,float partialTick) {
        var cameraFoot=FirstPersonCompatibility.localFeet(target,side,partialTick);if(cameraFoot!=null)return cameraFoot;
        var old=frames.get(target.getId());long tick=target.level().getGameTime();
        if(old!=null && old.target().get()==target && old.tick()==tick && old.partialTick()==partialTick)return side==0?old.right():old.left();
        if(frames.size()>128)frames.clear();
        Foot right=uncached(target,0,partialTick),left=uncached(target,1,partialTick);
        frames.put(target.getId(),new Frame(new java.lang.ref.WeakReference<>(target),tick,partialTick,right,left));
        return side==0?right:left;
    }
    private Foot uncached(Entity target,int side,float partialTick) {
        if(target instanceof AbstractClientPlayer player) {
            var sampler=player.getSkin().model()==PlayerModelType.SLIM?slim:wide;
            var state=sampler.createRenderState(player,partialTick);
            StruggleClient.capture(player,state);
            return sampler.sample(state,side);
        }
        if(target instanceof LivingEntity living) {
            String kind=BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
            var animated=animatedAnimals.get(kind);
            if(animated!=null)return animated.sample(animated.state(living,partialTick),side);
            // Generic humanoid fallback; the supported animals above use their actual poses.
            double yaw=Math.toRadians(net.minecraft.util.Mth.rotLerp(partialTick,living.yBodyRotO,living.yBodyRot));
            double modelScale=target.getBbWidth()/.6;
            double lateral=(side==0?-1.9:1.9)*.9375/16*modelScale;
            double fore=0;
            Vec3 foot=new Vec3(lateral*Math.cos(yaw)-fore*Math.sin(yaw),.045,lateral*Math.sin(yaw)+fore*Math.cos(yaw));
            return new Foot(foot,foot.add(0,Math.min(.3,target.getBbHeight()*.25),0),new WetAdhesiveStyle.Frame(vector(foot),new CoatingVoxels.Vec(0,1,0),
                    new CoatingVoxels.Vec(Math.cos(yaw),0,Math.sin(yaw)),new CoatingVoxels.Vec(-Math.sin(yaw),0,Math.cos(yaw)),.125*modelScale,.125*modelScale));
        }
        return new Foot(new Vec3(0,.04,0),new Vec3(0,.2,0));
    }
    public Foot avatar(AvatarRenderState state,int side,boolean isSlim) { return (isSlim?slim:wide).sample(state,side); }
    public Foot animal(String kind,LivingEntityRenderState state,int side) {
        var animated=animatedAnimals.get(kind);
        if(animated!=null)return animated.sample(state,side);
        throw new IllegalArgumentException("Unsupported adhesive animal: "+kind);
    }
    private static Vec3 position(PoseStack pose,double x,double y,double z) {
        var point=pose.last().pose().transformPosition(new Vector3f((float)x,(float)y,(float)z));
        return new Vec3(point.x,point.y,point.z);
    }
    private static CoatingVoxels.Vec vector(Vec3 p){return new CoatingVoxels.Vec(p.x,p.y,p.z);}
    private static Foot surface(PoseStack pose,ModelPart part,Vec3 ankle,Vec3 shin,double ankleY,double padding) {
        var box=BOUNDS.computeIfAbsent(part,key->{
            double[] extents={Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
            key.visit(new PoseStack(),(matrix,path,index,cube)->{if(path.isEmpty()){extents[0]=Math.min(extents[0],cube.minX);extents[1]=Math.max(extents[1],cube.maxX);extents[2]=Math.min(extents[2],cube.minZ);extents[3]=Math.max(extents[3],cube.maxZ);}});
            return Double.isFinite(extents[0])?new Bounds(extents[0],extents[1],extents[2],extents[3]):new Bounds(-1,1,-1,1);
        });
        var zero=position(pose,0,0,0);var right=position(pose,1./16,0,0).subtract(zero);var front=position(pose,0,0,1./16).subtract(zero);
        var center=position(pose,(box.minX()+box.maxX())/32,ankleY/16,(box.minZ()+box.maxZ())/32);
        var up=position(pose,0,-1./16,0).subtract(zero).normalize();
        return new Foot(ankle,shin,new WetAdhesiveStyle.Frame(vector(center),vector(up),vector(right.normalize()),vector(front.normalize()),
                ((box.maxX()-box.minX())*.5+padding)*right.length(),((box.maxZ()-box.minZ())*.5+padding)*front.length()));
    }
    private interface AnimalSampler {
        LivingEntityRenderState state(LivingEntity target,float partialTick);
        Foot sample(LivingEntityRenderState state,int side);
    }
    private static <S extends LivingEntityRenderState> PoseStack animalPose(EntityModel<S> model,S state,Consumer<PoseStack> rendererTransform) {
        model.setupAnim(state);
        var pose=new PoseStack();
        if(state.passengerOffset!=null)pose.translate(state.passengerOffset.x,state.passengerOffset.y,state.passengerOffset.z);
        pose.scale(state.scale,state.scale,state.scale);
        rendererTransform.accept(pose);
        pose.translate(0,-1.501,0);
        model.root().translateAndRotate(pose);
        return pose;
    }
    private static Foot legFoot(PoseStack pose,EntityModel<?> model,String part,double x,double ankleY,double shinY,double z) {
        var leg=model.root().getChild(part);leg.translateAndRotate(pose);
        return surface(pose,leg,position(pose,x/16,ankleY/16,z/16),position(pose,x/16,shinY/16,z/16),ankleY,.025);
    }
    private static final class CatSampler extends CatRenderer implements AnimalSampler {
        private final CatModel adult,baby;
        CatSampler(EntityRendererProvider.Context context) {
            super(context);adult=new CatModel(context.bakeLayer(ModelLayers.CAT));baby=new CatModel(context.bakeLayer(ModelLayers.CAT_BABY));
        }
        public LivingEntityRenderState state(LivingEntity target,float partialTick) {
            return createRenderState((net.minecraft.world.entity.animal.feline.Cat)target,partialTick);
        }
        public Foot sample(LivingEntityRenderState input,int side) {
            var state=(CatRenderState)input;var sampled=state.isBaby?baby:adult;
            var pose=animalPose(sampled,state,p->{setupRotations(state,p,state.bodyRot,state.scale);p.scale(-1,-1,1);scale(state,p);});
            return legFoot(pose,sampled,side==0?"right_front_leg":"left_hind_leg",0,side==0?9.5:5.5,side==0?5:2,side==0?1:2);
        }
    }
    private static final class WolfSampler extends WolfRenderer implements AnimalSampler {
        private final WolfModel adult,baby;
        WolfSampler(EntityRendererProvider.Context context) {
            super(context);adult=new WolfModel(context.bakeLayer(ModelLayers.WOLF));baby=new WolfModel(context.bakeLayer(ModelLayers.WOLF_BABY));
        }
        public LivingEntityRenderState state(LivingEntity target,float partialTick) {
            return createRenderState((net.minecraft.world.entity.animal.wolf.Wolf)target,partialTick);
        }
        public Foot sample(LivingEntityRenderState input,int side) {
            var state=(WolfRenderState)input;var sampled=state.isBaby?baby:adult;
            var pose=animalPose(sampled,state,p->{setupRotations(state,p,state.bodyRot,state.scale);p.scale(-1,-1,1);scale(state,p);});
            return legFoot(pose,sampled,side==0?"right_front_leg":"left_hind_leg",1,7.5,3,0);
        }
    }
    private static final class RabbitSampler extends RabbitRenderer implements AnimalSampler {
        private final RabbitModel adult,baby;
        RabbitSampler(EntityRendererProvider.Context context) {
            super(context);adult=new RabbitModel(context.bakeLayer(ModelLayers.RABBIT));baby=new RabbitModel(context.bakeLayer(ModelLayers.RABBIT_BABY));
        }
        public LivingEntityRenderState state(LivingEntity target,float partialTick) {
            return createRenderState((net.minecraft.world.entity.animal.rabbit.Rabbit)target,partialTick);
        }
        public Foot sample(LivingEntityRenderState input,int side) {
            var state=(RabbitRenderState)input;var sampled=state.isBaby?baby:adult;
            var pose=animalPose(sampled,state,p->{setupRotations(state,p,state.bodyRot,state.scale);p.scale(-1,-1,1);scale(state,p);});
            if(side==0)return legFoot(pose,sampled,"right_front_leg",0,6.5,3,0);
            var haunch=sampled.root().getChild("left_haunch");haunch.translateAndRotate(pose);
            Vec3 shin=position(pose,0,3./16,1./16);
            var foot=haunch.getChild("left_hind_foot");foot.translateAndRotate(pose);
            return surface(pose,foot,position(pose,0,6./16,-.2/16),shin,6,.025);
        }
    }
    private static final class ChickenSampler extends ChickenRenderer implements AnimalSampler {
        private final ChickenModel adult,baby,coldAdult,coldBaby;
        ChickenSampler(EntityRendererProvider.Context context) {
            super(context);
            adult=new ChickenModel(context.bakeLayer(ModelLayers.CHICKEN));baby=new ChickenModel(context.bakeLayer(ModelLayers.CHICKEN_BABY));
            coldAdult=new ColdChickenModel(context.bakeLayer(ModelLayers.COLD_CHICKEN));coldBaby=new ColdChickenModel(context.bakeLayer(ModelLayers.COLD_CHICKEN_BABY));
        }
        public LivingEntityRenderState state(LivingEntity target,float partialTick) {
            return createRenderState((net.minecraft.world.entity.animal.chicken.Chicken)target,partialTick);
        }
        public Foot sample(LivingEntityRenderState input,int side) {
            var state=(ChickenRenderState)input;
            boolean cold=state.variant!=null && state.variant.modelAndTexture().model()==net.minecraft.world.entity.animal.chicken.ChickenVariant.ModelType.COLD;
            var sampled=cold?(state.isBaby?coldBaby:coldAdult):(state.isBaby?baby:adult);
            var pose=animalPose(sampled,state,p->{setupRotations(state,p,state.bodyRot,state.scale);p.scale(-1,-1,1);scale(state,p);});
            return legFoot(pose,sampled,side==0?"right_leg":"left_leg",.5,4.5,1,-1.5);
        }
    }
    private static final class CowSampler extends CowRenderer implements AnimalSampler {
        private final CowModel adult,baby,warmAdult,warmBaby,coldAdult,coldBaby;
        CowSampler(EntityRendererProvider.Context context) {
            super(context);
            adult=new CowModel(context.bakeLayer(ModelLayers.COW));baby=new CowModel(context.bakeLayer(ModelLayers.COW_BABY));
            warmAdult=new CowModel(context.bakeLayer(ModelLayers.WARM_COW));warmBaby=new CowModel(context.bakeLayer(ModelLayers.WARM_COW_BABY));
            coldAdult=new CowModel(context.bakeLayer(ModelLayers.COLD_COW));coldBaby=new CowModel(context.bakeLayer(ModelLayers.COLD_COW_BABY));
        }
        public LivingEntityRenderState state(LivingEntity target,float partialTick) {
            return createRenderState((net.minecraft.world.entity.animal.cow.Cow)target,partialTick);
        }
        public Foot sample(LivingEntityRenderState input,int side) {
            var state=(CowRenderState)input;
            var variant=state.variant==null?net.minecraft.world.entity.animal.cow.CowVariant.ModelType.NORMAL:state.variant.modelAndTexture().model();
            var sampled=switch(variant){case WARM->state.isBaby?warmBaby:warmAdult;case COLD->state.isBaby?coldBaby:coldAdult;default->state.isBaby?baby:adult;};
            var pose=animalPose(sampled,state,p->{setupRotations(state,p,state.bodyRot,state.scale);p.scale(-1,-1,1);scale(state,p);});
            return legFoot(pose,sampled,side==0?"right_front_leg":"left_hind_leg",0,11.5,7,0);
        }
    }
    private static final class PigSampler extends PigRenderer implements AnimalSampler {
        private final PigModel adult,baby,coldAdult,coldBaby;
        PigSampler(EntityRendererProvider.Context context) {
            super(context);
            adult=new PigModel(context.bakeLayer(ModelLayers.PIG));baby=new PigModel(context.bakeLayer(ModelLayers.PIG_BABY));
            coldAdult=new ColdPigModel(context.bakeLayer(ModelLayers.COLD_PIG));coldBaby=new ColdPigModel(context.bakeLayer(ModelLayers.COLD_PIG_BABY));
        }
        public LivingEntityRenderState state(LivingEntity target,float partialTick) {
            return createRenderState((net.minecraft.world.entity.animal.pig.Pig)target,partialTick);
        }
        public Foot sample(LivingEntityRenderState input,int side) {
            var state=(PigRenderState)input;
            boolean cold=state.variant!=null && state.variant.modelAndTexture().model()==net.minecraft.world.entity.animal.pig.PigVariant.ModelType.COLD;
            var sampled=cold?(state.isBaby?coldBaby:coldAdult):(state.isBaby?baby:adult);
            var pose=animalPose(sampled,state,p->{setupRotations(state,p,state.bodyRot,state.scale);p.scale(-1,-1,1);scale(state,p);});
            return legFoot(pose,sampled,side==0?"right_front_leg":"left_hind_leg",0,5.5,1,0);
        }
    }
    private static final class SheepSampler extends SheepRenderer implements AnimalSampler {
        private final SheepModel adult,baby;
        SheepSampler(EntityRendererProvider.Context context) {
            super(context);adult=new SheepModel(context.bakeLayer(ModelLayers.SHEEP));baby=new SheepModel(context.bakeLayer(ModelLayers.SHEEP_BABY));
        }
        public LivingEntityRenderState state(LivingEntity target,float partialTick) {
            return createRenderState((net.minecraft.world.entity.animal.sheep.Sheep)target,partialTick);
        }
        public Foot sample(LivingEntityRenderState input,int side) {
            var state=(SheepRenderState)input;var sampled=state.isBaby?baby:adult;
            var pose=animalPose(sampled,state,p->{setupRotations(state,p,state.bodyRot,state.scale);p.scale(-1,-1,1);scale(state,p);});
            return legFoot(pose,sampled,side==0?"right_front_leg":"left_hind_leg",0,11.5,7,0);
        }
    }
    private static final class AvatarSampler extends AvatarRenderer<AbstractClientPlayer> {
        private AvatarSampler(EntityRendererProvider.Context context,boolean slim) { super(context,slim); }
        private Foot sample(AvatarRenderState state,int side) {
            getModel().setupAnim(state);
            var pose=new PoseStack();
            Vec3 offset=getRenderOffset(state);pose.translate(offset.x,offset.y,offset.z);
            pose.scale(state.scale,state.scale,state.scale);
            setupRotations(state,pose,state.bodyRot,state.scale);
            pose.scale(-1,-1,1);scale(state,pose);pose.translate(0,-1.501,0);
            getModel().root().translateAndRotate(pose);
            var leg=side==0?getModel().rightLeg:getModel().leftLeg;
            leg.translateAndRotate(pose);
            var outer=side==0?getModel().rightPants:getModel().leftPants;
            double padding=outer.visible?SkinLayerClearance.padding(side==0?"right_leg":"left_leg",false):.02;
            return surface(pose,leg,position(pose,0,11.5/16,0),position(pose,0,7./16,0),11.5,padding);
        }
    }
}
