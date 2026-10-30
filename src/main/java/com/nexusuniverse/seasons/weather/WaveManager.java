package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Continuous, always-on ambient effect (like WindManager, not a start/stop event) -- for anyone
 * standing at or in a body of water big enough to count (waves.min-body-size, default 40x40 --
 * same threshold and same WaterBodyDetector the shore-break waves use, so a lake either qualifies
 * for both systems or neither), spawns rhythmic splash/foam particles and gives swimmers a gentle
 * push, both driven by a slow sine wave over real time so it reads as actual swell rather than
 * random noise -- including well out in the middle of a large body, not just near its edge.
 * "Different types of waves" comes from tying amplitude and frequency directly to WindManager's
 * current strength: calm wind gives small, slow, gentle waves that still happen (there's always a
 * non-zero base amplitude/frequency even at zero wind, so water never goes fully still); strong
 * wind gives bigger, faster, rougher ones -- the same wind that pushes players on land is what's
 * stirring up the water they're standing in, rather than two unrelated systems.
 *
 * The actual body-size check (a bounded flood fill, see WaterBodyDetector) is too expensive to run
 * on every tick for every online player, so each player's eligibility is cached for
 * waves.eligibility-cache-seconds and only recomputed once that expires -- the cheap "is there
 * even water within reach" first check still happens every tick, so someone who just left the
 * water stops getting the effect immediately rather than waiting out a stale cache entry.
 */
public class WaveManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final WindManager wind;
    private final Random random = new Random();
    private final Map<UUID, Boolean> eligibilityCache = new HashMap<>();
    private final Map<UUID, Long> eligibilityCacheExpiry = new HashMap<>();

    private long clockTicks = 0;
    private BukkitTask task;

    public WaveManager(JavaPlugin plugin, SeasonsConfig config, WindManager wind) {
        this.plugin = plugin;
        this.config = config;
        this.wind = wind;
    }

    public void start() {
        if (!config.wavesEnabled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 2L); // every other tick -- ambient dressing doesn't need per-tick precision
    }

    public void stop() {
        if (task != null) task.cancel();
        eligibilityCache.clear();
        eligibilityCacheExpiry.clear();
    }

    private void tick() {
        clockTicks += 2;
        double windStrength = wind != null ? wind.currentStrength() : 0.2;
        // amplitude/frequency both scale with wind -- rougher wind means bigger, faster swell
        double amplitude = config.wavesBaseAmplitude() + windStrength * config.wavesWindAmplitudeMultiplier();
        double frequency = config.wavesBaseFrequency() + windStrength * config.wavesWindFrequencyMultiplier();
        double phase = clockTicks * frequency;
        double swell = Math.sin(phase) * amplitude;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;
            Location feet = player.getLocation();
            Location eye = player.getEyeLocation();
            Location waterSeed = feet.getBlock().getType() == Material.WATER ? feet
                    : eye.getBlock().getType() == Material.WATER ? eye : null;
            if (waterSeed == null) continue;
            if (!isEligible(player, waterSeed)) continue;

            spawnFoam(player, swell, windStrength);
            if (config.wavesPushSwimmers()) {
                pushSwimmer(player, swell, windStrength);
            }
        }
    }

    /** Cached per-player -- see class doc for why. waterSeed must actually be a WATER block. */
    private boolean isEligible(Player player, Location waterSeed) {
        UUID id = player.getUniqueId();
        Long expiry = eligibilityCacheExpiry.get(id);
        if (expiry != null && clockTicks < expiry) {
            return eligibilityCache.getOrDefault(id, false);
        }

        boolean eligible = WaterBodyDetector.isLargeBody(waterSeed, config.wavesMinBodySize(), config.wavesBodyDetectionMaxBlocks());
        eligibilityCache.put(id, eligible);
        eligibilityCacheExpiry.put(id, clockTicks + 20L * config.wavesEligibilityCacheSeconds());
        return eligible;
    }

    private void spawnFoam(Player player, double swell, double windStrength) {
        if (random.nextDouble() >= 0.15 + windStrength * 0.3) return; // rougher water throws up foam more often

        Location surface = player.getLocation().clone();
        surface.setY(player.getLocation().getBlockY() + 1 + swell * 0.3); // the water's OWN local surface, not a hardcoded sea level -- works the same in a mountain lake as the ocean
        Color foam = Color.fromRGB(230, 230, 235);
        player.getWorld().spawnParticle(Particle.DUST, surface, 4, 1.2, 0.15, 1.2, new Particle.DustOptions(foam, 1.5f));
        if (windStrength > 0.5) {
            player.getWorld().spawnParticle(Particle.BUBBLE_POP, surface, 2, 1.0, 0.1, 1.0, 0.02);
        }
    }

    private void pushSwimmer(Player player, double swell, double windStrength) {
        if (windStrength < config.wavesPushMinWindStrength()) return;
        Vector direction = wind != null ? wind.currentDirection() : new Vector(1, 0, 0);
        Vector push = direction.clone().multiply(windStrength * config.wavesPushMultiplier());
        push.setY(Math.max(-0.05, swell * 0.02)); // bob up and down slightly with the swell, never pulled hard downward
        player.setVelocity(player.getVelocity().add(push));
    }
}
