package dev.lino.blocksregenerator.admin;

import dev.lino.blocksregenerator.model.Bounds;
import dev.lino.blocksregenerator.model.DelayRange;
import dev.lino.blocksregenerator.model.Placeable;
import dev.lino.blocksregenerator.model.RegenChain;
import dev.lino.blocksregenerator.model.RegenRule;
import dev.lino.blocksregenerator.model.Region;
import dev.lino.blocksregenerator.model.RegionSetting;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

final class AdminMenus {
    private static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final AdminBackend backend;
    private final AdminSessions sessions;
    private final AdminCommands commands;

    AdminMenus(AdminBackend backend, AdminSessions sessions, AdminCommands commands) {
        this.backend = backend;
        this.sessions = sessions;
        this.commands = commands;
    }

    void regions(Player player, int page, String filter) {
        List<Region> regions = backend.regions().stream().filter(r -> contains(r.id(), filter)).sorted(Comparator.comparing(Region::id)).toList();
        AdminSessions.Menu menu = list(player, "BlocksRegenerator: regions", regions, page,
                r -> item(material(r.icon()), r.id(), r.global() ? "Global region in " + r.world() : "Cuboid in " + r.world(),
                        "Rules: " + r.rules().size() + " | Chains: " + r.chains().size(), overlaps(r) ? "Warning: overlapping regions" : "Click to edit"),
                r -> region(player, r.id()), next -> regions(player, next, filter), () -> player.closeInventory());
        button(menu, 46, Material.LIME_WOOL, "Create selected region", event -> prompt(player, "Enter the new region ID.", text -> {
            commands.create(player, text);
            region(player, text);
        }, () -> regions(player, page, filter)));
        button(menu, 47, Material.GRASS_BLOCK, "Create global for this world", event -> prompt(player, "Enter a region ID for world " + player.getWorld().getName() + ".", text -> {
            commands.global(player.getWorld().getName(), text);
            region(player, text);
        }, () -> regions(player, page, filter)));
        search(menu, player, filter, text -> regions(player, 0, text), () -> regions(player, page, filter));
    }

    void region(Player player, String regionId) {
        Region region = commands.region(regionId);
        AdminSessions.Menu menu = open(player, "Region: " + regionId);
        button(menu, 13, item(material(region.icon()), regionId,
                "World: " + region.world(), "Priority: " + region.priority(), "Active blocks: " + backend.activeCount(regionId),
                overlaps(region) ? "Warning: overlapping cuboids" : "Click to change icon"), event -> {
            menu.picker = null;
            prompt(player, "Enter an item material for the icon.", text -> {
                Material material = Material.matchMaterial(text);
                if (material == null || !material.isItem()) {
                    throw new IllegalArgumentException("Choose an item material.");
                }
                backend.save(commands.region(regionId).withIcon(material.name()));
                region(player, regionId);
            }, () -> region(player, regionId));
        });
        button(menu, 20, Material.STONE, "Regenerating blocks", event -> rules(player, regionId, 0, ""));
        button(menu, 22, Material.IRON_CHAIN, "Regen chains", event -> chains(player, regionId, 0, ""));
        button(menu, 24, Material.COMPARATOR, "Region settings", event -> settings(player, regionId));
        button(menu, 29, Material.CLOCK, "Restore active blocks", event -> confirm(player, "Restore active blocks?", () -> {
            backend.restore(regionId);
            region(player, regionId);
        }, () -> region(player, regionId)));
        button(menu, 31, Material.ENDER_PEARL, "Teleport to region", event -> {
            Region current = commands.region(regionId);
            World world = Bukkit.getWorld(current.world());
            if (world == null) {
                throw new IllegalArgumentException("The region world is not loaded.");
            }
            Bounds b = current.bounds();
            Location target = b == null ? world.getSpawnLocation() : new Location(world,
                    ((double) b.minX() + b.maxX() + 1) / 2, ((double) b.minY() + b.maxY()) / 2,
                    ((double) b.minZ() + b.maxZ() + 1) / 2);
            player.closeInventory();
            player.teleport(target);
        });
        button(menu, 33, Material.REPEATER, "Edit priority", event -> prompt(player, "Enter the region priority.", text -> {
            backend.save(commands.region(regionId).withPriority(AdminCommands.number(text)));
            region(player, regionId);
        }, () -> region(player, regionId)));
        button(menu, 45, Material.BARRIER, "Delete region", event -> confirm(player, "Delete region " + regionId + "?", () -> {
            commands.region(regionId);
            backend.delete(regionId);
            regions(player, 0, "");
        }, () -> region(player, regionId)));
        button(menu, 53, Material.ARROW, "Back", event -> regions(player, 0, ""));
    }

