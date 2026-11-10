package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import com.nexusuniverse.seasons.WeatherCycleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * The most "everything at once" event in this plugin -- a hurricane is really an orchestration of
 * systems that already exist (WindManager, WeatherCycleManager, CoastalFloodEngine) rather than a
 * new effect of its own. What it adds is a real intensity CURVE over the storm's lifetime instead
 * of a flat on/off: ramps up, holds at peak, an actual calm "eye" partway through if enabled
 * (wind and rain both genuinely drop off, then build back to peak on the other side), then
 * weakens back down to nothing. Progress-based (0.0-1.0 through the storm), not phase-based, so
 * the curve is smooth rather than snapping between discrete states.
 *
 * Rain and thunder during a hurricane are REAL vanilla weather (via WeatherCycleManager#forceState
 * when that manager exists, or direct World#setStorm/setThundering calls on every NORMAL world if
 * weather.enabled is off and there's no WeatherCycleManager to delegate to) -- not a particle
 * simulation, same honest-API spirit as DryThunderstormManager.
 */
public class HurricaneManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final WindManager wind;
    private final WeatherCycleManager weatherCycle; // nullable -- see applyWeather()
    private final Random random = new Random();
    private final CoastalFloodEngine surge = new CoastalFloodEngine();

    private boolean active = false;
    private long elapsedTicks;
    private long totalTicks;
    private long ticksUntilNaturalCheck;
    private long ticksUntilNextSurgePulse;
    private long ticksUntilNextThunderRoll;
    private boolean currentlyThundering;
    private BukkitTask task;

    public HurricaneManager(JavaPlugin plugin, SeasonsConfig config, WindManager wind, WeatherCycleManager weatherCycle) {
        this.plugin = plugin;
        this.config = config;
        this.wind = wind;
        this.weatherCycle = weatherCycle;
    }

    public void start() {
        if (!config.hurricaneEnabled()) return;
        surge.setSpawnProtection(config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks());
        ticksUntilNaturalCheck = checkIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (active) restoreVanillaWeatherControl();
    }

    public boolean isActive() {
        return active;
    }

    public void forceStart(int durationMinutes) {
        active = true;
        elapsedTicks = 0;
        totalTicks = 20L * 60L * durationMinutes;
        ticksUntilNextSurgePulse = 20L * config.hurricaneSurgeIntervalSeconds();
        ticksUntilNextThunderRoll = 0;
        currentlyThundering = false;
        WeatherAnnouncer.announceGlobal(config, "hurricane-start", "§9§lStorm warning: §fa hurricane is bearing down on the coast!");
    }

    public void forceStop() {
        boolean wasActive = active;
        active = false;
        restoreVanillaWeatherControl();
        if (wasActive) {
            WeatherAnnouncer.announceGlobal(config, "hurricane-end", "§9The hurricane has passed.");
        }
    }

    private void tick() {
        if (!active) {
            ticksUntilNaturalCheck--;
            if (ticksUntilNaturalCheck <= 0) {
                ticksUntilNaturalCheck = checkIntervalTicks();
                if (random.nextDouble() < config.hurricaneNaturalChance()) {
                    int minutes = randomBetween(config.hurricaneDurationMinMinutes(), config.hurricaneDurationMaxMinutes());
                    forceStart(minutes);
                }
            }
            if (surge.isActive()) {
                boolean finished = surge.tick(); // let a pulse that was still draining when the storm ended finish naturally
                if (finished && config.waterLevelingEnabled() && surge.coastAnchor() != null) {
                    WaterLevelingEngine.levelArea(surge.coastAnchor(), config.waterLevelingRadius(), config.waterLevelingMaxBlocksPerPass(), config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks());
                }
            }
            return;
        }

        elapsedTicks++;
        double progress = totalTicks > 0 ? (double) elapsedTicks / totalTicks : 1.0;
        if (progress >= 1.0) {
            forceStop();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("§7The hurricane has passed.");
            }
            return;
        }

        double intensity = intensityAt(progress);
        applyWind(intensity);
        applyWeather(intensity);
        if (config.hurricaneStormSurgeEnabled()) {
            tickStormSurge(intensity);
        }
    }

    /** 0.0-1.0 intensity curve: ramps up, holds at peak, optionally dips for a calm eye around the midpoint, holds at peak again, ramps back down. */
    private double intensityAt(double progress) {
        double ramp = config.hurricaneRampFraction();
        if (progress < ramp) return progress / ramp;
        if (progress > 1.0 - ramp) return (1.0 - progress) / ramp;

        if (config.hurricaneEyeEnabled()) {
            double eyeCenter = 0.5;
            double eyeHalfWidth = config.hurricaneEyeWidthFraction() / 2.0;
            double distanceFromEye = Math.abs(progress - eyeCenter);
            if (distanceFromEye < eyeHalfWidth) {
                double eyeIntensity = config.hurricaneEyeIntensity();
                double t = distanceFromEye / eyeHalfWidth; // 0 at dead center, 1 at the eye's edge
                return eyeIntensity + (1.0 - eyeIntensity) * (1.0 - t);
            }
        }
        return 1.0;
    }

    private void applyWind(double intensity) {
        if (wind == null) return;
        double minStrength = config.hurricaneMinWindStrength() + intensity * (1.0 - config.hurricaneMinWindStrength());
        wind.forceSeverity(minStrength, 40L); // refreshed every tick this method runs, so it decays naturally within ~2 seconds if the hurricane ends abruptly
    }

    private void applyWeather(double intensity) {
        boolean stormy = intensity >= config.hurricaneRainThreshold();
        boolean thunderEligible = stormy && intensity >= config.hurricaneThunderThreshold();

        // re-decide thundering only periodically (roughly every 5 seconds) rather than every
        // tick -- rolling fresh every tick would make thunder flicker on and off almost
        // instantly instead of holding for a real stretch the way actual thunder does
        ticksUntilNextThunderRoll--;
        if (ticksUntilNextThunderRoll <= 0) {
            ticksUntilNextThunderRoll = 100L;
            currentlyThundering = thunderEligible && random.nextDouble() < config.hurricaneThunderChancePerTick();
        }
        boolean thundering = thunderEligible && currentlyThundering;

        if (weatherCycle != null) {
            weatherCycle.forceState(stormy, thundering, 40L);
            return;
        }

        // no WeatherCycleManager running (weather.enabled is off) -- take direct control ourselves for the duration
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            if (world.hasStorm() != stormy) world.setStorm(stormy);
            if (world.isThundering() != thundering) world.setThundering(thundering);
        }
    }

    /** Storm surge is a gentler, sustained version of a tsunami -- short pulses that advance a little, recede, and try again periodically, rather than one single dramatic wave. Silently does nothing if no coastline is found nearby, since a hurricane happening entirely inland shouldn't spam failed-search noise. */
    private void tickStormSurge(double intensity) {
        if (surge.isActive()) {
            boolean finished = surge.tick();
            if (finished && config.waterLevelingEnabled() && surge.coastAnchor() != null) {
                WaterLevelingEngine.levelArea(surge.coastAnchor(), config.waterLevelingRadius(), config.waterLevelingMaxBlocksPerPass(), config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks());
            }
            return;
        }

        ticksUntilNextSurgePulse--;
        if (ticksUntilNextSurgePulse > 0) return;
        ticksUntilNextSurgePulse = 20L * config.hurricaneSurgeIntervalSeconds();
        if (intensity < config.hurricaneRainThreshold()) return; // no surge pulses during the calm eye

        var players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .toList();
        if (players.isEmpty()) return;
        Player anchor = players.get(random.nextInt(players.size()));
        // same fix as TsunamiManager -- prefer the water's own actual local surface over blindly
        // assuming World#getSeaLevel(), which silently fails to find a coastline at all if the
        // ocean here doesn't happen to sit exactly at the world's configured sea level
        var nearbyWater = WaterBodyDetector.findNearbyWater(anchor.getLocation(), config.wavesWaterSearchRadius());
        int waterSurfaceY = nearbyWater != null ? nearbyWater.getBlockY() : anchor.getWorld().getSeaLevel();
        if (!surge.findCoastNear(anchor.getLocation(), config.hurricaneSurgeCoastSearchRadius(), waterSurfaceY)) return;

        surge.start(config.hurricaneSurgeMaxInlandDistance() * intensity, config.hurricaneSurgeAdvanceSpeed(),
                config.hurricaneSurgeFrontWidth(), config.hurricaneSurgeWaveHeight() * intensity,
                config.hurricaneSurgeKnockbackStrength(), config.hurricaneSurgeMaxAffectedBlocks());
    }

    private void restoreVanillaWeatherControl() {
        if (weatherCycle != null) return; // WeatherCycleManager owns handing gamerules back on its own stop(); nothing to restore here
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        }
    }

    private long checkIntervalTicks() {
        return 20L * 60L * config.hurricaneCheckIntervalMinutes();
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return Math.max(1, min);
        return min + random.nextInt(max - min + 1);
    }
}
