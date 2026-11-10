package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Continuously re-asserts the day/night and weather gamerules NexusSeasons needs, so a
 * /gamerule command -- run by anyone with permission, on purpose or by accident -- can't disable
 * the day/night or weather cycle out from under this plugin. This exists specifically so
 * "someone flips a gamerule" isn't a way around the cycle running; the only sanctioned way to
 * change that is /nexusseasons cyclelock off, which this class checks every pass and backs off
 * completely the moment it's set.
 *
 * What "locked" enforces, per Environment.NORMAL world:
 *  - DO_DAYLIGHT_CYCLE: false if day-night.enabled (DayNightCycleManager owns time advancement
 *    itself at that point, and vanilla's own per-tick increment would otherwise fight it), or
 *    true if day-night.enabled is off (so vanilla's own day/night keeps running naturally
 *    instead of getting stuck frozen wherever it was when doDaylightCycle got set to false).
 *  - DO_WEATHER_CYCLE: the same idea, applied to weather. False if weather.enabled
 *    (WeatherCycleManager owns storm/thunder state itself), true if weather.enabled is off (so
 *    vanilla's own random weather engine keeps running instead of getting stuck on one state).
 *
 * Runs every tick rather than on a longer interval: a command block can't be intercepted before
 * it executes (see CycleLockGuard's class doc for why), so this periodic correction is the actual
 * mitigation for that case -- running it every tick keeps the worst-case visible flicker down to
 * about one tick (~50ms) instead of up to a second. Still cheap even at that frequency: it checks
 * the current gamerule value first and only calls setGameRule when it's actually different from
 * what's wanted, so in the (very common) case where nobody's touched a gamerule recently, each
 * tick's pass is just a couple of reads per world, no writes.
 */
public class CycleLockManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private BukkitTask task;

    public CycleLockManager(JavaPlugin plugin, SeasonsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::enforce, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void enforce() {
        if (!config.cycleLockEnabled()) return;

        boolean desiredDaylightCycle = !config.customDayNightEnabled();
        boolean desiredWeatherCycle = !config.weatherCycleEnabled();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;

            Boolean daylight = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
            if (daylight == null || daylight != desiredDaylightCycle) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, desiredDaylightCycle);
            }

            Boolean weather = world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE);
            if (weather == null || weather != desiredWeatherCycle) {
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, desiredWeatherCycle);
            }
        }
    }
}