    private void settings(Player player, String regionId) {
        Region region = commands.region(regionId);
        AdminSessions.Menu menu = open(player, "Settings: " + regionId);
        int i = 0;
        for (RegionSetting setting : RegionSetting.values()) {
            button(menu, CONTENT[i++], region.setting(setting) ? Material.LIME_WOOL : Material.RED_WOOL,
                    setting.key() + ": " + region.setting(setting), event -> {
                        Region current = commands.region(regionId);
                        backend.save(current.withSetting(setting, !current.setting(setting)));
                        settings(player, regionId);
                    });
        }
        button(menu, 53, Material.ARROW, "Back", event -> region(player, regionId));
    }

    private void rules(Player player, String regionId, int page, String filter) {
        Region region = commands.region(regionId);
        List<RegenRule> rules = region.rules().stream().filter(r -> contains(r.original().display(), filter))
                .sorted(Comparator.comparing(r -> r.original().display())).toList();
        AdminSessions.Menu menu = list(player, "Blocks: " + regionId, rules, page,
                r -> display(r.original(), r.original().display(), "Replacement: " + r.replacement().display(),
                        "Delay: " + times(r.delay()), replacementConflict(region, r) ? "Warning: replacement is also regenerating" : "Click to edit"),
                r -> rule(player, regionId, r.id()), next -> rules(player, regionId, next, filter), () -> region(player, regionId));
        button(menu, 46, Material.LIME_WOOL, "Add block", event -> pickTypes(player, placeable -> {
            RegenRule rule = commands.defaultRule(placeable);
            commands.addRule(regionId, rule);
            rule(player, regionId, rule.id());
        }, () -> rules(player, regionId, page, filter), false));
        button(menu, 47, Material.CLOCK, "Set all timers", event -> delayPrompt(player, range -> {
            Region current = commands.region(regionId);
            backend.save(current.withRules(current.rules().stream().map(r -> r.withDelay(range)).toList()));
            rules(player, regionId, page, filter);
        }, () -> rules(player, regionId, page, filter)));
        button(menu, 48, item(Material.PAPER, "Copy to clipboard", "Left: blocks + chains", "Shift-left: blocks only", "Right: chains only"),
                event -> commands.copy(player, regionId, event.isRightClick() ? "chains" : event.isShiftClick() ? "blocks" : "all"));
        button(menu, 51, Material.WRITABLE_BOOK, "Paste clipboard", event -> confirm(player, "Replace copied configuration?", () -> {
            commands.paste(player, regionId);
            rules(player, regionId, page, filter);
        }, () -> rules(player, regionId, page, filter)));
        search(menu, player, filter, text -> rules(player, regionId, 0, text), () -> rules(player, regionId, page, filter));
    }

