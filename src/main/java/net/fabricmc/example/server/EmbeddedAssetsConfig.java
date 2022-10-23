package net.fabricmc.example.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("InstantiationOfUtilityClass")
public class EmbeddedAssetsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().excludeFieldsWithModifiers(Modifier.PRIVATE).create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("EmbeddedAssetsLoader.json");

    public static boolean regenerateOnReload = false;
    public static String basePack = "";
    public static List<String> priority = new ArrayList<>();
    public static Set<String> disabledResourcePacks = new HashSet<>();
    public static LocalResourcePackHostingConfig localResourcePackHostingConfig = new LocalResourcePackHostingConfig();

    public static class LocalResourcePackHostingConfig {
        public boolean enabled = false;
        public int port = 25564;
        public boolean verboseLogging = false;
        public boolean local = false;
        public String promptMsg = "Includes assets for datapacks on this server";
        public boolean requireClientToHavePack = false;
    }

    public static Text getPromptMsg() {
        try {
            return Text.Serializer.fromJson(EmbeddedAssetsConfig.localResourcePackHostingConfig.promptMsg);
        }
        catch (Exception e) {
            return Text.literal(EmbeddedAssetsConfig.localResourcePackHostingConfig.promptMsg);
        }
    }

    public static boolean read() {
        if (!Files.exists(PATH)) {
            save();
            return true;
        }

        try (BufferedReader reader = Files.newBufferedReader(PATH)) {
            GSON.fromJson(reader, EmbeddedAssetsConfig.class);
            return true;
        } catch (IOException e) {
            System.out.println("Failed to read config, priorities unavailable until config is fixed:");
            e.printStackTrace();
            return false;
        }
    }

    public static void save() {
        try {
            Files.write(PATH, GSON.toJson(EmbeddedAssetsConfig.class.newInstance()).getBytes());
        } catch (Exception e) {
            System.out.println("Failed to save config:");
            e.printStackTrace();
        }
    }

}
