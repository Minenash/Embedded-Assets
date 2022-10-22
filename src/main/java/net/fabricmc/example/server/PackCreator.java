package net.fabricmc.example.server;

import com.google.gson.JsonElement;
import net.fabricmc.example.mixin.AbstractFileResourcePackAccessor;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.ZipResourcePack;
import net.minecraft.util.JsonHelper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PackCreator {

    public Map<String,byte[]> workingData = new HashMap<>();

    public Map<String,byte[]> create(List<AbstractFileResourcePack> packs) throws IOException {
        workingData.put("pack.mcmeta", EmbeddedAssetsServer.metadata);
        for (AbstractFileResourcePack datapack : packs) {
            File file = ((AbstractFileResourcePackAccessor)datapack).getBase();
            if (datapack instanceof ZipResourcePack)
                readFromZip(new FileInputStream(file));
            else {
                Path path = file.toPath().resolve("assets");
                if (Files.exists(path))
                    readDirFromDir(path);
                for (File possiblePack : file.listFiles())
                    if (possiblePack.isFile() && possiblePack.getName().endsWith(".zip"))
                        readZip(workingData, new FileInputStream(possiblePack));
            }

        }

        return workingData;
    }

    private static void readZip(Map<String,byte[]> data, InputStream rpInputStream) throws IOException {
        ZipInputStream stream = new ZipInputStream(rpInputStream);

        for (ZipEntry entry; (entry = stream.getNextEntry()) != null;) {
            if ((entry.getName().startsWith("assets/") && !data.containsKey(entry.getName())) ) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                stream.transferTo(bytes);
                data.put(entry.getName(), bytes.toByteArray());
                bytes.close();
            }
        }
        rpInputStream.close();
    }

    private void readFromZip(InputStream rpInputStream) throws IOException {
        ZipInputStream stream = new ZipInputStream(rpInputStream);
        Map<String,byte[]> data = new TreeMap<>();

        for (ZipEntry entry; (entry = stream.getNextEntry()) != null;) {
            boolean isZip = !entry.isDirectory() && !entry.getName().contains("/") && entry.getName().endsWith(".zip");

            if (isZip || (entry.getName().startsWith("assets/") && !workingData.containsKey(entry.getName())) ) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                stream.transferTo(bytes);
                if (isZip)
                    readZip(data, new ByteArrayInputStream(bytes.toByteArray()));
                else
                    workingData.put(entry.getName(), bytes.toByteArray());
                bytes.close();
            }
        }
        for (var entry : data.entrySet()) {
            if (!workingData.containsKey(entry.getKey()))
                workingData.put(entry.getKey(), entry.getValue());
        }
        rpInputStream.close();
    }

    private void readDirFromDir(Path mainPath) throws IOException {
        Files.walk(mainPath).forEach( p -> {
            String pathStr = "assets/" + mainPath.relativize(p);
            if (!workingData.containsKey(pathStr) && !Files.isDirectory(p))
                try { readBytes(pathStr, Files.newInputStream(p)); }
                catch (IOException e) { e.printStackTrace(); }
        } );
    }

    public void addBasePack() throws IOException {
        if (EmbeddedAssetsConfig.basePack.isEmpty())
            return;
        File file = new File(EmbeddedAssetsConfig.basePack);
        if (!file.exists())
            return;

        if (file.isDirectory())
            readDirFromDir(file.toPath());
        else
            readZip(workingData, new FileInputStream(file));
    }

    private void readBytes(String path, InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            input.close();
            workingData.put(path, output.toByteArray());
        }
    }

}
