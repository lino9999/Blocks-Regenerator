import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class UpgradeSettings {
    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("generate-global-regions", "worlds.automatic-coverage"),
            Map.entry("persistent-timers", "recovery.remember-on-restart"),
            Map.entry("storage.autosave-seconds", "recovery.checkpoint-interval-seconds"),
            Map.entry("engine.max-operations-per-tick", "scheduler.work-budget"),
            Map.entry("hooks.anti-grief.enabled", "protection.consult-providers"),
            Map.entry("hooks.anti-grief.ignore-op", "protection.operators-exempt"),
            Map.entry("hooks.anti-grief.bypass-permission", "protection.exemption-node"),
            Map.entry("advanced.regen-delay.expression", "timing.formula"),
            Map.entry("advanced.region-edges.render-radius", "visuals.outline-distance-blocks"),
            Map.entry("advanced.region-edges.render-interval", "visuals.outline-refresh-ticks"),
            Map.entry("advanced.stacked-blocks.fire-break-events", "harvesting.notify-stacked-breaks"));
    private static final Map<String, String> NOTICES = Map.of(
            "cant-break-block", "mining-denied", "cant-place-block", "building-denied",
            "cant-use-bucket", "bucket-blocked", "cant-trample", "soil-protected",
            "regen-time-remaining", "recovering", "integration-error", "provider-not-ready",
            "pending-block", "recovery-pending");

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected plugin directory and both default document paths");
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) throw new IOException("The plugin directory does not exist");
        checkStopped(directory);
        Path settingsFile = directory.resolve("settings.yml");
        Path noticesFile = directory.resolve("locale/en.yml");
        if (Files.exists(settingsFile) || Files.exists(noticesFile)) {
            throw new IOException("Version 2 documents already exist; no files were replaced");
        }
        Map<String, Object> settings = read(Path.of(args[1]));
        Map<String, Object> notices = read(Path.of(args[2]));
        Path oldSettings = directory.resolve("config.yml");
        Path oldNotices = directory.resolve("messages.yml");
        if (!Files.isRegularFile(oldSettings)) throw new IOException("No version 1 settings document exists");
        Map<String, Object> previous = read(oldSettings);
        for (var entry : PATHS.entrySet()) {
            Object value = get(previous, entry.getKey());
            if (value != null) {
                put(settings, entry.getValue(), entry.getValue().equals("timing.formula") ? formula(value) : value);
            }
        }
        List<String> imports = new ArrayList<>();
        for (String id : List.of("ultimate-regen", "minexfarm-regen")) {
            if (Boolean.TRUE.equals(get(previous, "advanced.converters." + id + ".enabled"))) imports.add(id);
        }
        put(settings, "imports.on-start", imports);
        if (Files.isRegularFile(oldNotices)) {
            Map<String, Object> old = read(oldNotices);
            Object badge = old.get("prefix");
            if (badge != null) {
                if (!(badge instanceof String)) throw new IOException("The old message prefix must be text");
                notices.put("badge", badge);
            }
            for (var entry : NOTICES.entrySet()) {
                Object value = old.get(entry.getKey());
                if (value == null) continue;
                if (!(value instanceof String line)) throw new IOException("An old notice is not text");
                var delay = java.util.regex.Pattern.compile("<delay:(\\d+)>").matcher(line);
                long cooldown = delay.find() ? Math.min(60_000, Long.parseLong(delay.group(1))) : 0;
                String rewritten = line.replaceAll("<delay:\\d+>", "")
                        .replace("%dd%", "{days}").replace("%hh%", "{hours}")
                        .replace("%mm%", "{minutes}").replace("%ss%", "{seconds}").replace("%ms%", "{millis}");
                put(notices, "notices." + entry.getValue() + ".text", rewritten);
                put(notices, "notices." + entry.getValue() + ".cooldown-ms", cooldown);
            }
        }
        String settingsText = dump(settings);
        String noticesText = dump(notices);
        Path backup = directory.resolve("upgrade-backups").resolve("v1-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")));
        Files.createDirectories(backup);
        for (Path file : List.of(oldSettings, oldNotices, directory.resolve("data.json"), directory.resolve("data.json.bak"))) {
            if (Files.isRegularFile(file)) Files.copy(file, backup.resolve(file.getFileName()));
        }
        boolean settingsCreated = false;
        boolean noticesCreated = false;
        try {
            Files.writeString(settingsFile, settingsText, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            settingsCreated = true;
            Files.createDirectories(noticesFile.getParent());
            Files.writeString(noticesFile, noticesText, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            noticesCreated = true;
        } catch (IOException error) {
            if (noticesCreated) Files.deleteIfExists(noticesFile);
            if (settingsCreated) Files.deleteIfExists(settingsFile);
            throw error;
        }
        System.out.println("Version 2 settings and notices prepared. Data and recovery snapshots were not modified.");
        System.out.println("Version 1 backup: " + backup);
        System.out.println("Review the new documents, install the version 2 plugin and start the server.");
    }

    private static void checkStopped(Path directory) throws IOException {
        Path server = directory.getParent() == null ? null : directory.getParent().getParent();
        if (server == null || !Files.isDirectory(server)) throw new IOException("Cannot identify the server folder");
        try (var paths = Files.list(server)) {
            for (Path world : paths.filter(Files::isDirectory).toList()) {
                Path lockPath = world.resolve("session.lock");
                if (!Files.isRegularFile(lockPath)) continue;
                try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE)) {
                    try (FileLock lock = channel.tryLock()) {
                        if (lock == null) throw new IOException("Stop the server before upgrading its settings");
                    }
                } catch (OverlappingFileLockException error) {
                    throw new IOException("Stop the server before upgrading its settings", error);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(Path file) throws IOException {
        if (Files.size(file) > 262_144) throw new IOException("The YAML document is too large");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(262_144);
        options.setNestingDepthLimit(16);
        options.setMaxAliasesForCollections(16);
        Object value = new Yaml(new SafeConstructor(options)).load(Files.readString(file));
        if (!(value instanceof Map<?, ?>)) throw new IOException("Expected a YAML mapping");
        return (Map<String, Object>) value;
    }

    private static Object get(Map<?, ?> root, String path) {
        Object value = root;
        for (String part : path.split("\\.")) value = value instanceof Map<?, ?> map ? map.get(part) : null;
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> section = root;
        for (int index = 0; index < parts.length - 1; index++) {
            section = (Map<String, Object>) section.computeIfAbsent(parts[index], ignored -> new LinkedHashMap<>());
        }
        section.put(parts[parts.length - 1], value);
    }

    private static String formula(Object value) throws IOException {
        if (!(value instanceof String text)) throw new IOException("The old formula must be text");
        return text.replace("%min_delay%", "lower").replace("%max_delay%", "upper")
                .replace("%player_count%", "players").replaceAll("(?i)\\brUni\\s*\\(", "uniform(");
    }

    private static String dump(Map<String, Object> document) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options).dump(document);
    }
}
