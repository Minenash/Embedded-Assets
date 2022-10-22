package net.fabricmc.example.client;

import com.google.gson.JsonElement;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.example.mixin.AbstractFileResourcePackAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
import net.minecraft.resource.metadata.PackResourceMetadata;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class EmbeddedAssetsClient implements ClientModInitializer {
	public static final MinecraftClient client = MinecraftClient.getInstance();
	public static final Logger LOGGER = LoggerFactory.getLogger("content-packs");

	public static List<ResourcePackProfile> packs = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		ServerLifecycleEvents.SERVER_STARTED.register(new Identifier("content-packs_load-resources"), EmbeddedAssetsClient::getResourcePacks);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(new Identifier("content-packs_load-resources"), (server, resourceManager, success) -> getResourcePacks(server));

		ServerLifecycleEvents.SERVER_STOPPING.register(new Identifier("content-packs_remove-resources"), server -> {
			if (packs.isEmpty())
				return;
			packs.clear();
			client.getResourcePackManager().scanPacks();
			client.reloadResources();
		});
	}

	private static void getResourcePacks(MinecraftServer server) {
		packs.clear();
		for (ResourcePackProfile profile : server.getDataPackManager().getEnabledProfiles())
			if (profile.createResourcePack() instanceof AbstractFileResourcePack datapack)
				try { getResourcePack(datapack, profile); }
				catch (Throwable e) { e.printStackTrace(); }
		if (!packs.isEmpty()) {
			client.getResourcePackManager().scanPacks();
			List<String> c = new ArrayList<>(client.getResourcePackManager().getEnabledNames());
			for (ResourcePackProfile pack : packs)
				c.add(pack.getName());
			client.getResourcePackManager().setEnabledProfiles(c);
			client.reloadResources();
		}

	}

	private static void getResourcePack(AbstractFileResourcePack datapack, ResourcePackProfile profile) throws IOException {
		File file = ((AbstractFileResourcePackAccessor) datapack).getBase();
		if (!file.exists())
			return;

		if (datapack instanceof ZipResourcePack) {
			if (((ZipResourcePack) datapack).containsFile("assets"))
				addPackToList(datapack, datapack.getName(), profile.getDescription(), profile.getCompatibility());

			ZipFile zip = new ZipFile(file);
			var entries = zip.entries();
			while (entries.hasMoreElements() ) {
				String name = entries.nextElement().getName();
				if (name.endsWith(".zip") && !name.contains("/"))
					createAndAdd("embedded/" + datapack.getName(), datapack.openRoot(name));
			}
				zip.close();
		}
		else {
			if (Files.exists(file.toPath().resolve("assets")))
				addPackToList(datapack, datapack.getName(), profile.getDescription(), profile.getCompatibility());
			for (File possiblePack : file.listFiles())
				if (possiblePack.isFile() && possiblePack.getName().endsWith(".zip"))
					createAndAdd("embedded/" + datapack.getName(), new FileInputStream(possiblePack));
		}

	}

	public static void addPackToList(ResourcePack pack, String name, Text description, ResourcePackCompatibility compat) {
		packs.add(new ResourcePackProfile(pack.getName(), false, () -> pack, Text.literal(name),
				description, compat, ResourcePackProfile.InsertionPosition.TOP, false,
				desc -> Text.literal("(datapack) ").append(desc)));
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
		PackResourceMetadata meta = pack.parseMetadata(PackResourceMetadata.READER);
		addPackToList(pack, sourceName.substring(9), meta.getDescription(),
				ResourcePackCompatibility.from(meta, ResourceType.CLIENT_RESOURCES));
	}

}
