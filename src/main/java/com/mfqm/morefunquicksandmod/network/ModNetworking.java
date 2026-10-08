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
    public record InputPayload(boolean jump, boolean sneak, boolean moving) implements CustomPacketPayload {
        public static final Type<InputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "sinking_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, InputPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, InputPayload::jump, ByteBufCodecs.BOOL, InputPayload::sneak,
                ByteBufCodecs.BOOL, InputPayload::moving, InputPayload::new);
        @Override public Type<InputPayload> type() { return TYPE; }
    }
    public record StrugglePayload() implements CustomPacketPayload {
        public static final Type<StrugglePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "struggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StrugglePayload> CODEC = StreamCodec.unit(new StrugglePayload());
        @Override public Type<StrugglePayload> type() { return TYPE; }
    }
    public record ControlPayload(int entityId, int action) implements CustomPacketPayload {
        public static final Type<ControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MFQM.MOD_ID, "connector_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ControlPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ControlPayload::entityId, ByteBufCodecs.VAR_INT, ControlPayload::action, ControlPayload::new);
        @Override public Type<ControlPayload> type() { return TYPE; }
    }
    public static void register(IEventBus bus) { bus.addListener(ModNetworking::payloads); }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        // Connected boards add block states; reject older clients before incompatible state IDs arrive.
        var registrar = event.registrar("7");
        registrar.playToServer(InputPayload.TYPE, InputPayload.CODEC, (data, context) -> {
            if (context.player() instanceof ServerPlayer player && player.isAlive()) QuicksandPhysics.setInput(player, data.jump(), data.sneak(), data.moving());
        });
        registrar.playToServer(StrugglePayload.TYPE, StrugglePayload.CODEC, (data, context) -> {
            if (context.player() instanceof ServerPlayer player && player.isAlive()) AdhesionRequest.request(player);
        });
        registrar.playToServer(ControlPayload.TYPE, ControlPayload.CODEC, (data, context) -> {
            if (data.action() >= 0 && data.action() <= 3 && context.player() instanceof ServerPlayer player && player.isAlive())
                ModEntities.controlConnector(player, data.entityId(), data.action());
        });
    }
    private static final class AdhesionRequest {
        static void request(ServerPlayer player) { com.mfqm.morefunquicksandmod.gameplay.AdhesionController.requestStruggle(player); }
    }
    private ModNetworking() {}
}
