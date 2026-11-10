package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * The one effect in this whole batch that's a completely genuine, unsimulated use of the real
 * API -- not a particle illusion standing in for something Bukkit can't actually do. Never calls
 * World#setStorm(true), so the sky stays clear and it never actually rains. Instead, on a random
 * interval, it calls World#strikeLightningEffect() (the VISUAL-ONLY variant -- no block damage,
 * no fire, no entity damage, unlike strikeLightning()) near a random online player, plus the
 * thunder sound. A real dry thunderstorm, exactly as asked for.
 */
public class DryThunderstormManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final Random random = new Random();

    private boolean active = false;
    private long ticksUntilNextStrike;
    private long ticksUntilNaturalCheck;
    private BukkitTask task;

    public DryThunderstormManager(JavaPlugin plugin, SeasonsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.dryThunderEnabled()) return;
        ticksUntilNaturalCheck = checkIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public boolean isActive() {
        return active;
    }

    /** /nexusseasons drythunder start [seconds] -- forces it on regardless of the natural-chance roll. */
    public void forceStart(int durationSeconds) {
        active = true;
        scheduleNextStrike();
        Bukkit.getScheduler().runTaskLater(plugin, this::forceStop, 20L * durationSeconds);
        WeatherAnnouncer.announceGlobal(config, "thunderstorm-start", "§7Dark clouds gather on the horizon... §fa thunderstorm is rolling in.");
    }

    public void forceStop() {
        boolean wasActive = active;
        active = false;
        if (wasActive) {
            WeatherAnnouncer.announceGlobal(config, "thunderstorm-end", "§7The thunder fades into the distance.");
        }
    }

    private void tick() {
        if (!active) {
            ticksUntilNaturalCheck--;
            if (ticksUntilNaturalCheck <= 0) {
                ticksUntilNaturalCheck = checkIntervalTicks();
                if (random.nextDouble() < config.dryThunderNaturalChance()) {
                    int duration = randomBetween(config.dryThunderDurationMinSeconds(), config.dryThunderDurationMaxSeconds());
                    forceStart(duration);
                }
            }
            return;
        }

        ticksUntilNextStrike--;
        if (ticksUntilNextStrike <= 0) {
            strikeNearRandomPlayer();
            scheduleNextStrike();
        }
    }

    private void scheduleNextStrike() {
        int seconds = randomBetween(config.dryThunderStrikeMinIntervalSeconds(), config.dryThunderStrikeMaxIntervalSeconds());
        ticksUntilNextStrike = 20L * seconds;
    }

    private void strikeNearRandomPlayer() {
        var players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .toList();
        if (players.isEmpty()) return;

        Player player = players.get(random.nextInt(players.size()));
        int radius = config.dryThunderStrikeRadius();
        Location center = player.getLocation();
        int x = center.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
        int z = center.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
        World world = center.getWorld();
        Location strikeLocation = world.getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);

        world.strikeLightningEffect(strikeLocation); // visual/audio only -- no damage, no fire, no block changes
        if (config.dryThunderPlaySound()) {
            for (Player nearby : players) {
                if (nearby.getLocation().distanceSquared(strikeLocation) <= 200.0 * 200.0) {
                    nearby.playSound(nearby.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 1.0f);
                }
            }
        }
    }

    private long checkIntervalTicks() {
        return 20L * 60L * config.dryThunderCheckIntervalMinutes();
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return Math.max(1, min);
        return min + random.nextInt(max - min + 1);
    }
}
