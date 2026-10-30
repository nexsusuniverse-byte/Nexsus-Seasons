package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.SeasonClock;
import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The server-side half of the climate system: owns the day's forecast, issues the 7:00 AM report,
 * eases the weather-dependent bits so the sidebar never snaps, and works out a {@link Reading} for any
 * player. All the actual maths lives in the pure classes next to this one.
 *
 * Runs once a second. The report is tied to the in-game clock (world tick 1000 is 7:00 AM), not to
 * real time, so with the default 24-real-hour day it is issued once a real day.
 */
public final class ClimateManager implements ClimateApi {

    private static final double EASE = 0.04;

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final ClimateSettings settings;
    private final SeasonClock clock;
    /** Supplies the upcoming wet spells for a horizon in server ticks, or null if weather is vanilla. */
    private final java.util.function.LongFunction<List<WeatherSpan>> schedule;
    private final Predicate<Location> exposedToSky;
    private final BooleanSupplier fogActive;
    private final BooleanSupplier blizzardActive;
    private final Supplier<Boolean> coolingCapHint;
    private final ClimatePlayers players;
    private final DoubleSupplier windStrength;
    private final File stateFile;

    private ClimateParams params;
    private DayPlan plan;
    private String lastReportKey = "";
    private double easedOffsetF;
    private double easedCloud = 0.3;
    private boolean easedReady;
    private volatile boolean bodyHeatElsewhere;
    private BukkitTask task;

    public ClimateManager(JavaPlugin plugin, SeasonsConfig config, ClimateSettings settings, SeasonClock clock,
                          java.util.function.LongFunction<List<WeatherSpan>> schedule, Predicate<Location> exposedToSky,
                          BooleanSupplier fogActive, BooleanSupplier blizzardActive, Supplier<Boolean> coolingCapHint,
                          ClimatePlayers players, DoubleSupplier windStrength) {
        this.plugin = plugin;
        this.config = config;
        this.settings = settings;
        this.clock = clock;
        this.schedule = schedule;
        this.exposedToSky = exposedToSky;
        this.fogActive = fogActive;
        this.blizzardActive = blizzardActive;
        this.coolingCapHint = coolingCapHint;
        this.players = players;
        this.windStrength = windStrength;
        this.stateFile = new File(plugin.getDataFolder(), "climate-state.yml");
        loadState();
    }

    // ---- lifecycle -------------------------------------------------------------------------------------

    public void start() {
        refreshParams();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        saveState();
    }

    // ---- the reference world, params, rates ---------------------------------------------------------

    /** The overworld the report is about: the configured one, or the first normal world. */
    public World referenceWorld() {
        String wanted = settings.forecastWorld();
        if (wanted != null && !wanted.isBlank()) {
            World w = Bukkit.getWorld(wanted);
            if (w != null) {
                return w;
            }
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return null;
    }

    private void refreshParams() {
        World w = referenceWorld();
        params = settings.params(w == null ? 0L : w.getSeed(), config.daysPerSeason());
    }

    public ClimateParams params() {
        if (params == null) {
            refreshParams();
        }
        return params;
    }

    public GameClock.Rates rates() {
        return new GameClock.Rates(config.customDayNightEnabled(), config.dayLengthMinutes(), config.nightLengthMinutes());
    }

    public ClimateSettings settings() {
        return settings;
    }

    // ---- once a second ------------------------------------------------------------------------------------

    private void tick() {
        if (!settings.climateEnabled()) {
            return;
        }
        refreshParams();
        World w = referenceWorld();
        if (w == null) {
            return;
        }
        double tick = w.getTime();
        double x = Climate.dayCoord(clock.year(), clock.season(), clock.dayOfSeason(), params.daysPerSeason, tick);
        boolean precip = w.hasStorm() || w.isThundering();
        double targetOffset = Climate.weatherOffsetF(params, precip, w.isThundering(), fogActive.getAsBoolean(),
                blizzardActive.getAsBoolean());
        double targetCloud = precip ? 1.0 : Climate.cloudBase(params, x);
        if (!easedReady) {
            easedOffsetF = targetOffset;
            easedCloud = targetCloud;
            easedReady = true;
        } else {
            easedOffsetF += (targetOffset - easedOffsetF) * EASE;
            easedCloud += (targetCloud - easedCloud) * EASE;
        }

        if (settings.forecastEnabled()) {
            String key = ForecastBuilder.dayKeyOf(clock.year(), clock.season(), clock.dayOfSeason());
            long t = w.getTime();
            long from = settings.forecastTick();
            if (!key.equals(lastReportKey) && t >= from && t < from + settings.forecastCatchUpTicks()) {
                issue(key, settings.forecastBroadcast());
            }
        }
    }

    // ---- the forecast ---------------------------------------------------------------------------------------

    /** Builds a fresh plan for the next 24 game hours starting now, stores it, and optionally broadcasts it. */
    private void issue(String key, boolean broadcast) {
        World w = referenceWorld();
        if (w == null) {
            return;
        }
        plan = build(w.getTime());
        lastReportKey = key;
        saveState();
        if (!broadcast) {
            return;
        }
        List<String> lines = ForecastFormatter.report(plan, settings.celsius(), coolingCapHint.get());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (players.muted(player.getUniqueId())) {
                continue;
            }
            for (String line : lines) {
                player.sendMessage(line);
            }
        }
        plugin.getLogger().info("Issued the daily forecast: high " + Climate.degrees(plan.highF, settings.celsius())
                + ", low " + Climate.degrees(plan.lowF, settings.celsius()) + ", " + plan.precipHeadline.toLowerCase() + ".");
    }

