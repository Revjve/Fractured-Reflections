package com.revjve.fractured.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A client-only spectral copy of a teammate who is still in the Main World, shown to a survivor
 * trapped in the Mirror Dimension. It is "invisible but not invisible to the viewer", which makes
 * vanilla's LivingEntityRenderer draw it with its built-in 15% alpha ghost pass — no custom
 * shader required. It cannot be hit, pushed or collided with.
 */
@Environment(EnvType.CLIENT)
public final class GhostEchoPlayer extends OtherClientPlayerEntity {
    private final UUID realUuid;

    public GhostEchoPlayer(ClientWorld world, GameProfile ghostProfile, UUID realUuid) {
        super(world, ghostProfile);
        this.realUuid = realUuid;
        this.noClip = true;
        setSilent(true);
        setNoGravity(true);
    }

    public UUID getRealUuid() {
        return realUuid;
    }

    @Nullable
    @Override
    protected PlayerListEntry getPlayerListEntry() {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        return handler == null ? null : handler.getPlayerListEntry(realUuid);
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isInvisibleTo(PlayerEntity player) {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }
}
