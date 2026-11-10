package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Zero-dependency bridge to NexusHeartbeat, the server's shared "world mood" plugin.
 * Reports a "moment" if NexusHeartbeat is installed; a harmless no-op if it isn't. No
 * compile-time or plugin.yml dependency needed -- works by dispatching NexusHeartbeat's own
 * public "/heartbeat report" console command.
 */
public final class HeartbeatBridge {
    private HeartbeatBridge() {
    }

    /**
     * @param plugin   the calling plugin (used only to hop to the main thread if needed)
     * @param category one of: LIFE, DEATH, CONFLICT, COMMERCE, DISCOVERY, MISCHIEF, NATURE,
     *                 MYSTERY, SECURITY, COMMUNITY. An unrecognized category still works fine.
     * @param weight   -10 (worst) to +10 (best) impact on the world's shared mood.
     * @param summary  a short, human-readable sentence, single line, no newlines.
     */
    public static void report(JavaPlugin plugin, String category, int weight, String summary) {
        if (plugin == null || summary == null || summary.isBlank()) {
            return;
        }
        String safeCategory = (category == null || category.isBlank()) ? "MISC" : category;
        String safeSummary = summary.replace("\n", " ").replace("\r", " ");
        Runnable dispatch = () -> {
            try {
                if (!Bukkit.getPluginManager().isPluginEnabled("NexusHeartbeat")) {
                    return;
                }
                String cmd = "heartbeat report " + safeCategory + " " + weight + " " + safeSummary;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Throwable ignored) {
                // Never let a missing/misbehaving NexusHeartbeat break the calling plugin.
            }
        };
        if (Bukkit.isPrimaryThread()) {
            dispatch.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, dispatch);
        }
    }
}
