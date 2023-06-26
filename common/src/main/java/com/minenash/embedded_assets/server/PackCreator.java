package com.minenash.embedded_assets.server;

import com.google.gson.*;
import com.minenash.embedded_assets.mixin.DirectoryResourcePackAccessor;
import com.minenash.embedded_assets.mixin.ZipResourcePackAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ZipResourcePack;
import net.minecraft.resource.metadata.ResourceFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PackCreator {

    public Map<String,byte[]> workingData = new HashMap<>();
    public Map<String,FileTime> lastModified = new HashMap<>();
    public Map<String,JsonObject> mergables = new HashMap<>();
    public List<ResourceFilter> filters = new ArrayList<>();
    public List<JsonObject> filterObjects = new ArrayList<>();
    public ResourceFilter tempFilter = null;

    public void create(List<AbstractFileResourcePack> packs) throws IOException {
        for (AbstractFileResourcePack datapack : packs) {
            if (datapack instanceof ZipResourcePack)
                readFromZip(new FileInputStream(((ZipResourcePackAccessor) datapack).getBackingZipFile()));
            else if (datapack instanceof DirectoryResourcePack){
                Path path = ((DirectoryResourcePackAccessor) datapack).getRoot();
                Path assets = path.resolve("assets");
                if (Files.exists(assets)) {
                    readDirFromDir(assets);
                    Path meta = path.resolve("pack.mcmeta");
                    if (Files.exists(meta)) {
                        processMetaForFilter(Files.readAllBytes(meta));
                        maybeAddFilter();
                    }
                }

                for (Path possiblePack : Files.list(path).toList())
                    if (Files.isRegularFile(possiblePack) && possiblePack.toString().endsWith(".zip"))
                        readZip(Files.newInputStream(possiblePack));
            }

        }
        addBasePack();

        for (var entry : mergables.entrySet()) {
            workingData.put(entry.getKey(), GSON.toJson(entry.getValue()).getBytes(StandardCharsets.UTF_8));
            lastModified.putIfAbsent(entry.getKey(), FileTime.fromMillis(0));
        }

        workingData.put("pack.mcmeta", createMetadata());
        lastModified.put("pack.mcmeta", FileTime.fromMillis(0));

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("resources.zip"))) {
            for (var entry : workingData.entrySet()) {
                ZipEntry e = new ZipEntry(entry.getKey().replace("\\", "/"));
                e.setLastModifiedTime(lastModified.getOrDefault(entry.getKey(), FileTime.from(Instant.now())));
                zos.putNextEntry(e);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            zos.flush();
        }
        LocalResourcePackHoster.hashCache = LocalResourcePackHoster.calcSHA1();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private void readZip(InputStream rpInputStream) throws IOException {
        ZipInputStream stream = new ZipInputStream(rpInputStream);

        for (ZipEntry entry; (entry = stream.getNextEntry()) != null;) {
            String name = entry.getName();
            if ((name.startsWith("assets/") && !workingData.containsKey(name) && notBlocked(name)) ) {
                Mergable mergable = mergeType(name);
                if (mergable != null)
                    mergeJsonObjects(mergable, name, mergables, stream.readAllBytes());
                else {
                    workingData.put(name, stream.readAllBytes());
                    lastModified.put(name, entry.getLastModifiedTime());
                }
            }
            else if (name.equals("pack.mcmeta"))
                processMetaForFilter( stream.readAllBytes() );
        }
        maybeAddFilter();
        rpInputStream.close();
    }

    private void readFromZip(InputStream rpInputStream) throws IOException {
        ZipInputStream stream = new ZipInputStream(rpInputStream);
        List<byte[]> zips = new ArrayList<>();

        for (ZipEntry entry; (entry = stream.getNextEntry()) != null;) {
            String name = entry.getName();
            if (name.startsWith("assets/") && !workingData.containsKey(name) && notBlocked(name)) {
                Mergable mergable = mergeType(name);
                if (mergable != null)
                    mergeJsonObjects(mergable, name, mergables, stream.readAllBytes());
                else {
                    workingData.put(name, stream.readAllBytes());
                    lastModified.put(name, entry.getLastModifiedTime());
                }
            }
            else if (name.equals("pack.mcmeta"))
                processMetaForFilter( stream.readAllBytes() );
            else if ( !entry.isDirectory() && !entry.getName().contains("/") && entry.getName().endsWith(".zip") )
                zips.add(stream.readAllBytes());
        }
        maybeAddFilter();

        for (byte[] zip : zips)
            readZip(new ByteArrayInputStream( zip ));

        rpInputStream.close();
    }

    private void readDirFromDir(Path mainPath) throws IOException {
        Files.walk(mainPath).forEach( p -> {
            String pathStr = "assets/" + mainPath.relativize(p);
            if (!workingData.containsKey(pathStr) && !Files.isDirectory(p) && notBlocked(pathStr))
                try (InputStream input = Files.newInputStream(p)) {
                    Mergable mergable = mergeType(pathStr);
                    if (mergable != null)
                        mergeJsonObjects(mergable, pathStr, mergables, input.readAllBytes());
                    else {
                        workingData.put(pathStr, input.readAllBytes());
                        lastModified.put(pathStr, Files.getLastModifiedTime(p));
                    }

                }
                catch (IOException e) { e.printStackTrace(); }
        } );
    }

    public void addBasePack() throws IOException {
        if (EAConfig.basePack.isEmpty())
            return;
        File file = new File(EAConfig.basePack);
        if (!file.exists())
            return;

        if (file.isDirectory())
            readDirFromDir(file.toPath());
        else
            readZip(new FileInputStream(file));
    }

    public void processMetaForFilter(byte[] meta) {
        try {
            JsonObject json = GSON.fromJson(new String(meta), JsonObject.class);
            if (json.has("filter")) {
                JsonObject filter = json.get("filter").getAsJsonObject();
                tempFilter = ResourceFilter.SERIALIZER.fromJson(filter);
                if (filter.has("block"))
                    for (JsonElement block : filter.get("block").getAsJsonArray())
                        filterObjects.add(block.getAsJsonObject());
            }
        }
        catch (Exception ignored) {}
    }
    public void maybeAddFilter() {
        if (tempFilter != null) {
            filters.add(tempFilter);
            tempFilter = null;
        }
    }
    public boolean notBlocked(String name) {
        if (filters.isEmpty())
            return true;

        int index = name.indexOf('/', 7);
        if (index == -1)
            return true;
        String namespace = name.substring(7,index);
        String path = name.substring(index+1);

        for (ResourceFilter filter : filters)
            if (filter.isNamespaceBlocked(namespace) && filter.isPathBlocked(path))
                return false;
        return true;

    }

    enum Mergable {MERGE_ROOT, MERGE_PROVIDERS}
    public static Mergable mergeType(String name) {
        int index = name.indexOf('/', 7) + 1;
        if (index == 0 || !name.endsWith(".json"))
            return null;
        name = name.substring(index);
        if (name.equals("sounds.json") || name.startsWith("lang/"))
            return Mergable.MERGE_ROOT;
        if (name.startsWith("font/"))
            return Mergable.MERGE_PROVIDERS;
        return null;
    }


    public static void mergeJsonObjects(Mergable type, String name, Map<String,JsonObject> mergables, byte[] fromBytes) {
        try {
            JsonObject fromEntry = GSON.fromJson(new String(fromBytes), JsonElement.class).getAsJsonObject();
            JsonObject inMap = mergables.get(name);
            mergables.put(name, mergeJsonObjects(type, inMap, fromEntry));
        }
        catch (JsonSyntaxException e) {
            System.out.println("Malformed json: " + name);
        }
    }

    public static JsonObject mergeJsonObjects(Mergable type, JsonObject inMap, JsonObject fromEntry) {
        if (inMap == null)
           return fromEntry;

        else if (type == Mergable.MERGE_ROOT) {
            for (Map.Entry<String,JsonElement> a : fromEntry.entrySet())
                if (!inMap.has(a.getKey()))
                    inMap.add(a.getKey(), a.getValue());
        }

        else if (type == Mergable.MERGE_PROVIDERS) {
            JsonArray into = inMap.getAsJsonArray("providers");
            if (into == null)
                into = new JsonArray();

            JsonArray from = inMap.getAsJsonArray("providers");
            if (from != null)
                into.addAll(from);

            inMap.add("providers", into);
        }
        return inMap;
    }

    public byte[] createMetadata() {
        JsonObject root = new JsonObject();

        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES) );
        pack.addProperty("description", "(datapacks) Do §e/embedded_assets§7 for detailed info");
        root.add("pack", pack);

        if (!filterObjects.isEmpty()) {
            JsonObject filter = new JsonObject();
            JsonArray block = new JsonArray();
            for (JsonObject o : filterObjects)
                block.add(o);
            filter.add("block", block);
            root.add("filter", filter);
        }
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);

    }

}
