package com.friendfinder.waypoint;

import com.friendfinder.FriendFinderMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WaypointManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path WAYPOINTS_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(FriendFinderMod.MOD_ID)
            .resolve("waypoints.json");

    private static WaypointManager instance;
    private final List<Waypoint> waypoints = new ArrayList<>();

    public static WaypointManager getInstance() {
        if (instance == null) {
            instance = new WaypointManager();
            instance.load();
        }
        return instance;
    }

    public void addWaypoint(Waypoint waypoint) {
        waypoints.removeIf(w -> w.getName().equalsIgnoreCase(waypoint.getName()));
        waypoints.add(waypoint);
        save();
    }

    public boolean removeWaypoint(String name) {
        boolean removed = waypoints.removeIf(w -> w.getName().equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    public Optional<Waypoint> getWaypoint(String name) {
        return waypoints.stream()
                .filter(w -> w.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public List<Waypoint> getWaypoints() {
        return List.copyOf(waypoints);
    }

    private void load() {
        try {
            if (Files.exists(WAYPOINTS_FILE)) {
                String json = Files.readString(WAYPOINTS_FILE);
                Type listType = new TypeToken<List<Waypoint>>() {}.getType();
                List<Waypoint> loaded = GSON.fromJson(json, listType);
                if (loaded != null) {
                    waypoints.addAll(loaded);
                }
            }
        } catch (IOException e) {
            FriendFinderMod.LOGGER.error("Failed to load waypoints", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(WAYPOINTS_FILE.getParent());
            Files.writeString(WAYPOINTS_FILE, GSON.toJson(waypoints));
        } catch (IOException e) {
            FriendFinderMod.LOGGER.error("Failed to save waypoints", e);
        }
    }
}
