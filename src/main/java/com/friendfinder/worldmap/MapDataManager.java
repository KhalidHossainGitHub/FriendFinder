package com.friendfinder.worldmap;

import com.friendfinder.FriendFinderMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Continuously scans loaded chunks in the background and persists
 * explored terrain data to disk so the world map shows everywhere
 * the player has ever visited.
 *
 * Storage: .minecraft/config/friendfinder/maps/{world}/{dimension}.dat
 */
public class MapDataManager {

    private static final Path BASE_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve(FriendFinderMod.MOD_ID).resolve("maps");
    private static final int DATA_VERSION = 1;

    private static MapDataManager instance;

    private final Map<Long, int[]> explored = new HashMap<>();
    private String worldId = "";
    private String dimension = "";
    private boolean dirty;
    private int tickCounter;
    private int saveTimer;

    public static MapDataManager getInstance() {
        if (instance == null) {
            instance = new MapDataManager();
            cleanupStaleCache();
        }
        return instance;
    }

    private static void cleanupStaleCache() {
        Path stale = BASE_DIR.resolve("unknown");
        if (Files.exists(stale)) {
            try {
                try (var entries = Files.list(stale)) {
                    entries.forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException ignored) {} });
                }
                Files.deleteIfExists(stale);
                FriendFinderMod.LOGGER.info("Cleaned up stale 'unknown' map cache");
            } catch (IOException ignored) {}
        }
    }

    // ── tick (called every client tick from FriendFinderMod) ─────────────

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            if (!worldId.isEmpty() && dirty) save();
            if (!worldId.isEmpty()) {
                worldId = "";
                dimension = "";
                explored.clear();
            }
            return;
        }

        String wid = resolveWorldId(client);
        String dim = client.world.getRegistryKey().getValue().toString();

        if (!wid.equals(worldId) || !dim.equals(dimension)) {
            if (dirty) save();
            worldId = wid;
            dimension = dim;
            explored.clear();
            load();
            FriendFinderMod.LOGGER.info("Map world ID resolved to: {} (dim: {})", worldId, dimension);
        }

        tickCounter++;
        if (tickCounter % 10 == 0) scanNearby(client);

        saveTimer++;
        if (saveTimer >= 2400 && dirty) {
            save();
            saveTimer = 0;
        }
    }

    // ── public API for WorldMapScreen ────────────────────────────────────

    /**
     * Returns cached colors for the chunk, or samples it live if the chunk
     * is currently loaded but not yet in the cache.  Returns null if the
     * chunk has never been explored and isn't loaded right now.
     */
    public int[] getOrSample(World world, int cx, int cz) {
        long key = key(cx, cz);
        int[] cached = explored.get(key);
        if (cached != null) return cached;

        if (!world.isChunkLoaded(cx, cz)) return null;

        int[] colors = sampleChunk(world, cx, cz);
        if (colors == null) return null;
        explored.put(key, colors);
        dirty = true;
        return colors;
    }

    public void forceSave() {
        if (dirty) save();
    }

    // ── background scanning ──────────────────────────────────────────────

    private void scanNearby(MinecraftClient client) {
        World world = client.world;
        int pcx = client.player.getChunkPos().x;
        int pcz = client.player.getChunkPos().z;
        int rd = client.options.getViewDistance().getValue();
        int scanned = 0;

        // spiral outward from player so nearest chunks are cached first
        for (int r = 0; r <= rd && scanned < 8; r++) {
            for (int dx = -r; dx <= r && scanned < 8; dx++) {
                for (int dz = -r; dz <= r && scanned < 8; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int cx = pcx + dx, cz = pcz + dz;
                    long k = key(cx, cz);
                    if (explored.containsKey(k)) continue;
                    if (!world.isChunkLoaded(cx, cz)) continue;

                    int[] colors = sampleChunk(world, cx, cz);
                    if (colors != null) {
                        explored.put(k, colors);
                        dirty = true;
                    }
                    scanned++;
                }
            }
        }
    }

    // ── chunk sampling (height-shaded, water-depth, ravine-aware) ────────

    static int[] sampleChunk(World world, int cx, int cz) {
        int bx = cx << 4, bz = cz << 4;
        int[] colors = new int[256];
        int[] heights = new int[256];
        boolean any = false;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = bx + lx, wz = bz + lz, idx = lx + lz * 16;
                try {
                    int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1;
                    if (topY < world.getBottomY()) continue;

                    BlockPos pos = new BlockPos(wx, topY, wz);
                    BlockState st = world.getBlockState(pos);
                    if (st.isAir()) { heights[idx] = topY; continue; }

                    heights[idx] = topY;

                    if (!st.getFluidState().isEmpty()) {
                        int waterBase = 0xFF000000 | BiomeColors.getWaterColor(world, pos);
                        int depth = 0;
                        for (int dy = topY - 1; dy >= world.getBottomY() && depth < 24; dy--) {
                            BlockState b = world.getBlockState(new BlockPos(wx, dy, wz));
                            if (b.getFluidState().isEmpty() && !b.isAir()) break;
                            depth++;
                        }
                        colors[idx] = shade(waterBase,
                                Math.max(0.5f, 1.0f - depth * 0.03f));
                        any = true;
                        continue;
                    }

                    MapColor mc = st.getMapColor(world, pos);
                    if (mc == MapColor.CLEAR) continue;

                    if (isFoliage(st)) {
                        BlockState below = world.getBlockState(new BlockPos(wx, topY - 1, wz));
                        if (below.isAir() || !below.getFluidState().isEmpty()) {
                            int real = topY - 1;
                            while (real > world.getBottomY()) {
                                BlockState s = world.getBlockState(new BlockPos(wx, real, wz));
                                if (!s.isAir() && s.getFluidState().isEmpty() && !isFoliage(s))
                                    break;
                                real--;
                            }
                            heights[idx] = real;
                            BlockState surf = world.getBlockState(new BlockPos(wx, real, wz));
                            MapColor sm = surf.getMapColor(world, new BlockPos(wx, real, wz));
                            if (sm != MapColor.CLEAR) {
                                colors[idx] = 0xFF000000 | sm.color;
                                any = true;
                                continue;
                            }
                        }
                        colors[idx] = 0xFF000000 | BiomeColors.getGrassColor(world, pos);
                        any = true;
                        continue;
                    }

                    colors[idx] = 0xFF000000 | mc.color;
                    any = true;
                } catch (Exception e) { /* skip */ }
            }
        }

        if (!any) return null;

        int[] shaded = new int[256];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int idx = lx + lz * 16;
                if (colors[idx] == 0) continue;
                int h = heights[idx];
                int hN = lz > 0 ? heights[lx + (lz - 1) * 16] : h;
                int hW = lx > 0 ? heights[(lx - 1) + lz * 16] : h;
                int diff = h - (hN + hW) / 2;
                float s;
                if (diff > 0) s = Math.min(1.3f, 1.0f + diff * 0.06f);
                else if (diff < 0) s = Math.max(0.65f, 1.0f + diff * 0.06f);
                else s = 1.0f;
                shaded[idx] = shade(colors[idx], s);
            }
        }
        return shaded;
    }

    private static boolean isFoliage(BlockState st) {
        return st.getBlock() == Blocks.GRASS_BLOCK
                || st.getBlock() == Blocks.SHORT_GRASS
                || st.getBlock() == Blocks.TALL_GRASS
                || st.getBlock() == Blocks.FERN;
    }

    static int shade(int argb, float f) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ── persistence (GZIP-compressed binary, per world + dimension) ──────

    private void save() {
        if (worldId.isEmpty() || "unknown".equals(worldId) || dimension.isEmpty()) return;
        try {
            Path dir = BASE_DIR.resolve(sanitize(worldId));
            Files.createDirectories(dir);
            Path file = dir.resolve(sanitize(dimension) + ".dat");

            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(DATA_VERSION);
                out.writeInt(explored.size());
                for (var entry : explored.entrySet()) {
                    long k = entry.getKey();
                    out.writeInt((int) (k >> 32));
                    out.writeInt((int) k);
                    for (int c : entry.getValue()) out.writeInt(c);
                }
            }
            dirty = false;
            FriendFinderMod.LOGGER.debug("Saved {} explored chunks for {}/{}",
                    explored.size(), worldId, dimension);
        } catch (IOException e) {
            FriendFinderMod.LOGGER.error("Failed to save map data", e);
        }
    }

    private void load() {
        if ("unknown".equals(worldId)) return;
        Path file = BASE_DIR.resolve(sanitize(worldId))
                .resolve(sanitize(dimension) + ".dat");
        if (!Files.exists(file)) return;

        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version != DATA_VERSION) return;

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                int[] colors = new int[256];
                for (int j = 0; j < 256; j++) colors[j] = in.readInt();
                explored.put(key(cx, cz), colors);
            }
            FriendFinderMod.LOGGER.info("Loaded {} explored chunks for {}/{}",
                    explored.size(), worldId, dimension);
        } catch (IOException e) {
            FriendFinderMod.LOGGER.error("Failed to load map data", e);
        }
    }

    // ── utils ────────────────────────────────────────────────────────────

    private static String resolveWorldId(MinecraftClient client) {
        ServerInfo entry = client.getCurrentServerEntry();
        if (entry != null && entry.address != null && !entry.address.isEmpty()) {
            return "mp_" + entry.address;
        }

        ClientPlayNetworkHandler netHandler = client.getNetworkHandler();
        if (netHandler != null) {
            ServerInfo netInfo = netHandler.getServerInfo();
            if (netInfo != null && netInfo.address != null && !netInfo.address.isEmpty()) {
                return "mp_" + netInfo.address;
            }
        }

        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            return "sp_" + client.getServer().getSaveProperties().getLevelName();
        }
        return "unknown";
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
