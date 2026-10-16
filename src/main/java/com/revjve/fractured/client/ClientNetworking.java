package com.revjve.fractured.client;

import com.revjve.fractured.client.screen.PuzzleChestScreen;
import com.revjve.fractured.game.Ability;
import com.revjve.fractured.game.MatchPhase;
import com.revjve.fractured.game.Role;
import com.revjve.fractured.game.RoleRegistry;
import com.revjve.fractured.network.FRPackets;
import com.revjve.fractured.registry.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class ClientNetworking {
    private ClientNetworking() {}

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(FRPackets.ROLE_SYNC, (client, handler, buf, sender) -> {
            MatchPhase phase = MatchPhase.byOrdinal(buf.readVarInt());
            int ticks = buf.readVarInt();
            int count = buf.readVarInt();
            Map<UUID, Role> roles = new HashMap<>();
            Map<UUID, Boolean> eliminated = new HashMap<>();
            List<UUID> hiding = new ArrayList<>();
            List<UUID> panicking = new ArrayList<>();
            for (int n = 0; n < count; n++) {
                UUID uuid = buf.readUuid();
                roles.put(uuid, Role.byOrdinal(buf.readVarInt()));
                eliminated.put(uuid, buf.readBoolean());
                if (buf.readBoolean()) hiding.add(uuid);
                if (buf.readBoolean()) panicking.add(uuid);
            }
            client.execute(() -> {
                if (phase == MatchPhase.IDLE) {
                    ClientGameState.reset();
                    GhostEchoManager.clear(client);
                    return;
                }
                ClientGameState.phase = phase;
                ClientGameState.phaseTicks = ticks;
                ClientGameState.roles.clear();
                ClientGameState.roles.putAll(roles);
                ClientGameState.eliminated.clear();
                ClientGameState.eliminated.putAll(eliminated);
                List<UUID> fractured = new ArrayList<>();
                roles.forEach((uuid, role) -> {
                    if (role == Role.FRACTURED) fractured.add(uuid);
                });
                RoleRegistry.setClient(fractured);
                RoleRegistry.setClientHiding(hiding);
                ClientGameState.panicking.clear();
                ClientGameState.panicking.addAll(panicking);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.SURVIVOR_STATS, (client, handler, buf, sender) -> {
            float stamina = buf.readFloat(), staminaMax = buf.readFloat(), sanity = buf.readFloat(), sanityMax = buf.readFloat();
            boolean exhausted = buf.readBoolean(), trapped = buf.readBoolean();
            client.execute(() -> {
                ClientGameState.stamina = stamina;
                ClientGameState.staminaMax = staminaMax;
                ClientGameState.sanity = sanity;
                ClientGameState.sanityMax = sanityMax;
                ClientGameState.exhausted = exhausted;
                if (ClientGameState.trapped && !trapped) GhostEchoManager.clear(client);
                ClientGameState.trapped = trapped;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.FRACTURED_HUD, (client, handler, buf, sender) -> {
            int n = Math.min(buf.readVarInt(), Ability.values().length);
            int[] cds = new int[n], max = new int[n];
            for (int i = 0; i < n; i++) {
                cds[i] = buf.readVarInt();
                max[i] = buf.readVarInt();
            }
            int disguiseTicks = buf.readVarInt();
            String disguiseName = buf.readString();
            int windup = buf.readVarInt();
            client.execute(() -> {
                System.arraycopy(cds, 0, ClientGameState.cooldowns, 0, n);
                System.arraycopy(max, 0, ClientGameState.cooldownMax, 0, n);
                ClientGameState.disguiseTicks = disguiseTicks;
                ClientGameState.disguiseName = disguiseName;
                ClientGameState.dragWindup = windup;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.TASKS, (client, handler, buf, sender) -> {
            int perAltar = buf.readVarInt();
            int count = buf.readVarInt();
            List<ClientGameState.AltarTask> tasks = new ArrayList<>();
            for (int n = 0; n < count; n++) {
                tasks.add(new ClientGameState.AltarTask(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readFloat()));
            }
            client.execute(() -> {
                ClientGameState.fragmentsPerAltar = perAltar;
                ClientGameState.altars.clear();
                ClientGameState.altars.addAll(tasks);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.DISGUISES, (client, handler, buf, sender) -> {
            int count = buf.readVarInt();
            Map<UUID, UUID> map = new HashMap<>();
            for (int n = 0; n < count; n++) map.put(buf.readUuid(), buf.readUuid());
            client.execute(() -> {
                ClientGameState.disguises.clear();
                ClientGameState.disguises.putAll(map);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.GHOST_ECHOES, (client, handler, buf, sender) -> {
            List<GhostEchoManager.Snapshot> snapshots = GhostEchoManager.read(buf);
            client.execute(() -> GhostEchoManager.update(client, snapshots));
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.MIRROR_CRAWL, (client, handler, buf, sender) -> {
            int entityId = buf.readVarInt();
            Direction facing = Direction.fromHorizontal(buf.readVarInt());
            int duration = buf.readVarInt();
            client.execute(() -> {
                ClientGameState.crawls.put(entityId, new ClientGameState.Crawl(facing, ClientGameState.clientTicks, duration));
                net.minecraft.entity.Entity emerging = client.world == null ? null : client.world.getEntityById(entityId);
                if (emerging != null && client.player != null) {
                    float near = (float) Math.max(0.0, 1.0 - Math.sqrt(emerging.squaredDistanceTo(client.player)) / 12.0);
                    ClientGameState.shake = Math.max(ClientGameState.shake, near * 0.7f);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.ALTAR_REVEAL, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int ticks = buf.readVarInt();
            client.execute(() -> {
                ClientGameState.reveals.removeIf(r -> r.pos().equals(pos));
                ClientGameState.reveals.add(new ClientGameState.Reveal(pos, ClientGameState.clientTicks + ticks));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.SCREEN_EFFECT, (client, handler, buf, sender) -> {
            FRPackets.ScreenEffect effect = FRPackets.ScreenEffect.byOrdinal(buf.readVarInt());
            float intensity = buf.readFloat();
            client.execute(() -> onScreenEffect(client, effect, intensity));
        });

        ClientPlayNetworking.registerGlobalReceiver(FRPackets.OPEN_PUZZLE, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int dials = buf.readVarInt();
            client.execute(() -> client.setScreen(new PuzzleChestScreen(pos, dials)));
        });
    }

    private static void onScreenEffect(MinecraftClient client, FRPackets.ScreenEffect effect, float intensity) {
        switch (effect) {
            case BLACKOUT_FLASH -> {
                ClientGameState.screenPulse = Math.max(ClientGameState.screenPulse, intensity);
                ClientGameState.crackPulse = Math.max(ClientGameState.crackPulse, 0.6f * intensity);
                ClientGameState.shake = Math.max(ClientGameState.shake, 0.8f * intensity);
            }
            case ESCAPED -> {
                ClientGameState.screenPulse = Math.max(ClientGameState.screenPulse, intensity);
                ClientGameState.crackPulse = Math.max(ClientGameState.crackPulse, 0.9f);
            }
            case BANISHMENT -> {
                ClientGameState.screenPulse = Math.max(ClientGameState.screenPulse, intensity);
                ClientGameState.shake = Math.max(ClientGameState.shake, 1.2f);
            }
            case DRAGGED -> {
                ClientGameState.dragPulse = Math.max(ClientGameState.dragPulse, intensity);
                ClientGameState.crackPulse = Math.max(ClientGameState.crackPulse, 1.0f);
                ClientGameState.shake = Math.max(ClientGameState.shake, 1.0f);
            }
            case PORTAL -> {
                ClientGameState.shake = Math.max(ClientGameState.shake, 0.5f * intensity);
                ClientGameState.crackPulse = Math.max(ClientGameState.crackPulse, 0.3f * intensity);
            }
            case SHATTERED, STING -> {
                ClientGameState.screenPulse = Math.max(ClientGameState.screenPulse, intensity * 0.6f);
                ClientGameState.crackPulse = Math.max(ClientGameState.crackPulse, intensity);
                ClientGameState.shake = Math.max(ClientGameState.shake, 0.6f);
                client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.STING, 1.0f));
            }
        }
    }

    // --- C2S ------------------------------------------------------------------------------------

    public static void sendAbility(Ability ability) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(ability.ordinal());
        ClientPlayNetworking.send(FRPackets.USE_ABILITY, buf);
    }

    public static void sendToggleFlashlight() {
        ClientPlayNetworking.send(FRPackets.TOGGLE_FLASHLIGHT, PacketByteBufs.empty());
    }

    public static void sendPuzzleSubmit(BlockPos pos, int[] dials) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(dials.length);
        for (int d : dials) buf.writeVarInt(d);
        ClientPlayNetworking.send(FRPackets.PUZZLE_SUBMIT, buf);
    }
}
