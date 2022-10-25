package com.minenash.embedded_assets.server;

import net.minecraft.resource.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EmbeddedAssetsServer {

    public static MinecraftServer server;
    public static Path configPath;

    public static void init(Path config) {
        EmbeddedAssetsServer.configPath = config;
        EAConfig.read();
        LocalResourcePackHoster.startHttpd();
    }

    public static final List<ResourcePackProfile> profiles = new ArrayList<>();
    public static void generateMasterPack(MinecraftServer server) {
        EmbeddedAssetsServer.server = server;
        try {
            new PackCreator().create(sortDataPacks(server.getDataPackManager()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void onReload() {
        if (EAConfig.regenerateOnReload)
            EmbeddedAssetsServer.generateMasterPack(server);
    }

    public static List<AbstractFileResourcePack> sortDataPacks(ResourcePackManager manager) {
        profiles.clear();
        List<AbstractFileResourcePack> packs = new ArrayList<>();

        EAConfig.priority.removeIf(profile -> !manager.hasProfile(profile) );

        for (ResourcePackProfile profile : manager.getProfiles()) {
            if (!EAConfig.priority.contains(profile.getName()) && isNormalDatapack(profile.createResourcePack()))
                EAConfig.priority.add(profile.getName());
        }

        EAConfig.save();

        Collection<ResourcePackProfile> enabled = manager.getEnabledProfiles();
        for (String profileName : EAConfig.priority) {
            ResourcePackProfile profile = manager.getProfile(profileName);
            if (enabled.contains(profile) && !EAConfig.disabledResourcePacks.contains(profileName)) {
                ResourcePack pack = profile.createResourcePack();
                if (isNormalDatapack(pack)) {
                    packs.add( (AbstractFileResourcePack) pack);
                    profiles.add(profile);
                }
            }
        }

        return packs;
    }

    public static boolean isNormalDatapack(ResourcePack pack) {
        return pack instanceof ZipResourcePack || pack instanceof DirectoryResourcePack;
    }

}
