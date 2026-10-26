package dev.lino.blocksregenerator.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.logging.Logger;
import dev.lino.blocksregenerator.configuration.SettingsRepository;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClaimsServiceTest {
    private PluginManager manager;
    private YamlConfiguration config;
    private ClaimsService service;
    private Player player;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        manager = mock(PluginManager.class);
        config = new YamlConfiguration();
        player = mock(Player.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        config.set("format", 1);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        service = new ClaimsService(plugin, () -> {
            try {
                return SettingsRepository.parse(config.saveToString());
            } catch (java.io.IOException error) {
                throw new IllegalArgumentException(error);
            }
        });
    }

    @Test
    void noProtectionPluginAllowsActionsButDoesNotGrantGlobalOverride() {
        assertTrue(service.allowed(player, mock(Location.class), ClaimsService.Action.BREAK));
        assertFalse(service.grants(player, mock(Location.class)));
    }

    @Test
    void enabledIncompatibleProtectionFailsClosed() {
        Plugin dependency = mock(Plugin.class);
        when(dependency.isEnabled()).thenReturn(true);
        when(manager.getPlugin("WorldGuard")).thenReturn(dependency);
        assertFalse(service.allowed(player, mock(Location.class), ClaimsService.Action.BREAK));
        assertFalse(service.grants(player, mock(Location.class)));
    }

    @Test
    void configuredOperatorBypassAndPermissionBypassApply() {
        when(player.isOp()).thenReturn(true);
        assertTrue(service.allowed(player, mock(Location.class), ClaimsService.Action.PLACE));
        config.set("protection.operators-exempt", false);
        config.set("protection.exemption-node", "blocksregenerator.bypass.claims");
        when(player.hasPermission("blocksregenerator.bypass.claims")).thenReturn(true);
        assertTrue(service.allowed(player, mock(Location.class), ClaimsService.Action.PLACE));
    }

    @Test
    void defaultBypassPermissionUsesThePluginNamespace() {
        Plugin dependency = mock(Plugin.class);
        when(dependency.isEnabled()).thenReturn(true);
        when(manager.getPlugin("WorldGuard")).thenReturn(dependency);
        when(player.hasPermission("blocksregenerator.bypass.claims")).thenReturn(true);

        assertTrue(service.allowed(player, mock(Location.class), ClaimsService.Action.BREAK));
        verify(player).hasPermission("blocksregenerator.bypass.claims");
    }
}
