package dev.lino.blocksregenerator.model;

import java.util.Objects;
import java.util.UUID;

public record RegenRule(UUID id, Placeable original, Placeable replacement, DelayRange delay) {
    public RegenRule {
        Objects.requireNonNull(id);
        Objects.requireNonNull(original);
        Objects.requireNonNull(replacement);
        Objects.requireNonNull(delay);
    }

    public static RegenRule create(Placeable original) {
        String replacement = original.furniture() || original.type().equals("crop") ? "AIR" : "STONE";
        return new RegenRule(UUID.randomUUID(), original, Placeable.material(replacement), new DelayRange(5, 5));
    }

    public RegenRule copy() {
        return new RegenRule(UUID.randomUUID(), original, replacement, delay);
    }

    public RegenRule withDelay(DelayRange value) {
        return new RegenRule(id, original, replacement, value);
    }

    public RegenRule withReplacement(Placeable value) {
        return new RegenRule(id, original, value, delay);
    }

    public RegenRule withOriginal(Placeable value) {
        return new RegenRule(id, value, replacement, delay);
    }
}
