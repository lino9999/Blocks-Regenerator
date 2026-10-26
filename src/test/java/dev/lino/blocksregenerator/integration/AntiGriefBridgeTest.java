package dev.lino.blocksregenerator.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Logger;
import net.momirealms.antigrieflib.AntiGriefCompatibility;
import net.momirealms.antigrieflib.AntiGriefLib;
import net.momirealms.antigrieflib.Flag;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class AntiGriefBridgeTest {
    private JavaPlugin plugin;
    private PluginManager manager;
    private Player player;
    private Location location;
    private Plugin extra;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        manager = mock(PluginManager.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        player = mock(Player.class);
        location = mock(Location.class);
        extra = mock(Plugin.class);
        when(extra.getName()).thenReturn("HuskClaims");
        when(extra.isEnabled()).thenReturn(true);
    }

    @Test
    void noAdditionalProviderDoesNotInitializeTheLibrary() {
        AtomicInteger calls = new AtomicInteger();
        AntiGriefBridge bridge = new AntiGriefBridge(plugin, ignored -> { calls.incrementAndGet(); return null; });
        assertTrue(bridge.allowed(player, location, ClaimsService.Action.BREAK));
        assertEquals(0, calls.get());
    }

    @Test
    void lazyBridgeLoadsAfterProviderEnableAndKeepsItsDenial() {
        AntiGriefLib library = supportedLibrary();
        AtomicInteger calls = new AtomicInteger();
        AntiGriefBridge bridge = new AntiGriefBridge(plugin, ignored -> { calls.incrementAndGet(); return library; });
        assertTrue(bridge.allowed(player, location, ClaimsService.Action.BREAK));
        when(manager.getPlugin("HuskClaims")).thenReturn(extra);
        when(library.test(player, Flag.BREAK, location)).thenReturn(false);
        assertFalse(bridge.allowed(player, location, ClaimsService.Action.BREAK));
        when(library.test(player, Flag.PLACE, location)).thenReturn(true);
        assertTrue(bridge.allowed(player, location, ClaimsService.Action.PLACE));
        assertEquals(1, calls.get());
    }

    @Test
    void missingAdapterFailsClosedInsteadOfAcceptingAnEmptyLibrary() {
        when(manager.getPlugin("HuskClaims")).thenReturn(extra);
        AntiGriefLib library = mock(AntiGriefLib.class);
        when(library.providers()).thenReturn(new AntiGriefCompatibility[0]);
        AtomicInteger calls = new AtomicInteger();
        AntiGriefBridge bridge = new AntiGriefBridge(plugin, ignored -> { calls.incrementAndGet(); return library; });
        assertFalse(bridge.allowed(player, location, ClaimsService.Action.BREAK));
        assertFalse(bridge.allowed(player, location, ClaimsService.Action.PLACE));
        assertEquals(1, calls.get());
        verify(library, never()).test(any(), any(), any());
    }

    @Test
    void queryErrorsAndTrampleMappingAreHandledWithoutAllowingTheAction() {
        when(manager.getPlugin("HuskClaims")).thenReturn(extra);
        AntiGriefLib library = supportedLibrary();
        when(library.test(player, Flag.INTERACT, location)).thenThrow(new IllegalStateException("Broken provider"));
        AntiGriefBridge bridge = new AntiGriefBridge(plugin, ignored -> library);
        assertFalse(bridge.allowed(player, location, ClaimsService.Action.TRAMPLE));
        verify(library).test(player, Flag.INTERACT, location);
    }

    @Test
    @SuppressWarnings("unchecked")
    void builderExcludesDirectAdaptersAndPreservesFailClosedConfiguration() {
        when(manager.getPlugin("HuskClaims")).thenReturn(extra);
        AntiGriefLib library = supportedLibrary();
        when(library.test(player, Flag.BREAK, location)).thenReturn(true);
        AntiGriefLib.Builder builder = mock(AntiGriefLib.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(library);
        try (MockedStatic<AntiGriefLib> type = mockStatic(AntiGriefLib.class)) {
            type.when(() -> AntiGriefLib.builder(plugin)).thenReturn(builder);
            assertTrue(new AntiGriefBridge(plugin).allowed(player, location, ClaimsService.Action.BREAK));
        }
        verify(builder).ignoreOP(false);
        verify(builder).bypassPermission(null);
        verify(builder).suppressErrors(false);
        ArgumentCaptor<Predicate<Plugin>> exclude = ArgumentCaptor.forClass(Predicate.class);
        verify(builder).exclude(exclude.capture());
        Plugin direct = mock(Plugin.class);
        when(direct.getName()).thenReturn("WorldGuard");
        when(direct.isEnabled()).thenReturn(true);
        assertTrue(exclude.getValue().test(direct));
        assertFalse(exclude.getValue().test(extra));
        when(extra.isEnabled()).thenReturn(false);
        assertTrue(exclude.getValue().test(extra));
    }

    private AntiGriefLib supportedLibrary() {
        AntiGriefLib library = mock(AntiGriefLib.class);
        AntiGriefCompatibility adapter = mock(AntiGriefCompatibility.class);
        when(adapter.plugin()).thenReturn(extra);
        when(library.providers()).thenReturn(new AntiGriefCompatibility[]{adapter});
        return library;
    }
}
