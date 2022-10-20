package net.fabricmc.example.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.fabricmc.example.server.EmbeddedAssetsConfig.localResourcePackHosterConfig;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EmbeddedAssetsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("embedded_assets")
            .executes( EmbeddedAssetsCommand::getInfo )
            .then(literal("regenerate").requires( source -> source.hasPermissionLevel(2) )
                .executes( EmbeddedAssetsCommand::regenerate ))
            .then(literal("priority").requires( source -> source.hasPermissionLevel(2) )
                .executes( EmbeddedAssetsCommand::getPriority )
                .then(literal("set")
                    .then(argument("datapack", string()).suggests(EmbeddedAssetsCommand::getDatapackSuggestions)
                        .then(argument("priority", integer(1))
                            .executes( EmbeddedAssetsCommand::setPriority )))))
            .then(literal("pack_hosting").requires( source -> source.hasPermissionLevel(2) )
                .executes( EmbeddedAssetsCommand::statusLocalHosting )
                .then(literal("status").executes( EmbeddedAssetsCommand::statusLocalHosting ))
                    .then(literal("enable").executes(EmbeddedAssetsCommand::enableLocalHosting ))
                    .then(literal("disable").executes(EmbeddedAssetsCommand::disableLocalHosting ))
                    .then(literal("configure")
                        .then(argument("port", integer())
                            .then(argument("local", bool())
                                .then(argument("verboseLogging", bool())
                                    .executes( EmbeddedAssetsCommand::configureLocalHosting )))))
                )

        );
    }

    private static CompletableFuture<Suggestions> getDatapackSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        for (var t : context.getSource().getServer().getDataPackManager().getProfiles())
            if (t.createResourcePack() instanceof AbstractFileResourcePack)
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

    private static int getInfo(CommandContext<ServerCommandSource> context) {
        if (EmbeddedAssetsServer.profiles.isEmpty())
            return sendMessage(context, "No Resourced Data Packs are Loaded", true);

        StringBuilder builder = new StringBuilder("§8§u[§aResourced Data Packs§8]§r");

        for (ResourcePackProfile profile : EmbeddedAssetsServer.profiles) {
            builder.append("\n\n§l").append(profile.getName());
            builder.append("\n").append(profile.getDescription());
        }
        context.getSource().sendFeedback(Text.literal(builder.toString()), false);
        return 1;
    }

    private static int getPriority(CommandContext<ServerCommandSource> context) {
        if (EmbeddedAssetsServer.profiles.isEmpty())
            return sendMessage(context, "No Resourced Data Packs are Loaded", true);

        StringBuilder builder = new StringBuilder("§8§u[§aResourced Data Pack Priorities§8]§r");

        boolean doubleDigits = EmbeddedAssetsConfig.priority.size() >= 10;
        for (int i = 0; i < EmbeddedAssetsConfig.priority.size(); i++) {
            builder.append("\n§e");
            if (doubleDigits && i+1 >= 10)
                builder.append(" ");
            builder.append(i+1).append("§7: ").append(EmbeddedAssetsConfig.priority.get(i));
        }


        context.getSource().sendFeedback(Text.literal(builder.toString()), false);
        return 1;
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


        sendMessage(context, "§e" + datapack + "§7 priority set to §e" + (priority + 1), false);
        return sendMessage(context, "Run §e/embedded_assets regenerate§7 for this to take effect", false);
    }

    private static int statusLocalHosting(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(Text.literal(
                "§8§u[§aLocal Pack Hosting§8]§r"
                + "\n\n§7Enabled: §e" + localResourcePackHosterConfig.enabled
                + "\n§7IP: §e" + (LocalResourcePackHoster.ip.isEmpty() ? "N/A" : LocalResourcePackHoster.ip) + (localResourcePackHosterConfig.local ? "(local)" : "")
                + "\n§7Port: §e" + localResourcePackHosterConfig.port
                + "\n§7Verbose Logging: §e" + localResourcePackHosterConfig.verboseLogging
                + "\n\n§7Enable: §e/embedded_assets pack_hosting enable"
                + "\n§7Disable: §e/embedded_assets pack_hosting disable"
                + "\n§7Configure:\n §e/embedded_assets pack_hosting configure <port> <local> <verbose_logging>"
        ), false);
        return 1;
    }

    private static int enableLocalHosting(CommandContext<ServerCommandSource> context) {
        if (localResourcePackHosterConfig.enabled)
            LocalResourcePackHoster.stopHttpd();
        else {
            localResourcePackHosterConfig.enabled = true;
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

    private static int disableLocalHosting(CommandContext<ServerCommandSource> context) {
        if (!localResourcePackHosterConfig.enabled && !LocalResourcePackHoster.running)
            return sendMessage(context, "The Local Pack Server wasn't running", true);
        else {
            localResourcePackHosterConfig.enabled = false;
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
        localResourcePackHosterConfig.port = IntegerArgumentType.getInteger(context, "port");
        localResourcePackHosterConfig.local = BoolArgumentType.getBool(context, "local");
        localResourcePackHosterConfig.verboseLogging = BoolArgumentType.getBool(context, "verboseLogging");
        EmbeddedAssetsConfig.save();
        return sendMessage(context, "The Local Pack Server has been configured. Do §e/embedded_assets pack_hosting enable§7 to (re)start the server", false);
    }

    private static int sendMessage(CommandContext<ServerCommandSource> context, String msg, boolean error) {
        context.getSource().sendFeedback(Text.literal( "§8[§" + (error ? "c" : "a") + "EmbeddedAssets§8]§7 " + msg), false);
        return 1;
    }

}
