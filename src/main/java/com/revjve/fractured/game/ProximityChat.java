package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * During a match all chat becomes proximity chat delivered as system messages, which lets the
 * server choose the displayed sender: a disguised Fractured speaks with the copied survivor's
 * name (Identity Spoofing); an undisguised one is shown as "The Fractured". Eliminated players
 * talk only among themselves.
 */
public final class ProximityChat {
    private ProximityChat() {}

    /** Fabric ALLOW_CHAT_MESSAGE hook. Returning false cancels the vanilla broadcast. */
    public static boolean onChat(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        Match match = MatchManager.get().getMatch();
        if (match == null || match.phase() == MatchPhase.IDLE || match.phase() == MatchPhase.ENDED) return true;
        PlayerState senderState = match.state(sender.getUuid());
        if (senderState == null) return true;

        Text name = displayName(match, sender, senderState);
        MutableText line = Text.translatable("chat.type.text", name, message.getContent());
        double range = FRConfig.get().proximityChatRange;
        boolean deadChat = senderState.eliminated || senderState.role == Role.SPECTATOR;
        if (deadChat) line = Text.literal("[").append(Text.translatable("chat.fractured_reflections.dead")).append("] ").formatted(Formatting.GRAY).append(line);

        for (ServerPlayerEntity receiver : sender.getServer().getPlayerManager().getPlayerList()) {
            PlayerState rs = match.state(receiver.getUuid());
            boolean receiverDead = rs == null || rs.eliminated || rs.role == Role.SPECTATOR;
            boolean deliver;
            if (deadChat) {
                deliver = receiverDead;
            } else if (receiverDead) {
                deliver = true; // spectators hear everything
            } else {
                deliver = receiver.getWorld() == sender.getWorld() && receiver.squaredDistanceTo(sender) <= range * range;
            }
            if (deliver) receiver.sendMessage(line, false);
        }
        return false;
    }

    public static Text displayName(Match match, ServerPlayerEntity sender, PlayerState state) {
        if (state.isFractured()) {
            if (state.disguiseTarget != null) {
                ServerPlayerEntity target = match.player(state.disguiseTarget);
                PlayerState targetState = match.state(state.disguiseTarget);
                if (target != null) return target.getDisplayName();
                if (targetState != null) return Text.literal(targetState.name);
            }
            return Text.translatable("role.fractured_reflections.fractured").formatted(Formatting.DARK_AQUA);
        }
        return sender.getDisplayName();
    }
}
