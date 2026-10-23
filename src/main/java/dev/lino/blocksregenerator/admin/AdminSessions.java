package dev.lino.blocksregenerator.admin;

import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.RegenChain;
import dev.lino.blocksregenerator.model.RegenRule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class AdminSessions {
    static final String PERMISSION = "blocksregenerator.admin";
    private final AdminBackend backend;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Menu> menus = new HashMap<>();
    private final Map<UUID, Clipboard> clipboards = new HashMap<>();
    private final Set<UUID> manual = new HashSet<>();
    private final Map<UUID, Pending> prompts = new ConcurrentHashMap<>();

    AdminSessions(AdminBackend backend) {
        this.backend = backend;
    }

    static boolean allowed(Player player) {
        return player.hasPermission(PERMISSION) || player.hasPermission("oreregenerator.admin");
    }

    Selection selection(Player player) {
        return selections.getOrDefault(player.getUniqueId(), new Selection(null, null));
    }

    void select(Player player, BlockKey key, boolean first) {
        Selection old = selection(player);
        selections.put(player.getUniqueId(), first ? new Selection(key, old.second()) : new Selection(old.first(), key));
        player.sendMessage("Position " + (first ? "one" : "two") + " set to " + key.x() + ", " + key.y() + ", " + key.z() + " in " + key.world() + ".");
    }

    Menu open(Player player, String title) {
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 54, title.length() > 64 ? title.substring(0, 64) : title);
        menus.put(player.getUniqueId(), menu);
        player.openInventory(menu.inventory);
        return menu;
    }

    boolean owns(Player player, Inventory inventory) {
        return inventory.getHolder() instanceof Menu menu && menus.get(player.getUniqueId()) == menu;
    }

    void clicked(Player player, InventoryClickEvent event) {
        Menu menu = menus.get(player.getUniqueId());
        if (menu != null && event.getRawSlot() >= 0 && event.getRawSlot() < 54) {
            Consumer<InventoryClickEvent> action = menu.actions.get(event.getRawSlot());
            if (action != null) {
                action.accept(event);
            }
        } else if (menu != null && menu.picker != null && event.getClickedInventory() == player.getInventory()
                && event.isLeftClick() && !event.isShiftClick() && event.getCurrentItem() != null) {
            menu.picker.accept(event.getCurrentItem().clone());
        }
    }

    void closed(Player player, Inventory inventory) {
        Menu menu = menus.get(player.getUniqueId());
        if (menu != null && menu.inventory == inventory) {
            menus.remove(player.getUniqueId());
        }
    }

    boolean manual(Player player) {
        return manual.contains(player.getUniqueId());
    }

    void manual(Player player, boolean value) {
        if (value) {
            manual.add(player.getUniqueId());
        } else {
            manual.remove(player.getUniqueId());
        }
    }

    Clipboard clipboard(Player player) {
        return clipboards.get(player.getUniqueId());
    }

    void clipboard(Player player, Clipboard value) {
        clipboards.put(player.getUniqueId(), value);
    }

    void prompt(Player player, String message, Consumer<String> accept, Runnable cancel) {
        Pending pending = new Pending(accept, cancel);
        prompts.put(player.getUniqueId(), pending);
        player.closeInventory();
        player.sendMessage(message + " Type cancel to return.");
        Bukkit.getScheduler().runTaskLater(backend.plugin(), () -> {
            if (prompts.remove(player.getUniqueId(), pending) && player.isOnline() && allowed(player)) {
                player.sendMessage("Input expired.");
                cancel.run();
            }
        }, 2400L);
    }

    Object promptToken(UUID player) {
        return prompts.get(player);
    }

    void input(Player player, String input, Object expected) {
        if (!(expected instanceof Pending pending) || !prompts.remove(player.getUniqueId(), pending)
                || !player.isOnline() || !allowed(player)) {
            return;
        }
        if (input.equalsIgnoreCase("cancel")) {
            pending.cancel().run();
            return;
        }
        try {
            pending.accept().accept(input.trim());
        } catch (IllegalArgumentException exception) {
            player.sendMessage(exception.getMessage());
            prompt(player, "Try again.", pending.accept(), pending.cancel());
        }
    }

    void clear(Player player) {
        UUID id = player.getUniqueId();
        selections.remove(id);
        prompts.remove(id);
        menus.remove(id);
        clipboards.remove(id);
        manual.remove(id);
    }

    void close() {
        for (UUID id : List.copyOf(menus.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.closeInventory();
            }
        }
        menus.clear();
        selections.clear();
        prompts.clear();
        clipboards.clear();
        manual.clear();
    }

    record Selection(BlockKey first, BlockKey second) { }
    record Clipboard(List<RegenRule> rules, List<RegenChain> chains) { }
    private record Pending(Consumer<String> accept, Runnable cancel) { }

    static final class Menu implements InventoryHolder {
        private Inventory inventory;
        final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
        Consumer<ItemStack> picker;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
