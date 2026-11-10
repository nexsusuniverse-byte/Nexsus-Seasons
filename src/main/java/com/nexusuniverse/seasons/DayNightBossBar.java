package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * A second, separate boss bar -- SeasonBossBar already owns the season/
 * year/day one -- showing a live, second-by-second clock for the current
 * day/night cycle, the named phase of the day it's currently in (Sunrise,
 * Morning, Noon, Afternoon, an explicit nightfall warning, Sunset, Night,
 * Midnight, Late Night), and a fill that completes once per full cycle.
 * One shared bar for the whole server, same reasoning as SeasonBossBar:
 * the time of day is the same for everyone, so there's no per-player state
 * to track beyond who's currently added to the bar.
 *
 * Reads directly off whichever NORMAL world's own time value is currently
 * set -- NOT off DayNightCycleManager's internal accumulator -- so this
 * works identically whether the custom day/night cycle (day-night.enabled)
 * is on or off. Both DayNightCycleManager (custom mode) and vanilla itself
 * (when that's off) drive every managed world's time through the exact
 * same 0-23999 range, so there's exactly one value to read regardless of
 * which is actually in charge, and this class doesn't need to know or care
 * which one that is.
 *
 * The displayed clock uses vanilla's own tick-to-hour convention (tick 0 =
 * 6:00 AM, 6000 = noon, 12000 = 6:00 PM, 18000 = midnight -- exactly 1000
 * ticks per clock hour, since a full day is 24000 ticks = 24 hours) rather
 * than inventing a different mapping, so "6:00 AM" here means the same
 * moment a player would already recognize as dawn. With the default
 * day-night config (720 real minutes of day + 720 of night = a full
 * 24-real-hour cycle), that also means this clock advances at genuinely
 * real-world pace -- one real second is one displayed clock second -- and
 * that pace scales proportionally with whatever day-length-minutes/
 * night-length-minutes are actually set to. That's what makes this read as
 * "the realistic time" rather than an arbitrary bar: it's the same
 * real-time-paced cycle DayNightCycleManager already drives, just handed
 * back to players as a clock instead of only being visible through the sky.
 *
 * HONEST LIMITATION: same ceiling SeasonBossBar's own doc comment already
 * covers -- a boss bar is title text, a color, and a fill fraction, nothing
 * else. There's no separate digit display or icon slot; the live clock,
 * the phase name, and the nightfall warning are all just segments of one
 * title string re-built every refresh.
 */
public class DayNightBossBar implements Listener {

    private final JavaPlugin plugin;
    private final BossBar bar;
    private final long nightfallWarningLeadTicks;
    private BukkitTask task;

    public DayNightBossBar(JavaPlugin plugin, long nightfallWarningLeadTicks) {
        this.plugin = plugin;
        this.nightfallWarningLeadTicks = Math.max(0L, nightfallWarningLeadTicks);
        this.bar = Bukkit.createBossBar("§7Loading time...", BarColor.YELLOW, BarStyle.SOLID);
        this.bar.setProgress(0.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        bar.addPlayer(event.getPlayer());
    }

    /** Call once from onEnable, only when config.timeBossBarEnabled() is true. */
    public void start(int refreshIntervalSeconds) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        update(); // show correct info immediately, don't wait for the first scheduled refresh
        long interval = Math.max(1L, 20L * refreshIntervalSeconds);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        bar.removeAll();
    }

    private void update() {
        long ticks = currentWorldTime();
        Phase phase = Phase.forTicks(ticks, nightfallWarningLeadTicks);

        bar.setProgress(Math.max(0.0, Math.min(1.0, ticks / 24000.0)));
        bar.setColor(phase.barColor);
        bar.setTitle(buildTitle(ticks, phase));
    }

    /**
     * Every managed world is kept in lockstep by DayNightCycleManager (or,
     * when that's off, by vanilla's own cycle running unmodified) -- so any
     * one NORMAL world's time value represents "the" current time for the
     * whole server, the same assumption DayNightCycleManager itself already
     * makes when it writes that value out to every managed world.
     */
    private long currentWorldTime() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return Math.floorMod(world.getTime(), 24000L);
            }
        }
        if (Bukkit.getWorlds().isEmpty()) {
            return 0L;
        }
        return Math.floorMod(Bukkit.getWorlds().get(0).getTime(), 24000L);
    }

    private String buildTitle(long ticks, Phase phase) {
        String clock = formatClock(ticks);
        return "§f§l" + clock + " §8✦ " + phase.textColor + "§l" + phase.glyph + " " + phase.label;
    }

    /** tick 0 = 6:00:00 AM, 1000 ticks = 1 clock hour -- vanilla's own convention, just spelled out as H:MM:SS instead of a sky angle. */
    private String formatClock(long ticks) {
        double totalSecondsOfDay = (((ticks / 1000.0) + 6.0) % 24.0) * 3600.0;
        long totalSeconds = Math.round(totalSecondsOfDay) % 86400L;

        int hour24 = (int) (totalSeconds / 3600);
        int minute = (int) ((totalSeconds % 3600) / 60);
        int second = (int) (totalSeconds % 60);

        int hour12 = hour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String meridiem = hour24 < 12 ? "AM" : "PM";
        return String.format("%d:%02d:%02d %s", hour12, minute, second, meridiem);
    }

    /**
     * Every named stretch of the day, in ascending tick order except SUNRISE
     * which wraps across the 24000/0 boundary. NIGHTFALL_WARNING's start is
     * the only boundary driven by config (time-boss-bar.
     * nightfall-warning-lead-ticks) -- how much notice players get before
     * full dusk that monsters are about to start spawning; every other
     * boundary below is a fixed, documented constant rather than more
     * config sprawl for cutoffs nobody's likely to want to retune per-server.
     */
    private enum Phase {
        SUNRISE("Sunrise", "☀", "§d", BarColor.PINK),
        MORNING("Morning", "☀", "§e", BarColor.YELLOW),
        NOON("Noon", "☀", "§e", BarColor.YELLOW),
        AFTERNOON("Afternoon", "☀", "§f", BarColor.YELLOW),
        NIGHTFALL_WARNING("Nightfall Approaching", "⚠", "§c", BarColor.RED),
        SUNSET("Sunset", "☀", "§6", BarColor.RED),
        NIGHT("Night", "☽", "§9", BarColor.BLUE),
        MIDNIGHT("Midnight", "☽", "§5", BarColor.PURPLE),
        LATE_NIGHT("Late Night", "✦", "§9", BarColor.BLUE);

        final String label;
        final String glyph;
        final String textColor;
        final BarColor barColor;

        Phase(String label, String glyph, String textColor, BarColor barColor) {
            this.label = label;
            this.glyph = glyph;
            this.textColor = textColor;
            this.barColor = barColor;
        }

        static Phase forTicks(long ticks, long nightfallWarningLeadTicks) {
            long warningStart = Math.max(0L, 12000L - nightfallWarningLeadTicks);
            if (ticks >= 23500L || ticks < 500L) return SUNRISE;
            if (ticks < 5500L) return MORNING;
            if (ticks < 6500L) return NOON;
            if (ticks < warningStart) return AFTERNOON;
            if (ticks < 12000L) return NIGHTFALL_WARNING;
            if (ticks < 13000L) return SUNSET;
            if (ticks < 17000L) return NIGHT;
            if (ticks < 19000L) return MIDNIGHT;
            return LATE_NIGHT; // 19000 - 23499
        }
    }
}
