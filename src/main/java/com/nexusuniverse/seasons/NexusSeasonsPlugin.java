package com.nexusuniverse.seasons;

import com.nexusuniverse.seasons.advisor.SeasonProblemAdvisor;
import com.nexusuniverse.seasons.climate.AllergyManager;
import com.nexusuniverse.seasons.climate.ClimateCommand;
import com.nexusuniverse.seasons.climate.ClimateManager;
import com.nexusuniverse.seasons.climate.ClimatePlayers;
import com.nexusuniverse.seasons.climate.ClimateSettings;
import com.nexusuniverse.seasons.weather.BlizzardManager;
import com.nexusuniverse.seasons.weather.CoastalWaveManager;
import com.nexusuniverse.seasons.weather.CoolingCap;
import com.nexusuniverse.seasons.weather.CoolingCapRecipe;
import com.nexusuniverse.seasons.weather.DryThunderstormManager;
import com.nexusuniverse.seasons.weather.EarthquakeManager;
import com.nexusuniverse.seasons.weather.FogManager;
import com.nexusuniverse.seasons.weather.HeatExhaustionManager;
import com.nexusuniverse.seasons.weather.HurricaneManager;
import com.nexusuniverse.seasons.weather.MeteorShowerManager;
import com.nexusuniverse.seasons.weather.SandstormManager;
import com.nexusuniverse.seasons.weather.TornadoManager;
import com.nexusuniverse.seasons.weather.TsunamiManager;
import com.nexusuniverse.seasons.weather.WaveManager;
import com.nexusuniverse.seasons.weather.WaveTrainManager;
import com.nexusuniverse.seasons.weather.WeatherHudManager;
import com.nexusuniverse.seasons.weather.WindManager;
import com.nexusuniverse.seasons.weather.WindproofBoots;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class NexusSeasonsPlugin extends JavaPlugin implements NexusSeasonsAPI {

    private SeasonsConfig config;
    private SeasonClock clock;
    private WorldVisualManager visualManager;
    private SeasonBossBar bossBar;
    private DayNightBossBar timeBossBar;
    private SeasonAmbianceManager ambianceManager;
    private SeasonMusicManager musicManager;
    private DayNightCycleManager dayNightCycle;
    private WeatherCycleManager weatherCycle;
    private CycleLockManager cycleLock;
    private KeepInventoryEventManager keepInventoryEvents;
    private WindManager wind;
    private WindproofBoots windproofBoots;
    private SeasonProblemAdvisor problemAdvisor;
    private CoolingCap coolingCap;
    private HeatExhaustionManager heatExhaustion;
    private DryThunderstormManager dryThunder;
    private FogManager fog;
    private TornadoManager tornado;
    private BlizzardManager blizzard;
    private WaveManager wave;
    private CoastalWaveManager coastalWave;
    private TsunamiManager tsunami;
    private HurricaneManager hurricane;
    private WaveTrainManager waveTrain;
    private WeatherHudManager weatherHud;
    private EarthquakeManager earthquake;
    private SandstormManager sandstorm;
    private MeteorShowerManager meteorShower;
    private ClimateSettings climateSettings;
    private ClimatePlayers climatePlayers;
    private ClimateManager climate;
    private AllergyManager allergies;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        this.config = new SeasonsConfig(this);
        this.clock = new SeasonClock(this, config);
        this.visualManager = new WorldVisualManager(config);
        this.bossBar = new SeasonBossBar();
        if (config.timeBossBarEnabled()) {
            this.timeBossBar = new DayNightBossBar(this, config.timeBossBarNightfallWarningLeadTicks());
            timeBossBar.start(config.timeBossBarRefreshIntervalSeconds());
        }
        this.ambianceManager = new SeasonAmbianceManager();
        this.musicManager = new SeasonMusicManager(config);
        // shared "what's going on + how do I fix it" messenger -- handed to every system below
        // that can actually deal damage to a player. See SeasonProblemAdvisor's own doc comment.
        this.problemAdvisor = new SeasonProblemAdvisor(config);
        getServer().getPluginManager().registerEvents(problemAdvisor, this);

        getServer().getServicesManager().register(NexusSeasonsAPI.class, this, this, ServicePriority.Normal);

        if (config.weatherCycleEnabled()) {
            weatherCycle = new WeatherCycleManager(this, config);
            weatherCycle.start();
        }

        // "crazy weather" layer -- wind, dry thunderstorms, fog, tornadoes, blizzards, ocean
        // waves, tsunamis, and hurricanes. Each is independently toggleable and fully
        // config-driven; tornado/blizzard/wave all take the WindManager reference, and
        // hurricane additionally takes weatherCycle (created just above -- may be null if
        // weather.enabled is off, which HurricaneManager tolerates and handles itself), so
        // everything stays visually/mechanically consistent rather than being unrelated
        // systems. Created before the command below since the command needs live references
        // to control several of these directly.
        this.windproofBoots = new WindproofBoots(this);
        this.wind = new WindManager(this, config, windproofBoots);
        wind.start();
        this.dryThunder = new DryThunderstormManager(this, config);
        dryThunder.start();
        this.fog = new FogManager(this, config);
        fog.start();
        this.tornado = new TornadoManager(this, config, wind, weatherCycle);
        tornado.start();
        this.blizzard = new BlizzardManager(this, config, wind);
        blizzard.start();
        this.wave = new WaveManager(this, config, wind);
        wave.start();
        this.coastalWave = new CoastalWaveManager(this, config, wind);
        coastalWave.start();
        this.tsunami = new TsunamiManager(this, config);
        tsunami.start();
        this.hurricane = new HurricaneManager(this, config, wind, weatherCycle);
        hurricane.start();
        this.waveTrain = new WaveTrainManager(this, config, wind, tsunami, hurricane);
        waveTrain.start();
        this.earthquake = new EarthquakeManager(this, config);
        earthquake.start();
        this.sandstorm = new SandstormManager(this, config, wind);
        sandstorm.start();
        this.meteorShower = new MeteorShowerManager(this, config, problemAdvisor);
        meteorShower.start();

        // Climate: temperature, humidity, dew point, UV, pollen and allergies, plus the 7:00 AM in-game
        // forecast built from WeatherCycleManager's real, rolled-ahead schedule (no schedule, and so
        // no precipitation forecast, if weather.enabled is off). Must exist before the sidebar below.
        this.climateSettings = new ClimateSettings(this);
        this.climatePlayers = new ClimatePlayers(this);
        final WeatherCycleManager schedule = weatherCycle;
        this.climate = new ClimateManager(this, config, climateSettings, clock,
                ticks -> schedule == null ? null : schedule.upcoming(ticks),
                wind::isExposedToSky, fog::isActive, blizzard::isActive,
                () -> config.heatExhaustionEnabled() && !(climate != null && climate.bodyHeatHandledElsewhere()),
                climatePlayers, wind::currentStrength);
        climate.start();
        // other plugins (NexusWardrobe) read the weather around a player through this
        getServer().getServicesManager().register(com.nexusuniverse.seasons.climate.ClimateApi.class, climate, this,
                ServicePriority.Normal);
        this.allergies = new AllergyManager(this, climateSettings, climatePlayers, climate);
        allergies.start();
        ClimateCommand climateCommand = new ClimateCommand(climate, climatePlayers, climateSettings);
        getCommand("forecast").setExecutor(climateCommand);
        getCommand("forecast").setTabCompleter(climateCommand);
        getCommand("allergy").setExecutor(climateCommand);
        getCommand("allergy").setTabCompleter(climateCommand);

        this.weatherHud = new WeatherHudManager(this, config, wind, dryThunder, fog, blizzard, tornado, tsunami, hurricane,
                climate, climatePlayers, climateSettings);
        weatherHud.start();

        // Summer heat exhaustion -- same "crazy weather" layer, reuses wind's isExposedToSky()
        // check rather than duplicating it. See HeatExhaustionManager/CoolingCap.
        this.coolingCap = new CoolingCap(this);
        CoolingCapRecipe.register(this, coolingCap);
        this.heatExhaustion = new HeatExhaustionManager(this, config, clock, wind, coolingCap, problemAdvisor);
        heatExhaustion.setStandDown(climate::bodyHeatHandledElsewhere);
        heatExhaustion.start();
        getServer().getPluginManager().registerEvents(heatExhaustion, this);
        getLogger().info("Summer heat exhaustion is " + (config.heatExhaustionEnabled() ? "ON" : "OFF")
                + (config.heatExhaustionEnabled()
                        ? " -- players exposed to open sky in summer will build up heat; a Cooling Cap "
                          + "(craft: Hay Bale/Leather/Hay Bale over Leather/ /Leather, or /nexusseasons coolingcap give) "
                          + "grants full immunity."
                        : " (summer-heat.enabled: false in config.yml)."));

        getServer().getPluginManager().registerEvents(bossBar, this);
        getServer().getPluginManager().registerEvents(musicManager, this);

        getServer().getPluginManager().registerEvents(new PlantGrowthModifier(clock, config), this);
        getServer().getPluginManager().registerEvents(new SeasonalMobSpawner(clock, config), this);

        refreshBossBar(); // show correct info immediately, don't wait for the first day to tick over

        if (config.musicEnabled()) {
            musicManager.switchToSeason(clock.season()); // start the soundtrack immediately on enable
            scheduleNextTrackRotation();
        }

        if (config.customDayNightEnabled()) {
            // custom day/night cycle drives world time itself, and signals advanceOneDay when a full cycle completes
            dayNightCycle = new DayNightCycleManager(this, config, this::advanceOneDay);
            dayNightCycle.start();
            getLogger().info("Custom day/night cycle is ON -- " + config.dayLengthMinutes()
                    + " real minute(s) of day, " + config.nightLengthMinutes() + " real minute(s) of night.");
        } else {
            // one Minecraft day (24000 ticks) = one season-day
            Bukkit.getScheduler().runTaskTimer(this, this::advanceOneDay, 24000L, 24000L);
            getLogger().warning("Custom day/night cycle is OFF (day-night.enabled: false in config.yml) -- "
                    + "vanilla's own day/night is running instead. If you expected the custom cycle to be "
                    + "active, this is very likely why -- flip that key to true and restart.");
        }

        // independent of which day/night/weather mode is active above -- also protects vanilla's
        // own cycles when either is off, not just the custom ones
        this.cycleLock = new CycleLockManager(this, config);
        cycleLock.start();
        getServer().getPluginManager().registerEvents(new CycleLockGuard(config), this);
        getLogger().info("Cycle lock is " + (config.cycleLockEnabled() ? "ON" : "OFF")
                + (config.cycleLockEnabled()
                        ? " -- gamerule/time/weather commands that would disable the cycle(s) above are blocked/corrected."
                        : " -- gamerule/time/weather commands can freely disable the cycle(s) above. Turn this on with "
                          + "/nexusseasons cyclelock on if something on the server keeps turning the day cycle off."));

        // scheduled keep-inventory windows -- independent of cycle lock above (that's day/night/
        // weather; this is the keepInventory gamerule on its own server-wall-clock-time schedule).
        // Same "runs every tick and forces the value back" strategy as cycle lock, for the exact
        // same reason: no way to intercept a command block's own command before it runs.
        this.keepInventoryEvents = new KeepInventoryEventManager(this, config);
        keepInventoryEvents.start();
        getServer().getPluginManager().registerEvents(new KeepInventoryEventGuard(config), this);
        getLogger().info("Keep inventory events: " + (config.keepInventoryEventsEnabled() ? "ON -- "
                + keepInventoryEvents.windows().size() + " window(s) scheduled" : "OFF (keep-inventory-events.enabled: "
                + "false in config.yml)"));

        // registered here, after keepInventoryEvents exists, since the command needs a live
        // reference to it (for the reload hook and the status line)
        getCommand("nexusseasons").setExecutor(new NexusSeasonsCommand(clock, config, this::syncDisplayState, this::advanceOneDay,
                dryThunder, fog, tornado, blizzard, tsunami, hurricane, waveTrain, earthquake, sandstorm, meteorShower, windproofBoots,
                keepInventoryEvents, coolingCap));

        long sweepIntervalTicks = 20L * config.sweepIntervalSeconds();
        Bukkit.getScheduler().runTaskTimer(this, () -> visualManager.tick(clock.season()), sweepIntervalTicks, sweepIntervalTicks);

        if (config.ambianceEnabled()) {
            scheduleNextAmbientLine();
        }

        getLogger().info("NexusSeasons enabled -- Year " + clock.year() + ", " + clock.season().displayName()
                + ", day " + clock.dayOfSeason() + "/" + clock.daysPerSeason() + ".");
    }

    /** Self-rescheduling: picks a fresh random delay after every line so it never falls into a predictable rhythm. */
    private void scheduleNextAmbientLine() {
        int minTicks = 20 * 60 * config.ambianceMinIntervalMinutes();
        int maxTicks = 20 * 60 * config.ambianceMaxIntervalMinutes();
        int delay = minTicks + (maxTicks > minTicks ? random.nextInt(maxTicks - minTicks) : 0);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            ambianceManager.ambientTick(clock.season());
            if (config.ambianceEnabled()) scheduleNextAmbientLine();
        }, delay);
    }

    /** Fixed-interval rotation, same pattern as the ambient line scheduler but on a regular timer instead of randomized. */
    private void scheduleNextTrackRotation() {
        long delayTicks = 20L * config.musicTrackLengthSeconds();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            musicManager.rotateTrack(clock.season());
            if (config.musicEnabled()) scheduleNextTrackRotation();
        }, delayTicks);
    }

    /** Advances the clock by one day, refreshes the boss bar, and broadcasts an announcement if that crossed into a new season. */
    private void advanceOneDay() {
        Season before = clock.season();
        int yearBefore = clock.year();

        clock.advanceDay();

        if (clock.season() != before) {
            if (config.transitionMessagesEnabled()) {
                ambianceManager.announceSeasonChange(clock.season(), clock.year(), clock.year() != yearBefore);
            }
            if (config.musicEnabled()) {
                musicManager.switchToSeason(clock.season()); // don't wait for the current track's rotation timer
            }
        }
        refreshBossBar();
    }

    /** Used after a silent admin edit (setseason/setday/setyear) -- updates the boss bar and matches the music to the current season, no fanfare banner. */
    private void syncDisplayState() {
        refreshBossBar();
        if (config.musicEnabled()) {
            musicManager.switchToSeason(clock.season());
        }
    }

    private void refreshBossBar() {
        bossBar.update(clock.season(), clock.year(), clock.dayOfSeason(), clock.daysPerSeason());
    }

    @Override
    public void onDisable() {
        if (clock != null) clock.save();
        if (bossBar != null) bossBar.removeAll();
        if (timeBossBar != null) timeBossBar.stop();
        if (musicManager != null) musicManager.stopAll();
        if (dayNightCycle != null) dayNightCycle.stop();
        if (cycleLock != null) cycleLock.stop();
        if (keepInventoryEvents != null) keepInventoryEvents.stop();
        if (heatExhaustion != null) heatExhaustion.stop();
        if (weatherHud != null) weatherHud.stop();
        if (allergies != null) allergies.stop();
        if (climate != null) climate.stop();
        if (climatePlayers != null) climatePlayers.save();
        if (waveTrain != null) waveTrain.stop();
        if (meteorShower != null) meteorShower.stop();
        if (sandstorm != null) sandstorm.stop();
        if (earthquake != null) earthquake.stop();
        if (hurricane != null) hurricane.stop();
        if (tsunami != null) tsunami.stop();
        if (coastalWave != null) coastalWave.stop();
        if (wave != null) wave.stop();
        if (wind != null) wind.stop();
        if (dryThunder != null) dryThunder.stop();
        if (fog != null) fog.stop();
        if (tornado != null) tornado.stop();
        if (blizzard != null) blizzard.stop();
        if (weatherCycle != null) weatherCycle.stop();
        getServer().getServicesManager().unregisterAll(this);
    }

    @Override
    public Season getCurrentSeason() {
        return clock.season();
    }

    @Override
    public int getCurrentYear() {
        return clock.year();
    }

    @Override
    public int getDayOfSeason() {
        return clock.dayOfSeason();
    }

    @Override
    public int getDaysPerSeason() {
        return clock.daysPerSeason();
    }
}
