package dev.lino.blocksregenerator.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerNoticesTest {
    @TempDir Path directory;

    @Test
    void rendersRoundedRemainingTimeAndNamedTokens() throws Exception {
        NoticeCatalog catalog = NoticeCatalog.parse(defaults());
        assertTrue(catalog.render(NoticeId.RECOVERING, 60_001).endsWith("1m 1s."));
        assertTrue(catalog.render(NoticeId.RECOVERING, -1).endsWith("0s."));
        assertTrue(catalog.render(NoticeId.RECOVERING, Long.MAX_VALUE).contains("d "));
    }

    @Test
    void cooldownsArePerPlayerAndPerNoticeAndCanBeCleared() {
        MutableClock clock = new MutableClock();
        PlayerNotices notices = new PlayerNotices(directory.resolve("en.yml"), () -> getClass().getResourceAsStream("/locale/en.yml"), clock);
        Player first = player();
        Player second = player();
        notices.send(first, NoticeId.MINING_DENIED);
        notices.send(first, NoticeId.MINING_DENIED);
        notices.send(second, NoticeId.MINING_DENIED);
        notices.send(first, NoticeId.BUILDING_DENIED);
        verify(first, times(2)).sendMessage(anyString());
        verify(second).sendMessage(anyString());
        clock.now = 3000;
        notices.send(first, NoticeId.MINING_DENIED);
        notices.forget(first.getUniqueId());
        notices.send(first, NoticeId.MINING_DENIED);
        verify(first, times(4)).sendMessage(anyString());
    }

    @Test
    void malformedReloadPreservesTheDocumentAndActiveCatalog() throws Exception {
        Path file = directory.resolve("en.yml");
        PlayerNotices notices = new PlayerNotices(file, () -> getClass().getResourceAsStream("/locale/en.yml"), Clock.systemUTC());
        String invalid = "format: 1\nnotices: [broken\n";
        Files.writeString(file, invalid);
        assertThrows(RuntimeException.class, notices::read);
        assertEquals(invalid, Files.readString(file));
        Player player = player();
        notices.send(player, NoticeId.RECOVERING, 5000);
        verify(player).sendMessage(contains("5s"));
    }

    @Test
    void rejectsUnknownEntriesDuplicateKeysAndFractionalCooldowns() throws Exception {
        String original = defaults();
        assertThrows(RuntimeException.class, () -> NoticeCatalog.parse(original + "format: 1\n"));
        assertThrows(RuntimeException.class, () -> NoticeCatalog.parse(original.replace("cooldown-ms: 3000", "cooldown-ms: 1.5")));
        assertThrows(RuntimeException.class, () -> NoticeCatalog.parse(original.replace("mining-denied:", "unknown-notice:")));
        assertThrows(RuntimeException.class, () -> NoticeCatalog.parse(original.replace("format: 1", "format: 2")));
    }

    private String defaults() throws Exception {
        try (var input = getClass().getResourceAsStream("/locale/en.yml")) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static final class MutableClock extends Clock {
        private long now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
        public long millis() { return now; }
    }
}
