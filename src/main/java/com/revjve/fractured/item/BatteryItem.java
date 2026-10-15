package com.revjve.fractured.item;

import com.revjve.fractured.FRConfig;
import com.revjve.fractured.registry.ModItems;
import com.revjve.fractured.registry.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Use to top up the emptiest flashlight in the inventory. */
public class BatteryItem extends Item {
    public BatteryItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack battery = user.getStackInHand(hand);
        ItemStack target = null;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < user.getInventory().size(); i++) {
            ItemStack s = user.getInventory().getStack(i);
            if (s.isOf(ModItems.FLASHLIGHT)) {
                int charge = FlashlightItem.getCharge(s);
                if (charge < lowest) {
                    lowest = charge;
                    target = s;
                }
            }
        }
        if (target == null || lowest >= FlashlightItem.maxCharge()) {
            if (!world.isClient) user.sendMessage(Text.translatable("message.fractured_reflections.battery_unneeded"), true);
            return TypedActionResult.fail(battery);
        }
        if (!world.isClient) {
            FlashlightItem.recharge(target, FRConfig.get().batteryRechargeTicks);
            if (!user.getAbilities().creativeMode) battery.decrement(1);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.FLASHLIGHT_CLICK, SoundCategory.PLAYERS, 0.8f, 0.6f);
        }
        return TypedActionResult.success(battery, world.isClient);
    }
}
