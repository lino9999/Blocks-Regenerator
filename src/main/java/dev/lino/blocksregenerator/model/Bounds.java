package dev.lino.blocksregenerator.model;

public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Bounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid region bounds");
        }
    }

    public static Bounds between(BlockKey a, BlockKey b) {
        if (!a.world().equals(b.world())) {
            throw new IllegalArgumentException("Both positions must be in the same world");
        }
        return new Bounds(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }

    public boolean contains(BlockKey key) {
        return key.x() >= minX && key.x() <= maxX && key.y() >= minY && key.y() <= maxY
                && key.z() >= minZ && key.z() <= maxZ;
    }

    public boolean overlaps(Bounds other) {
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