    private void rule(Player player, String regionId, UUID ruleId) {
        RegenRule rule = requireRule(regionId, ruleId);
        AdminSessions.Menu menu = open(player, "Block: " + rule.original().display());
        button(menu, 13, display(rule.original(), rule.original().display(), "Click to change original block"), event -> pickTypes(player, value -> {
            commands.updateRule(regionId, ruleId, r -> r.withOriginal(value));
            rule(player, regionId, ruleId);
        }, () -> rule(player, regionId, ruleId), false));
        button(menu, 21, display(rule.replacement(), "Replacement", rule.replacement().display()), event -> pickTypes(player, value -> {
            commands.updateRule(regionId, ruleId, r -> r.withReplacement(value));
            rule(player, regionId, ruleId);
        }, () -> rule(player, regionId, ruleId), false));
        button(menu, 23, Material.CLOCK, "Delay: " + times(rule.delay()), event -> timers(player, rule.delay(),
                range -> commands.updateRule(regionId, ruleId, r -> r.withDelay(range)), () -> rule(player, regionId, ruleId)));
        if (rule.original().type().equals("crop")) {
            button(menu, 31, Material.WHEAT, "Required age: " + rule.original().age(), event -> prompt(player, "Enter crop age; -1 allows any age.", text -> {
                commands.updateRule(regionId, ruleId, r -> r.withOriginal(new Placeable("crop", r.original().id(), AdminCommands.number(text))));
                rule(player, regionId, ruleId);
            }, () -> rule(player, regionId, ruleId)));
        }
        Region region = commands.region(regionId);
        if (replacementConflict(region, rule)) {
            button(menu, 40, Material.IRON_CHAIN, "Merge replacement conflict into a chain", event -> confirm(player, "Merge conflicting rules?", () -> merge(player, regionId, ruleId), () -> rule(player, regionId, ruleId)));
        }
        button(menu, 45, Material.BARRIER, "Delete rule", event -> confirm(player, "Delete this rule?", () -> {
            commands.removeRule(regionId, ruleId);
            rules(player, regionId, 0, "");
        }, () -> rule(player, regionId, ruleId)));
        button(menu, 53, Material.ARROW, "Back", event -> rules(player, regionId, 0, ""));
    }

    private void chains(Player player, String regionId, int page, String filter) {
        Region region = commands.region(regionId);
        List<RegenChain> chains = region.chains().stream().filter(c -> contains(c.name(), filter)).sorted(Comparator.comparing(RegenChain::name)).toList();
        AdminSessions.Menu menu = list(player, "Chains: " + regionId, chains, page,
                c -> item(Material.IRON_CHAIN, c.name(), "Stages: " + c.stages().size(), "Style: " + c.style(),
                        "Final stage: " + (c.lastStageBreakable() ? "breakable" : "unbreakable"), chainConflict(region, c) ? "Warning: entry block conflicts" : "Click to edit"),
                c -> chain(player, regionId, c.id(), 0), next -> chains(player, regionId, next, filter), () -> region(player, regionId));
        button(menu, 46, Material.LIME_WOOL, "Create chain", event -> prompt(player, "Enter the new chain name.", text -> {
            UUID id = commands.addChain(regionId, text);
            chain(player, regionId, id, 0);
        }, () -> chains(player, regionId, page, filter)));
        search(menu, player, filter, text -> chains(player, regionId, 0, text), () -> chains(player, regionId, page, filter));
    }

    private void chain(Player player, String regionId, UUID chainId, int page) {
        RegenChain chain = requireChain(regionId, chainId);
        AdminSessions.Menu menu = list(player, "Chain: " + chain.name(), chain.stages(), page,
                r -> display(r.original(), "Stage " + (chain.stages().indexOf(r) + 1) + ": " + r.original().display(), "Delay: " + times(r.delay()), "Click to edit, move or remove"),
                r -> stage(player, regionId, chainId, r.id()), next -> chain(player, regionId, chainId, next), () -> chains(player, regionId, 0, ""));
        button(menu, 46, Material.LIME_WOOL, "Add stage", event -> pickTypes(player, value -> {
            commands.addStage(regionId, chainId, commands.defaultRule(value));
            chain(player, regionId, chainId, page);
        }, () -> chain(player, regionId, chainId, page), true));
        button(menu, 47, Material.NAME_TAG, "Rename chain", event -> prompt(player, "Enter the new chain name.", text -> {
            commands.updateChain(regionId, chainId, c -> new RegenChain(c.id(), text, c.stages(), c.style(), c.lastStageBreakable()));
            chain(player, regionId, chainId, page);
        }, () -> chain(player, regionId, chainId, page)));
        button(menu, 48, Material.RAIL, "Style: " + chain.style(), event -> {
            commands.updateChain(regionId, chainId, c -> new RegenChain(c.id(), c.name(), c.stages(),
                    RegenChain.Style.values()[(c.style().ordinal() + 1) % RegenChain.Style.values().length], c.lastStageBreakable()));
            chain(player, regionId, chainId, page);
        });
        button(menu, 50, Material.IRON_PICKAXE, "Final stage breakable: " + chain.lastStageBreakable(), event -> {
            commands.updateChain(regionId, chainId, c -> new RegenChain(c.id(), c.name(), c.stages(), c.style(), !c.lastStageBreakable()));
            chain(player, regionId, chainId, page);
        });
        button(menu, 51, Material.CLOCK, "Set all stage timers", event -> delayPrompt(player, range -> {
            commands.updateChain(regionId, chainId, c -> new RegenChain(c.id(), c.name(), c.stages().stream().map(r -> r.withDelay(range)).toList(), c.style(), c.lastStageBreakable()));
            chain(player, regionId, chainId, page);
        }, () -> chain(player, regionId, chainId, page)));
        button(menu, 52, Material.BARRIER, "Delete chain", event -> confirm(player, "Delete this chain?", () -> {
            commands.removeChain(regionId, chainId);
            chains(player, regionId, 0, "");
        }, () -> chain(player, regionId, chainId, page)));
    }

