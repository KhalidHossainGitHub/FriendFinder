package com.friendfinder;

import com.friendfinder.config.FriendFinderConfig;
import com.friendfinder.menu.FriendFinderMenuScreen;
import com.friendfinder.minimap.MinimapRenderer;
import com.friendfinder.network.NetworkHandler;
import com.friendfinder.ping.PingBeamRenderer;
import com.friendfinder.ping.PingManager;
import com.friendfinder.radar.FriendRadarRenderer;
import com.friendfinder.waypoint.WaypointCommand;
import com.friendfinder.waypoint.WaypointRenderer;
import com.friendfinder.worldmap.MapDataManager;
import com.friendfinder.worldmap.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FriendFinderMod implements ClientModInitializer {
    public static final String MOD_ID = "friendfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding worldMapKey;
    private static KeyBinding menuKey;
    private static KeyBinding pingKey;

    @Override
    public void onInitializeClient() {
        FriendFinderConfig.getInstance();

        registerKeybinds();
        WaypointCommand.register();
        NetworkHandler.registerClientHandlers();
        new MinimapRenderer().init();
        new WaypointRenderer().init();
        new FriendRadarRenderer().init();
        new PingBeamRenderer().init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PingManager.getInstance().tick();
            MapDataManager.getInstance().tick(client);
            while (worldMapKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new WorldMapScreen());
                }
            }
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new FriendFinderMenuScreen());
                }
            }
            while (pingKey.wasPressed()) {
                PingManager.getInstance().createLocalPing(client);
            }
        });

        LOGGER.info("FriendFinder loaded successfully!");
    }

    private void registerKeybinds() {
        worldMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.friendfinder.world_map",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.friendfinder"
        ));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.friendfinder.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.friendfinder"
        ));
        pingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.friendfinder.ping",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.friendfinder"
        ));
    }
}
