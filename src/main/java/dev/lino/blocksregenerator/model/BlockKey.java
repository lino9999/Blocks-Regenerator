package dev.lino.blocksregenerator.model;

import java.util.Objects;

public record BlockKey(String world, int x, int y, int z) {
    public BlockKey {
        Objects.requireNonNull(world, "World is required");
    }

    public long chunk() {
        return ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
    }

    public BlockKey above() {
        return new BlockKey(world, x, y + 1, z);
    }

    public BlockKey below() {
        return new BlockKey(world, x, y - 1, z);
    }
}
