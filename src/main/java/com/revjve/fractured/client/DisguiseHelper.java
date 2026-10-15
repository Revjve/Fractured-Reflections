package com.revjve.fractured.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Resolves the survivor a disguised Fractured is currently copying. */
@Environment(EnvType.CLIENT)
public final class DisguiseHelper {
    private DisguiseHelper() {}

    @Nullable
    public static PlayerListEntry disguiseEntry(PlayerEntity player) {
        if (ClientGameState.disguises.isEmpty()) return null;
        UUID target = ClientGameState.disguises.get(player.getUuid());
        if (target == null) return null;
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        return handler == null ? null : handler.getPlayerListEntry(target);
    }

    /** The copied survivor's nametag, decorated with their team exactly like the real one. */
    public static Text displayName(PlayerListEntry entry) {
        Text name = Text.literal(entry.getProfile().getName());
        Team team = entry.getScoreboardTeam();
        return team != null ? Team.decorateName(team, name) : name;
    }
}
