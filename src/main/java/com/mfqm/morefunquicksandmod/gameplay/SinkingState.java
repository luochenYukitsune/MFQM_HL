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
    public double cachedLoad;
    public float previousYaw;
    public double previousY;
    public boolean jumpInput;
    public boolean sneakInput;
    public long inputTick = Long.MIN_VALUE;
    public int rescueTicks;

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
