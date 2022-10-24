package net.fabricmc.example.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.fabricmc.example.server.EmbeddedAssetsConfig.localResourcePackHostingConfig;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EACommands {

    private static final Predicate<ServerCommandSource> OP = source -> source.hasPermissionLevel(2);
    private static final Predicate<String> RP_ENABLED = pack -> !EmbeddedAssetsConfig.disabledResourcePacks.contains(pack);
    private static final Predicate<String> RP_DISABLED = pack -> EmbeddedAssetsConfig.disabledResourcePacks.contains(pack);
    private static final Predicate<String> RP_ALL = pack -> true;
    private static RequiredArgumentBuilder<ServerCommandSource, String> datpackArg(Predicate<String> filter) {
        return argument("datapack", string()).suggests( (context,builder) -> getDatapackSuggestions(context, builder, filter) );
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("embedded_assets")
            .executes( EACommands::getInfo )
            .then(literal("regenerate").requires(OP)
                .executes( EACommands::regenerate ))
                .then(literal("regen_on_reload").requires(OP)
                        .executes(EACommands::showRegenOnReload)
                    .then(argument("regenerate_pack_on_reload", bool())
                        .executes(EACommands::regenOnReload)))
            .then(literal("packs").requires(OP)
                .executes( EACommands::getPacks)
                    .then(literal("enable")
                        .then(datpackArg(RP_DISABLED)
                            .executes(EACommands::enabledRP)))
                    .then(literal("disable")
                        .then(datpackArg(RP_ENABLED)
                            .executes(EACommands::disableRP)))
                .then(literal("set_priority")
                    .then(datpackArg(RP_ALL)
                        .then(argument("priority", integer(1))
                            .executes( EACommands::setPriority )))))
            .then(literal("pack_hosting").requires(OP)
                .executes( EACommands::statusLocalHosting )
                .then(literal("status").executes( EACommands::statusLocalHosting ))
                    .then(literal("enable").executes(EACommands::enableLocalHosting ))
                    .then(literal("disable").executes(EACommands::disableLocalHosting ))
                    .then(literal("config")
                        .then(literal("reload").executes( EACommands::reload ))
                        .then(literal("set")
                            .then(argument("port", integer())
                                .then(argument("local", bool())
                                    .then(argument("verbose_logging", bool())
                                        .then(argument("require_pack", bool())
                                            .then(argument("prompt_msg", greedyString())
                                                .executes( EACommands::configureLocalHosting ))))))))
                )

        );
    }

    private static CompletableFuture<Suggestions> getDatapackSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder, Predicate<String> filter) {
        for (var t : context.getSource().getServer().getDataPackManager().getProfiles())
            if (t.createResourcePack() instanceof AbstractFileResourcePack && filter.test(t.getName()))
                builder.suggest("\"" + t.getName() + "\"");
        return builder.buildFuture();
    }

    private static int regenerate(CommandContext<ServerCommandSource> context) {
        sendMessage(context, "Resource Pack regenerating", false);
        EmbeddedAssetsServer.generateMasterPack(context.getSource().getServer());

        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList())
            LocalResourcePackHoster.sendPack(player);

        return sendMessage(context, "Resource Pack regenerated", false);
    }

    private static int regenOnReload(CommandContext<ServerCommandSource> context) {
        EmbeddedAssetsConfig.regenerateOnReload = BoolArgumentType.getBool(context, "regenerate_pack_on_reload");
        EmbeddedAssetsConfig.save();

        if (EmbeddedAssetsConfig.regenerateOnReload)
            return sendMessage(context, "Resources will now regenerate on §e/reload", false);
        return sendMessage(context, "Resources will no longer regenerate on §e/reload", false);
    }

    private static int showRegenOnReload(CommandContext<ServerCommandSource> context) {
        return sendMessage(context, "Regenerate Resources on Reload: §e" + EmbeddedAssetsConfig.regenerateOnReload, false);
    }

    private static int getInfo(CommandContext<ServerCommandSource> context) {
        if (EmbeddedAssetsServer.profiles.isEmpty())
            return sendMessage(context, "No Resourced Data Packs are Loaded", true);

        MutableText text = Text.literal("\n§8§u[§aResourced Data Packs§8]§r");
        for (ResourcePackProfile profile : EmbeddedAssetsServer.profiles) {
            text.append("\n\n§l" + profile.getName());
            text.append("\n§7").append(profile.getDescription());
        }
        context.getSource().sendFeedback(text, false);
        return 1;
    }

    private static int getPacks(CommandContext<ServerCommandSource> context) {
        if (EmbeddedAssetsServer.profiles.isEmpty())
            return sendMessage(context, "No Embedded Assets are Loaded", true);

        StringBuilder builder = new StringBuilder("\n§8§u[§aPack Priorities and Enablement§8]§r\n");

        boolean doubleDigits = EmbeddedAssetsConfig.priority.size() >= 10;
        for (int i = 0; i < EmbeddedAssetsConfig.priority.size(); i++) {
            builder.append("\n§e");
            if (doubleDigits && i+1 >= 10)
                builder.append(" ");
            String name = EmbeddedAssetsConfig.priority.get(i);
            builder.append(i+1).append("§7: ").append(getDatapackColor(context, name)).append(name);
        }


        context.getSource().sendFeedback(Text.literal(builder.toString()), false);
        return 1;
    }

    private static String getDatapackColor(CommandContext<ServerCommandSource> context, String pack) {
        if (context.getSource().getServer().getDataPackManager().getEnabledNames().contains(pack))
            return EmbeddedAssetsConfig.disabledResourcePacks.contains(pack) ? "§c" : "§a";
        return "§7§o";
    }

    private static int enabledRP(CommandContext<ServerCommandSource> context) {
        String datapack = StringArgumentType.getString(context, "datapack");
        ResourcePackProfile profile = context.getSource().getServer().getDataPackManager().getProfile( datapack );
        if (profile == null)
            return sendMessage(context, "Datapack not found: §e" + datapack, true);

        if (EmbeddedAssetsConfig.disabledResourcePacks.remove(profile.getName())) {
            EmbeddedAssetsConfig.save();
            return sendMessage(context, "Resource Pack for " + profile.getName() + " has been enabled.\n- Run §e/embedded_assets regenerate§7 for this to take effect", false);
        }
        return sendMessage(context, "Resource Pack for " + profile.getName() + " was already enabled", true);
    }

    private static int disableRP(CommandContext<ServerCommandSource> context) {
        String datapack = StringArgumentType.getString(context, "datapack");
        ResourcePackProfile profile = context.getSource().getServer().getDataPackManager().getProfile( datapack );
        if (profile == null)
            return sendMessage(context, "Datapack not found: §e" + datapack, true);

        if (EmbeddedAssetsConfig.disabledResourcePacks.add(profile.getName())) {
            EmbeddedAssetsConfig.save();
            return sendMessage(context, "Resource Pack for " + profile.getName() + " has been disabled.\n- Run §e/embedded_assets regenerate§7 for this to take effect", false);
        }
        return sendMessage(context, "Resource Pack for " + profile.getName() + " was already disabled", true);
    }

    private static int setPriority(CommandContext<ServerCommandSource> context) {
        String datapack = StringArgumentType.getString(context, "datapack");
        ResourcePackProfile profile = context.getSource().getServer().getDataPackManager().getProfile( datapack );
        if (profile == null)
            return sendMessage(context, "Datapack not found: §e" + datapack, true);

        int priority = IntegerArgumentType.getInteger(context, "priority") - 1;
        if (priority > EmbeddedAssetsConfig.priority.size())
            priority = EmbeddedAssetsConfig.priority.size();

        EmbeddedAssetsConfig.priority.remove(profile.getName());
        EmbeddedAssetsConfig.priority.add(priority, profile.getName());
        EmbeddedAssetsConfig.save();

        return sendMessage(context, "§e" + datapack + "§7 priority set to §e" + (priority + 1) + "§7.\n- Run §e/embedded_assets regenerate§7 for this to take effect", false);
    }

    private static int statusLocalHosting(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(Text.literal(
                "\n§8§u[§aLocal Pack Hosting§8]§r"
                + "\n\n§7Enabled: §e" + localResourcePackHostingConfig.enabled
                + "\n§7IP: §e" + (LocalResourcePackHoster.ip.isEmpty() ? "N/A" : LocalResourcePackHoster.ip) + (localResourcePackHostingConfig.local ? "(local)" : "")
                + "\n§7Port: §e" + localResourcePackHostingConfig.port
                + "\n§7Verbose Logging: §e" + localResourcePackHostingConfig.verboseLogging
                + "\n§7Require Pack: §e" + localResourcePackHostingConfig.requireClientToHavePack
                + "\n§7Prompt Msg: §e").append(EmbeddedAssetsConfig.getPromptMsg()).append(
                  "\n\n§7Enable: §e/embedded_assets pack_hosting enable"
                + "\n§7Disable: §e/embedded_assets pack_hosting disable"
                + "\n§7Configure: §e/embedded_assets pack_hosting configure <port> <local> <verbose_logging> <require_pack> <pack_msg>"
        ), false);
        return 1;
    }

    private static int enableLocalHosting(CommandContext<ServerCommandSource> context) {
        try {
            if (localResourcePackHostingConfig.enabled)
                LocalResourcePackHoster.stopHttpd();
            else {
                localResourcePackHostingConfig.enabled = true;
                EmbeddedAssetsConfig.save();
            }

            if (LocalResourcePackHoster.startHttpd())
                sendMessage(context, "The Local Pack Server has been started", false);
            else
                sendMessage(context, "Failed to start Local Pack Server", true);

            for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList())
                LocalResourcePackHoster.sendPack(player);

            return 1;
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static int disableLocalHosting(CommandContext<ServerCommandSource> context) {
        if (!localResourcePackHostingConfig.enabled && !LocalResourcePackHoster.running)
            return sendMessage(context, "The Local Pack Server wasn't running", true);
        else {
            localResourcePackHostingConfig.enabled = false;
            EmbeddedAssetsConfig.save();

            for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList())
                LocalResourcePackHoster.reset(player);

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    LocalResourcePackHoster.stopHttpd();
                    sendMessage(context, "The Local Pack Server has been stopped", false);
                }
            }, 10000);

            return sendMessage(context, "The Local Pack Server will stop in 10 seconds", false);
        }
    }

    private static int configureLocalHosting(CommandContext<ServerCommandSource> context) {
        localResourcePackHostingConfig.port = IntegerArgumentType.getInteger(context, "port");
        localResourcePackHostingConfig.local = BoolArgumentType.getBool(context, "local");
        localResourcePackHostingConfig.verboseLogging = BoolArgumentType.getBool(context, "verbose_logging");
        localResourcePackHostingConfig.requireClientToHavePack = BoolArgumentType.getBool(context, "require_pack");
        localResourcePackHostingConfig.promptMsg = StringArgumentType.getString(context, "prompt_msg");
        EmbeddedAssetsConfig.save();
        return sendMessage(context, "The Local Pack Server has been configured.\n- Run §e/embedded_assets pack_hosting enable§7 to (re)start the server", false);
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        if (EmbeddedAssetsConfig.read())
            return sendMessage(context, "Config Reloaded", false);
        return sendMessage(context, "Failed to Reload Config", true);
    }


    private static int sendMessage(CommandContext<ServerCommandSource> context, String msg, boolean error) {
        context.getSource().sendFeedback(Text.literal( "§8[§" + (error ? "c" : "a") + "EmbeddedAssets§8]§7 " + msg), false);
        return 1;
    }

}
