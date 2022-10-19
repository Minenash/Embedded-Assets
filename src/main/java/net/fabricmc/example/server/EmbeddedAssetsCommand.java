package net.fabricmc.example.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EmbeddedAssetsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("embedded_assets")
            .executes( EmbeddedAssetsCommand::getInfo )
            .then(literal("priority").requires( source -> source.hasPermissionLevel(2) )
                .executes( EmbeddedAssetsCommand::getPriority )
                .then(literal("set")
                    .then(argument("datapack", StringArgumentType.string())
                            .then(argument("priority", IntegerArgumentType.integer(1))
                                    .executes( EmbeddedAssetsCommand::setPriority )))))

        );
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


        return sendMessage(context, "§e" + datapack + "§7 priority set to §e" + (priority + 1), false);
    }

    private static int sendMessage(CommandContext<ServerCommandSource> context, String msg, boolean error) {
        context.getSource().sendFeedback(Text.literal( "§8[§" + (error ? "c" : "a") + "EmbeddedAssets§8]§7 " + msg), false);
        return 1;
    }

}
