package dev.lino.blocksregenerator.engine;

import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Placeable;

public interface WorldAccess {
    boolean loaded(BlockKey key);
    boolean available(Placeable placeable);
    void place(BlockKey key, Placeable placeable);
    void restore(BlockKey key, BlockSnapshot snapshot);
    boolean needsSupport(BlockKey key, BlockSnapshot snapshot);
    void failure(BlockKey key, RuntimeException error);
}
