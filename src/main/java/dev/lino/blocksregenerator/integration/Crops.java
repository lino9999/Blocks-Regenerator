package dev.lino.blocksregenerator.integration;

import java.util.Map;
import org.bukkit.Material;

final class Crops {
    static final Map<Material, Material> BLOCKS = Map.ofEntries(
            Map.entry(Material.WHEAT_SEEDS, Material.WHEAT),
            Map.entry(Material.POTATO, Material.POTATOES),
            Map.entry(Material.CARROT, Material.CARROTS),
            Map.entry(Material.BEETROOT_SEEDS, Material.BEETROOTS),
            Map.entry(Material.NETHER_WART, Material.NETHER_WART),
            Map.entry(Material.COCOA_BEANS, Material.COCOA),
            Map.entry(Material.SWEET_BERRIES, Material.SWEET_BERRY_BUSH),
            Map.entry(Material.MELON_SEEDS, Material.MELON_STEM),
            Map.entry(Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM),
            Map.entry(Material.SUGAR_CANE, Material.SUGAR_CANE),
            Map.entry(Material.CACTUS, Material.CACTUS));

    private Crops() {
    }

    static Material item(Material block) {
        return BLOCKS.entrySet().stream().filter(entry -> entry.getValue() == block)
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
}
