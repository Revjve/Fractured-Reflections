package com.revjve.fractured.compat.voicechat;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.FracturedReflections;
import com.revjve.fractured.game.Match;
import com.revjve.fractured.game.MatchManager;
import com.revjve.fractured.game.MatchPhase;
import com.revjve.fractured.game.PlayerState;
import com.revjve.fractured.registry.ModDimensions;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Simple Voice Chat plugin (entrypoint "voicechat").
 * <ul>
 *   <li><b>Identity Spoofing</b> — proximity voice is attached to the speaker's entity, and the
 *       speaking indicator is drawn over that entity's nametag. A disguised Fractured renders with
 *       the copied survivor's skin, model and nametag (client mixins), so its voice and talk icon
 *       present as that survivor. This plugin keeps that path untouched for disguised killers.</li>
 *   <li>An undisguised Fractured is silent to everyone (configurable): the horror never speaks.</li>
 *   <li>Survivors trapped in the Mirror Dimension leak a faint whisper of their voice into the
 *       Main World at the same coordinates.</li>
 * </ul>
 * MicrophonePacketEvent fires on the voice thread; match state is only read here.
 */
public final class FRVoicechatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return FracturedReflections.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null) return;
        if (!(connection.getPlayer().getPlayer() instanceof ServerPlayerEntity sender)) return;
        Match match = MatchManager.get().getMatch();
        if (match == null || match.phase() != MatchPhase.HUNT) return;
        PlayerState state = match.state(sender.getUuid());
        if (state == null) return;

        if (state.isFractured()) {
            if (state.disguiseTarget == null && FRConfig.get().silenceUndisguisedFractured) event.cancel();
            return;
        }

        if (state.isActiveSurvivor() && FRConfig.get().mirrorVoiceLeak && ModDimensions.isMirror(sender.getWorld())) {
            leakIntoMainWorld(event, match, sender);
        }
    }

    private static void leakIntoMainWorld(MicrophonePacketEvent event, Match match, ServerPlayerEntity sender) {
        VoicechatServerApi api = event.getVoicechat();
        double range = api.getVoiceChatDistance() * 0.5;
        LocationalSoundPacket packet = event.getPacket().toLocationalSoundPacketBuilder()
                .position(api.createPosition(sender.getX(), sender.getEyeY(), sender.getZ()))
                .distance((float) range)
                .build();
        for (PlayerState other : match.states()) {
            ServerPlayerEntity listener = match.player(other.uuid);
            if (listener == null || listener == sender || listener.getWorld() != match.mainWorld()) continue;
            if (listener.squaredDistanceTo(sender.getX(), sender.getY(), sender.getZ()) > range * range) continue;
            VoicechatConnection target = api.getConnectionOf(listener.getUuid());
            if (target != null) api.sendLocationalSoundPacketTo(target, packet);
        }
    }
}
