package dev.lino.blocksregenerator.admin;

import dev.lino.blocksregenerator.integration.IntegrationRegistry;
import dev.lino.blocksregenerator.configuration.RuntimeSettings;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Region;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

public interface AdminBackend {
    Collection<Region> regions();
    Region region(String id);
    Region regionAt(BlockKey key);
    void save(Region region);
    void delete(String id);
    void restore(String regionId);
    void reload();
    IntegrationRegistry integrations();
    JavaPlugin plugin();
    void importData(String source);
    int activeCount(String regionId);
    double evaluate(String expression);
    RuntimeSettings settings();
}
