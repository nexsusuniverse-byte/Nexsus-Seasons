package com.nexusuniverse.seasons;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/**
 * Cancels day/night/weather-disrupting commands outright, at the source, before they run --
 * for the two sources Bukkit actually gives a cancellable pre-execution event for: a player
 * typing one in chat (PlayerCommandPreprocessEvent), and a command typed directly at the server
 * console (ServerCommandEvent).
 *
 * COMMAND BLOCKS ARE NOT COVERED HERE, AND CAN'T BE THIS WAY. This isn't a gap in this plugin --
 * Bukkit and Paper have never exposed an event that fires when a redstone-triggered command
 * block executes its command; ServerCommandEvent and PlayerCommandPreprocessEvent are the only
 * two pre-execution command hooks that exist, and neither one fires for a command block (this
 * has been asked about and confirmed absent on the Bukkit forums going back over a decade). A
 * command block running "/gamerule doDaylightCycle false" cannot be intercepted before it runs
 * by any plugin, not just this one.
 *
 * What actually protects against a command block, instead:
 *  - CycleLockManager's periodic correction (see that class) now runs every tick rather than
 *    once a second, specifically so a command block's change gets corrected almost immediately
 *    (worst case ~50ms) rather than visibly sticking for up to a second.
 *  - When day-night.enabled is on, DayNightCycleManager already overwrites world time every
 *    single tick unconditionally -- a command block's "/time set" gets overwritten the very next
 *    tick as a side effect of that, not because anything here is watching for it specifically.
 *  - Weather forced by a command block (e.g. "/weather clear" on a repeating clock) is NOT fully
 *    solved by any of the above -- doWeatherCycle staying true only means weather is ALLOWED to
 *    change over time, it doesn't force a change against a command actively re-forcing one state.
 *    Closing that gap for real would mean either a dedicated weather-cycling manager (this plugin
 *    doesn't have one) or finding and removing/disabling the specific command block -- ask if you
 *    want either built; I didn't want to build a half-solution and imply it was complete.
 */
public class CycleLockGuard implements Listener {

    private final SeasonsConfig config;

    public CycleLockGuard(SeasonsConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!config.cycleLockEnabled()) return;
        if (!isLockedCommand(event.getCommand())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!config.cycleLockEnabled()) return;
        // strip the leading "/" player commands carry that console commands don't
        String command = event.getMessage().startsWith("/") ? event.getMessage().substring(1) : event.getMessage();
        if (!isLockedCommand(command)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage("§cThe day/night and weather cycle is locked (/nexusseasons cyclelock is ON) "
                + "-- that command won't run until it's switched off.");
    }

    /**
     * True if this command tries to disable day/night or weather progression, or jump the clock
     * directly.
     */
    private boolean isLockedCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) return false;
        String command = rawCommand.trim();
        if (command.startsWith("/")) command = command.substring(1);
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) return false;

        String base = parts[0].toLowerCase(Locale.ROOT);
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";

        if (base.equals("gamerule")) {
            boolean targetsCycleRule = sub.equals("dodaylightcycle") || sub.equals("doweathercycle");
            boolean hasValue = parts.length > 2; // no value = a read, not a write -- allowed
            return targetsCycleRule && hasValue;
        }
        if (base.equals("time")) {
            return sub.equals("set") || sub.equals("add");
        }
        if (base.equals("weather")) {
            return sub.equals("clear") || sub.equals("rain") || sub.equals("thunder");
        }
        return false;
    }
}
