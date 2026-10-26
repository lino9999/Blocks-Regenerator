package dev.lino.blocksregenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class ArtifactPackagingIT {
    @Test
    void shippedPluginContainsIndependentResourcesAndOnlyItsOwnNamespace() throws Exception {
        try (JarFile jar = new JarFile(Path.of(System.getProperty("plugin.artifact")).toFile())) {
            for (String expected : List.of("plugin.yml", "settings.yml", "locale/en.yml", "META-INF/LICENSE-antigrieflib.txt")) {
                assertNotNull(jar.getEntry(expected), expected);
            }
            assertNull(jar.getEntry("config.yml"));
            assertNull(jar.getEntry("messages.yml"));
            for (var entry : jar.stream().filter(item -> item.getName().endsWith(".class")).toList()) {
                assertTrue(entry.getName().startsWith("dev/lino/blocksregenerator/"), entry.getName());
                String bytes = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.ISO_8859_1);
                for (String retired : List.of("OreRegenerator", "oreregenerator.admin", "dev/mrshawn/",
                        "generate-global-regions", "hooks.anti-grief", "advanced.regen-delay", "cant-break-block",
                        "regen-time-remaining", "<delay:", "%min_delay%", "%player_count%")) {
                    assertFalse(bytes.contains(retired), entry.getName() + " contains " + retired);
                }
            }
        }
    }
}
