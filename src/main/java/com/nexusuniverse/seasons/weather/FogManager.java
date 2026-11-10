package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * IMPORTANT HONEST LIMIT: this is NOT real fog. There is no plugin-only way to shrink a player's
 * actual render/fog distance or add genuine atmospheric fog to the world -- that's a client-side
 * rendering effect that normally needs either a resource pack (explicitly ruled out) or fog
 * packets sent through something like ProtocolLib (which this server does have installed, per its
 * plugin list, but wiring that up is real extra scope beyond a config option here -- flagged as a
 * possible follow-up, not attempted in this version).
 *
 * What this DOES do instead: surrounds each affected player with a dense, slowly-drifting cloud
 * of white smoke-style particles at head height, spawned in a ring around them every tick while
 * active. It genuinely does obscure vision up close (you can't see much past a few blocks through
 * a wall of particles) and reads as "foggy," but it's a moving haze centered on the player, not an
 * actual change to how far the world renders -- a player standing still and looking hard past the
 * particle cloud can still see the same distance they normally would.
 */
public class FogManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final Random random = new Random();

    private boolean active = false;
    private long ticksUntilNaturalCheck;
    private BukkitTask task;

    public FogManager(JavaPlugin plugin, SeasonsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.fogEnabled()) return;
        ticksUntilNaturalCheck = checkIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 2L); // every other tick -- particle density doesn't need every-tick precision
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public boolean isActive() {
        return active;
    }

    public void forceStart(int durationSeconds) {
        active = true;
        Bukkit.getScheduler().runTaskLater(plugin, this::forceStop, 20L * durationSeconds);
        WeatherAnnouncer.announceGlobal(config, "fog-start", "§7A thick fog rolls in, swallowing the horizon.");
    }

    public void forceStop() {
        boolean wasActive = active;
        active = false;
        if (wasActive) {
            WeatherAnnouncer.announceGlobal(config, "fog-end", "§7The fog begins to lift.");
        }
    }

    private void tick() {
        if (!active) {
            ticksUntilNaturalCheck -= 2;
            if (ticksUntilNaturalCheck <= 0) {
                ticksUntilNaturalCheck = checkIntervalTicks();
                if (random.nextDouble() < config.fogNaturalChance()) {
                    int duration = randomBetween(config.fogDurationMinSeconds(), config.fogDurationMaxSeconds());
                    forceStart(duration);
                }
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnFogAround(player);
        }
    }

    private void spawnFogAround(Player player) {
        Location eye = player.getEyeLocation();
        double radius = config.fogRadius();
        Particle.DustOptions options = new Particle.DustOptions(config.fogColor(), 3.0f);

        for (int i = 0; i < config.fogDensity(); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * radius;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            double dy = (random.nextDouble() - 0.3) * 2.0;

            Location point = eye.clone().add(dx, dy, dz);
            player.getWorld().spawnParticle(Particle.WHITE_SMOKE, point, 1, 0, 0, 0, 0);
            // a little colored dust mixed in reads as thicker/tinted haze, not just plain smoke
            if (random.nextDouble() < 0.3) {
                player.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, options);
            }
        }
    }

    private long checkIntervalTicks() {
        return 20L * 60L * config.fogCheckIntervalMinutes();
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return Math.max(1, min);
        return min + random.nextInt(max - min + 1);
    }
}
