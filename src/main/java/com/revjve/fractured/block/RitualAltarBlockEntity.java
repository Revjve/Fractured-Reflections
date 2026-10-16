package com.revjve.fractured.block;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.game.Match;
import com.revjve.fractured.game.MatchManager;
import com.revjve.fractured.game.Role;
import com.revjve.fractured.registry.ModBlockEntities;
import com.revjve.fractured.registry.ModBlocks;
import com.revjve.fractured.registry.ModItems;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ritual state machine for one Glass Ritual Altar:
 * <ol>
 *   <li><b>Refraction Puzzle</b> — every Lumen Prism beam, redirected by the rotatable lenses,
 *       must land on one of the altar's focal points.</li>
 *   <li><b>Shard Forging</b> — a Pure Mirror (forged from smelted cursed fragments) is seated.</li>
 *   <li><b>The Cleansing Stand</b> — survivors hold the ring for {@code cleansingSeconds} while
 *       the altar emits loud cues and reveals itself to The Fractured.</li>
 * </ol>
 */
public class RitualAltarBlockEntity extends BlockEntity {
    private int index;
    private final List<BlockPos> emitters = new ArrayList<>();
    private final List<BlockPos> focals = new ArrayList<>();
    private final List<BlockPos> lenses = new ArrayList<>();
    private boolean refracted;
    private boolean forged;
    private boolean cleansingStarted;
    private boolean completed;
    private int cleanseTicks;
    private int age;
    private float clientRadius = 3.5f;
    private int syncedTotalTicks;