    private void stage(Player player, String regionId, UUID chainId, UUID stageId) {
        RegenChain chain = requireChain(regionId, chainId);
        RegenRule stage = chain.stages().stream().filter(r -> r.id().equals(stageId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Stage no longer exists."));
        AdminSessions.Menu menu = open(player, "Chain stage: " + stage.original().display());
        button(menu, 13, display(stage.original(), stage.original().display(), "Click to replace the stage block"), event -> pickTypes(player, value -> {
            updateStage(regionId, chainId, stageId, r -> r.withOriginal(value));
            stage(player, regionId, chainId, stageId);
        }, () -> stage(player, regionId, chainId, stageId), true));
        button(menu, 22, Material.CLOCK, "Delay: " + times(stage.delay()), event -> timers(player, stage.delay(),
                range -> updateStage(regionId, chainId, stageId, r -> r.withDelay(range)), () -> stage(player, regionId, chainId, stageId)));
        button(menu, 30, Material.ARROW, "Move earlier", event -> moveStage(player, regionId, chainId, stageId, -1));
        button(menu, 32, Material.ARROW, "Move later", event -> moveStage(player, regionId, chainId, stageId, 1));
        button(menu, 40, Material.PAPER, "Move to position", event -> prompt(player, "Enter the target stage number, starting at 1.", text -> {
            int target = AdminCommands.number(text) - 1;
            commands.updateChain(regionId, chainId, c -> {
                List<RegenRule> stages = new ArrayList<>(c.stages());
                if (target < 0 || target >= stages.size()) {
                    throw new IllegalArgumentException("Choose an existing stage number.");
                }
                RegenRule current = stages.stream().filter(r -> r.id().equals(stageId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Stage no longer exists."));
                stages.remove(current);
                stages.add(target, current);
                return withStages(c, stages);
            });
            stage(player, regionId, chainId, stageId);
        }, () -> stage(player, regionId, chainId, stageId)));
        button(menu, 45, Material.BARRIER, "Remove stage", event -> confirm(player, "Remove this stage?", () -> {
            commands.updateChain(regionId, chainId, c -> withStages(c, c.stages().stream().filter(r -> !r.id().equals(stageId)).toList()));
            chain(player, regionId, chainId, 0);
        }, () -> stage(player, regionId, chainId, stageId)));
        button(menu, 53, Material.ARROW, "Back", event -> chain(player, regionId, chainId, 0));
    }

    private void moveStage(Player player, String regionId, UUID chainId, UUID stageId, int offset) {
        commands.updateChain(regionId, chainId, c -> {
            List<RegenRule> stages = new ArrayList<>(c.stages());
            int index = -1;
            for (int i = 0; i < stages.size(); i++) {
                if (stages.get(i).id().equals(stageId)) {
                    index = i;
                    break;
                }
            }
            if (index < 0 || index + offset < 0 || index + offset >= stages.size()) {
                throw new IllegalArgumentException("The stage cannot move further.");
            }
            stages.add(index + offset, stages.remove(index));
            return withStages(c, stages);
        });
        stage(player, regionId, chainId, stageId);
    }

    private void updateStage(String regionId, UUID chainId, UUID stageId, java.util.function.UnaryOperator<RegenRule> update) {
        commands.updateChain(regionId, chainId, c -> {
            if (c.stages().stream().noneMatch(r -> r.id().equals(stageId))) {
                throw new IllegalArgumentException("Stage no longer exists.");
            }
            return withStages(c, c.stages().stream().map(r -> r.id().equals(stageId) ? update.apply(r) : r).toList());
        });
    }

    private void timers(Player player, DelayRange range, Consumer<DelayRange> save, Runnable back) {
        AdminSessions.Menu menu = open(player, "Regeneration delay");
        button(menu, 13, Material.CLOCK, "Minimum: " + range.min() + "s | Maximum: " + range.max() + "s", event -> delayPrompt(player, value -> {
            save.accept(value);
            back.run();
        }, back));
        int[] increments = {-60, -15, -1, 1, 15, 60};
        for (int i = 0; i < increments.length; i++) {
            int delta = increments[i];
            button(menu, 19 + i, delta < 0 ? Material.RED_CONCRETE : Material.LIME_CONCRETE, "Minimum " + (delta > 0 ? "+" : "") + delta + "s", event ->
                    timers(player, new DelayRange(Math.clamp((long) range.min() + delta, 0, range.max()), range.max()), save, back));
            button(menu, 28 + i, delta < 0 ? Material.RED_CONCRETE : Material.LIME_CONCRETE, "Maximum " + (delta > 0 ? "+" : "") + delta + "s", event ->
                    timers(player, new DelayRange(range.min(), Math.clamp((long) range.max() + delta, range.min(), DelayRange.LIMIT)), save, back));
        }
        button(menu, 40, Material.PAPER, "Make maximum equal minimum", event -> timers(player, new DelayRange(range.min(), range.min()), save, back));
        button(menu, 49, Material.LIME_WOOL, "Save timers", event -> {
            save.accept(range);
            back.run();
        });
        button(menu, 53, Material.ARROW, "Cancel", event -> back.run());
    }

    private void delayPrompt(Player player, Consumer<DelayRange> save, Runnable back) {
        prompt(player, "Enter a delay in seconds, or minimum and maximum separated by a space.", text -> {
            String[] values = text.split("\\s+");
            if (values.length < 1 || values.length > 2) {
                throw new IllegalArgumentException("Enter one or two numbers.");
            }
            DelayRange range = AdminCommands.delay(values[0], values.length > 1 ? values[1] : values[0]);
            confirm(player, "Apply delay " + times(range) + "?", () -> save.accept(range), back);
        }, back);
    }

    private void pickTypes(Player player, Consumer<Placeable> selected, Runnable back, boolean stage) {
        AdminSessions.Menu menu = open(player, "Choose block type");
        int i = 0;
        for (String type : List.of("material", "crop", "itemsadder", "itemsadder-furniture", "oraxen", "oraxen-furniture", "nexo", "nexo-furniture")) {
            if (stage && (type.equals("crop") || type.endsWith("-furniture"))) {
                continue;
            }
            if (backend.integrations().available(type)) {
                button(menu, CONTENT[i++], Material.CHEST, type, event -> catalog(player, type, 0, "", selected, back, stage));
            }
        }
        button(menu, 49, Material.NAME_TAG, "Enter block ID", event -> prompt(player, "Enter a material or type:id, optionally with @cropAge.", text -> select(commands.parse(text), selected, stage), back));
        button(menu, 53, Material.ARROW, "Back", event -> back.run());
        menu.picker = stack -> select(commands.fromItem(stack), selected, stage);
    }

    private void catalog(Player player, String type, int page, String filter, Consumer<Placeable> selected, Runnable back, boolean stage) {
        List<Placeable> values = backend.integrations().catalog(type).stream().filter(p -> contains(p.display(), filter))
                .sorted(Comparator.comparing(Placeable::display)).toList();
        AdminSessions.Menu menu = list(player, "Choose " + type, values, page, p -> display(p, p.display()),
                p -> select(p, selected, stage), next -> catalog(player, type, next, filter, selected, back, stage), () -> pickTypes(player, selected, back, stage));
        search(menu, player, filter, text -> catalog(player, type, 0, text, selected, back, stage), () -> catalog(player, type, page, filter, selected, back, stage));
        menu.picker = stack -> select(commands.fromItem(stack), selected, stage);
    }

    private void select(Placeable placeable, Consumer<Placeable> selected, boolean stage) {
        backend.integrations().validate(placeable);
        if (stage && (placeable.furniture() || placeable.type().equals("crop"))) {
            throw new IllegalArgumentException("Crops and furniture cannot be chain stages.");
        }
        selected.accept(placeable);
    }

    private void merge(Player player, String regionId, UUID ruleId) {
        Region region = commands.region(regionId);
        RegenRule source = requireRule(regionId, ruleId);
        if (!stageable(source.original()) || !stageable(source.replacement()) || source.replacement().id().equals("AIR")) {
            throw new IllegalArgumentException("Crops, furniture, and AIR cannot be merged automatically.");
        }
        RegenRule conflict = region.rules().stream().filter(r -> !r.id().equals(ruleId) && same(r.original(), source.replacement())).findFirst().orElse(null);
        List<RegenChain> chains = new ArrayList<>(region.chains());
        UUID target;
        List<RegenRule> rules;
        if (conflict != null) {
            if (!stageable(conflict.replacement())) {
                throw new IllegalArgumentException("The replacement cannot be a chain stage.");
            }
            RegenChain chain = new RegenChain(UUID.randomUUID(), source.original().id() + " Chain", List.of(
                    source.withReplacement(Placeable.material("AIR")),
                    new RegenRule(UUID.randomUUID(), source.replacement(), Placeable.material("AIR"), source.delay()),
                    new RegenRule(UUID.randomUUID(), conflict.replacement(), Placeable.material("AIR"), conflict.delay())), RegenChain.Style.STAGED_PER_STAGE, false);
            chains.add(chain);
            target = chain.id();
            rules = region.rules().stream().filter(r -> !r.id().equals(ruleId) && !r.id().equals(conflict.id())).toList();
        } else {
            RegenChain chain = region.chains().stream().filter(c -> !c.stages().isEmpty() && same(c.stages().getFirst().original(), source.replacement())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The conflict no longer exists."));
            List<RegenRule> stages = new ArrayList<>(chain.stages());
            stages.addFirst(source.withReplacement(Placeable.material("AIR")));
            chains.set(chains.indexOf(chain), withStages(chain, stages));
            target = chain.id();
            rules = region.rules().stream().filter(r -> !r.id().equals(ruleId)).toList();
        }
        backend.save(region.withRules(rules).withChains(chains));
        chain(player, regionId, target, 0);
    }

    private boolean replacementConflict(Region region, RegenRule rule) {
        return !rule.replacement().id().equals("AIR") && (region.rules().stream().anyMatch(r -> !r.id().equals(rule.id()) && same(r.original(), rule.replacement()))
                || region.chains().stream().anyMatch(c -> !c.stages().isEmpty() && same(c.stages().getFirst().original(), rule.replacement())));
    }

    private boolean chainConflict(Region region, RegenChain chain) {
        if (chain.stages().isEmpty()) {
            return false;
        }
        Placeable entry = chain.stages().getFirst().original();
        return region.rules().stream().anyMatch(r -> same(r.original(), entry)) || region.chains().stream()
                .anyMatch(c -> !c.id().equals(chain.id()) && !c.stages().isEmpty() && same(c.stages().getFirst().original(), entry));
    }

    private boolean overlaps(Region region) {
        return !region.global() && backend.regions().stream().anyMatch(r -> !r.id().equals(region.id()) && !r.global()
                && r.world().equals(region.world()) && r.bounds().overlaps(region.bounds()));
    }

    private RegenRule requireRule(String regionId, UUID ruleId) {
        RegenRule rule = commands.region(regionId).rule(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("This rule no longer exists.");
        }
        return rule;
    }

    private RegenChain requireChain(String regionId, UUID chainId) {
        RegenChain chain = commands.region(regionId).chain(chainId);
        if (chain == null) {
            throw new IllegalArgumentException("This chain no longer exists.");
        }
        return chain;
    }

    private static RegenChain withStages(RegenChain chain, List<RegenRule> stages) {
        return new RegenChain(chain.id(), chain.name(), stages, chain.style(), chain.lastStageBreakable());
    }

    private static boolean same(Placeable a, Placeable b) {
        return a.type().equals(b.type()) && a.id().equals(b.id());
    }

    private static boolean stageable(Placeable value) {
        return !value.furniture() && !value.type().equals("crop");
    }

    private void search(AdminSessions.Menu menu, Player player, String current, Consumer<String> apply, Runnable back) {
        button(menu, 8, item(Material.SPYGLASS, "Search", "Current: " + (current.isEmpty() ? "all" : current), "Left-click to search; right-click to clear"), event -> {
            if (event.isRightClick()) {
                apply.accept("");
            } else {
                prompt(player, "Enter search text (maximum 128 characters).", text -> {
                    if (text.length() > 128) {
                        throw new IllegalArgumentException("Search text is too long.");
                    }
                    apply.accept(text);
                }, back);
            }
        });
    }

    private <T> AdminSessions.Menu list(Player player, String title, List<T> values, int requestedPage, Function<T, ItemStack> render,
                                         Consumer<T> select, Consumer<Integer> navigate, Runnable back) {
        int pages = Math.max(1, (values.size() + CONTENT.length - 1) / CONTENT.length);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        AdminSessions.Menu menu = open(player, title);
        int from = page * CONTENT.length;
        for (int i = 0; i < CONTENT.length && from + i < values.size(); i++) {
            T value = values.get(from + i);
            button(menu, CONTENT[i], render.apply(value), event -> select.accept(value));
        }
        if (values.isEmpty()) {
            menu.getInventory().setItem(22, item(Material.BARRIER, "No entries"));
        }
        button(menu, 45, Material.ARROW, "Previous page", event -> navigate.accept(Math.max(0, page - 1)));
        menu.getInventory().setItem(49, item(Material.PAPER, "Page " + (page + 1) + " / " + pages));
        button(menu, 53, Material.ARROW, "Next page", event -> navigate.accept(Math.min(pages - 1, page + 1)));
        button(menu, 0, Material.ARROW, "Back", event -> back.run());
        return menu;
    }

    private AdminSessions.Menu open(Player player, String title) {
        AdminSessions.Menu menu = sessions.open(player, title);
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            menu.getInventory().setItem(i, filler);
        }
        return menu;
    }

    private void confirm(Player player, String title, Runnable accept, Runnable cancel) {
        AdminSessions.Menu menu = open(player, title);
        button(menu, 20, Material.LIME_WOOL, "Confirm", event -> accept.run());
        button(menu, 24, Material.RED_WOOL, "Cancel", event -> cancel.run());
    }

    private void prompt(Player player, String message, Consumer<String> accept, Runnable back) {
        sessions.prompt(player, message, accept, back);
    }

    private static boolean contains(String value, String filter) {
        return value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    private static String times(DelayRange range) {
        return range.min() == range.max() ? range.min() + "s" : range.min() + "-" + range.max() + "s";
    }

    private ItemStack display(Placeable placeable, String name, String... lore) {
        ItemStack result;
        try {
            result = backend.integrations().displayItem(placeable).clone();
        } catch (RuntimeException exception) {
            result = new ItemStack(Material.BARRIER);
        }
        if (result.getType().isAir()) {
            result = new ItemStack(Material.BARRIER);
        }
        return named(result, name, lore);
    }

    private static Material material(String id) {
        Material material = Material.matchMaterial(id);
        return material == null || !material.isItem() || material.isAir() ? Material.STONE : material;
    }

    private static ItemStack item(Material material, String name, String... lore) {
        return named(new ItemStack(material), name, lore);
    }

    private static ItemStack named(ItemStack item, String name, String... lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void button(AdminSessions.Menu menu, int slot, Material material, String name, Consumer<InventoryClickEvent> action) {
        button(menu, slot, item(material, name), action);
    }

    private static void button(AdminSessions.Menu menu, int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        menu.getInventory().setItem(slot, item);
        menu.actions.put(slot, action);
    }
}
