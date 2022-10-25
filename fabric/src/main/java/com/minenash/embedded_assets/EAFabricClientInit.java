package com.minenash.embedded_assets;

import com.minenash.embedded_assets.client.EmbeddedAssetsClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;

public class EAFabricClientInit  implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ServerLifecycleEvents.SERVER_STARTED.register(new Identifier("embedded_assets_load-resources"), EmbeddedAssetsClient::getResourcePacks);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(new Identifier("embedded_assets_load-resources"), (server, resourceManager, success) -> EmbeddedAssetsClient.getResourcePacks(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(new Identifier("embedded_assets_remove-resources"), EmbeddedAssetsClient::removeResources);
    }

}
