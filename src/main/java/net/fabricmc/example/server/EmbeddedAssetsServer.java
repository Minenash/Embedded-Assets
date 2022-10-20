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
import net.minecraft.util.JsonHelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EmbeddedAssetsServer implements DedicatedServerModInitializer {

    public Identifier id(String path) { return new Identifier("embedded_assets", path); }
    public static MinecraftServer server;

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
    private static final List<String> files = new ArrayList<>();
    public static void generateMasterPack(MinecraftServer server) {
        EmbeddedAssetsServer.server = server;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("resources.zip"))) {
            files.clear();

            for (AbstractFileResourcePack datapack : sortDataPacks(server.getDataPackManager())) {

                File file = ((AbstractFileResourcePackAccessor)datapack).getBase();
                if (datapack instanceof ZipResourcePack) {
                    InputStream stream = datapack.openRoot("assets");
                    if (stream != null) {
                        stream.close();
                        readDirFromZip( new ZipFile(file), zos);
                    }
                }
                else {
                    Path path = file.toPath().resolve("assets");
                    if (Files.exists(path))
                        readDirFromDir(path, zos);
                }

                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(datapack.openRoot("pack.mcmeta")))){
                    JsonElement assets = JsonHelper.deserialize(bufferedReader).get("pack").getAsJsonObject().get("assets");
                    if (assets != null)
                        readZipInputStream( datapack.openRoot(assets.getAsString()), zos);
                }

            }

            addMetadata(zos);
            zos.flush();


        } catch (IOException e) {
            e.printStackTrace();
        }
        LocalResourcePackHoster.hashCache = LocalResourcePackHoster.calcSHA1();
    }

    public static void addMetadata(ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry("pack.mcmeta"));
        zos.write("""
                    {
                      "pack": {
                        "pack_format": %s,
                        "description": "(datapacks) Do §e/embedded_assets§7 for detailed info"
                      }
                    }
                    """.formatted(SharedConstants.getGameVersion().getPackVersion(PackType.RESOURCE)).getBytes());
        zos.closeEntry();
    }

    private static List<AbstractFileResourcePack> sortDataPacks(ResourcePackManager manager) {
        profiles.clear();
        List<AbstractFileResourcePack> packs = new ArrayList<>();

        for (ResourcePackProfile profile : manager.getProfiles()) {
            if (!EmbeddedAssetsConfig.priority.contains(profile.getName()) && profile.createResourcePack() instanceof AbstractFileResourcePack)
                EmbeddedAssetsConfig.priority.add(profile.getName());
        }
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

    private static void readZipInputStream(InputStream rpInputStream, ZipOutputStream out) throws IOException {
        ZipInputStream stream = new ZipInputStream(rpInputStream);
        byte[] buffer = new byte[2048];

        for (ZipEntry entry; (entry = stream.getNextEntry()) != null;) {
            if (entry.getName().startsWith("assets/")) {
                if (files.contains(entry.getName()))
                    continue;
                out.putNextEntry(entry);
                for (int len; (len = stream.read(buffer)) > 0; )
                    out.write(buffer, 0, len);
                out.closeEntry();
                files.add(entry.getName());
            }
        }
    }

    private static void readDirFromZip(ZipFile zip, ZipOutputStream out) throws IOException {
        byte[] buffer = new byte[2048];
        Enumeration<? extends ZipEntry> entries = zip.entries();
        for (ZipEntry entry = entries.nextElement(); entries.hasMoreElements(); entry = entries.nextElement()) {
            if (entry.getName().startsWith("assets/")) {
                if (files.contains(entry.getName()))
                    continue;
                out.putNextEntry(entry);
                try (InputStream is = zip.getInputStream(entry)) {
                    for (int len; (len = is.read(buffer)) > 0; )
                        out.write(buffer, 0, len);
                }
                out.closeEntry();
                files.add(entry.getName());
            }
        }
    }

    private static void readDirFromDir(Path mainPath, ZipOutputStream out) throws IOException {
        byte[] buffer = new byte[2048];
        Files.walk(mainPath).forEach( p -> {
            String pathStr = "assets/" + mainPath.relativize(p);
            if (files.contains(pathStr) || Files.isDirectory(p))
                return;
            try (InputStream is = Files.newInputStream(p)) {
                out.putNextEntry(new ZipEntry(pathStr));
                for (int len; (len = is.read(buffer)) > 0; )
                    out.write(buffer, 0, len);
                out.closeEntry();
                files.add(pathStr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } );
    }

}
