package dev.lino.blocksregenerator.engine;

import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Placeable;

import java.util.Map;
import java.util.Objects;

public record BlockSnapshot(Placeable source, Map<BlockKey, String> data) {
    public BlockSnapshot {
        Objects.requireNonNull(source);
        data = Map.copyOf(data);
    }
}
