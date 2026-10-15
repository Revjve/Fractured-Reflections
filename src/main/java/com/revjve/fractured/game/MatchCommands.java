package com.revjve.fractured.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /fr start [fractured]   — start a match with every non-spectator player online
 * /fr stop                — abort the running match
 * /fr status              — phase, timer and roles
 * /fr sanity targets value — debug: set survivors' sanity
 */
public final class MatchCommands {
    private MatchCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        dispatcher.register(CommandManager.literal("fr")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("start")
                        .executes(ctx -> start(ctx, null))
                        .then(CommandManager.argument("fractured", EntityArgumentType.player())
                                .executes(ctx -> start(ctx, EntityArgumentType.getPlayer(ctx, "fractured")))))
                .then(CommandManager.literal("stop").executes(MatchCommands::stop))
                .then(CommandManager.literal("status").executes(MatchCommands::status))
                .then(CommandManager.literal("sanity")
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .then(CommandManager.argument("value", FloatArgumentType.floatArg(0, 1000))
                                        .executes(MatchCommands::setSanity)))));
    }

    private static int start(CommandContext<ServerCommandSource> ctx, @Nullable ServerPlayerEntity fractured) {
        ServerCommandSource src = ctx.getSource();
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
            if (!p.isSpectator()) players.add(p);
        }
        if (fractured != null && !players.contains(fractured)) players.add(fractured);
        Text error = MatchManager.get().start(src.getServer(), players, fractured);
        if (error != null) {
            src.sendError(error);
            return 0;
        }
        src.sendFeedback(() -> Text.translatable("command.fractured_reflections.started", players.size()).formatted(Formatting.DARK_AQUA), true);
        return 1;
    }

    private static int stop(CommandContext<ServerCommandSource> ctx) {
        if (MatchManager.get().getMatch() == null) {
            ctx.getSource().sendError(Text.translatable("command.fractured_reflections.no_match"));
            return 0;
        }
        MatchManager.get().stop(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("command.fractured_reflections.stopped"), true);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) {
        Match match = MatchManager.get().getMatch();
        if (match == null) {
            ctx.getSource().sendFeedback(() -> Text.translatable("command.fractured_reflections.no_match"), false);
            return 0;
        }
        int seconds = match.phaseTicksRemaining() / 20;
        ctx.getSource().sendFeedback(() -> Text.literal("Phase: " + match.phase() + " (" + seconds / 60 + ":" + String.format("%02d", seconds % 60) + ")"), false);
        for (PlayerState s : match.states()) {
            String line = String.format(" - %s: %s%s  stamina %.0f  sanity %.0f", s.name, s.role, s.eliminated ? " (eliminated)" : "", s.stamina, s.sanity);
            ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    private static int setSanity(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Match match = MatchManager.get().getMatch();
        if (match == null) {
            ctx.getSource().sendError(Text.translatable("command.fractured_reflections.no_match"));
            return 0;
        }
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
        float value = FloatArgumentType.getFloat(ctx, "value");
        int changed = 0;
        for (ServerPlayerEntity p : targets) {
            PlayerState s = match.state(p.getUuid());
            if (s != null) {
                s.sanity = value;
                changed++;
            }
        }
        int count = changed;
        ctx.getSource().sendFeedback(() -> Text.literal("Set sanity of " + count + " player(s) to " + value), true);
        return count;
    }
}
