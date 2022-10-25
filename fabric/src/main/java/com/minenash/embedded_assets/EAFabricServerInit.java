package com.minenash.embedded_assets;

import com.minenash.embedded_assets.server.EACommands;
import com.minenash.embedded_assets.server.EmbeddedAssetsServer;
import com.minenash.embedded_assets.server.LocalResourcePackHoster;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

public class EAFabricServerInit implements DedicatedServerModInitializer {

    public Identifier id(String path) { return new Identifier("embedded_assets", path); }
    @Override
    public void onInitializeServer() {
        EmbeddedAssetsServer.init( FabricLoader.getInstance().getConfigDir().resolve("embedded_assets.json") );
        ServerLifecycleEvents.SERVER_STARTED.register(id("load-resources"), EmbeddedAssetsServer::generateMasterPack);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(id("load-resources"), (server, resourceManager, success) -> EmbeddedAssetsServer.onReload());
        CommandRegistrationCallback.EVENT.register(id("commands"), EACommands::register);
        ServerPlayConnectionEvents.JOIN.register(id("send-pack"), (handler, sender, server1) -> LocalResourcePackHoster.sendPack(handler));
        ServerLifecycleEvents.SERVER_STOPPED.register(id("stop-http"), server -> LocalResourcePackHoster.stopHttpd());
    }

}
