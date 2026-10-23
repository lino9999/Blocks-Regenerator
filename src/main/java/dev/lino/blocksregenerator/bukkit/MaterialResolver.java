package dev.lino.blocksregenerator.bukkit;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.bukkit.Material;

public final class MaterialResolver {
    private MaterialResolver() {
    }

    public static Material match(String id) {
        return resolve(id, Material::matchMaterial);
    }

    static <T> T resolve(String id, Function<String, T> lookup) {
        if (id == null || id.isBlank()) {
            return null;
        }
        T direct = lookup.apply(id);
        if (direct != null) {
            return direct;
        }
        String normalized = id.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        List<String> alternatives = switch (normalized) {
            case "CHAIN" -> List.of("IRON_CHAIN");
            case "IRON_CHAIN" -> List.of("CHAIN");
            default -> List.of();
        };
        for (String alternative : alternatives) {
            T result = lookup.apply(alternative);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static Material chainIcon() {
        Material material = match("IRON_CHAIN");
        return material == null ? Material.IRON_BARS : material;
    }
}
