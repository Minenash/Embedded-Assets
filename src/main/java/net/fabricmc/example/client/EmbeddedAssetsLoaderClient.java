package net.fabricmc.example.client;

import com.google.gson.JsonElement;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.example.mixin.AbstractFileResourcePackAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class EmbeddedAssetsLoaderClient implements ClientModInitializer {
	public static final MinecraftClient client = MinecraftClient.getInstance();
	public static final Logger LOGGER = LoggerFactory.getLogger("content-packs");

	public static List<ResourcePackProfile> packs = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		ServerLifecycleEvents.SERVER_STARTED.register(new Identifier("content-packs_load-resources"), EmbeddedAssetsLoaderClient::getResourcePacks);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(new Identifier("content-packs_load-resources"), (server, resourceManager, success) -> getResourcePacks(server));

		ServerLifecycleEvents.SERVER_STOPPING.register(new Identifier("content-packs_remove-resources"), server -> {
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
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(datapack.openRoot("pack.mcmeta")))){
			JsonElement assets = JsonHelper.deserialize(bufferedReader).get("pack").getAsJsonObject().get("assets");
			if (assets != null)
				EmbeddedZipResourcePack.createAndAdd("embedded/" + datapack.getName(), datapack.openRoot( assets.getAsString() ));
		}

		boolean hasAssetsFolder = false;
		if (datapack instanceof ZipResourcePack) {
			InputStream stream = datapack.openRoot("assets");
			if ( hasAssetsFolder = (stream != null) )
				stream.close();
		}
		else
			hasAssetsFolder = Files.isDirectory(((AbstractFileResourcePackAccessor) datapack).getBase().toPath().resolve("assets"));

		if (hasAssetsFolder)
			addPackToList(datapack, datapack.getName(), profile.getDescription(), profile.getCompatibility());


	}

	public static void addPackToList(ResourcePack pack, String name, Text description, ResourcePackCompatibility compat) {
		packs.add(new ResourcePackProfile(pack.getName(), false, () -> pack, Text.literal(name),
				description, compat, ResourcePackProfile.InsertionPosition.TOP, false,
				desc -> Text.literal("(datapack) ").append(desc)));
	}

}
