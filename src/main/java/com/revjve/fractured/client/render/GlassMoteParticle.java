package com.revjve.fractured.client.render;

import com.revjve.fractured.registry.ModDimensions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/** A tiny twinkling flake of glass — a smouldering ember inside the Mirror Dimension (sprites from vanilla's spark set). */
@Environment(EnvType.CLIENT)
public class GlassMoteParticle extends SpriteBillboardParticle {
    private final float twinklePhase;

    protected GlassMoteParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z, vx, vy, vz);
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.velocityMultiplier = 0.99f;
        this.gravityStrength = 0f;
        this.collidesWithWorld = false;
        this.maxAge = 80 + this.random.nextInt(100);
        this.scale = 0.03f + this.random.nextFloat() * 0.05f;
        this.twinklePhase = this.random.nextFloat() * MathHelper.TAU;
        if (world.getRegistryKey() == ModDimensions.MIRROR) {
            // in the Mirror Dimension the glass dust smoulders like embers
            float heat = this.random.nextFloat();
            setColor(1.0f, 0.12f + heat * 0.35f, 0.06f + heat * 0.1f);
        } else {
            setColor(0.82f, 0.9f, 1.0f);
        }
        setAlpha(0f);
        setSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = age / (float) maxAge;
        float twinkle = 0.55f + 0.45f * MathHelper.sin(age * 0.35f + twinklePhase);
        setAlpha(MathHelper.sin(life * MathHelper.PI) * 0.85f * twinkle);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Environment(EnvType.CLIENT)
    public static final class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
            return new GlassMoteParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
