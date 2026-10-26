package dev.lino.blocksregenerator.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

public final class SettingsRepository {
    private static final int MAX_BYTES = 262_144;
    private static final Set<String> KEYS = Set.of(
            "format", "worlds", "worlds.automatic-coverage", "recovery", "recovery.remember-on-restart",
            "recovery.checkpoint-interval-seconds", "scheduler", "scheduler.work-budget", "protection",
            "protection.consult-providers", "protection.operators-exempt", "protection.exemption-node",
            "timing", "timing.formula", "visuals", "visuals.outline-distance-blocks",
            "visuals.outline-refresh-ticks", "harvesting",
            "harvesting.notify-stacked-breaks", "imports", "imports.on-start");
    private static final Set<String> SECTIONS = Set.of(
            "worlds", "recovery", "scheduler", "protection", "timing", "visuals", "harvesting", "imports");
    private final Path file;

    public SettingsRepository(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public RuntimeSettings load(InputStream defaults) throws IOException {
        if (Files.exists(file)) {
            return read();
        }
        if (defaults == null) {
            throw new IOException("The default settings resource is unavailable");
        }
        byte[] bytes = defaults.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("The settings document exceeds the supported size");
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        RuntimeSettings result = parse(content);
        Files.createDirectories(file.getParent());
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return result;
        } catch (FileAlreadyExistsException concurrentCreation) {
            return read();
        }
    }

    public RuntimeSettings read() throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) {
            throw new IOException("The settings path must contain a regular document of at most " + MAX_BYTES + " bytes");
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static RuntimeSettings parse(String content) throws IOException {
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("The settings document is missing or exceeds the supported size");
        }
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setCodePointLimit(MAX_BYTES);
            options.setNestingDepthLimit(16);
            options.setMaxAliasesForCollections(16);
            Object parsed = new Yaml(new SafeConstructor(options)).load(content);
            if (!(parsed instanceof Map<?, ?> document)) {
                throw new IllegalArgumentException("The settings document must be a mapping");
            }
            validateEntries(document, "");
            RuntimeSettings fallback = RuntimeSettings.defaults();
            int format = integer(document, "format", 0);
            return new RuntimeSettings(format,
                    new RuntimeSettings.Worlds(bool(document, "worlds.automatic-coverage", fallback.worlds().automaticCoverage())),
                    new RuntimeSettings.Recovery(
                            bool(document, "recovery.remember-on-restart", fallback.recovery().rememberOnRestart()),
                            integer(document, "recovery.checkpoint-interval-seconds", fallback.recovery().checkpointIntervalSeconds())),
                    new RuntimeSettings.Scheduler(integer(document, "scheduler.work-budget", fallback.scheduler().workBudget())),
                    new RuntimeSettings.Protection(
                            bool(document, "protection.consult-providers", fallback.protection().consultProviders()),
                            bool(document, "protection.operators-exempt", fallback.protection().operatorsExempt()),
                            text(document, "protection.exemption-node", fallback.protection().exemptionNode())),
                    new RuntimeSettings.Timing(text(document, "timing.formula", fallback.timing().formula())),
                    new RuntimeSettings.Visuals(
                            integer(document, "visuals.outline-distance-blocks", fallback.visuals().outlineDistanceBlocks()),
                            integer(document, "visuals.outline-refresh-ticks", fallback.visuals().outlineRefreshTicks())),
                    new RuntimeSettings.Harvesting(
                            bool(document, "harvesting.notify-stacked-breaks", fallback.harvesting().notifyStackedBreaks())),
                    imports(document));
        } catch (YAMLException | IllegalArgumentException error) {
            throw new IOException("Settings validation failed: " + error.getMessage(), error);
        }
    }

    private static void validateEntries(Map<?, ?> entries, String prefix) {
        for (var entry : entries.entrySet()) {
            if (!(entry.getKey() instanceof String name) || name.contains(".")) {
                throw new IllegalArgumentException("Settings keys must be simple text names");
            }
            String key = prefix.isEmpty() ? name : prefix + "." + name;
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown settings key: " + key);
            }
            Object value = entry.getValue();
            if (value == null) {
                throw new IllegalArgumentException(key + " must not be null");
            }
            if (SECTIONS.contains(key)) {
                if (!(value instanceof Map<?, ?> nested)) {
                    throw new IllegalArgumentException(key + " must be a mapping");
                }
                validateEntries(nested, key);
            } else if (value instanceof Map<?, ?>) {
                throw new IllegalArgumentException(key + " must be a value, not a mapping");
            }
        }
    }

    private static Object value(Map<?, ?> document, String key) {
        Object current = document;
        for (String component : key.split("\\.")) {
            if (!(current instanceof Map<?, ?> section)) {
                return null;
            }
            current = section.get(component);
        }
        return current;
    }

    private static boolean bool(Map<?, ?> document, String key, boolean fallback) {
        Object value = value(document, key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return flag;
    }

    private static int integer(Map<?, ?> document, String key, int fallback) {
        Object value = value(document, key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(key + " must be a whole number");
        }
        long number = ((Number) value).longValue();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " is outside the supported integer range");
        }
        return (int) number;
    }

    private static String text(Map<?, ?> document, String key, String fallback) {
        Object value = value(document, key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(key + " must be text");
        }
        return text;
    }

    private static List<String> imports(Map<?, ?> document) {
        Object value = value(document, "imports.on-start");
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException("imports.on-start must be a list of source IDs");
        }
        List<String> result = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof String source)) {
                throw new IllegalArgumentException("imports.on-start entries must be text");
            }
            result.add(source);
        }
        return result;
    }
}
