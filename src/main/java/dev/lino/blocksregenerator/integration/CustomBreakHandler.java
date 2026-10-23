package dev.lino.blocksregenerator.integration;

import dev.lino.blocksregenerator.model.Placeable;
import java.util.function.Consumer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

@FunctionalInterface
public interface CustomBreakHandler {
    void handle(Player player, Block block, Placeable placeable, Cancellable event, Consumer<Boolean> setDrops);
}
