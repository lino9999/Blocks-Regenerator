package dev.lino.blocksregenerator.engine;

import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Placeable;
import dev.lino.blocksregenerator.model.RegenChain;

import java.util.Objects;
import java.util.UUID;

public record PendingRegen(BlockKey key, String regionId, UUID ruleId, RegenChain chain, int stage,
                           BlockSnapshot snapshot, Placeable replacement, long dueAt) {
    public PendingRegen {
        Objects.requireNonNull(key);
        Objects.requireNonNull(regionId);
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(replacement);
        if (chain != null && (chain.stages().isEmpty() || stage < 1 || stage > chain.stages().size())) {
            throw new IllegalArgumentException("Invalid pending chain stage");
        }
    }

    public PendingRegen withDueAt(long value) {
        return new PendingRegen(key, regionId, ruleId, chain, stage, snapshot, replacement, value);
    }

    public PendingRegen withStage(int value, Placeable block, long due) {
        return new PendingRegen(key, regionId, ruleId, chain, value, snapshot, block, due);
    }
}
