package dev.lino.blocksregenerator.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record Placeable(String type, String id, int age) {
    public static final Set<String> TYPES = Set.of("material", "crop", "itemsadder", "itemsadder-furniture",
            "oraxen", "oraxen-furniture", "nexo", "nexo-furniture");

    public Placeable {
        type = Objects.requireNonNull(type, "Block type is required").toLowerCase(Locale.ROOT).replace('_', '-');
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown block type: " + type);
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Block ID is required");
        }
        if (age < -1) {
            throw new IllegalArgumentException("Crop age must be -1 or greater");
        }
        if (type.equals("material") || type.equals("crop")) {
            id = id.toUpperCase(Locale.ROOT);
        }
    }

    public Placeable(String type, String id) {
        this(type, id, -1);
    }

    public static Placeable material(String material) {
        return new Placeable("material", material);
    }

    public boolean vanilla() {
        return type.equals("material") || type.equals("crop");
    }

    public boolean furniture() {
        return type.endsWith("-furniture");
    }

    public int specificity() {
        return furniture() ? 4 : vanilla() ? (type.equals("crop") && age >= 0 ? 2 : 1) : 3;
    }

    public String display() {
        return (vanilla() ? id : type + ":" + id) + (age >= 0 ? " (age " + age + ")" : "");
    }
}
