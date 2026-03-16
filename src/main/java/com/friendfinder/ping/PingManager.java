package com.friendfinder.ping;

import com.friendfinder.FriendFinderMod;
import com.friendfinder.config.FriendFinderConfig;
import com.friendfinder.network.NetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PingManager {
    private static PingManager instance;
    private final List<Ping> activePings = new ArrayList<>();

    public static PingManager getInstance() {
        if (instance == null) {
            instance = new PingManager();
        }
        return instance;
    }

    public void createLocalPing(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        BlockPos pos = client.player.getBlockPos();
        String sender = client.player.getName().getString();

        Ping ping = new Ping(sender, pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis());
        activePings.add(ping);

        client.player.sendMessage(
                Text.literal(sender + " pinged: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                        .formatted(Formatting.LIGHT_PURPLE),
                false);

        NetworkHandler.sendPing(sender, pos.getX(), pos.getY(), pos.getZ());

        FriendFinderMod.LOGGER.info("Ping created at {}, {}, {}", pos.getX(), pos.getY(), pos.getZ());
    }

    public void addReceivedPing(String sender, int x, int y, int z) {
        Ping ping = new Ping(sender, x, y, z, System.currentTimeMillis());
        activePings.add(ping);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal(sender + " pinged: " + x + ", " + y + ", " + z)
                            .formatted(Formatting.LIGHT_PURPLE),
                    false);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long durationMs = FriendFinderConfig.getInstance().pingDurationSeconds * 1000L;
        Iterator<Ping> it = activePings.iterator();
        while (it.hasNext()) {
            if (now - it.next().timestamp() > durationMs) {
                it.remove();
            }
        }
    }

    public void clear() {
        activePings.clear();
    }

    public List<Ping> getActivePings() {
        return List.copyOf(activePings);
    }

    /**
     * Returns a unique bright color for a player name, consistent across calls.
     * Uses HSB color space so every name gets a distinct, saturated hue.
     */
    public static int getPlayerColor(String playerName) {
        float hue = ((playerName.hashCode() & 0x7FFFFFFF) % 360) / 360.0f;
        return hsbToRgb(hue, 0.8f, 1.0f);
    }

    public static float[] getPlayerColorF(String playerName) {
        int c = getPlayerColor(playerName);
        return new float[]{
                ((c >> 16) & 0xFF) / 255.0f,
                ((c >> 8) & 0xFF) / 255.0f,
                (c & 0xFF) / 255.0f
        };
    }

    private static int hsbToRgb(float hue, float sat, float bri) {
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        float f = h - (float) Math.floor(h);
        float p = bri * (1 - sat);
        float q = bri * (1 - sat * f);
        float t = bri * (1 - sat * (1 - f));
        float r, g, b;
        switch ((int) h) {
            case 0 -> { r = bri; g = t;   b = p; }
            case 1 -> { r = q;   g = bri; b = p; }
            case 2 -> { r = p;   g = bri; b = t; }
            case 3 -> { r = p;   g = q;   b = bri; }
            case 4 -> { r = t;   g = p;   b = bri; }
            default -> { r = bri; g = p;   b = q; }
        }
        return 0xFF000000
                | ((int) (r * 255 + 0.5f) << 16)
                | ((int) (g * 255 + 0.5f) << 8)
                | (int) (b * 255 + 0.5f);
    }

    public record Ping(String sender, int x, int y, int z, long timestamp) {
        public float getAlpha(long now, long durationMs) {
            long age = now - timestamp;
            long fadeStart = durationMs - 5_000L;
            if (age < fadeStart) return 1.0f;
            if (age > durationMs) return 0.0f;
            return 1.0f - (float) (age - fadeStart) / 5_000f;
        }
    }
}
