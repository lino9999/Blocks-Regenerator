package dev.lino.blocksregenerator.configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RuntimeSettings(int format, Worlds worlds, Recovery recovery, Scheduler scheduler,
                              Protection protection, Timing timing, Visuals visuals,
                              Harvesting harvesting, List<String> imports) {
    private static final Set<String> IMPORT_SOURCES = Set.of("legacy", "ultimate-regen", "minexfarm-regen");

    public RuntimeSettings {
        if (format != 1) {
            throw new IllegalArgumentException("The settings format must be 1");
        }
        Objects.requireNonNull(worlds, "World settings are required");
        Objects.requireNonNull(recovery, "Recovery settings are required");
        Objects.requireNonNull(scheduler, "Scheduler settings are required");
        Objects.requireNonNull(protection, "Protection settings are required");
        Objects.requireNonNull(timing, "Timing settings are required");
        Objects.requireNonNull(visuals, "Visual settings are required");
        Objects.requireNonNull(harvesting, "Harvesting settings are required");
        imports = List.copyOf(imports);
        if (new HashSet<>(imports).size() != imports.size() || !IMPORT_SOURCES.containsAll(imports)) {
            throw new IllegalArgumentException("Startup imports must contain unique supported source IDs");
        }
    }

    public static RuntimeSettings defaults() {
        return new RuntimeSettings(1, new Worlds(false), new Recovery(true, 30), new Scheduler(500),
                new Protection(true, true, "blocksregenerator.bypass.claims"),
                new Timing("uniform(lower, upper)"), new Visuals(40, 10),
                new Harvesting(true), List.of());
    }

    public record Worlds(boolean automaticCoverage) {
    }

    public record Recovery(boolean rememberOnRestart, int checkpointIntervalSeconds) {
        public Recovery {
            range("recovery.checkpoint-interval-seconds", checkpointIntervalSeconds, 5, 86_400);
        }
    }

    public record Scheduler(int workBudget) {
        public Scheduler {
            range("scheduler.work-budget", workBudget, 1, 10_000);
        }
    }

    public record Protection(boolean consultProviders, boolean operatorsExempt, String exemptionNode) {
        public Protection {
            nonblank("protection.exemption-node", exemptionNode);
        }
    }

    public record Timing(String formula) {
        public Timing {
            nonblank("timing.formula", formula);
            if (formula.length() > 1024) {
                throw new IllegalArgumentException("timing.formula must not exceed 1024 characters");
            }
        }
    }

    public record Visuals(int outlineDistanceBlocks, int outlineRefreshTicks) {
        public Visuals {
            range("visuals.outline-distance-blocks", outlineDistanceBlocks, 1, 256);
            range("visuals.outline-refresh-ticks", outlineRefreshTicks, 1, 1200);
        }
    }

    public record Harvesting(boolean notifyStackedBreaks) {
    }

    private static void range(String key, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void nonblank(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must be nonblank text");
        }
    }
}
