package com.minenash.embedded_assets.forge;

import com.minenash.embedded_assets.client.EmbeddedAssetsClient;
import com.minenash.embedded_assets.server.EACommands;
import com.minenash.embedded_assets.server.EmbeddedAssetsServer;
import com.minenash.embedded_assets.server.LocalResourcePackHoster;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;


@Mod("embedded_assets")
public class EmbeddedAssetsMod {

    static MinecraftServer server;

    public EmbeddedAssetsMod() {

        FMLJavaModLoadingContext.get().getModEventBus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerClient(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(EmbeddedAssetsMod::clientServerStart);
        MinecraftForge.EVENT_BUS.addListener(EmbeddedAssetsMod::clientServerReload);
        MinecraftForge.EVENT_BUS.addListener(EmbeddedAssetsMod::clientServerStop);
    }

    @SubscribeEvent
    public void registerServer(FMLDedicatedServerSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(EmbeddedAssetsMod::serverServerStart);
        MinecraftForge.EVENT_BUS.addListener(EmbeddedAssetsMod::serverServerReload);
        MinecraftForge.EVENT_BUS.addListener(EmbeddedAssetsMod::serverServerStop);
    }

    public static void clientServerStart(ServerStartedEvent event) {
        server = event.getServer();
        EmbeddedAssetsClient.getResourcePacks(server);

    }

    public static void clientServerReload(AddReloadListenerEvent event) {
        if (server != null)
            EmbeddedAssetsClient.getResourcePacks(server);
    }

    public static void clientServerStop(ServerStoppedEvent event) {
        EmbeddedAssetsClient.removeResources(server);
    }

    public static void serverServerStart(ServerStartedEvent event) {
        EmbeddedAssetsServer.init( FMLPaths.CONFIGDIR.get().resolve("embedded_assets.json") );
        EmbeddedAssetsServer.generateMasterPack(event.getServer());
    }

    public static void serverServerReload(AddReloadListenerEvent event) {
        EmbeddedAssetsServer.onReload();
        EACommands.register( event.getServerResources().getCommandManager().getDispatcher(), null, null );

    }

    public static void serverServerStop(ServerStoppedEvent event) {
        LocalResourcePackHoster.stopHttpd();
    }



}
