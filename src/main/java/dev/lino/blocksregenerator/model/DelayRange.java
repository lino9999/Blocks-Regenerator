package dev.lino.blocksregenerator.model;

import java.util.random.RandomGenerator;

public record DelayRange(int min, int max) {
    public static final int LIMIT = 31_536_000;

    public DelayRange {
        if (min < 0 || max < min || max > LIMIT) {
            throw new IllegalArgumentException("Delay must satisfy 0 <= minimum <= maximum <= " + LIMIT);
        }
    }

    public int sample(RandomGenerator random) {
        return min == max ? min : random.nextInt(min, max + 1);
    }
}
