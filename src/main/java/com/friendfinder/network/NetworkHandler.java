package com.friendfinder.network;

import com.friendfinder.FriendFinderMod;
import com.friendfinder.ping.PingManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkHandler {

    private static final long MIN_PING_INTERVAL_MS = 3000;
    private static final Map<UUID, Long> lastPingTime = new ConcurrentHashMap<>();

    /**
     * Register payload types. Must be called from the main initializer
     * so both client and server know about the packet format.
     */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(PingPayload.ID, PingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PingPayload.ID, PingPayload.CODEC);
    }

    /**
     * Register server-side handler that relays pings to other players.
     * Safe to call from the main initializer (runs on integrated server too).
     */
    public static void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(PingPayload.ID, (payload, context) -> {
            ServerPlayerEntity sender = context.player();
            UUID senderId = sender.getUuid();

            long now = System.currentTimeMillis();
            Long lastTime = lastPingTime.get(senderId);
            if (lastTime != null && now - lastTime < MIN_PING_INTERVAL_MS) {
                return;
            }
            lastPingTime.put(senderId, now);

            if (sender.getServer() == null) return;

            for (ServerPlayerEntity player : sender.getServer().getPlayerManager().getPlayerList()) {
                if (player != sender && ServerPlayNetworking.canSend(player, PingPayload.ID)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        });
    }

    /**
     * Register client-side handler that receives pings from other players.
     * Must be called from the client initializer only.
     */
    public static void registerClientHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(PingPayload.ID, (payload, context) -> {
            context.client().execute(() ->
                    PingManager.getInstance().addReceivedPing(
                            payload.sender(), payload.x(), payload.y(), payload.z()));
        });
    }

    /**
     * Send a ping to the server for relay. Falls back silently if the
     * server doesn't have the mod installed.
     */
    public static void sendPing(String sender, int x, int y, int z) {
        try {
            if (ClientPlayNetworking.canSend(PingPayload.ID)) {
                ClientPlayNetworking.send(new PingPayload(sender, x, y, z));
            }
        } catch (Exception e) {
            FriendFinderMod.LOGGER.debug("Ping networking unavailable: {}", e.getMessage());
        }
    }
}
