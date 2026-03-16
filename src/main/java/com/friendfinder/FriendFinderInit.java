package com.friendfinder;

import com.friendfinder.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;

public class FriendFinderInit implements ModInitializer {
    @Override
    public void onInitialize() {
        NetworkHandler.registerPayloads();
        NetworkHandler.registerServerHandlers();
    }
}
