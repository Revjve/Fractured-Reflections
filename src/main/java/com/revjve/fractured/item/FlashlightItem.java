package com.revjve.fractured.item;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.registry.ModItems;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Battery-powered flashlight. The light itself is produced client-side by LambDynamicLights
 * (see FRDynamicLights) and the beam cone by the composite post shader; both read the
 * {@code On}/{@code Charge} NBT synced from the server.
 */
public class FlashlightItem extends Item {
    public static final String ON = "On";
    public static final String CHARGE = "Charge";
    private static final int DRAIN_INTERVAL = 20;

    public FlashlightItem(Settings settings) {
        super(settings);
    }

    public static ItemStack charged() {
        ItemStack stack = new ItemStack(ModItems.FLASHLIGHT);
        stack.getOrCreateNbt().putInt(CHARGE, maxCharge());
        return stack;
    }

    public static int maxCharge() {
        return Math.max(DRAIN_INTERVAL, FRConfig.get().flashlightBatteryTicks);
    }

    public static int getCharge(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getInt(CHARGE);
    }

    public static boolean isOn(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return stack.isOf(ModItems.FLASHLIGHT) && nbt != null && nbt.getBoolean(ON) && nbt.getInt(CHARGE) > 0;
    }

    /** True if the entity holds a switched-on, charged flashlight in either hand. */
    public static boolean isLitInHand(LivingEntity entity) {
        return isOn(entity.getMainHandStack()) || isOn(entity.getOffHandStack());
    }

    public static void recharge(ItemStack stack, int ticks) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt(CHARGE, Math.min(maxCharge(), nbt.getInt(CHARGE) + ticks));
    }

    /** Keybind toggle: prefers the flashlight in hand, otherwise the first one in the hotbar. */
    public static void toggleFor(PlayerEntity player) {
        ItemStack target = null;
        if (player.getMainHandStack().isOf(ModItems.FLASHLIGHT)) target = player.getMainHandStack();
        else if (player.getOffHandStack().isOf(ModItems.FLASHLIGHT)) target = player.getOffHandStack();
        else {
            for (int i = 0; i < 9; i++) {
                ItemStack s = player.getInventory().getStack(i);
                if (s.isOf(ModItems.FLASHLIGHT)) {
                    target = s;
                    break;
                }
            }
        }
        if (target != null) toggle(player.getWorld(), player, target);
    }

    private static void toggle(World world, PlayerEntity player, ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        boolean on = !nbt.getBoolean(ON) && nbt.getInt(CHARGE) > 0;
        nbt.putBoolean(ON, on);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FLASHLIGHT_CLICK, SoundCategory.PLAYERS, 0.6f, on ? 1.2f : 0.9f);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) toggle(world, user, stack);
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof LivingEntity living)) return;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.getBoolean(ON)) return;
        boolean held = selected || living.getOffHandStack() == stack;
        if (!held || world.getTime() % DRAIN_INTERVAL != 0) return;
        int charge = nbt.getInt(CHARGE) - DRAIN_INTERVAL;
        nbt.putInt(CHARGE, Math.max(0, charge));
        if (charge <= 0) {
            nbt.putBoolean(ON, false);
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.FLASHLIGHT_FLICKER, SoundCategory.PLAYERS, 0.8f, 0.6f);
        }
    }

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return isOn(stack);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0f * getCharge(stack) / maxCharge());
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        float f = MathHelper.clamp(getCharge(stack) / (float) maxCharge(), 0f, 1f);
        return MathHelper.hsvToRgb(0.5f * f, 0.8f, 1.0f);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        int percent = Math.round(100f * getCharge(stack) / maxCharge());
        tooltip.add(Text.translatable("item.fractured_reflections.flashlight.charge", percent).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(isOn(stack) ? "item.fractured_reflections.flashlight.on" : "item.fractured_reflections.flashlight.off")
                .formatted(isOn(stack) ? Formatting.YELLOW : Formatting.DARK_GRAY));
    }
}
