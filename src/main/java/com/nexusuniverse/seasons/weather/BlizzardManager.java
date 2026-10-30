package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * A blizzard is treated as a localized effect around each online player, not a change to the
 * world's actual weather/biome state -- it works anywhere, snowy biome or not, same idea as
 * FogManager. It leans on WindManager for two things: it forces wind severity up for its own
 * duration (blizzard.force-wind, via WindManager#forceSeverity -- a blizzard should feel windier
 * than an ordinary day), and it reads WindManager's current direction so the snow particles blow
 * sideways in a direction that actually matches what's pushing the player around, rather than
 * just falling straight down like normal snowfall.
 */
public class BlizzardManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final WindManager wind;
    private final Random random = new Random();

    private boolean active = false;
    private long ticksUntilNaturalCheck;
    private BukkitTask task;

    public BlizzardManager(JavaPlugin plugin, SeasonsConfig config, WindManager wind) {
        this.plugin = plugin;
        this.config = config;
        this.wind = wind;
    }

    public void start() {
        if (!config.blizzardEnabled()) return;
        ticksUntilNaturalCheck = checkIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public boolean isActive() {
        return active;
    }

    public void forceStart(int durationSeconds) {
        active = true;
        long ticks = 20L * durationSeconds;
        if (wind != null && config.blizzardForceWind()) wind.forceSeverity(config.windSevereThreshold(), ticks);
        Bukkit.getScheduler().runTaskLater(plugin, this::forceStop, ticks);
        WeatherAnnouncer.announceGlobal(config, "blizzard-start", "§b§lA bitter wind picks up... §fa blizzard is moving in!");
    }

    public void forceStop() {
        boolean wasActive = active;
        active = false;
        if (wasActive) {
            WeatherAnnouncer.announceGlobal(config, "blizzard-end", "§bThe blizzard finally breaks.");
        }
    }

    private void tick() {
        if (!active) {
            ticksUntilNaturalCheck--;
            if (ticksUntilNaturalCheck <= 0) {
                ticksUntilNaturalCheck = checkIntervalTicks();
                if (random.nextDouble() < config.blizzardNaturalChance()) {
                    int duration = randomBetween(config.blizzardDurationMinSeconds(), config.blizzardDurationMaxSeconds());
                    forceStart(duration);
                }
            }
            return;
        }

        Vector blowDirection = wind != null ? wind.currentDirection() : new Vector(1, 0, 0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnSnowAround(player, blowDirection);
            if (config.blizzardApplySlowness()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, config.blizzardSlownessAmplifier(), true, true));
            }
        }
    }

    private void spawnSnowAround(Player player, Vector blowDirection) {
        Location eye = player.getEyeLocation();
        double radius = config.blizzardRadius();

        for (int i = 0; i < config.blizzardDensity(); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * radius;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            double dy = (random.nextDouble() - 0.2) * 2.5;

            Location point = eye.clone().add(dx, dy, dz);
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, point, 0,
                    blowDirection.getX() * 0.3, -0.05, blowDirection.getZ() * 0.3, 0.15);
        }

        // a touch of white smoke mixed in thickens the whiteout beyond what snowflakes alone read as
        player.getWorld().spawnParticle(Particle.WHITE_SMOKE, eye.clone().add(0, 0.5, 0), 3,
                radius * 0.4, 1.0, radius * 0.4, 0.01);
    }

    private long checkIntervalTicks() {
        return 20L * 60L * config.blizzardCheckIntervalMinutes();
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return Math.max(1, min);
        return min + random.nextInt(max - min + 1);
    }
}
