package com.mfqm.morefunquicksandmod.gameplay;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

/** Persistent coating and synchronized contact data; death creates a clean state. */
public final class SinkingState implements ValueIOSerializable {
    public int air = 300;
    public int coatingLevel;
    public int coatingTicks;
    public String coatingType = "";
    public String material = "";
    public double depth;
    public boolean eyesCovered;
    public int contactTicks;
    public long lastTick = Long.MIN_VALUE;
    /** Transient identity: positions and action clocks must never cross a world boundary. */
    public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> physicsDimension;
    public double cachedLoad;
    public float previousYaw;
    public double previousY;
    public boolean jumpInput;
    public boolean sneakInput;
    public long inputTick = Long.MIN_VALUE;
    public int rescueTicks;
    /** Cumulative motion costs let local prediction consume each accepted cost once. */
    public double totalStruggleSink;
    public long rescueSequence;
    public int externalMotionTicks;
    public long externalMotionSequence;
    public net.minecraft.world.phys.Vec3 adhesiveForce = net.minecraft.world.phys.Vec3.ZERO;
    public net.minecraft.world.phys.Vec3 adhesiveOrigin;
    public double adhesiveStrength=1;
    public String adhesiveMaterial="";
    public AdhesiveMotion.Jump adhesiveJump=new AdhesiveMotion.Jump();
    public long preparedTick = Long.MIN_VALUE;
    public long nativeTravelTick = Long.MIN_VALUE;
    public net.minecraft.world.phys.Vec3 lowSpeedVelocity = net.minecraft.world.phys.Vec3.ZERO;
    public net.minecraft.world.phys.Vec3 motionRemainder = net.minecraft.world.phys.Vec3.ZERO;
    public String travelMaterial = "";
    public boolean movingInput;
    public long struggleRequestTick = Long.MIN_VALUE;
    public StruggleRules.State struggle = StruggleRules.State.initial();
    public StruggleRules.Result struggleResult;
    public String episodeMaterial = "";
    public int dryTicks;
    public long struggleAnimationTick = Long.MIN_VALUE;
    public int struggleSide;
    public int adhesiveConnections;
    public int consumedBoardSteps;
    public net.minecraft.core.BlockPos episodeBoard;
    public int previousBoardCharge;
    /** Synchronized release snapshot; meaningful only for the same board and coating episode. */
    public boolean boardReleased;
    public final java.util.List<AdhesionController.Anchor> anchors = new java.util.ArrayList<>();

    public static final StreamCodec<RegistryFriendlyByteBuf, SinkingState> STREAM_CODEC = new StreamCodec<>() {
        @Override public SinkingState decode(RegistryFriendlyByteBuf buffer) {
            var state = new SinkingState();
            state.air = buffer.readVarInt();
            state.coatingLevel = buffer.readVarInt();
            state.coatingTicks = buffer.readVarInt();
            state.coatingType = buffer.readUtf(64);
            state.material = buffer.readUtf(64);
            state.depth = buffer.readDouble();
            state.eyesCovered = buffer.readBoolean();
            state.struggleAnimationTick = buffer.readLong();
            state.struggleSide = buffer.readVarInt();
            state.adhesiveConnections = buffer.readVarInt();
            state.boardReleased = buffer.readBoolean();
            state.episodeBoard = buffer.readBoolean() ? buffer.readBlockPos() : null;
            state.previousBoardCharge = buffer.readVarInt();
            state.cachedLoad = Math.clamp(buffer.readDouble(), 0, 4);
            state.rescueTicks = Math.clamp(buffer.readVarInt(), 0, 6);
            state.rescueSequence = buffer.readLong();
            state.totalStruggleSink = Math.max(0, buffer.readDouble());
            state.adhesiveForce = new net.minecraft.world.phys.Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            state.contactTicks = Math.max(0,buffer.readVarInt());
            state.externalMotionTicks = Math.clamp(buffer.readVarInt(),0,4);
            state.externalMotionSequence = buffer.readLong();
            state.adhesiveOrigin=buffer.readBoolean()?new net.minecraft.world.phys.Vec3(buffer.readDouble(),buffer.readDouble(),buffer.readDouble()):null;
            state.adhesiveStrength=buffer.readDouble();state.adhesiveMaterial=buffer.readUtf(64);
            return state;
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SinkingState state) {
            buffer.writeVarInt(state.air);
            buffer.writeVarInt(state.coatingLevel);
            buffer.writeVarInt(state.coatingTicks);
            buffer.writeUtf(state.coatingType, 64);
            buffer.writeUtf(state.material, 64);
            buffer.writeDouble(state.depth);
            buffer.writeBoolean(state.eyesCovered);
            buffer.writeLong(state.struggleAnimationTick);
            buffer.writeVarInt(state.struggleSide);
            buffer.writeVarInt(state.adhesiveConnections);
            buffer.writeBoolean(state.boardReleased);
            buffer.writeBoolean(state.episodeBoard != null);
            if(state.episodeBoard != null)buffer.writeBlockPos(state.episodeBoard);
            buffer.writeVarInt(state.previousBoardCharge);
            buffer.writeDouble(state.cachedLoad);
            buffer.writeVarInt(state.rescueTicks);
            buffer.writeLong(state.rescueSequence);
            buffer.writeDouble(state.totalStruggleSink);
            buffer.writeDouble(state.adhesiveForce.x);
            buffer.writeDouble(state.adhesiveForce.y);
            buffer.writeDouble(state.adhesiveForce.z);
            buffer.writeVarInt(state.contactTicks);
            buffer.writeVarInt(state.externalMotionTicks);
            buffer.writeLong(state.externalMotionSequence);
            buffer.writeBoolean(state.adhesiveOrigin!=null);
            if(state.adhesiveOrigin!=null){buffer.writeDouble(state.adhesiveOrigin.x);buffer.writeDouble(state.adhesiveOrigin.y);buffer.writeDouble(state.adhesiveOrigin.z);}
            buffer.writeDouble(state.adhesiveStrength);buffer.writeUtf(state.adhesiveMaterial,64);
        }
    };

    @Override public void serialize(ValueOutput output) {
        output.putInt("air", air);
        output.putInt("coating_level", coatingLevel);
        output.putInt("coating_ticks", coatingTicks);
        output.putString("coating_type", coatingType);
    }
    @Override public void deserialize(ValueInput input) {
        air = Math.clamp(input.getIntOr("air", 300), -1, 300);
        coatingLevel = Math.clamp(input.getIntOr("coating_level", 0), 0, 10);
        coatingTicks = Math.max(0, input.getIntOr("coating_ticks", 0));
        coatingType = input.getStringOr("coating_type", "");
        if (coatingType.length() > 64) coatingType = "";
    }
}
