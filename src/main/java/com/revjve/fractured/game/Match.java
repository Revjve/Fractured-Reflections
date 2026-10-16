package com.revjve.fractured.game;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.FracturedReflections;
import com.revjve.fractured.block.RitualAltarBlockEntity;
import com.revjve.fractured.entity.GlassEchoEntity;
import com.revjve.fractured.item.FlashlightItem;
import com.revjve.fractured.network.FRPackets;
import com.revjve.fractured.network.ServerNetworking;
import com.revjve.fractured.registry.ModDimensions;
import com.revjve.fractured.registry.ModItems;
import com.revjve.fractured.registry.ModSounds;
import com.revjve.fractured.world.mansion.BuildJob;
import com.revjve.fractured.world.mansion.MansionGenerator;
import com.revjve.fractured.world.mansion.MansionLayout;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One round of Fractured Reflections, from mansion generation to the result screen. */
public final class Match {
    public static final UUID ATTACK_MODIFIER = UUID.fromString("b1b52b0e-1f0a-4f1e-8d3c-6e4f3a2c7d11");

    private final MinecraftServer server;
    private final ServerWorld main;
    private final ServerWorld mirror;
    private final MansionLayout layout;
    private final BuildJob buildJob;
    private final Map<UUID, PlayerState> states = new LinkedHashMap<>();
    private final PortalManager portals = new PortalManager();
    private final BlackoutManager blackouts = new BlackoutManager();
    private final EscapeMirrors escapes = new EscapeMirrors();
    private final HidingManager hiding = new HidingManager();

    private MatchPhase phase = MatchPhase.GENERATING;
    private int phaseTicks;
    private int age;
    private boolean rolesDirty = true;
    private boolean tasksDirty = true;
    private boolean disguisesDirty = true;
    private int lastCountdownSecond = -1;

    Match(MinecraftServer server, ServerWorld main, ServerWorld mirror, List<ServerPlayerEntity> survivors, @Nullable ServerPlayerEntity fractured) {
        this.server = server;
        this.main = main;
        this.mirror = mirror;
        long seed = main.getRandom().nextLong();
        BlockPos origin = new BlockPos(-(FRConfig.get().mansionWidth * MansionLayout.CELL) / 2, FRConfig.get().mansionOriginY,
                -(FRConfig.get().mansionDepth * MansionLayout.CELL) / 2);
        MansionGenerator.Result result = MansionGenerator.generate(seed, origin);
        this.layout = result.layout();
        this.buildJob = new BuildJob(main, mirror, layout, result.plan());
        this.buildJob.begin();

        if (fractured != null) states.put(fractured.getUuid(), new PlayerState(fractured.getUuid(), fractured.getGameProfile().getName(), Role.FRACTURED));
        for (ServerPlayerEntity s : survivors) {
            states.put(s.getUuid(), new PlayerState(s.getUuid(), s.getGameProfile().getName(), Role.SURVIVOR));
        }
        refreshRoleRegistry();
        FracturedReflections.LOGGER.info("Generating mansion ({} placements) for {} players, seed {}", result.plan().size(), states.size(), seed);
    }

    // --- Accessors ---------------------------------------------------------------------------

    public MatchPhase phase() { return phase; }
    public int phaseTicksRemaining() { return phaseTicks; }
    public MansionLayout layout() { return layout; }
    public ServerWorld mainWorld() { return main; }
    public ServerWorld mirrorWorld() { return mirror; }
    public PortalManager portals() { return portals; }
    public BlackoutManager blackouts() { return blackouts; }
    public EscapeMirrors escapes() { return escapes; }
    public HidingManager hiding() { return hiding; }
    public Collection<PlayerState> states() { return states.values(); }

    @Nullable
    public PlayerState state(@Nullable UUID uuid) {
        return uuid == null ? null : states.get(uuid);
    }

    @Nullable
    public ServerPlayerEntity player(@Nullable UUID uuid) {
        return uuid == null ? null : server.getPlayerManager().getPlayer(uuid);
    }

    public Role roleOf(ServerPlayerEntity player) {
        PlayerState s = states.get(player.getUuid());
        return s == null ? Role.NONE : s.role;
    }

