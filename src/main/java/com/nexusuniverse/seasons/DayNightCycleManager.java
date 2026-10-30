package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Overrides vanilla's day/night cycle with a fixed, even split -- twelve
 * real-world hours of daylight followed by twelve real-world hours of
 * night, every world day, instead of vanilla's day-weighted ~10/~7 minute
 * split (a full vanilla day is 24000 ticks, but day and night aren't
 * actually cut in half: day runs ticks 0-12000, night 12000-24000, so
 * the timestamps ARE already an even half-and-half split -- what vanilla
 * doesn't offer is stretching that split across real hours instead of
 * real minutes).
 *
 * Only touches Environment.NORMAL worlds. The Nether and the End don't
 * have a meaningful day/night cycle (Nether time is frozen, the End has
 * no sky), so both are left alone.
 *
 * Takes over completely: the doDaylightCycle gamerule is switched off on
 * every managed world so vanilla's own per-tick time advance doesn't
 * fight the value this manager sets, and world time is driven from a
 * single accumulator advanced once per server tick. When the accumulator
 * completes a full 24000-tick cycle, onDayComplete runs -- this is what
 * the main plugin wires to SeasonClock's advanceDay(), replacing the
 * fixed 24000-server-tick timer used when this feature is off.
 *
 * Like season.starting-year/starting-season, day-night.enabled is read
 * once at startup (this manager is only ever start()-ed or left alone
 * during onEnable) -- flipping it in config.yml and running /nexusseasons
 * reload will NOT toggle the cycle live, a server restart is needed,
 * since swapping the time-advance mechanism out from under a live world
 * mid-session isn't worth the added complexity for a setting nobody
 * changes often.
 */
public class DayNightCycleManager implements Listener {

    private static final long DAY_TICKS = 12000L;
    private static final long NIGHT_TICKS = 12000L;
    private static final long FULL_CYCLE_TICKS = DAY_TICKS + NIGHT_TICKS;

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final Runnable onDayComplete;

    private double timeOfDay = 0.0; // 0 (dawn) up to (exclusive) FULL_CYCLE_TICKS
    private BukkitTask task;

    public DayNightCycleManager(JavaPlugin plugin, SeasonsConfig config, Runnable onDayComplete) {
        this.plugin = plugin;
        this.config = config;
        this.onDayComplete = onDayComplete;
    }

    /** Call once from onEnable, only when config.customDayNightEnabled() is true. */
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (World world : Bukkit.getWorlds()) {
            takeOver(world);
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Picks up worlds that load after startup (e.g. a Multiverse world added later). */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        takeOver(event.getWorld());
    }

    private void takeOver(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime((long) timeOfDay);
    }

    private void tick() {
        double dayPhaseServerTicks = config.dayLengthMinutes() * 60.0 * 20.0;
        double nightPhaseServerTicks = config.nightLengthMinutes() * 60.0 * 20.0;

        double increment = (timeOfDay < DAY_TICKS)
                ? DAY_TICKS / dayPhaseServerTicks
                : NIGHT_TICKS / nightPhaseServerTicks;

        timeOfDay += increment;

        boolean completedCycle = false;
        if (timeOfDay >= FULL_CYCLE_TICKS) {
            timeOfDay -= FULL_CYCLE_TICKS;
            completedCycle = true;
        }

        long ticks = (long) timeOfDay;
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            world.setTime(ticks);
        }

        if (completedCycle) onDayComplete.run();
    }

    /** Restores vanilla's own daylight cycle so a world doesn't get stuck frozen in time if this plugin is disabled or removed. */
    public void stop() {
        if (task != null) task.cancel();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        }
    }
}
