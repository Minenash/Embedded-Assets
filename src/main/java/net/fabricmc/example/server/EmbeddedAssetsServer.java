package net.fabricmc.example.server;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resource.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.*;
import java.util.*;

public class EmbeddedAssetsServer implements DedicatedServerModInitializer {

    public Identifier id(String path) { return new Identifier("embedded_assets", path); }
    public static MinecraftServer server;

    @Override
    public void onInitializeServer() {
        EAConfig.read();
        LocalResourcePackHoster.startHttpd();
        ServerLifecycleEvents.SERVER_STARTED.register(id("load-resources"), EmbeddedAssetsServer::generateMasterPack);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(id("load-resources"), (server, resourceManager, success) -> {
            if (EAConfig.regenerateOnReload)
                generateMasterPack(server);
        });
        CommandRegistrationCallback.EVENT.register(id("commands"), EACommands::register);
        ServerPlayConnectionEvents.JOIN.register(id("send-pack"), LocalResourcePackHoster::sendPack);
        ServerLifecycleEvents.SERVER_STOPPED.register(id("stop-http"), server -> LocalResourcePackHoster.stopHttpd());
    }


    public static final List<ResourcePackProfile> profiles = new ArrayList<>();
    public static void generateMasterPack(MinecraftServer server) {
        try {
            new PackCreator().create(sortDataPacks(server.getDataPackManager()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<AbstractFileResourcePack> sortDataPacks(ResourcePackManager manager) {
        profiles.clear();
        List<AbstractFileResourcePack> packs = new ArrayList<>();

        EAConfig.priority.removeIf(profile -> !manager.hasProfile(profile));

        for (ResourcePackProfile profile : manager.getProfiles())
            if (!EAConfig.priority.contains(profile.getName()) && profile.createResourcePack() instanceof AbstractFileResourcePack)
                EAConfig.priority.add(profile.getName());

        EAConfig.save();

        Collection<ResourcePackProfile> enabled = manager.getEnabledProfiles();
        for (String profileName : EAConfig.priority) {
            ResourcePackProfile profile = manager.getProfile(profileName);
            if (enabled.contains(profile) && !EAConfig.disabledResourcePacks.contains(profileName)) {
                ResourcePack pack = profile.createResourcePack();
                if (pack instanceof AbstractFileResourcePack frp) {
                    packs.add(frp);
                    profiles.add(profile);
                }
            }
        }

        return packs;
    }

}