    public List<ServerPlayerEntity> onlineParticipants() {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (UUID uuid : states.keySet()) {
            ServerPlayerEntity p = player(uuid);
            if (p != null) out.add(p);
        }
        return out;
    }

    @Nullable
    public ServerPlayerEntity fracturedPlayer() {
        for (PlayerState s : states.values()) if (s.isFractured()) return player(s.uuid);
        return null;
    }

    public void markRolesDirty() { rolesDirty = true; }
    public void markTasksDirty() { tasksDirty = true; }
    public void markDisguisesDirty() { disguisesDirty = true; }

    // --- Tick ---------------------------------------------------------------------------------

    /** @return false once the match is over and should be discarded. */
    boolean tick() {
        age++;
        switch (phase) {
            case GENERATING -> tickGenerating();
            case PREPARING -> tickPreparing();
            case HUNT -> tickHunt();
            case ENDED -> {
                if (--phaseTicks <= 0) {
                    cleanup();
                    return false;
                }
            }
            default -> { }
        }
        syncClients();
        return true;
    }

    private void tickGenerating() {
        buildJob.tick(Math.max(500, FRConfig.get().blocksPerTick));
        if (age % 10 == 0) {
            int percent = Math.round(buildJob.progress() * 100);
            Text bar = Text.translatable("message.fractured_reflections.generating", percent).formatted(Formatting.DARK_AQUA);
            for (ServerPlayerEntity p : onlineParticipants()) p.sendMessage(bar, true);
        }
        if (buildJob.isDone()) {
            placePlayers();
            portals.spawnReflections(main, layout.mirrors);
            portals.spawnReflections(mirror, layout.mirrors);
            phase = MatchPhase.PREPARING;
            phaseTicks = Math.max(20, FRConfig.get().prepareSeconds * 20);
            rolesDirty = true;
            tasksDirty = true;
        }
    }

