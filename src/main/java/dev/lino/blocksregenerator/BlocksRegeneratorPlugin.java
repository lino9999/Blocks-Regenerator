package dev.lino.blocksregenerator;

import dev.lino.blocksregenerator.admin.AdminBackend;
import dev.lino.blocksregenerator.admin.AdminController;
import dev.lino.blocksregenerator.bukkit.BukkitWorldAccess;
import dev.lino.blocksregenerator.bukkit.Messages;
import dev.lino.blocksregenerator.bukkit.RegenerationListener;
import dev.lino.blocksregenerator.bukkit.RegionPermissions;
import dev.lino.blocksregenerator.engine.DelayExpression;
import dev.lino.blocksregenerator.engine.RegenerationEngine;
import dev.lino.blocksregenerator.integration.ClaimsService;
import dev.lino.blocksregenerator.integration.IntegrationRegistry;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.DelayRange;
import dev.lino.blocksregenerator.model.RegenRule;
import dev.lino.blocksregenerator.model.Region;
import dev.lino.blocksregenerator.model.RegionSetting;
import dev.lino.blocksregenerator.service.RegionIndex;
import dev.lino.blocksregenerator.storage.LegacyImporter;
import dev.lino.blocksregenerator.storage.SavedState;
import dev.lino.blocksregenerator.storage.StateStore;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class BlocksRegeneratorPlugin extends JavaPlugin implements AdminBackend, Listener {
    private final RegionIndex regions = new RegionIndex();
    private final Clock clock = Clock.systemUTC();
    private final RandomGenerator random = RandomGenerator.getDefault();
    private IntegrationRegistry integrations;
    private RegenerationEngine engine;
    private RegenerationListener listener;
    private Messages messages;
    private BukkitWorldAccess world;
    private StateStore storage;
    private AdminController admin;
    private BukkitTask ticker;
    private boolean initialized;
    private boolean warnedExpression;
    private long ticks;
    private volatile long savedRevision = -1;
    private java.util.concurrent.CompletableFuture<Void> autosave = java.util.concurrent.CompletableFuture.completedFuture(null);

    @Override
    public void onEnable() {
        try {
            if (getServer().getPluginManager().isPluginEnabled("OreRegenerator")) {
                throw new IllegalStateException("Remove the original OreRegenerator JAR before enabling BlocksRegenerator");
            }
            saveDefaultConfig();
            validateConfiguration();
            integrations = new IntegrationRegistry(this);
            messages = new Messages(this);
            world = new BukkitWorldAccess(this, integrations);
            engine = new RegenerationEngine(world, clock, regions::get, this::delay,
                    getConfig().getInt("engine.max-operations-per-tick", 500));
            storage = new StateStore(getDataFolder().toPath().resolve("data.json"), clock, getLogger()::severe);
            SavedState state = storage.load();
            for (Region region : state.regions()) {
                for (RegenRule rule : region.rules()) {
                    validateVanillaRule(rule);
                }
                region.chains().forEach(chain -> chain.stages().forEach(this::validateVanillaRule));
            }
            regions.replaceAll(state.regions());
            engine.resume(state.pending().stream().map(world::normalize).toList());
            listener = new RegenerationListener(this, regions, engine, integrations, world,
                    new RegionPermissions(new ClaimsService(this)), messages);
            getServer().getPluginManager().registerEvents(listener, this);
            getServer().getPluginManager().registerEvents(this, this);
            integrations.registerCustomBreaks(listener::onCustomBreak);
            admin = new AdminController(this);
            var command = Objects.requireNonNull(getCommand("blocksregenerator"));
            command.setExecutor(admin);
            command.setTabCompleter(admin);
            initialized = true;
            generateGlobals();
            runConfiguredImports();
            ticker = getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
            getLogger().info("BlocksRegenerator by Lino enabled with " + regions.all().size()
                    + " regions and " + engine.snapshot().size() + " pending regenerations.");
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Cannot enable BlocksRegenerator. Existing data was preserved.", error);
            initialized = false;
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.cancel();
        }
        if (admin != null) {
            admin.close();
        }
        try {
            if (initialized) {
                listener.flush();
                if (!getConfig().getBoolean("persistent-timers", true)) {
                    engine.restoreAllLoaded();
                }
                storage.saveBlocking(state());
            }
        } catch (RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Cannot save shutdown state", error);
        } finally {
            if (listener != null) {
                listener.clear();
            }
            if (storage != null) {
                storage.close();
            }
            initialized = false;
        }
    }

    private void tick() {
        listener.tick();
        engine.tick();
        long interval = Math.max(5, getConfig().getLong("storage.autosave-seconds", 30)) * 20;
        if (++ticks % interval == 0 && engine.revision() != savedRevision && autosave.isDone()) {
            long revision = engine.revision();
            autosave = storage.save(state()).thenRun(() -> savedRevision = revision);
        }
    }

    private SavedState state() {
        return new SavedState(regions.all(), List.copyOf(engine.snapshot()));
    }

    private int delay(Region region, RegenRule rule) {
        if (region.setting(RegionSetting.USE_ADVANCED_REGEN_DELAY)) {
            try {
                double value = DelayExpression.evaluate(getConfig().getString("advanced.regen-delay.expression",
                                "rUni(%min_delay%, %max_delay%)"),
                        Map.of("min_delay", (double) rule.delay().min(), "max_delay", (double) rule.delay().max(),
                                "player_count", (double) getServer().getOnlinePlayers().size()), random);
                if (value < 0 || value > DelayRange.LIMIT) {
                    throw new IllegalArgumentException("Calculated delay is outside the supported range");
                }
                return (int) Math.round(value);
            } catch (IllegalArgumentException error) {
                if (!warnedExpression) {
                    getLogger().warning("Invalid regeneration expression; using each rule's delay range: " + error.getMessage());
                    warnedExpression = true;
                }
            }
        }
        return rule.delay().sample(random);
    }

    @Override
    public Collection<Region> regions() {
        return regions.all();
    }

    @Override
    public Region region(String id) {
        return regions.get(id);
    }

    @Override
    public Region regionAt(BlockKey key) {
        return regions.at(key);
    }

    @Override
    public void save(Region region) {
        validateRegion(region);
        listener.flush();
        Region previous = regions.get(region.id());
        if (previous != null && !previous.equals(region)) {
            engine.restoreRegion(previous.id());
        }
        regions.put(region);
        try {
            storage.saveBlocking(state());
        } catch (RuntimeException error) {
            if (previous == null) {
                regions.remove(region.id());
            } else {
                regions.put(previous);
            }
            throw error;
        }
    }

    @Override
    public void delete(String id) {
        Region region = regions.get(id);
        if (region == null) {
            throw new IllegalArgumentException("Unknown region: " + id);
        }
        listener.flush();
        engine.restoreRegion(id);
        regions.remove(id);
        try {
            storage.saveBlocking(state());
        } catch (RuntimeException error) {
            regions.put(region);
            throw error;
        }
    }

    @Override
    public void restore(String regionId) {
        listener.flush();
        engine.restoreRegion(regionId);
        storage.saveBlocking(state());
    }

    @Override
    public void reload() {
        validateConfiguration();
        reloadConfig();
        messages.reload();
        warnedExpression = false;
        engine.budget(getConfig().getInt("engine.max-operations-per-tick", 500));
        generateGlobals();
    }

    @Override
    public IntegrationRegistry integrations() {
        return integrations;
    }

    @Override
    public JavaPlugin plugin() {
        return this;
    }

    @Override
    public void importData(String source) {
        String normalized = source.toLowerCase(java.util.Locale.ROOT);
        Path plugins = getDataFolder().toPath().toAbsolutePath().getParent();
        LegacyImporter importer = new LegacyImporter();
        SavedState imported;
        try {
            imported = switch (normalized) {
                case "oreregenerator", "ore-regenerator" -> importer.oreRegenerator(plugins.resolve("OreRegenerator"), clock.millis());
                case "ultimate-regen", "ultimateregen" -> new SavedState(importer.foreignYaml(plugins.resolve("Ultimate_BlockRegeneration"), true), List.of());
                case "minexfarm-regen", "minexfarmregen" -> new SavedState(importer.foreignYaml(plugins.resolve("MineXFarmRegen"), false), List.of());
                default -> throw new IllegalArgumentException("Source must be oreregenerator, ultimate-regen or minexfarm-regen");
            };
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot import source: " + error.getMessage(), error);
        }
        List<Region> combined = new ArrayList<>(regions.all());
        for (Region region : imported.regions()) {
            if (regions.get(region.id()) != null) {
                throw new IllegalArgumentException("Import would overwrite existing region: " + region.id());
            }
            validateRegion(region);
            combined.add(region);
        }
        var importedTimers = imported.pending().stream().map(world::normalize).toList();
        List<dev.lino.blocksregenerator.engine.PendingRegen> pending = new ArrayList<>(engine.snapshot());
        pending.addAll(importedTimers);
        SavedState combinedState = new SavedState(combined, pending);
        new dev.lino.blocksregenerator.storage.StateCodec().decode(
                new dev.lino.blocksregenerator.storage.StateCodec().encode(combinedState, clock.millis()), clock.millis());
        storage.saveBlocking(combinedState);
        regions.replaceAll(combined);
        engine.resume(importedTimers);
        getLogger().info("Imported " + imported.regions().size() + " regions and " + imported.pending().size() + " timers from " + normalized + ". Source files were preserved.");
    }

    @Override
    public int activeCount(String regionId) {
        return engine.count(regionId);
    }

    @Override
    public double evaluate(String expression) {
        return DelayExpression.evaluate(expression, Map.of("player_count", (double) getServer().getOnlinePlayers().size()), random);
    }

    private void validateRegion(Region region) {
        for (RegenRule rule : region.rules()) {
            validateRule(rule);
        }
        for (var chain : region.chains()) {
            chain.stages().forEach(this::validateRule);
        }
    }

    private void validateRule(RegenRule rule) {
        if (rule.original().vanilla() || integrations.available(rule.original().type())) {
            integrations.validate(rule.original());
        }
        if (rule.replacement().vanilla() || integrations.available(rule.replacement().type())) {
            integrations.validate(rule.replacement());
        }
    }

    private void validateVanillaRule(RegenRule rule) {
        if (rule.original().vanilla()) {
            integrations.validate(rule.original());
        }
        if (rule.replacement().vanilla()) {
            integrations.validate(rule.replacement());
        }
    }

    private void validateConfiguration() {
        try {
            YamlConfiguration checked = new YamlConfiguration();
            checked.load(new java.io.File(getDataFolder(), "config.yml"));
            int operations = checked.getInt("engine.max-operations-per-tick", 500);
            long autosave = checked.getLong("storage.autosave-seconds", 30);
            if (operations < 1 || operations > 10_000 || autosave < 5 || autosave > 86400) {
                throw new IllegalArgumentException("Engine budget must be 1-10000 and autosave seconds 5-86400");
            }
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IllegalArgumentException("Invalid config.yml", error);
        }
    }

    private void generateGlobals() {
        if (getConfig().getBoolean("generate-global-regions", false)) {
            for (World world : getServer().getWorlds()) {
                createGlobal(world);
            }
        }
    }

    private void createGlobal(World world) {
        if (regions.get(world.getName()) == null) {
            save(Region.create(world.getName(), world.getName(), null)
                    .withSetting(RegionSetting.ALLOW_BLOCK_BREAK, true)
                    .withSetting(RegionSetting.ALLOW_BLOCK_PLACE, true));
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (getConfig().getBoolean("generate-global-regions", false)) {
            createGlobal(event.getWorld());
        }
    }

    private void runConfiguredImports() throws IOException {
        for (String source : List.of("ultimate-regen", "minexfarm-regen")) {
            Path marker = getDataFolder().toPath().resolve("imports").resolve(source + ".complete");
            if (getConfig().getBoolean("advanced.converters." + source + ".enabled", false) && !Files.exists(marker)) {
                importData(source);
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, "Imported successfully. Original files were preserved.\n");
            }
        }
    }
}
