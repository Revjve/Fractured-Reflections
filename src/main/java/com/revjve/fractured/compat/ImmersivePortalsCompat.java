package com.revjve.fractured.compat;

import com.revjve.fractured.FracturedReflections;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Immersive Portals integration, bound by reflection against IP's public portal API
 * ({@code Portal}, {@code Mirror}, {@code PortalManipulation}, {@code DQuaternion}). This keeps
 * Fractured Reflections compiling and running with or without IP, and across IP releases that
 * renamed {@code Portal.entityType} → {@code Portal.ENTITY_TYPE} or the rotation setter.
 * If any call fails the integration disables itself and the mod falls back to its own
 * contact-teleport portals and shader-drawn mirror surfaces.
 */
public final class ImmersivePortalsCompat {
    private static final String PORTAL = "qouteall.imm_ptl.core.portal.Portal";
    private static final String MIRROR = "qouteall.imm_ptl.core.portal.Mirror";
    private static final String MANIPULATION = "qouteall.imm_ptl.core.portal.PortalManipulation";
    private static final String QUATERNION = "qouteall.q_misc_util.my_util.DQuaternion";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("imm_ptl_core");
    private static boolean failed;

    private ImmersivePortalsCompat() {}

    public static boolean isActive() {
        return LOADED && !failed;
    }

    /**
     * Turns a wall mirror into a two-way window into the twin world. The portal plane sits just
     * in front of the glass with its normal along {@code facing}; a 180° yaw rotation makes
     * travellers step <i>out</i> of the twin mirror rather than into the wall behind it.
     */
    public static List<Entity> openMirrorPortal(ServerWorld from, ServerWorld to, Vec3d center, Direction facing, double width, double height) {
        if (!isActive()) return List.of();
        try {
            Class<?> portalClass = Class.forName(PORTAL);
            EntityType<?> type = entityType(portalClass);
            Entity portal = type.create(from);
            if (portal == null) throw new IllegalStateException("Portal entity type returned null");
            configure(portal, to, center, facing, width, height);
            Object rotation = Class.forName(QUATERNION).getMethod("rotationByDegrees", Vec3d.class, double.class)
                    .invoke(null, new Vec3d(0, 1, 0), 180.0);
            if (!callFirst(portal, new String[]{"setRotationTransformationD", "setRotation", "setRotationTransformation"}, rotation)) {
                throw new NoSuchMethodException("No rotation setter on " + portal.getClass());
            }
            from.spawnEntity(portal);

            List<Entity> spawned = new ArrayList<>();
            spawned.add(portal);
            Object reverse = Class.forName(MANIPULATION).getMethod("completeBiWayPortal", portalClass, EntityType.class)
                    .invoke(null, portal, type);
            if (reverse instanceof Entity e) spawned.add(e);
            return spawned;
        } catch (Throwable t) {
            disable("open mirror portal", t);
            return List.of();
        }
    }

    /** Optional true planar reflection: an IP {@code Mirror} entity over a mirror block. */
    @Nullable
    public static Entity spawnReflection(ServerWorld world, Vec3d center, Direction facing, double width, double height) {
        if (!isActive()) return null;
        try {
            EntityType<?> type = entityType(Class.forName(MIRROR));
            Entity mirror = type.create(world);
            if (mirror == null) return null;
            configure(mirror, world, center, facing, width, height);
            world.spawnEntity(mirror);
            return mirror;
        } catch (Throwable t) {
            disable("spawn reflection", t);
            return null;
        }
    }

    private static void configure(Entity portal, ServerWorld destination, Vec3d center, Direction facing, double width, double height) throws ReflectiveOperationException {
        Vec3d axisW = Vec3d.of(facing.rotateYCounterclockwise().getVector());
        Vec3d axisH = new Vec3d(0, 1, 0);
        call(portal, "setOriginPos", center);
        call(portal, "setDestinationDimension", destination.getRegistryKey());
        call(portal, "setDestination", center);
        call(portal, "setOrientationAndSize", axisW, axisH, width, height);
    }

    private static EntityType<?> entityType(Class<?> cls) throws ReflectiveOperationException {
        for (String name : new String[]{"entityType", "ENTITY_TYPE"}) {
            try {
                Field field = cls.getField(name);
                if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof EntityType<?> type) return type;
            } catch (NoSuchFieldException ignored) {
                // try the next name
            }
        }
        throw new NoSuchFieldException("No static EntityType on " + cls.getName());
    }

    private static void call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Method method = find(target.getClass(), name, args);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        method.invoke(target, args);
    }

    private static boolean callFirst(Object target, String[] names, Object... args) throws ReflectiveOperationException {
        for (String name : names) {
            Method method = find(target.getClass(), name, args);
            if (method != null) {
                method.invoke(target, args);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Method find(Class<?> cls, String name, Object[] args) {
        outer:
        for (Method method : cls.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] params = method.getParameterTypes();
            for (int n = 0; n < params.length; n++) {
                if (!wrap(params[n]).isInstance(args[n])) continue outer;
            }
            return method;
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        return type;
    }

    private static void disable(String what, Throwable t) {
        failed = true;
        FracturedReflections.LOGGER.warn("Immersive Portals integration failed to {}; falling back to built-in mirror travel", what, t);
    }
}
