package com.revjve.fractured.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Sky/fog for the Mirror Dimension: no sky, no clouds, thick blood-black fog. */
@Environment(EnvType.CLIENT)
public class MirrorDimensionEffects extends DimensionEffects {
    private static final Vec3d FOG = new Vec3d(0.10, 0.004, 0.012);

    public MirrorDimensionEffects() {
        super(Float.NaN, false, SkyType.NONE, false, true);
    }

    @Override
    public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
        return FOG;
    }

    @Override
    public boolean useThickFog(int camX, int camY) {
        return true;
    }

    @Nullable
    @Override
    public float[] getFogColorOverride(float skyAngle, float tickDelta) {
        return null;
    }
}
