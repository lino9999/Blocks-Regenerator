package dev.lino.blocksregenerator.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RegenChain(UUID id, String name, List<RegenRule> stages, Style style, boolean lastStageBreakable) {
    public enum Style { DIRECT, STAGED_SAME_TIMER, STAGED_PER_STAGE }

    public RegenChain {
        Objects.requireNonNull(id);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Chain name is required");
        }
        stages = List.copyOf(stages);
        Objects.requireNonNull(style);
    }

    public int lastIndex() {
        return stages.size() - 1;
    }

    public int degradeTarget(int current) {
        return Math.min(current + 1, lastIndex());
    }

    public int regenTarget(int current) {
        return current > lastIndex() ? lastIndex() : style == Style.DIRECT ? 0 : Math.max(0, current - 1);
    }

    public RegenRule timerStage(int current) {
        return stages.get(style == Style.STAGED_SAME_TIMER ? 0 : Math.min(current, lastIndex()));
    }

    public RegenChain copy() {
        return new RegenChain(UUID.randomUUID(), name, stages.stream().map(RegenRule::copy).toList(), style, lastStageBreakable);
    }
}
