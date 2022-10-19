package net.fabricmc.example.server;

import com.google.gson.JsonElement;
import com.mojang.bridge.game.PackType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.example.client.EmbeddedZipResourcePack;
import net.fabricmc.example.mixin.server.AbstractFileResourcePackAccessor;
import net.fabricmc.example.mixin.server.ZipResourcePackAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.SharedConstants;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ZipResourcePack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EmbeddedAssetsLoaderServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(new Identifier("content-packs_load-resources"), EmbeddedAssetsLoaderServer::generateMasterPack);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(new Identifier("content-packs_load-resources"), (server, resourceManager, success) -> generateMasterPack(server));
    }

    private static List<String> files = new ArrayList<>();

    private static void generateMasterPack(MinecraftServer server) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("resources.zip"))) {
            files.clear();

            for (ResourcePackProfile profile : server.getDataPackManager().getEnabledProfiles()) {

                if (!(profile.createResourcePack() instanceof AbstractFileResourcePack datapack))
                    continue;

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

            zos.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
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