    private DayPlan build(long startTick) {
        GameClock.Rates rates = rates();
        long horizon = (long) Math.ceil(rates.serverTicksFor(startTick, GameClock.DAY)) + 40L;
        List<WeatherSpan> spans = schedule.apply(horizon);
        return ForecastBuilder.build(params(), rates, clock.year(), clock.season(), clock.dayOfSeason(), startTick, spans,
                Climate.windMph(config.windMinStrength()), Climate.windMph(config.windMaxStrength()));
    }

    /** Admin: issue the report right now, to everyone. */
    public void broadcastNow() {
        issue(ForecastBuilder.dayKeyOf(clock.year(), clock.season(), clock.dayOfSeason()), true);
    }

    /** Admin: recompute the plan from now without telling anyone. */
    public void rebuildSilently() {
        issue(ForecastBuilder.dayKeyOf(clock.year(), clock.season(), clock.dayOfSeason()), false);
    }

    /** The plan in force, rebuilt quietly if there is none or the last one has run out. */
    public DayPlan currentPlan() {
        World w = referenceWorld();
        if (plan == null || w == null || stale(w.getTime())) {
            if (w == null) {
                return plan;
            }
            plan = build(w.getTime());
        }
        return plan;
    }

    private boolean stale(long nowTick) {
        long planStart = Climate.dayIndex(plan.year, plan.season, plan.day, params().daysPerSeason) * (long) GameClock.DAY
                + plan.startTick;
        long now = Climate.dayIndex(clock.year(), clock.season(), clock.dayOfSeason(), params().daysPerSeason)
                * (long) GameClock.DAY + nowTick;
        long elapsed = now - planStart;
        return elapsed < 0 || elapsed >= GameClock.DAY;
    }

    public void sendForecast(CommandSender to) {
        DayPlan p = currentPlan();
        if (p == null) {
            to.sendMessage("§cNo forecast yet: there is no overworld loaded.");
            return;
        }
        for (String line : ForecastFormatter.report(p, settings.celsius(), coolingCapHint.get())) {
            to.sendMessage(line);
        }
    }

    // ---- what a player sees right now ------------------------------------------------------------------------

    /** The sidebar reading for a player, or null if they are not in an overworld-type world. */
    public Reading reading(Player player) {
        Location loc = player.getLocation();
        World w = loc.getWorld();
        if (w == null || w.getEnvironment() != World.Environment.NORMAL) {
            return null;
        }
        Block block = loc.getBlock();
        return LiveReader.read(params(), new LiveInput(clock.year(), clock.season(), clock.dayOfSeason(), w.getTime(),
                block.getTemperature(), block.getHumidity(), exposedToSky.test(loc), windStrength.getAsDouble(),
                w.hasStorm() || w.isThundering(), w.isThundering(), fogActive.getAsBoolean(),
                blizzardActive.getAsBoolean(), easedOffsetF, easedCloud));
    }

    // ---- ClimateApi ---------------------------------------------------------------------------------------------

    @Override
    public double temperatureF(Player player) {
        Reading r = reading(player);
        return r == null ? Double.NaN : r.tempF();
    }

    @Override
    public double feelsLikeF(Player player) {
        Reading r = reading(player);
        return r == null ? Double.NaN : r.feelsF();
    }

    @Override
    public double windMph(Player player) {
        Reading r = reading(player);
        return r == null ? Double.NaN : r.windMph();
    }

    @Override
    public double uvIndex(Player player) {
        Reading r = reading(player);
        return r == null ? Double.NaN : r.uv();
    }

    @Override
    public boolean sheltered(Player player) {
        Reading r = reading(player);
        return r != null && r.sheltered();
    }

    @Override
    public String precipitation(Player player) {
        Reading r = reading(player);
        World w = player.getWorld();
        if (r == null || r.sheltered() || !(w.hasStorm() || w.isThundering())) {
            return "NONE";
        }
        if (r.tempF() <= Climate.SNOW_LINE_F) {
            return "SNOW";
        }
        return w.isThundering() ? "STORM" : "RAIN";
    }

    @Override
    public void setBodyHeatHandledElsewhere(boolean handled) {
        bodyHeatElsewhere = handled;
    }

    @Override
    public boolean bodyHeatHandledElsewhere() {
        return bodyHeatElsewhere;
    }

    // ---- saving --------------------------------------------------------------------------------------------------

    private void loadState() {
        if (!stateFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(stateFile);
        lastReportKey = data.getString("last-report-key", "");
        var section = data.getConfigurationSection("plan");
        if (section != null) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            for (String k : section.getKeys(false)) {
                map.put(k, section.get(k));
            }
            plan = DayPlan.fromMap(map);
        }
    }

    private void saveState() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("last-report-key", lastReportKey);
        if (plan != null) {
            for (java.util.Map.Entry<String, Object> e : plan.toMap().entrySet()) {
                data.set("plan." + e.getKey(), e.getValue());
            }
        }
        try {
            data.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusSeasons: failed to save climate-state.yml", e);
        }
    }
}
