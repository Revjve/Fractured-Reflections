package com.revjve.fractured.registry;

import com.revjve.fractured.FracturedReflections;
import com.revjve.fractured.item.BatteryItem;
import com.revjve.fractured.item.FlashlightItem;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
    public static final Item FLASHLIGHT = register("flashlight", new FlashlightItem(new Item.Settings().maxCount(1)));
    public static final Item BATTERY = register("battery", new BatteryItem(new Item.Settings().maxCount(16)));

    /** Found in puzzle chests; smelt in a mansion furnace. */
    public static final Item CURSED_GLASS_FRAGMENT = register("cursed_glass_fragment", new Item(new Item.Settings().maxCount(16)));
    /** Furnace output; three fuse into a Pure Mirror (shapeless, works in the 2x2 inventory grid). */
    public static final Item MOLTEN_MIRROR_GLASS = register("molten_mirror_glass", new Item(new Item.Settings().maxCount(16)));
    public static final Item PURE_MIRROR = register("pure_mirror", new Item(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

    public static final Item MIRROR = registerBlockItem("mirror", ModBlocks.MIRROR);
    public static final Item GLOWING_CRACKED_MIRROR = registerBlockItem("glowing_cracked_mirror", ModBlocks.ESCAPE_MIRROR);
    public static final Item RITUAL_ALTAR = registerBlockItem("ritual_altar", ModBlocks.RITUAL_ALTAR);
    public static final Item REFRACTION_LENS = registerBlockItem("refraction_lens", ModBlocks.REFRACTION_LENS);
    public static final Item LUMEN_PRISM = registerBlockItem("lumen_prism", ModBlocks.LUMEN_PRISM);
    public static final Item FOCAL_POINT = registerBlockItem("focal_point", ModBlocks.FOCAL_POINT);
    public static final Item PUZZLE_CHEST = registerBlockItem("puzzle_chest", ModBlocks.PUZZLE_CHEST);

    private ModItems() {}

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, FracturedReflections.id(name), item);
    }

    private static Item registerBlockItem(String name, Block block) {
        return register(name, new BlockItem(block, new Item.Settings()));
    }

    public static void register() {
        // class-load trigger
    }
}
