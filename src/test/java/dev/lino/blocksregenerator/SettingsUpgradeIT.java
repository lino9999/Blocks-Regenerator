package dev.lino.blocksregenerator;

import static org.junit.jupiter.api.Assertions.*;

import dev.lino.blocksregenerator.configuration.SettingsRepository;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsUpgradeIT {
    @TempDir Path directory;

    @Test
    void convertsCustomSettingsAndNoticesWithoutChangingState() throws Exception {
        Path plugin = fixture();
        String state = "{\"schema\":1,\"regions\":[],\"timers\":[]}\n";
        Files.writeString(plugin.resolve("data.json"), state);
        Result result = upgrade(plugin);
        assertEquals(0, result.code(), result.output());
        var settings = new SettingsRepository(plugin.resolve("settings.yml")).read();
        assertEquals(777, settings.scheduler().workBudget());
        assertFalse(settings.protection().operatorsExempt());
        assertEquals("uniform(lower, upper) / (1 + players)", settings.timing().formula());
        assertEquals(state, Files.readString(plugin.resolve("data.json")));
        String notices = Files.readString(plugin.resolve("locale/en.yml"));
        assertTrue(notices.contains("{seconds}"));
        assertTrue(notices.contains("cooldown-ms: 2500"));
        assertFalse(notices.contains("<delay:"));
        try (var backups = Files.list(plugin.resolve("upgrade-backups"))) {
            Path backup = backups.findFirst().orElseThrow();
            assertEquals(state, Files.readString(backup.resolve("data.json")));
            assertEquals(Files.readString(plugin.resolve("config.yml")), Files.readString(backup.resolve("config.yml")));
        }
    }

    @Test
    void refusesToOverwritePreparedVersionTwoDocuments() throws Exception {
        Path plugin = fixture();
        Files.writeString(plugin.resolve("settings.yml"), "keep this document");
        Result result = upgrade(plugin);
        assertNotEquals(0, result.code());
        assertEquals("keep this document", Files.readString(plugin.resolve("settings.yml")));
        assertFalse(Files.exists(plugin.resolve("locale/en.yml")));
    }

    @Test
    void malformedSourceDoesNotCreateReplacementFiles() throws Exception {
        Path plugin = fixture();
        Files.writeString(plugin.resolve("config.yml"), "broken: [mapping\n");
        assertNotEquals(0, upgrade(plugin).code());
        assertFalse(Files.exists(plugin.resolve("settings.yml")));
        assertFalse(Files.exists(plugin.resolve("locale/en.yml")));
        assertEquals("broken: [mapping\n", Files.readString(plugin.resolve("config.yml")));
    }

    @Test
    void activeWorldLockPreventsMigration() throws Exception {
        Path plugin = fixture();
        Path world = Files.createDirectories(directory.resolve("server/world"));
        try (FileChannel channel = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertTrue(lock.isValid());
            assertNotEquals(0, upgrade(plugin).code());
            assertFalse(Files.exists(plugin.resolve("settings.yml")));
        }
    }

    private Path fixture() throws Exception {
        Path plugin = Files.createDirectories(directory.resolve("server/plugins/BlocksRegenerator"));
        Files.writeString(plugin.resolve("config.yml"), """
                engine:
                  max-operations-per-tick: 777
                hooks:
                  anti-grief:
                    ignore-op: false
                advanced:
                  regen-delay:
                    expression: "rUni(%min_delay%, %max_delay%) / (1 + %player_count%)"
                """);
        Files.writeString(plugin.resolve("messages.yml"), """
                prefix: "&bCustom "
                regen-time-remaining: "Wait %ss%s.<delay:2500>"
                """);
        return plugin;
    }

    private Result upgrade(Path plugin) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(java, "--class-path", System.getProperty("java.class.path"),
                "tools/UpgradeSettings.java", plugin.toString(), "src/main/resources/settings.yml", "src/main/resources/locale/en.yml")
                .redirectErrorStream(true).start();
        boolean ended = process.waitFor(30, TimeUnit.SECONDS);
        if (!ended) process.destroyForcibly();
        assertTrue(ended, "Upgrade process timed out");
        return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private record Result(int code, String output) {
    }
}