    private void tickPreparing() {
        int second = (phaseTicks + 19) / 20;
        if (second != lastCountdownSecond && second <= 5) {
            lastCountdownSecond = second;
            for (ServerPlayerEntity p : onlineParticipants()) {
                title(p, Text.literal(String.valueOf(second)).formatted(Formatting.DARK_AQUA), Text.empty(), 0, 25, 5);
                p.playSound(ModSounds.MIRROR_AMBIENT, SoundCategory.MASTER, 0.6f, 0.6f + 0.1f * (5 - second));
            }
        }
        if (--phaseTicks <= 0) {
            phase = MatchPhase.HUNT;
            phaseTicks = Math.max(1, FRConfig.get().matchMinutes) * 60 * 20;
            rolesDirty = true;
            for (ServerPlayerEntity p : onlineParticipants()) {
                boolean killer = roleOf(p) == Role.FRACTURED;
                title(p, Text.translatable(killer ? "title.fractured_reflections.hunt_killer" : "title.fractured_reflections.hunt_survivor")
                                .formatted(killer ? Formatting.DARK_AQUA : Formatting.GOLD),
                        Text.translatable(killer ? "subtitle.fractured_reflections.hunt_killer" : "subtitle.fractured_reflections.hunt_survivor"),
                        10, 60, 20);
                p.playSound(ModSounds.MATCH_START, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    private void tickHunt() {
        for (PlayerState state : states.values()) {
            ServerPlayerEntity player = player(state.uuid);
            if (player == null) continue;
            RegistryKey<World> worldKey = player.getWorld().getRegistryKey();
            if (state.lastWorld != null && state.lastWorld != worldKey && ModDimensions.isArena(worldKey)) {
                onWorldChanged(player, state);
            }
            state.lastWorld = worldKey;
            if (state.invulnerableTicks > 0) state.invulnerableTicks--;

            if (state.isActiveSurvivor()) {
                SurvivorSystems.tick(this, player, state);
                if (!ModDimensions.isArena(worldKey)) returnToArena(player, state);
            } else if (state.isFractured()) {
                FracturedAbilities.tick(this, player, state);
                if (!ModDimensions.isArena(worldKey)) returnToArena(player, state);
            }
        }

        portals.tick();
        blackouts.tick();
        escapes.tick(this);
        hiding.tick(this);
        if (age % 2 == 0) sendGhostEchoes();
        float threshold = FRConfig.get().glassSenseThreshold * FRConfig.get().sanityMax;
        for (PlayerState s : states.values()) {
            boolean panicking = s.isActiveSurvivor() && s.sanity < threshold;
            if (panicking != s.panicking) {
                s.panicking = panicking;
                rolesDirty = true;
            }
        }
        if (age % 10 == 0 && anyCleansing()) tasksDirty = true;

        if (--phaseTicks <= 0) {
            end(Role.FRACTURED, "time");
            return;
        }
        checkWinConditions();
    }

    private void onWorldChanged(ServerPlayerEntity player, PlayerState state) {
        if (state.isFractured()) {
            Direction facing = player.getHorizontalFacing();
            List<ServerPlayerEntity> watchers = new ArrayList<>(player.getServerWorld().getPlayers());
            ServerNetworking.sendMirrorCrawl(watchers, player.getId(), facing, 30);
            player.getServerWorld().playSound(null, player.getBlockPos(), ModSounds.MIRROR_CRAWL, SoundCategory.HOSTILE, 1.4f, 1.0f);
            FracturedAbilities.grantFlight(player);
        }
        rolesDirty = true;
    }

    private void returnToArena(ServerPlayerEntity player, PlayerState state) {
        BlockPos spawn = state.isFractured() ? layout.fracturedSpawn : layout.survivorSpawns.get(0);
        ServerWorld world = state.isFractured() ? mirror : main;
        player.teleport(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYaw(), player.getPitch());
    }

    /** Called by MirrorTravel after any mirror crossing. */
    void onMirrorTravel(ServerPlayerEntity player, Direction facing) {
        PlayerState state = states.get(player.getUuid());
        if (state != null) state.lastWorld = null; // handled here; skip the generic world-change path next tick
        if (state != null && state.isFractured()) {
            ServerNetworking.sendMirrorCrawl(player.getServerWorld().getPlayers(), player.getId(), facing, 30);
            FracturedAbilities.grantFlight(player);
        }
        rolesDirty = true;
    }

    void onSurvivorEscaped(ServerPlayerEntity player) {
        PlayerState state = states.get(player.getUuid());
        if (state == null) return;
        state.invulnerableTicks = 40;
        state.lastWorld = null;
        broadcast(Text.translatable("message.fractured_reflections.escaped", player.getDisplayName()).formatted(Formatting.AQUA));
        rolesDirty = true;
    }

    private boolean anyCleansing() {
        for (MansionLayout.AltarSite site : layout.altars) {
            if (main.getBlockEntity(site.altar()) instanceof RitualAltarBlockEntity altar && altar.isCleansingStarted() && !altar.isCompleted()) {
                return true;
            }
        }
        return false;
    }

    private void checkWinConditions() {
        boolean anySurvivor = false;
        for (PlayerState s : states.values()) if (s.isActiveSurvivor()) anySurvivor = true;
        if (!anySurvivor) {
            end(Role.FRACTURED, "eliminated");
            return;
        }
        boolean hasFracturedRole = false;
        for (PlayerState s : states.values()) if (s.isFractured()) hasFracturedRole = true;
        if (hasFracturedRole && fracturedPlayer() == null) {
            end(Role.SURVIVOR, "fled");
        }
    }

    void onAltarCleansed(RitualAltarBlockEntity altar) {
        tasksDirty = true;
        int done = 0;
        for (MansionLayout.AltarSite site : layout.altars) {
            if (main.getBlockEntity(site.altar()) instanceof RitualAltarBlockEntity a && a.isCompleted()) done++;
        }
        broadcast(Text.translatable("message.fractured_reflections.altar_cleansed", done, layout.altars.size()).formatted(Formatting.GOLD));
        for (ServerPlayerEntity p : onlineParticipants()) p.playSound(ModSounds.ALTAR_CLEANSED, SoundCategory.MASTER, 0.8f, 1.0f);
        if (phase == MatchPhase.HUNT && done >= layout.altars.size()) end(Role.SURVIVOR, "banished");
    }

    // --- Elimination & damage -----------------------------------------------------------------

    public void eliminate(ServerPlayerEntity player, String cause) {
        PlayerState state = states.get(player.getUuid());
        if (state == null || state.eliminated || state.role != Role.SURVIVOR) return;
        hiding.exit(this, player, true);
        state.eliminated = true;
        state.role = Role.SPECTATOR;
        rolesDirty = true;

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.3, 0.6, 0.3, 0.1);
        world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK,
                        (ModDimensions.isMirror(world) ? net.minecraft.block.Blocks.RED_STAINED_GLASS : net.minecraft.block.Blocks.GLASS).getDefaultState()),
                player.getX(), player.getBodyY(0.5), player.getZ(), 120, 0.4, 0.9, 0.4, 0.3);
        world.playSound(null, player.getBlockPos(), ModSounds.GLASS_SHATTER, SoundCategory.PLAYERS, 2.0f, 0.6f);
        player.setHealth(player.getMaxHealth());
        player.clearStatusEffects();
        SurvivorSystems.applySpeedModifier(player, 0);
        player.changeGameMode(GameMode.SPECTATOR);
        if (ModDimensions.isMirror(world)) {
            player.teleport(main, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        }
        ServerNetworking.sendScreenEffect(player, FRPackets.ScreenEffect.SHATTERED, 1.0f);
        broadcast(Text.translatable("message.fractured_reflections.eliminated." + cause, player.getDisplayName()).formatted(Formatting.RED));

        for (PlayerState s : states.values()) {
            ServerPlayerEntity p = player(s.uuid);
            if (p != null && s.isActiveSurvivor()) {
                SurvivorSystems.shock(s, 15f);
                p.playSound(ModSounds.STING, SoundCategory.MASTER, 0.9f, 0.8f);
            }
        }
    }

    boolean allowDamage(ServerPlayerEntity victim, net.minecraft.entity.damage.DamageSource source) {
        PlayerState state = states.get(victim.getUuid());
        if (state == null) return true;
        if (phase != MatchPhase.HUNT) return false;
        if (state.isFractured()) {
            return source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)
                    || source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL);
        }
        if (state.invulnerableTicks > 0 || state.eliminated) return false;
        if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
            PlayerState attackerState = states.get(attacker.getUuid());
            if (attackerState == null || !attackerState.isFractured()) return false; // no friendly fire
            if (attackerState.disguiseTarget != null) FracturedAbilities.endDisguise(this, attacker, attackerState);
            SurvivorSystems.shock(state, 6f);
        }
        return true;
    }

    // --- Player placement & cleanup --------------------------------------------------------------

    private void placePlayers() {
        FRConfig cfg = FRConfig.get();
        int spawnIndex = 0;
        for (PlayerState state : states.values()) {
            ServerPlayerEntity p = player(state.uuid);
            if (p == null) {
                if (state.role == Role.SURVIVOR) {
                    state.eliminated = true;
                    state.role = Role.SPECTATOR;
                }
                continue;
            }
            resetPlayer(p);
            if (state.isFractured()) {
                BlockPos spawn = layout.fracturedSpawn;
                p.teleport(mirror, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
                p.changeGameMode(GameMode.ADVENTURE);
                FracturedAbilities.grantFlight(p);
                EntityAttributeInstance attack = p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                if (attack != null) {
                    attack.removeModifier(ATTACK_MODIFIER);
                    attack.addTemporaryModifier(new EntityAttributeModifier(ATTACK_MODIFIER, "fractured_reflections:shards",
                            cfg.fracturedAttackDamage - 1.0, EntityAttributeModifier.Operation.ADDITION));
                }
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, -1, 0, false, false));
                title(p, Text.translatable("title.fractured_reflections.role_fractured").formatted(Formatting.DARK_AQUA),
                        Text.translatable("subtitle.fractured_reflections.role_fractured"), 10, 80, 20);
            } else {
                BlockPos spawn = layout.survivorSpawns.get(spawnIndex++ % layout.survivorSpawns.size());
                p.teleport(main, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
                p.changeGameMode(cfg.allowBlockEditing ? GameMode.SURVIVAL : GameMode.ADVENTURE);
                p.getInventory().insertStack(FlashlightItem.charged());
                p.getInventory().insertStack(new ItemStack(ModItems.BATTERY));
                title(p, Text.translatable("title.fractured_reflections.role_survivor").formatted(Formatting.GOLD),
                        Text.translatable("subtitle.fractured_reflections.role_survivor"), 10, 80, 20);
            }
            state.lastWorld = p.getWorld().getRegistryKey();
        }
        ServerPlayerEntity fractured = fracturedPlayer();
        if (fractured != null) {
            broadcast(Text.translatable("message.fractured_reflections.fractured_is", fractured.getDisplayName()).formatted(Formatting.DARK_AQUA));
        }
        rolesDirty = true;
    }

    private static void resetPlayer(ServerPlayerEntity p) {
        p.getInventory().clear();
        p.clearStatusEffects();
        p.setHealth(p.getMaxHealth());
        p.getHungerManager().setFoodLevel(20);
        p.getHungerManager().setSaturationLevel(5f);
        p.setExperienceLevel(0);
        p.setExperiencePoints(0);
        p.extinguish();
        p.fallDistance = 0;
    }

    void end(Role winner, String reason) {
        if (phase == MatchPhase.ENDED) return;
        phase = MatchPhase.ENDED;
        phaseTicks = Math.max(60, FRConfig.get().endScreenSeconds * 20);
        rolesDirty = true;
        boolean survivorsWin = winner == Role.SURVIVOR;
        if (survivorsWin) {
            ServerPlayerEntity fractured = fracturedPlayer();
            if (fractured != null) {
                ServerWorld w = fractured.getServerWorld();
                w.spawnParticles(ParticleTypes.END_ROD, fractured.getX(), fractured.getBodyY(0.5), fractured.getZ(), 120, 0.5, 1.0, 0.5, 0.3);
                w.playSound(null, fractured.getBlockPos(), ModSounds.GLASS_SHATTER, SoundCategory.HOSTILE, 3.0f, 0.5f);
            }
        }
        for (ServerPlayerEntity p : onlineParticipants()) {
            title(p, Text.translatable(survivorsWin ? "title.fractured_reflections.survivors_win" : "title.fractured_reflections.fractured_wins")
                            .formatted(survivorsWin ? Formatting.GOLD : Formatting.DARK_AQUA),
                    Text.translatable("subtitle.fractured_reflections.end." + reason), 10, 100, 30);
            p.playSound(survivorsWin ? ModSounds.BANISHMENT : ModSounds.STING, SoundCategory.MASTER, 1.0f, 1.0f);
            if (survivorsWin) ServerNetworking.sendScreenEffect(p, FRPackets.ScreenEffect.BANISHMENT, 1.0f);
            PlayerState s = states.get(p.getUuid());
            if (s != null && s.isFractured() && s.disguiseTarget != null) FracturedAbilities.endDisguise(this, p, s);
        }
    }

    /** Immediately tears the match down and returns everyone to the overworld. */
    void cleanup() {
        hiding.clear(this);
        portals.closeAll();
        blackouts.clear();
        escapes.clear(mirror);
        MirrorTravel.clear();
        for (ServerWorld world : new ServerWorld[]{main, mirror}) {
            for (GlassEchoEntity echo : world.getEntitiesByClass(GlassEchoEntity.class, Box.from(layout.bounds()).expand(8), e -> true)) {
                echo.discard();
            }
        }
        BuildJob.release(main, mirror, layout);

        ServerWorld overworld = server.getOverworld();
        BlockPos spawn = overworld.getSpawnPos();
        for (ServerPlayerEntity p : onlineParticipants()) restorePlayer(p, overworld, spawn);
        states.clear();
        RoleRegistry.setServer(List.of());
    }

    void restorePlayer(ServerPlayerEntity p, ServerWorld overworld, BlockPos spawn) {
        resetPlayer(p);
        SurvivorSystems.applySpeedModifier(p, 0);
        EntityAttributeInstance attack = p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(ATTACK_MODIFIER);
        PlayerAbilities abilities = p.getAbilities();
        abilities.setFlySpeed(0.05f);
        p.changeGameMode(server.getDefaultGameMode());
        if (!p.isCreative() && !p.isSpectator()) {
            abilities.allowFlying = false;
            abilities.flying = false;
        }
        p.sendAbilitiesUpdate();
        p.teleport(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYaw(), p.getPitch());
    }

    void removeParticipant(ServerPlayerEntity player) {
        PlayerState state = states.get(player.getUuid());
        if (state == null) return;
        if (state.role == Role.SURVIVOR && !state.eliminated) {
            state.eliminated = true;
            state.role = Role.SPECTATOR;
            broadcast(Text.translatable("message.fractured_reflections.eliminated.left", player.getDisplayName()).formatted(Formatting.GRAY));
        }
        rolesDirty = true;
    }

    // --- Client sync --------------------------------------------------------------------------

    private void syncClients() {
        List<ServerPlayerEntity> everyone = server.getPlayerManager().getPlayerList();
        if (rolesDirty || age % 100 == 0) {
            rolesDirty = false;
            refreshRoleRegistry();
            ServerNetworking.sendRoleSync(everyone, this);
        }
        if (disguisesDirty) {
            disguisesDirty = false;
            Map<UUID, UUID> disguises = new HashMap<>();
            for (PlayerState s : states.values()) if (s.disguiseTarget != null) disguises.put(s.uuid, s.disguiseTarget);
            ServerNetworking.sendDisguises(everyone, disguises);
        }
        if (tasksDirty && phase != MatchPhase.GENERATING) {
            tasksDirty = false;
            List<float[]> rows = new ArrayList<>();
            for (MansionLayout.AltarSite site : layout.altars) {
                if (main.getBlockEntity(site.altar()) instanceof RitualAltarBlockEntity a) {
                    rows.add(new float[]{a.isRefracted() ? 1 : 0, a.isForged() ? 1 : 0, a.isCleansingStarted() ? 1 : 0, a.isCompleted() ? 1 : 0, a.getCleanseProgress()});
                } else {
                    rows.add(new float[]{0, 0, 0, 0, 0});
                }
            }
            ServerNetworking.sendTasks(onlineParticipants(), rows, FRConfig.get().fragmentsPerAltar);
        }
        if (phase != MatchPhase.HUNT && phase != MatchPhase.PREPARING) return;

        FRConfig cfg = FRConfig.get();
        for (PlayerState s : states.values()) {
            ServerPlayerEntity p = player(s.uuid);
            if (p == null) continue;
            if (s.role == Role.SURVIVOR && age % 2 == 0) {
                boolean trapped = ModDimensions.isMirror(p.getWorld());
                if (Math.abs(s.stamina - s.sentStamina) > 0.4f || Math.abs(s.sanity - s.sentSanity) > 0.2f
                        || trapped != s.sentTrapped || s.exhausted != s.sentExhausted) {
                    s.sentStamina = s.stamina;
                    s.sentSanity = s.sanity;
                    s.sentTrapped = trapped;
                    s.sentExhausted = s.exhausted;
                    ServerNetworking.sendSurvivorStats(p, s, trapped, cfg.staminaMax, cfg.sanityMax);
                }
            } else if (s.isFractured() && age % 5 == 0) {
                PlayerState target = state(s.disguiseTarget);
                ServerNetworking.sendFracturedHud(p, s, target == null ? "" : target.name);
            }
        }
    }

    private void sendGhostEchoes() {
        List<ServerPlayerEntity> livingInMain = new ArrayList<>();
        for (PlayerState s : states.values()) {
            ServerPlayerEntity p = player(s.uuid);
            if (p != null && s.isActiveSurvivor() && p.getWorld() == main) livingInMain.add(p);
        }
        for (PlayerState s : states.values()) {
            ServerPlayerEntity p = player(s.uuid);
            if (p == null || !s.isActiveSurvivor() || p.getWorld() != mirror) continue;
            List<ServerPlayerEntity> teammates = new ArrayList<>(livingInMain);
            teammates.remove(p);
            ServerNetworking.sendGhostEchoes(p, teammates);
        }
    }

    private void refreshRoleRegistry() {
        List<UUID> fractured = new ArrayList<>();
        for (PlayerState s : states.values()) if (s.isFractured()) fractured.add(s.uuid);
        RoleRegistry.setServer(fractured);
    }

    // --- Messaging ---------------------------------------------------------------------------

    void broadcast(Text text) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) p.sendMessage(text, false);
    }

    static void title(ServerPlayerEntity p, Text title, Text subtitle, int fadeIn, int stay, int fadeOut) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        p.networkHandler.sendPacket(new TitleS2CPacket(title));
    }
}
