package dev.lino.blocksregenerator.admin;

import dev.lino.blocksregenerator.model.Region;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {
    private MockedStatic<Bukkit> bukkit;
    private AdminBackend backend;
    private JavaPlugin plugin;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        backend = mock(AdminBackend.class);
        plugin = mock(JavaPlugin.class);
        scheduler = mock(BukkitScheduler.class);
        when(backend.plugin()).thenReturn(plugin);
        when(plugin.getName()).thenReturn("BlocksRegenerator");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AdminControllerTest"));
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void deniedCommandCannotDeleteOrExposeCompletions() {
        AdminController controller = new AdminController(backend);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);

        controller.onCommand(sender, command, "br", new String[]{"delete", "mine"});

        verify(backend, never()).region(anyString());
        verify(backend, never()).delete(anyString());
        assertEquals(0, controller.onTabComplete(sender, command, "br", new String[]{""}).size());
    }

    @Test
    void adminPermissionAllowsValidatedDeletion() {
        AdminController controller = new AdminController(backend);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("blocksregenerator.admin")).thenReturn(true);
        when(backend.region("mine")).thenReturn(Region.create("mine", "world", null));

        controller.onCommand(sender, mock(Command.class), "blocksregenerator", new String[]{"region", "delete", "mine"});

        verify(backend).region("mine");
        verify(backend).delete("mine");
    }

    @Test
    void unrelatedPermissionsDoNotGrantAdministration() {
        AdminController controller = new AdminController(backend);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenAnswer(invocation ->
                !AdminSessions.PERMISSION.equals(invocation.getArgument(0)));

        controller.onCommand(sender, mock(Command.class), "blocksregenerator", new String[]{"delete", "mine"});

        verify(backend, never()).region(anyString());
        verify(backend, never()).delete(anyString());
        assertEquals(0, controller.onTabComplete(sender, mock(Command.class), "blocksregenerator", new String[]{""}).size());
    }

    @Test
    void everyInventoryTransferIsCancelledEvenAfterPermissionRevocation() {
        AdminController controller = new AdminController(backend);
        Player player = mock(Player.class);
        Inventory top = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(top.getHolder()).thenReturn(new AdminSessions.Menu());
        when(view.getTopInventory()).thenReturn(top);
        for (ClickType click : new ClickType[]{ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND,
                ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.MIDDLE}) {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getWhoClicked()).thenReturn(player);
            when(event.getView()).thenReturn(view);
            when(event.getClick()).thenReturn(click);

            controller.onClick(event);

            verify(event).setCancelled(true);
        }
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        controller.onDrag(drag);
        verify(drag).setCancelled(true);
        verify(backend, never()).save(any());
        verify(backend, never()).delete(anyString());
    }

    @Test
    void queuedInputCannotConsumeANewerPrompt() {
        AdminSessions sessions = new AdminSessions(backend);
        Player player = permittedPlayer();
        AtomicInteger oldCalls = new AtomicInteger();
        AtomicInteger newCalls = new AtomicInteger();
        sessions.prompt(player, "Old input", text -> oldCalls.incrementAndGet(), () -> { });
        Object oldToken = sessions.promptToken(player.getUniqueId());
        sessions.prompt(player, "New input", text -> newCalls.incrementAndGet(), () -> { });
        Object newToken = sessions.promptToken(player.getUniqueId());

        sessions.input(player, "stale answer", oldToken);
        sessions.input(player, "current answer", newToken);

        assertEquals(0, oldCalls.get());
        assertEquals(1, newCalls.get());
        assertNull(sessions.promptToken(player.getUniqueId()));
    }

    @Test
    void revokedPermissionAndCancellationCannotApplyPromptValues() {
        AdminSessions sessions = new AdminSessions(backend);
        Player player = permittedPlayer();
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        sessions.prompt(player, "Input", text -> accepted.incrementAndGet(), cancelled::incrementAndGet);
        Object firstToken = sessions.promptToken(player.getUniqueId());
        sessions.input(player, "cancel", firstToken);
        sessions.prompt(player, "Input", text -> accepted.incrementAndGet(), cancelled::incrementAndGet);
        Object secondToken = sessions.promptToken(player.getUniqueId());
        when(player.hasPermission(AdminSessions.PERMISSION)).thenReturn(false);

        sessions.input(player, "123", secondToken);

        assertEquals(0, accepted.get());
        assertEquals(1, cancelled.get());
        assertNull(sessions.promptToken(player.getUniqueId()));
    }

    private Player permittedPlayer() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(AdminSessions.PERMISSION)).thenReturn(true);
        return player;
    }
}
