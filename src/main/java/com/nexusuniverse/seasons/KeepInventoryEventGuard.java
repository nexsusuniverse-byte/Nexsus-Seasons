package com.nexusuniverse.seasons;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/**
 * Cancels a player- or console-typed "/gamerule keepInventory <value>" outright, before it runs,
 * for the two sources Bukkit actually gives a cancellable pre-execution event for -- exact same
 * pattern and exact same limitation as CycleLockGuard (see that class's doc comment for the full
 * explanation, which applies here word for word).
 *
 * COMMAND BLOCKS ARE NOT COVERED HERE, AND CAN'T BE THIS WAY. Bukkit/Paper has never exposed a
 * pre-execution event that fires for a redstone-triggered command block's own command -- this
 * isn't a gap specific to this plugin, nothing can intercept it this way. This class only stops
 * two sources from firing the command in the first place, purely so it visibly fails with an
 * explanation instead of silently no-op'ing a moment later.
 *
 * The actual guarantee -- that keepInventory ends up correct no matter who or what changed it,
 * command blocks included -- comes from KeepInventoryEventManager's per-tick re-assertion, not
 * from this listener. This listener is a courtesy; that manager is the enforcement.
 */
public class KeepInventoryEventGuard implements Listener {

    private final SeasonsConfig config;

    public KeepInventoryEventGuard(SeasonsConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!shouldBlock()) return;
        if (!isKeepInventoryWrite(event.getCommand())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!shouldBlock()) return;
        String command = event.getMessage().startsWith("/") ? event.getMessage().substring(1) : event.getMessage();
        if (!isKeepInventoryWrite(command)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage("§cKeep Inventory is on a scheduled timer right now (keep-inventory-events "
                + "in config.yml) -- that command won't run until the current window ends, or the schedule is "
                + "turned off with keep-inventory-events.enabled: false.");
    }

    private boolean shouldBlock() {
        return config.keepInventoryEventsEnabled() && config.keepInventoryBlockManualChanges();
    }

    /** True if this is a "/gamerule keepInventory <value>" write (a bare read with no value is left alone). */
    private boolean isKeepInventoryWrite(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) return false;
        String command = rawCommand.trim();
        if (command.startsWith("/")) command = command.substring(1);
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 3) return false; // "gamerule keepInventory <value>" is 3 tokens minimum

        String base = parts[0].toLowerCase(Locale.ROOT);
        String sub = parts[1].toLowerCase(Locale.ROOT);
        return base.equals("gamerule") && sub.equals("keepinventory");
    }
}
