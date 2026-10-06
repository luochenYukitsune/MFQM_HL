package com.mfqm.morefunquicksandmod.network;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.gameplay.QuicksandPhysics;
import com.mfqm.morefunquicksandmod.registry.ModEntities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Clients report button intent. Geometry, air, damage and rescue forces remain server-owned. */
public final class ModNetworking {
    public record InputPayload(boolean jump, boolean sneak) implements CustomPacketPayload {
        public static final Type<InputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "sinking_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, InputPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, InputPayload::jump, ByteBufCodecs.BOOL, InputPayload::sneak, InputPayload::new);
        @Override public Type<InputPayload> type() { return TYPE; }
    }
    public record ControlPayload(int entityId, int action) implements CustomPacketPayload {
        public static final Type<ControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "connector_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ControlPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ControlPayload::entityId, ByteBufCodecs.VAR_INT, ControlPayload::action, ControlPayload::new);
        @Override public Type<ControlPayload> type() { return TYPE; }
    }
    public static void register(IEventBus bus) { bus.addListener(ModNetworking::payloads); }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(InputPayload.TYPE, InputPayload.CODEC, (data, context) -> {
            if (context.player() instanceof ServerPlayer player && player.isAlive()) QuicksandPhysics.setInput(player, data.jump(), data.sneak());
        });
        registrar.playToServer(ControlPayload.TYPE, ControlPayload.CODEC, (data, context) -> {
            if (data.action() >= 0 && data.action() <= 3 && context.player() instanceof ServerPlayer player && player.isAlive())
                ModEntities.controlConnector(player, data.entityId(), data.action());
        });
    }
    private ModNetworking() {}
}
