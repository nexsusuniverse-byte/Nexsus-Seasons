package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * A localized tremor around every online player -- real small, rapid velocity jitters (a
 * screen-shake stand-in, since a plugin can't move the client's actual camera), ground-crumble
 * particles pulled from whatever block is actually beneath each player (so the dust always
 * matches the terrain -- stone areas kick up stone dust, grass kicks up dirt, etc., via
 * Particle.BLOCK using that block's own real BlockData), a low rumble sound, and a chance
 * of shaking loose fragile blocks nearby (reuses wind.fragile-materials and the same
 * loosely-supported heuristic WindManager/TornadoManager already use, just triggered by tremor
 * pulses instead of wind).
 *
 * Intensity ramps in over the first couple seconds and fades out over the last couple, rather than
 * snapping instantly to full strength and cutting off -- reads as a real quake building up and
 * subsiding instead of an on/off switch.
 *
 * A quake has a real chance of a smaller AFTERSHOCK a short while after the main one ends --
 * config earthquake.aftershock-chance/aftershock-delay-*-seconds/aftershock-magnitude-multiplier.
 * Applies to any quake ending, natural or admin-triggered.
 */
public class EarthquakeManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final Random random = new Random();

    private boolean active = false;
    private double magnitude = 1.0; // 0..1, includes the ramp in/out envelope and any aftershock scale-down
    private double magnitudeScale = 1.0;
    private long remainingTicks;
    private long totalTicks;
    private long ticksUntilNaturalCheck;
    private BukkitTask task;

    public EarthquakeManager(JavaPlugin plugin, SeasonsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.earthquakeEnabled()) return;
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
        startInternal(durationSeconds, 1.0);
    }

    public void forceStop() {
        boolean wasActive = active;
        active = false;
        if (wasActive) {
            WeatherAnnouncer.announceGlobal(config, "earthquake-end", "§7The tremors have stopped.");
        }
    }

    private void startInternal(int durationSeconds, double magnitudeScale) {
        active = true;
        totalTicks = Math.max(1L, 20L * durationSeconds);
        remainingTicks = totalTicks;
        this.magnitudeScale = magnitudeScale;
        // aftershocks (magnitudeScale < 1.0) get their own distinct, quieter message rather than
        // reusing the full-quake one -- a smaller aftershock genuinely reads differently
        if (magnitudeScale >= 1.0) {
            WeatherAnnouncer.announceGlobal(config, "earthquake-start", "§4§lThe ground begins to shake violently!");
        } else {
            WeatherAnnouncer.announceGlobal(config, "earthquake-aftershock", "§4A smaller aftershock rumbles through...");
        }
    }

    private void tick() {
        if (!active) {
            ticksUntilNaturalCheck--;
            if (ticksUntilNaturalCheck <= 0) {
                ticksUntilNaturalCheck = checkIntervalTicks();
                if (random.nextDouble() < config.earthquakeNaturalChance()) {
                    int duration = randomBetween(config.earthquakeDurationMinSeconds(), config.earthquakeDurationMaxSeconds());
                    startInternal(duration, 1.0);
                }
            }
            return;
        }

        remainingTicks--;
        long elapsed = totalTicks - remainingTicks;
        double rampTicks = 40.0; // ~2 real seconds
        double envelope = (elapsed < rampTicks) ? elapsed / rampTicks
                : (remainingTicks < rampTicks) ? remainingTicks / rampTicks
                : 1.0;
        magnitude = Math.max(0.0, Math.min(1.0, envelope)) * magnitudeScale;

        if (remainingTicks <= 0) {
            active = false;
            WeatherAnnouncer.announceGlobal(config, "earthquake-end", "§7The tremors have stopped.");
            maybeScheduleAftershock();
            return;
        }

        if (random.nextDouble() < config.earthquakePulseChancePerTick() * magnitude) {
            pulse();
        }
    }

    private void maybeScheduleAftershock() {
        if (random.nextDouble() >= config.earthquakeAftershockChance()) return;
        int delaySeconds = randomBetween(config.earthquakeAftershockDelayMinSeconds(), config.earthquakeAftershockDelayMaxSeconds());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int duration = Math.max(5, randomBetween(config.earthquakeDurationMinSeconds(), config.earthquakeDurationMaxSeconds()) / 2);
            startInternal(duration, config.earthquakeAftershockMagnitudeMultiplier());
        }, 20L * delaySeconds);
    }

    private void pulse() {
        double shake = config.earthquakeShakeStrength() * magnitude;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;
            if (player.isFlying() || player.isInsideVehicle()) continue;

            // small rapid random jitter -- a screen-shake stand-in, since a plugin has no way to move the client's actual camera
            Vector jitter = new Vector((random.nextDouble() - 0.5) * shake, 0, (random.nextDouble() - 0.5) * shake);
            player.setVelocity(player.getVelocity().add(jitter));

            spawnGroundCrumble(player);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.AMBIENT,
                    0.4f * (float) magnitude, 0.5f);
        }

        if (config.earthquakeDestroyFragileBlocks()) {
            maybeDislodgeBlock();
        }
    }

    private void spawnGroundCrumble(Player player) {
        Location loc = player.getLocation();
        Block ground = loc.getBlock().getRelative(BlockFace.DOWN);
        if (ground.getType().isAir()) return;

        World world = loc.getWorld();
        for (int i = 0; i < 6; i++) {
            double dx = (random.nextDouble() - 0.5) * 1.5;
            double dz = (random.nextDouble() - 0.5) * 1.5;
            Location point = loc.clone().add(dx, 0.1, dz);
            world.spawnParticle(Particle.BLOCK, point, 3, 0.2, 0.1, 0.2, 0.0, ground.getBlockData());
        }
    }

    private void maybeDislodgeBlock() {
        if (random.nextDouble() >= config.earthquakeDislodgeChancePerTick() * magnitude) return;

        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) return;
        Player anchor = players.stream().skip(random.nextInt(players.size())).findFirst().orElse(null);
        if (anchor == null || anchor.getWorld().getEnvironment() != World.Environment.NORMAL) return;

        int radius = (int) config.earthquakeRadius();
        Location center = anchor.getLocation();
        int x = center.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
        int z = center.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
        Block block = center.getWorld().getHighestBlockAt(x, z).getRelative(BlockFace.DOWN);

        if (!config.windFragileMaterials().contains(block.getType())) return;
        if (!isLooselySupported(block)) return;
        if (SpawnProtection.isProtected(block.getWorld(), x, z, config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks())) return;

        blowLoose(block);
    }

    /** Cheap heuristic, not real structural analysis: fewer than 2 solid horizontal neighbors counts as "sitting by itself." Same rule WindManager uses. */
    private boolean isLooselySupported(Block block) {
        int solidNeighbors = 0;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (block.getRelative(face).getType().isSolid()) solidNeighbors++;
        }
        return solidNeighbors < 2;
    }

    private void blowLoose(Block block) {
        ItemStack drop = new ItemStack(block.getType());
        Location origin = block.getLocation().add(0.5, 0.5, 0.5);
        block.setType(Material.AIR);

        Item item = block.getWorld().dropItem(origin, drop);
        item.setPickupDelay(100);
        item.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.3, 0.3 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3));
    }

    private long checkIntervalTicks() {
        return 20L * 60L * config.earthquakeCheckIntervalMinutes();
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return Math.max(1, min);
        return min + random.nextInt(max - min + 1);
    }
}
