package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * BlizzardManager's structural twin, sand instead of snow -- a localized effect around each online
 * player rather than a world/biome state change, so it works anywhere, same idea as blizzard/fog.
 * There's no dedicated vanilla "blowing sand" particle, so the haze itself is tinted DUST particles
 * (a genuine sandy color, not just white) blown sideways in the current wind direction via the
 * spawnParticle(count=0, offsets-as-velocity) trick blizzard already uses for its snow, mixed with
 * real FALLING_DUST carrying actual SAND block data for visible grit texture beyond flat color.
 *
 * Applies Blindness (thick enough up close to genuinely impair vision, which blizzard doesn't do)
 * on top of Slowness, and can force wind severity up for its duration -- same WindManager
 * integration blizzard already has.
 */
public class SandstormManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final WindManager wind;
    private final Random random = new Random();

    private boolean active = false;
    private long ticksUntilNaturalCheck;
    private BukkitTask task;

    public SandstormManager(JavaPlugin plugin, SeasonsConfig config, WindManager wind) {
        this.plugin = plugin;
        this.config = config;
        this.wind = wind;
    }

    public void start() {
        if (!config.sandstormEnabled()) return;
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
        if (wind != null && config.sandstormForceWind()) wind.forceSeverity(config.windSevereThreshold(), ticks);
        Bukkit.getScheduler().runTaskLater(plugin, this::forceStop, ticks);
        WeatherAnnouncer.announceGlobal(config, "sandstorm-start", "§6§lA wall of dust rises on the horizon... §fa sandstorm approaches!");
    }

    public void forceStop() {
        boolean wasActive = active;
        active = false;
        if (wasActive) {
            WeatherAnnouncer.announceGlobal(config, "sandstorm-end", "§6The sandstorm settles.");
        }
    }

    private void tick() {
        if (!active) {
            ticksUntilNaturalCheck--;
            if (ticksUntilNaturalCheck <= 0) {
                ticksUntilNaturalCheck = checkIntervalTicks();
                if (random.nextDouble() < config.sandstormNaturalChance()) {
                    int duration = randomBetween(config.sandstormDurationMinSeconds(), config.sandstormDurationMaxSeconds());
                    forceStart(duration);
                }
            }
            return;
        }

        Vector blowDirection = wind != null ? wind.currentDirection() : new Vector(1, 0, 0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnSandAround(player, blowDirection);
            if (config.sandstormApplyBlindness()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, true));
            }
            if (config.sandstormApplySlowness()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, config.sandstormSlownessAmplifier(), true, true));
            }
        }
    }

    private void spawnSandAround(Player player, Vector blowDirection) {
        Location eye = player.getEyeLocation();
        double radius = config.sandstormRadius();
        Particle.DustOptions options = new Particle.DustOptions(Color.fromRGB(194, 165, 100), 2.5f);

        for (int i = 0; i < config.sandstormDensity(); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * radius;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            double dy = (random.nextDouble() - 0.3) * 2.0;

            Location point = eye.clone().add(dx, dy, dz);
            player.getWorld().spawnParticle(Particle.DUST, point, 0,
                    blowDirection.getX() * 0.35, -0.02, blowDirection.getZ() * 0.35, 0.1, options);
        }

        // real falling sand mixed in for grit texture beyond flat colored dust
        player.getWorld().spawnParticle(Particle.FALLING_DUST, eye.clone().add(0, 0.5, 0), 4,
                radius * 0.4, 1.0, radius * 0.4, 0.0, Material.SAND.createBlockData());
    }

    private long checkIntervalTicks() {
        return 20L * 60L * config.sandstormCheckIntervalMinutes();
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return Math.max(1, min);
        return min + random.nextInt(max - min + 1);
    }
}
