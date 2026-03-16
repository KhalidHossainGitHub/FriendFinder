package com.friendfinder.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.Collections;
import java.util.List;

/**
 * Tracks other players in the client world and provides position data
 * for the minimap, radar, and teleport menu.
 */
public class PlayerTracker {
    private static PlayerTracker instance;

    public static PlayerTracker getInstance() {
        if (instance == null) {
            instance = new PlayerTracker();
        }
        return instance;
    }

    public List<AbstractClientPlayerEntity> getNearbyPlayers() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return Collections.emptyList();
        }
        return client.world.getPlayers().stream()
                .filter(p -> p != client.player)
                .toList();
    }
}
