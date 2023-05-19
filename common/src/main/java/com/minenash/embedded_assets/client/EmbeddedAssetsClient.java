package com.minenash.embedded_assets.client;

import com.google.common.collect.ImmutableList;
import com.minenash.embedded_assets.AbstractFileResourcePackAccessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
import net.minecraft.resource.metadata.PackResourceMetadata;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class EmbeddedAssetsClient {
	public static final MinecraftClient client = MinecraftClient.getInstance();
	public static final Logger LOGGER = LoggerFactory.getLogger("embedded_assets");

	public static List<ResourcePackProfile> packs = new ArrayList<>();

	public static void init(MinecraftServer server) {

	}

	public static void removeResources(MinecraftServer server) {
		if (packs.isEmpty())
			return;
		packs.clear();
		client.getResourcePackManager().scanPacks();
		client.reloadResources();
	}

	public static void getResourcePacks(MinecraftServer server) {
		List<ResourcePackProfile> before = new ArrayList<>(packs);
		packs.clear();
		for (ResourcePackProfile profile : server.getDataPackManager().getEnabledProfiles())
			if (profile.createResourcePack() instanceof AbstractFileResourcePack datapack)
				try { getResourcePack(datapack, profile); }
				catch (Throwable e) { e.printStackTrace(); }

		client.getResourcePackManager().scanPacks();
		List<String> c = new ArrayList<>(client.getResourcePackManager().getEnabledNames());
		for (ResourcePackProfile pack : packs)
			if (!before.contains(pack) && !c.contains(pack.getName()))
					c.add(pack.getName());
		client.getResourcePackManager().setEnabledProfiles(c);

		ImmutableList<String> list = ImmutableList.copyOf(client.options.resourcePacks);
		client.options.resourcePacks.clear();
		client.options.incompatibleResourcePacks.clear();
		for (ResourcePackProfile resourcePackProfile : client.getResourcePackManager().getEnabledProfiles()) {
			if (resourcePackProfile.isPinned()) continue;
			client.options.resourcePacks.add(resourcePackProfile.getName());
			if (resourcePackProfile.getCompatibility().isCompatible()) continue;
			client.options.incompatibleResourcePacks.add(resourcePackProfile.getName());
		}
		ImmutableList<String> list2 = ImmutableList.copyOf(client.options.resourcePacks);
		if (!list2.equals(list))
			client.reloadResources();

	}

	private static void getResourcePack(AbstractFileResourcePack datapack, ResourcePackProfile profile) throws IOException {
		Path path = ((AbstractFileResourcePackAccessor) datapack).getPath();
		if (!Files.exists(path))
			return;

		if (datapack instanceof ZipResourcePack) {
			if (((ZipResourcePack) datapack).openRoot("assets") != null)
				addPackToList(datapack, datapack.getName(), profile.getDescription(), profile.getCompatibility());

			ZipFile zip = new ZipFile(path.toFile());
			var entries = zip.entries();
			while (entries.hasMoreElements() ) {
				String name = entries.nextElement().getName();
				if (name.endsWith(".zip") && !name.contains("/"))
					createAndAdd("embedded/" + datapack.getName(), datapack.openRoot(name).get());
			}
				zip.close();
		}
		else {
			if (Files.exists(path.resolve("assets")))
				addPackToList(datapack, datapack.getName(), profile.getDescription(), profile.getCompatibility());
			for (Path possiblePack : Files.list(path).toList())
				if (Files.isRegularFile(possiblePack) && possiblePack.endsWith(".zip"))
					createAndAdd("embedded/" + datapack.getName(), Files.newInputStream(path));
		}

	}

	public static void addPackToList(ResourcePack pack, String name, Text description, ResourcePackCompatibility compat) {
		packs.add(ResourcePackProfile.create(pack.getName(), Text.literal(name), false, var1 -> pack,
		        ResourceType.SERVER_DATA, ResourcePackProfile.InsertionPosition.TOP, ResourcePackSource.SERVER));
	}

	public static void createAndAdd(String sourceName, InputStream rpInputStream) throws IOException {
		if (rpInputStream == null || rpInputStream.available() <= 0)
			return;

		ZipInputStream stream = new ZipInputStream(rpInputStream);
		Map<String,byte[]> data = new HashMap<>();

		for (ZipEntry entry; (entry = stream.getNextEntry()) != null;) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			stream.transferTo(bytes);
			data.put(entry.getName(), bytes.toByteArray());
		}

		if (!data.containsKey("pack.mcmeta"))
			return;

		EmbeddedZipResourcePack pack = new EmbeddedZipResourcePack(sourceName, data);
		PackResourceMetadata meta = pack.parseMetadata(PackResourceMetadata.SERIALIZER);
		addPackToList(pack, sourceName.substring(9), meta.getDescription(),
				ResourcePackCompatibility.from(meta.getPackFormat(), ResourceType.CLIENT_RESOURCES));
	}

}
