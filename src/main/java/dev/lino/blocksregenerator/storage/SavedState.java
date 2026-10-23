package dev.lino.blocksregenerator.storage;

import dev.lino.blocksregenerator.engine.PendingRegen;
import dev.lino.blocksregenerator.model.Region;

import java.util.List;

public record SavedState(List<Region> regions, List<PendingRegen> pending) {
    public SavedState {
        regions = List.copyOf(regions);
        pending = List.copyOf(pending);
    }

    public static SavedState empty() {
        return new SavedState(List.of(), List.of());
    }
}
