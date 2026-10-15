package com.revjve.fractured.entity;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Glass Echo: a sprinting decoy wearing a survivor's skin and nametag. It flees through the
 * mansion and shatters into glass the moment it takes any damage (or when its time runs out).
 */
public class GlassEchoEntity extends PathAwareEntity {
    private static final TrackedData<Optional<UUID>> MIMIC = DataTracker.registerData(GlassEchoEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private int lifetime = 20 * 20;
    private UUID owner;

    public GlassEchoEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    /** Dress the echo as {@code target} and send it running away from {@code caster}. */
    public void mimic(ServerPlayerEntity target, ServerPlayerEntity caster) {
        dataTracker.set(MIMIC, Optional.of(target.getUuid()));
        setCustomName(target.getDisplayName().copy());
        setCustomNameVisible(true);
        owner = caster.getUuid();
        lifetime = FRConfig.get().echoLifetimeSeconds * 20;
        setYaw(caster.getYaw());
        setHeadYaw(caster.getYaw());
        setBodyYaw(caster.getYaw());
    }

    public Optional<UUID> getMimicked() {
        return dataTracker.get(MIMIC);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(MIMIC, Optional.empty());
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new SprintAwayGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        setSprinting(getVelocity().horizontalLengthSquared() > 1.0E-4);
        if (--lifetime <= 0) shatter();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || isRemoved()) return false;
        if (source.getAttacker() != null && source.getAttacker().getUuid().equals(owner)) return false;
        shatter();
        return false;
    }

    public void shatter() {
        if (!(getWorld() instanceof ServerWorld world) || isRemoved()) return;
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState()),
                getX(), getBodyY(0.5), getZ(), 60, 0.3, 0.6, 0.3, 0.15);
        world.spawnParticles(ParticleTypes.END_ROD, getX(), getBodyY(0.5), getZ(), 12, 0.3, 0.6, 0.3, 0.05);
        world.playSound(null, getX(), getY(), getZ(), ModSounds.ECHO_SHATTER, SoundCategory.HOSTILE, 1.5f, 0.9f + random.nextFloat() * 0.2f);
        discard();
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public Text getName() {
        Text custom = getCustomName();
        return custom != null ? custom : super.getName();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Lifetime", lifetime);
        getMimicked().ifPresent(uuid -> nbt.putUuid("Mimic", uuid));
        if (owner != null) nbt.putUuid("Owner", owner);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        lifetime = nbt.getInt("Lifetime");
        if (nbt.containsUuid("Mimic")) dataTracker.set(MIMIC, Optional.of(nbt.getUuid("Mimic")));
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");
    }

    /** Keeps picking far-away waypoints and sprints to them, like a panicked survivor. */
    private static final class SprintAwayGoal extends Goal {
        private final GlassEchoEntity echo;
        private int repath;

        SprintAwayGoal(GlassEchoEntity echo) {
            this.echo = echo;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return true;
        }

        @Override
        public void tick() {
            if (--repath > 0 && !echo.getNavigation().isIdle()) return;
            repath = 60 + echo.random.nextInt(40);
            Vec3d look = Vec3d.fromPolar(0, echo.getYaw());
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = (echo.random.nextDouble() - 0.5) * Math.PI * (attempt < 4 ? 0.6 : 2.0);
                Vec3d dir = look.rotateY((float) angle);
                double dist = 10 + echo.random.nextDouble() * 14;
                Vec3d target = echo.getPos().add(dir.multiply(dist));
                if (echo.getNavigation().startMovingTo(target.x, echo.getY(), target.z, 1.35)) return;
            }
        }
    }
}
