package net.fabricmc.example.server;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.mojang.bridge.game.PackType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.example.mixin.AbstractFileResourcePackAccessor;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.resource.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EmbeddedAssetsServer implements DedicatedServerModInitializer {

    public Identifier id(String path) { return new Identifier("embedded_assets", path); }
    public static MinecraftServer server;
    public static byte[] metadata = """
            {
              "pack": {
                "pack_format": %s,
                "description": "(datapacks) Do §e/embedded_assets§7 for detailed info"
              }
            }
            """.formatted(SharedConstants.getGameVersion().getPackVersion(PackType.RESOURCE)).getBytes();

    @Override
    public void onInitializeServer() {
        EmbeddedAssetsConfig.read();
        LocalResourcePackHoster.startHttpd();
        ServerLifecycleEvents.SERVER_STARTED.register(id("load-resources"), EmbeddedAssetsServer::generateMasterPack);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(id("load-resources"), (server, resourceManager, success) -> generateMasterPack(server));
        CommandRegistrationCallback.EVENT.register(id("commands"), EmbeddedAssetsCommand::register);
        ServerPlayConnectionEvents.JOIN.register(id("send-pack"), LocalResourcePackHoster::sendPack);
        ServerLifecycleEvents.SERVER_STOPPED.register(id("stop-http"), server -> LocalResourcePackHoster.stopHttpd());
    }


    public static final List<ResourcePackProfile> profiles = new ArrayList<>();
    public static void generateMasterPack(MinecraftServer server) {
        EmbeddedAssetsServer.server = server;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("resources.zip"))) {
            for (var entry : new PackCreator().create(sortDataPacks(server.getDataPackManager())).entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            zos.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
        LocalResourcePackHoster.hashCache = LocalResourcePackHoster.calcSHA1();
    }

    private static List<AbstractFileResourcePack> sortDataPacks(ResourcePackManager manager) {
        profiles.clear();
        List<AbstractFileResourcePack> packs = new ArrayList<>();

        for (ResourcePackProfile profile : manager.getProfiles())
            if (!EmbeddedAssetsConfig.priority.contains(profile.getName()) && profile.createResourcePack() instanceof AbstractFileResourcePack)
                EmbeddedAssetsConfig.priority.add(profile.getName());

        EmbeddedAssetsConfig.save();

        Collection<ResourcePackProfile> enabled = manager.getEnabledProfiles();
        for (String profileName : EmbeddedAssetsConfig.priority) {
            ResourcePackProfile profile = manager.getProfile(profileName);
            if (enabled.contains(profile)) {
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