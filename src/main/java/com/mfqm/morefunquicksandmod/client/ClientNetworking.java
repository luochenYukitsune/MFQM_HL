package com.mfqm.morefunquicksandmod.client;

import com.mfqm.morefunquicksandmod.network.ModNetworking;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Packet sending remains in the client package, which the dedicated server never loads. */
public final class ClientNetworking {
    public static void sendInput(boolean jump,boolean sneak){ClientPacketDistributor.sendToServer(new ModNetworking.InputPayload(jump,sneak));}
    public static void sendControl(int entityId,int action){ClientPacketDistributor.sendToServer(new ModNetworking.ControlPayload(entityId,action));}
    private ClientNetworking(){}
}
