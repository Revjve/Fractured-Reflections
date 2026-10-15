package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import com.revjve.fractured.item.FlashlightItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroups {
    public static final ItemGroup MAIN = Registry.register(Registries.ITEM_GROUP, FracturedReflections.id("main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModItems.PURE_MIRROR))
                    .displayName(Text.translatable("itemGroup.fractured_reflections.main"))
                    .entries((context, entries) -> {
                        entries.add(FlashlightItem.charged());
                        entries.add(ModItems.BATTERY);
                        entries.add(ModItems.CURSED_GLASS_FRAGMENT);
                        entries.add(ModItems.MOLTEN_MIRROR_GLASS);
                        entries.add(ModItems.PURE_MIRROR);
                        entries.add(ModItems.MIRROR);
                        entries.add(ModItems.GLOWING_CRACKED_MIRROR);
                        entries.add(ModItems.RITUAL_ALTAR);
                        entries.add(ModItems.REFRACTION_LENS);
                        entries.add(ModItems.LUMEN_PRISM);
                        entries.add(ModItems.FOCAL_POINT);
                        entries.add(ModItems.PUZZLE_CHEST);
                    })
                    .build());

    private ModItemGroups() {}

    public static void register() {
        // class-load trigger
    }
}