    public RitualAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_ALTAR, pos, state);
    }

    public void configure(int index, List<BlockPos> emitters, List<BlockPos> focals, List<BlockPos> lenses) {
        this.index = index;
        this.emitters.clear();
        this.emitters.addAll(emitters);
        this.focals.clear();
        this.focals.addAll(focals);
        this.lenses.clear();
        this.lenses.addAll(lenses);
        this.refracted = false;
        this.forged = false;
        this.cleansingStarted = false;
        this.completed = false;
        this.cleanseTicks = 0;
        sync();
    }

    public int getIndex() { return index; }
    public boolean isRefracted() { return refracted; }
    public boolean isForged() { return forged; }
    public boolean isCleansingStarted() { return cleansingStarted; }
    public boolean isCompleted() { return completed; }

    public float getCleanseProgress() {
        int total = world != null && world.isClient && syncedTotalTicks > 0 ? syncedTotalTicks : cleanseTotalTicks();
        return MathHelper.clamp(cleanseTicks / (float) total, 0f, 1f);
    }

    private static int cleanseTotalTicks() {
        return Math.max(20, FRConfig.get().cleansingSeconds * 20);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, RitualAltarBlockEntity altar) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        altar.age++;
        if (altar.completed) {
            if (altar.age % 20 == 0) {
                serverWorld.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 3, 0.3, 0.4, 0.3, 0.01);
            }
            return;
        }
        if (!altar.refracted && altar.age % 10 == 0) {
            altar.traceBeams(serverWorld);
        }
        if (altar.refracted && altar.forged && altar.cleansingStarted) {
            altar.tickCleansing(serverWorld);
        } else if (altar.refracted && altar.forged && altar.age % 10 == 0) {
            altar.drawRing(serverWorld, ParticleTypes.ENCHANT, 16);
        }
    }

    // --- Refraction ------------------------------------------------------------------------

    private void traceBeams(ServerWorld world) {
        if (emitters.isEmpty() || focals.isEmpty()) return;
        // the beams themselves are drawn client-side by RitualAltarRenderer from the same trace
        BeamTracer.Result trace = BeamTracer.trace(world, emitters);
        Set<BlockPos> litLenses = trace.litLenses();
        Set<BlockPos> litFocals = trace.litFocals();
        for (BlockPos lensPos : lenses) {
            BlockState st = world.getBlockState(lensPos);
            if (st.isOf(ModBlocks.REFRACTION_LENS)) {
                boolean lit = litLenses.contains(lensPos);
                if (st.get(LensBlock.LIT) != lit) world.setBlockState(lensPos, st.with(LensBlock.LIT, lit), Block.NOTIFY_ALL);
            }
        }
        for (BlockPos focalPos : focals) {
            BlockState st = world.getBlockState(focalPos);
            if (st.isOf(ModBlocks.FOCAL_POINT)) {
                boolean lit = litFocals.contains(focalPos);
                if (st.get(FocalPointBlock.LIT) != lit) world.setBlockState(focalPos, st.with(FocalPointBlock.LIT, lit), Block.NOTIFY_ALL);
            }
        }
        if (litFocals.size() >= focals.size()) {
            refracted = true;
            setStage(world, 1);
            world.playSound(null, pos, ModSounds.REFRACTION_LIT, SoundCategory.BLOCKS, 2.5f, 1.0f);
            for (BlockPos focal : focals) {
                world.spawnParticles(ParticleTypes.END_ROD, focal.getX() + 0.5, focal.getY() + 1.0, focal.getZ() + 0.5, 30, 0.2, 0.6, 0.2, 0.08);
            }
            broadcast(world, Text.translatable("message.fractured_reflections.altar_refracted", index + 1).formatted(Formatting.AQUA));
            MatchManager.get().onRitualProgress();
        }
    }

    // --- Interaction ---------------------------------------------------------------------

    public void onUse(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) this.world;
        if (world == null) return;
        Match match = MatchManager.get().getMatch();
        if (match != null && match.roleOf(player) == Role.FRACTURED) {
            player.sendMessage(Text.translatable("message.fractured_reflections.altar_rejects").formatted(Formatting.DARK_PURPLE), true);
            return;
        }
        if (completed) {
            player.sendMessage(Text.translatable("message.fractured_reflections.altar_done"), true);
        } else if (!refracted) {
            player.sendMessage(Text.translatable("message.fractured_reflections.altar_needs_refraction").formatted(Formatting.GRAY), true);
        } else if (!forged) {
            ItemStack held = player.getMainHandStack();
            if (held.isOf(ModItems.PURE_MIRROR)) {
                if (!player.getAbilities().creativeMode) held.decrement(1);
                forged = true;
                setStage(world, 2);
                world.playSound(null, pos, ModSounds.PURE_MIRROR_SEATED, SoundCategory.BLOCKS, 2.0f, 1.0f);
                broadcast(world, Text.translatable("message.fractured_reflections.altar_forged", index + 1).formatted(Formatting.LIGHT_PURPLE));
                MatchManager.get().onRitualProgress();
            } else {
                player.sendMessage(Text.translatable("message.fractured_reflections.altar_needs_mirror").formatted(Formatting.GRAY), true);
            }
        } else if (!cleansingStarted) {
            cleansingStarted = true;
            sync();
            world.playSound(null, pos, ModSounds.CLEANSING_CUE, SoundCategory.BLOCKS, 6.0f, 0.8f);
            broadcast(world, Text.translatable("message.fractured_reflections.cleansing_started", index + 1).formatted(Formatting.GOLD));
            MatchManager.get().onRitualProgress();
        } else {
            player.sendMessage(Text.translatable("message.fractured_reflections.cleansing_hold"), true);
        }
    }

    // --- Cleansing -----------------------------------------------------------------------

    private void tickCleansing(ServerWorld world) {
        FRConfig cfg = FRConfig.get();
        double radiusSq = cfg.cleansingRadius * cfg.cleansingRadius;
        List<ServerPlayerEntity> inside = new ArrayList<>();
        Match match = MatchManager.get().getMatch();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator()) continue;
            if (match != null && match.roleOf(p) != Role.SURVIVOR) continue;
            double dx = p.getX() - (pos.getX() + 0.5);
            double dz = p.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= radiusSq && Math.abs(p.getY() - pos.getY()) <= 2.5) inside.add(p);
        }

        if (!inside.isEmpty()) {
            cleanseTicks++;
            for (ServerPlayerEntity p : inside) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 30, 0, false, false));
            }
            if (age % Math.max(10, cfg.cleansingCueIntervalTicks) == 0) {
                world.playSound(null, pos, ModSounds.CLEANSING_CUE, SoundCategory.BLOCKS, 6.0f, 0.7f + getCleanseProgress() * 0.6f);
                MatchManager.get().revealToFractured(world, pos, 60);
            }
        } else if (age % 4 == 0 && cleanseTicks > 0) {
            cleanseTicks--;
        }

        if (age % 5 == 0) drawRing(world, inside.isEmpty() ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD, 24);
        if (age % 10 == 0) sync();

        if (cleanseTicks >= cleanseTotalTicks()) {
            completed = true;
            setStage(world, 3);
            world.playSound(null, pos, ModSounds.ALTAR_CLEANSED, SoundCategory.BLOCKS, 8.0f, 1.0f);
            world.spawnParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            MatchManager.get().onAltarCleansed(this);
        }
    }

    private void drawRing(ServerWorld world, net.minecraft.particle.ParticleEffect effect, int points) {
        double r = FRConfig.get().cleansingRadius;
        double phase = age * 0.05;
        for (int i = 0; i < points; i++) {
            double a = phase + i * (Math.PI * 2 / points);
            world.spawnParticles(effect, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.2, pos.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
        }
    }

    /** Pushes the block entity to watching clients (the renderer needs beams, flags and progress). */
    private void sync() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    public List<BlockPos> getEmitters() { return emitters; }
    public List<BlockPos> getFocals() { return focals; }
    public int getAge() { return age; }
    public float getRingRadius() { return clientRadius; }

    /** Client-side tick: advances the animation clock used by the renderer. */
    public static void clientTick(World world, BlockPos pos, BlockState state, RitualAltarBlockEntity altar) {
        altar.age++;
    }

    private void setStage(ServerWorld world, int stage) {
        BlockState state = world.getBlockState(pos);
        if (state.isOf(ModBlocks.RITUAL_ALTAR) && state.get(RitualAltarBlock.STAGE) != stage) {
            world.setBlockState(pos, state.with(RitualAltarBlock.STAGE, stage), Block.NOTIFY_ALL);
        }
        sync();
    }

    private static void broadcast(ServerWorld world, Text text) {
        MatchManager.get().broadcast(world.getServer(), text);
    }

    // --- Persistence -----------------------------------------------------------------------

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Index", index);
        nbt.put("Emitters", writePositions(emitters));
        nbt.put("Focals", writePositions(focals));
        nbt.put("Lenses", writePositions(lenses));
        nbt.putBoolean("Refracted", refracted);
        nbt.putBoolean("Forged", forged);
        nbt.putBoolean("CleansingStarted", cleansingStarted);
        nbt.putBoolean("Completed", completed);
        nbt.putInt("CleanseTicks", cleanseTicks);
        nbt.putFloat("Radius", (float) FRConfig.get().cleansingRadius);
        nbt.putInt("TotalTicks", cleanseTotalTicks());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        index = nbt.getInt("Index");
        readPositions(nbt.getList("Emitters", NbtElement.COMPOUND_TYPE), emitters);
        readPositions(nbt.getList("Focals", NbtElement.COMPOUND_TYPE), focals);
        readPositions(nbt.getList("Lenses", NbtElement.COMPOUND_TYPE), lenses);
        refracted = nbt.getBoolean("Refracted");
        forged = nbt.getBoolean("Forged");
        cleansingStarted = nbt.getBoolean("CleansingStarted");
        completed = nbt.getBoolean("Completed");
        cleanseTicks = nbt.getInt("CleanseTicks");
        if (nbt.contains("Radius")) clientRadius = nbt.getFloat("Radius");
        if (nbt.contains("TotalTicks")) syncedTotalTicks = nbt.getInt("TotalTicks");
    }

    private static NbtList writePositions(List<BlockPos> positions) {
        NbtList list = new NbtList();
        for (BlockPos p : positions) list.add(NbtHelper.fromBlockPos(p));
        return list;
    }

    private static void readPositions(NbtList list, List<BlockPos> out) {
        out.clear();
        for (int i = 0; i < list.size(); i++) out.add(NbtHelper.toBlockPos(list.getCompound(i)));
    }
}
