package com.revjve.fractured.client.sound;

import com.revjve.fractured.FracturedReflections;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * OpenAL EFX low-pass filter applied to every sound source while the local player is inside the
 * Mirror Dimension. Runs on the sound thread (from SourceMixin); {@link #active} is flipped by
 * the client tick. If the device lacks ALC_EXT_EFX the effect silently disables itself.
 */
@Environment(EnvType.CLIENT)
public final class MuffleFilter {
    public static volatile boolean active;
    private static int filter = -1;
    private static boolean unsupported;

    private MuffleFilter() {}

    public static void apply(int source) {
        if (unsupported) return;
        boolean muffle = active;
        if (filter == -1) {
            if (!muffle) return;
            init();
            if (unsupported) return;
        }
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, muffle ? filter : EXTEfx.AL_FILTER_NULL);
    }

    private static void init() {
        try {
            long device = ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext());
            if (device == 0L || !ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
                unsupported = true;
                return;
            }
            filter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 0.85f);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, 0.12f);
        } catch (Throwable t) {
            unsupported = true;
            FracturedReflections.LOGGER.warn("OpenAL EFX unavailable; Mirror Dimension audio will not be muffled", t);
        }
    }
}
