package dev.lino.blocksregenerator.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void bundledSettingsCreateAValidatedIndependentDocument() throws Exception {
        Path file = directory.resolve("settings.yml");
        SettingsRepository repository = new SettingsRepository(file);
        try (InputStream resource = getClass().getResourceAsStream("/settings.yml")) {
            RuntimeSettings settings = repository.load(resource);
            assertEquals(RuntimeSettings.defaults(), settings);
        }
        assertTrue(Files.isRegularFile(file));
        assertEquals(RuntimeSettings.defaults(), repository.read());
    }

    @Test
    void typedOverridesAndStartupImportsAreImmutable() throws Exception {
        RuntimeSettings settings = SettingsRepository.parse("""
                format: 1
                recovery:
                  remember-on-restart: false
                  checkpoint-interval-seconds: 120
                scheduler:
                  work-budget: 75
                protection:
                  operators-exempt: false
                  exemption-node: server.regeneration.exempt
                imports:
                  on-start: [legacy, minexfarm-regen]
                """);

        assertFalse(settings.recovery().rememberOnRestart());
        assertEquals(120, settings.recovery().checkpointIntervalSeconds());
        assertEquals(75, settings.scheduler().workBudget());
        assertFalse(settings.protection().operatorsExempt());
        assertEquals("server.regeneration.exempt", settings.protection().exemptionNode());
        assertEquals(List.of("legacy", "minexfarm-regen"), settings.imports());
        assertThrows(UnsupportedOperationException.class, () -> settings.imports().add("ultimate-regen"));

        List<String> sources = new ArrayList<>(List.of("legacy"));
        RuntimeSettings copied = new RuntimeSettings(settings.format(), settings.worlds(), settings.recovery(),
                settings.scheduler(), settings.protection(), settings.timing(), settings.visuals(), settings.harvesting(), sources);
        sources.clear();
        assertEquals(List.of("legacy"), copied.imports());
    }

    @Test
    void malformedExistingSettingsArePreservedInsteadOfReset() throws Exception {
        Path file = directory.resolve("settings.yml");
        String malformed = "format: 1\nscheduler: [broken\n";
        Files.writeString(file, malformed);
        SettingsRepository repository = new SettingsRepository(file);

        assertThrows(IOException.class, () -> repository.load(stream("format: 1\n")));
        assertEquals(malformed, Files.readString(file));
    }

    @Test
    void unsupportedVersionsAndUnknownKeysAreRejected() {
        for (String document : List.of("", "format: 2\n", "format: '1'\n",
                "format: 1\nschedulre:\n  work-budget: 3\n",
                "format: 1\nscheduler:\n  work-budegt: 3\n",
                "format: 1\nprotection: true\n")) {
            assertThrows(IOException.class, () -> SettingsRepository.parse(document), document);
        }
    }

    @Test
    void duplicateKeysNullsAndUnsupportedYamlObjectsAreRejected() {
        for (String document : List.of(
                "format: 1\nformat: 1\n",
                "format: 1\nscheduler:\n  work-budget: 20\n  work-budget: 30\n",
                "format: 1\nworlds: null\n",
                "format: 1\nworlds:\n  automatic-coverage: null\n",
                "format: 1\nscheduler.work-budget: 20\n",
                "!!java.util.Date {}\n")) {
            assertThrows(IOException.class, () -> SettingsRepository.parse(document), document);
        }
    }

    @Test
    void wrongScalarTypesAndOutOfRangeNumbersDoNotSilentlyFallBack() {
        for (String suffix : List.of(
                "worlds:\n  automatic-coverage: 'true'\n",
                "scheduler:\n  work-budget: 1.5\n",
                "scheduler:\n  work-budget: 10.0\n",
                "scheduler:\n  work-budget: 0\n",
                "scheduler:\n  work-budget: 10001\n",
                "scheduler:\n  work-budget: 999999999999\n",
                "recovery:\n  checkpoint-interval-seconds: 4\n",
                "recovery:\n  checkpoint-interval-seconds: 86401\n",
                "visuals:\n  outline-distance-blocks: 257\n",
                "visuals:\n  outline-refresh-ticks: 0\n",
                "protection:\n  exemption-node: ' '\n",
                "timing:\n  formula: 3\n",
                "timing:\n  formula: ''\n")) {
            assertThrows(IOException.class, () -> SettingsRepository.parse("format: 1\n" + suffix), suffix);
        }
    }

    @Test
    void invalidStartupImportListsAreRejected() {
        for (String value : List.of("legacy", "[unknown]", "[legacy, legacy]", "[1]", "[false]")) {
            assertThrows(IOException.class, () -> SettingsRepository.parse("format: 1\nimports:\n  on-start: " + value + "\n"), value);
        }
    }

    @Test
    void invalidBundledDefaultsDoNotCreateAFile() {
        Path file = directory.resolve("settings.yml");
        SettingsRepository repository = new SettingsRepository(file);
        assertThrows(IOException.class, () -> repository.load(stream("format: 12\n")));
        assertFalse(Files.exists(file));
    }

    @Test
    void formulaAndDocumentSizeLimitsAreEnforced() {
        assertThrows(IOException.class, () -> SettingsRepository.parse("format: 1\ntiming:\n  formula: " + "x".repeat(1025)));
        assertThrows(IOException.class, () -> SettingsRepository.parse(" ".repeat(262_145)));
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
