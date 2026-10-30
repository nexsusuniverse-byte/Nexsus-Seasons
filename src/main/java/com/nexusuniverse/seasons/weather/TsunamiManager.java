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
 * A single, dramatic coastal surge -- built on CoastalFloodEngine (see its doc comment for the
 * honest explanation of how the flooding actually works). Gives a real warning (message + sound,
 * config-driven lead time) before the wave actually starts advancing -- a tsunami with zero
 * warning isn't much of one; the dread of watching the tide pull out first is the point.
 *
 * Tracks "pending" (warned, counting down, not yet actually flooding) separately from the
 * engine's own "active" state (actually flooding/receding) -- isActive() covers both, so a second
 * trigger during the warning countdown can't stack a duplicate tsunami on top of the first one.
 */
public class TsunamiManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final Random random = new Random();
    private final CoastalFloodEngine engine = new CoastalFloodEngine();

    private boolean pending = false;
    private BukkitTask pendingLaunchTask;
    private long ticksUntilNaturalCheck;
    private BukkitTask task;

    public TsunamiManager(JavaPlugin plugin, SeasonsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.tsunamiEnabled()) return;
        engine.setSpawnProtection(config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks());
        ticksUntilNaturalCheck = checkIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (pendingLaunchTask != null) pendingLaunchTask.cancel();
    }

    public boolean isActive() {
        return pending || engine.isActive();
    }

    /** /nexusseasons tsunami spawn -- looks for a coastline near the given location and, if found, starts the warning countdown. Returns false if no coastline was found nearby, or one is already in progress. */
    public boolean spawnNear(Location location) {
        if (isActive()) return false;

        // Prefer the water's own ACTUAL local surface height over blindly assuming
        // World#getSeaLevel() -- if the ocean here doesn't happen to sit exactly at the world's
        // configured sea level (very possible on custom/heavily-built terrain), the old
        // sea-level-only check would search for water at a Y that's never actually wet anywhere
        // nearby, and silently fail to find a real coastline even standing in open water. Falls
        // back to sea level only if no water at all was found nearby first.
        var nearbyWater = WaterBodyDetector.findNearbyWater(location, config.wavesWaterSearchRadius());
        int waterSurfaceY = nearbyWater != null ? nearbyWater.getBlockY() : location.getWorld().getSeaLevel();

        if (!engine.findCoastNear(location, config.tsunamiCoastSearchRadius(), waterSurfaceY)) return false;

        pending = true;
        warnAndSchedule(location.getWorld());
        return true;
    }

    /** Cancels a pending warning outright, or fast-forwards an already-flooding tsunami straight to recession. */
    public void stopActive() {
        if (pendingLaunchTask != null) {
            pendingLaunchTask.cancel();
            pendingLaunchTask = null;
        }
        pending = false;
        engine.forceRecede();
    }

    private void tick() {
        if (!isActive()) {
            ticksUntilNaturalCheck--;
            if (ticksUntilNaturalCheck <= 0) {
                ticksUntilNaturalCheck = checkIntervalTicks();
                if (random.nextDouble() < config.tsunamiNaturalChance()) {
                    trySpawnNearRandomPlayer();
                }
            }
            return;
        }
        if (engine.isActive()) {
            boolean finished = engine.tick();
            if (finished && config.waterLevelingEnabled() && engine.coastAnchor() != null) {
                WaterLevelingEngine.levelArea(engine.coastAnchor(), config.waterLevelingRadius(), config.waterLevelingMaxBlocksPerPass(), config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks());
            }
        } // no-op during the pending warning countdown -- the engine hasn't actually started yet
    }

    private void trySpawnNearRandomPlayer() {
        var players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .toList();
        if (players.isEmpty()) return;
        Player anchor = players.get(random.nextInt(players.size()));
        spawnNear(anchor.getLocation());
    }

    private void warnAndSchedule(World world) {
        int warningSeconds = config.tsunamiWarningSeconds();
        for (Player player : world.getPlayers()) {
            player.sendMessage("§4§lThe tide pulls back strangely far... §c(a tsunami is coming)");
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 2.0f, 0.4f);
        }
        pendingLaunchTask = Bukkit.getScheduler().runTaskLater(plugin, () -> launch(world), 20L * warningSeconds);
    }

    private void launch(World world) {
        pending = false;
        pendingLaunchTask = null;
        for (Player player : world.getPlayers()) {
            player.sendMessage("§4§lTSUNAMI!");
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.3f);
        }
        engine.start(config.tsunamiMaxInlandDistance(), config.tsunamiAdvanceSpeed(), config.tsunamiFrontWidth(),
                config.tsunamiWaveHeight(), config.tsunamiKnockbackStrength(), config.tsunamiMaxAffectedBlocks());
    }

    private long checkIntervalTicks() {
        return 20L * 60L * config.tsunamiCheckIntervalMinutes();
    }
}
