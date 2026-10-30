package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * A continuous, ambient wind field -- not a one-off event like the tornado/blizzard/dry-thunder
 * managers, always running whenever wind.enabled is true. Direction and strength slowly drift
 * (a small random walk every tick) between wind.min-strength and wind.max-strength, with a bigger
 * re-roll every wind.change-interval-minutes so it doesn't feel perfectly smooth and predictable.
 *
 * On top of that steady drift, GUSTS (wind.gust.*) are short, sharper bursts that swing well off
 * the current steady direction -- up to wind.gust.direction-swing-degrees either side of it, which
 * at higher settings can mean a near-reversal -- and briefly boost strength above whatever the
 * steady wind is doing. This is the actual "pushed and pulled all over the place" feel: the slow
 * drift alone reads as wind slowly rotating, but a gust reads as a real sudden shove from a
 * different direction that then passes. Independently toggleable from the steady wind.
 *
 * Three effects, independently toggleable:
 *  - Pushing players: anyone standing with open sky above them (same getHighestBlockYAt technique
 *    NexusSurvival/NexusVitals use for "exposed to the sky") gets a gentle velocity nudge in the
 *    wind direction, scaled by current strength. Below wind.player-push-min-strength, nothing
 *    happens at all -- a light breeze shouldn't shove anyone around. That push is further scaled
 *    by how full the player's inventory is (wind.inventory-weight.*): on the ground, a heavier
 *    load anchors you and REDUCES the push; gliding on an elytra, the opposite -- a heavier load
 *    destabilizes you and INCREASES it, so a fully-loaded glider fights the wind much harder to
 *    hold a line than someone flying empty-handed. Disableable independently via
 *    wind.inventory-weight.enabled, which reverts to the old flat (weight-blind) push for everyone.
 *    Ahead of all of that: anyone wearing the custom windproof boots (see WindproofBoots) is
 *    skipped entirely, on foot or gliding -- no push, no inventory-weight scaling, nothing. This
 *    is the ambient wind's own push only; it also covers a hurricane, since hurricane just spikes
 *    this same wind's strength via forceSeverity() rather than pushing players itself. A tornado's
 *    suction is a separate, independent push (TornadoManager) and is NOT affected by these boots.
 *  - Dislodging fragile blocks: once strength crosses wind.severe-threshold, each tick has a small
 *    chance to pick a random exposed, lightly-supported block (matching wind.fragile-materials --
 *    default wool, fences, leaves, and other "sits by itself" block types) near an online player
 *    and blow it away -- removes the block and spawns a real dropped item with velocity in the
 *    wind direction, so it visibly goes tumbling off rather than just vanishing.
 *  - Gusting (above): a temporary direction/strength spike layered on top of the steady wind.
 *
 * Other event managers (tornado, blizzard, the shore-break waves) read currentDirection()/
 * currentStrength() to keep their own particle drift and push direction visually consistent with
 * whatever the wind is actually doing RIGHT NOW -- since gusts override those two methods
 * directly rather than being a separate signal, every consumer automatically reflects gusts too
 * without needing its own gust-awareness. Managers can also call forceSeverity() to temporarily
 * spike conditions for their own duration (a blizzard should feel windier than an ordinary day,
 * without permanently changing the ambient wind).
 */
public class WindManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final WindproofBoots windproofBoots;
    private final Random random = new Random();

    private double directionRadians;
    private double strength; // 0.0-1.0
    private long ticksUntilReroll;
    private BukkitTask task;

    // an event-driven override (e.g. a blizzard in progress) -- null when nothing is overriding
    private Double overrideStrength;
    private long overrideTicksRemaining;

    // gust state -- see class doc. gustDirectionRadians/gustStrength only mean anything while gusting is true
    private boolean gusting;
    private double gustDirectionRadians;
    private double gustStrength;
    private long gustTicksRemaining;
    private long ticksUntilNextGustRoll;

    public WindManager(JavaPlugin plugin, SeasonsConfig config, WindproofBoots windproofBoots) {
        this.plugin = plugin;
        this.config = config;
        this.windproofBoots = windproofBoots;
    }

    public void start() {
        if (!config.windEnabled()) return;
        rerollDirectionAndStrength();
        ticksUntilNextGustRoll = gustCheckIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /** Current wind direction as a flat (Y=0) unit vector, for other managers to align their own effects with -- reflects an active gust's direction while one is happening. */
    public Vector currentDirection() {
        double angle = gusting ? gustDirectionRadians : directionRadians;
        return new Vector(Math.cos(angle), 0, Math.sin(angle));
    }

    /** 0.0-1.0, including any active override from another event manager and any active gust. */
    public double currentStrength() {
        double base = overrideStrength != null ? overrideStrength : strength;
        return gusting ? Math.max(base, gustStrength) : base;
    }

    /** Temporarily forces wind strength to at least this value for durationTicks, e.g. a blizzard making conditions feel windier than the ambient day. Calling this again just refreshes the duration. */
    public void forceSeverity(double minStrength, long durationTicks) {
        overrideStrength = Math.max(minStrength, strength);
        overrideTicksRemaining = durationTicks;
    }

    private void tick() {
        if (overrideStrength != null) {
            overrideTicksRemaining--;
            if (overrideTicksRemaining <= 0) overrideStrength = null;
        }

        ticksUntilReroll--;
        if (ticksUntilReroll <= 0) {
            rerollDirectionAndStrength();
        } else {
            drift();
        }

        if (config.windGustEnabled()) {
            tickGust();
        }

        double effectiveStrength = currentStrength();
        if (config.windPushPlayers() && effectiveStrength >= config.windPlayerPushMinStrength()) {
            pushPlayers(effectiveStrength);
        }
        if (effectiveStrength >= config.windSevereThreshold()) {
            maybeDislodgeBlock();
        }
    }

    private void tickGust() {
        if (gusting) {
            gustTicksRemaining--;
            if (gustTicksRemaining <= 0) gusting = false;
            return;
        }

        ticksUntilNextGustRoll--;
        if (ticksUntilNextGustRoll > 0) return;
        ticksUntilNextGustRoll = gustCheckIntervalTicks();

        if (random.nextDouble() >= config.windGustChance()) return;
        startGust();
    }

    private void startGust() {
        double swingRadians = Math.toRadians(config.windGustDirectionSwingDegrees());
        gustDirectionRadians = directionRadians + (random.nextDouble() * 2 - 1) * swingRadians;
        gustStrength = Math.min(1.0, strength * config.windGustStrengthMultiplier());
        gustTicksRemaining = (long) (20L * randomBetween(config.windGustDurationMinSeconds(), config.windGustDurationMaxSeconds()));
        gusting = true;
    }

    private long gustCheckIntervalTicks() {
        return 20L * config.windGustCheckIntervalSeconds();
    }

    private void rerollDirectionAndStrength() {
        directionRadians = random.nextDouble() * Math.PI * 2;
        strength = randomBetween(config.windMinStrength(), config.windMaxStrength());
        ticksUntilReroll = 20L * 60L * config.windChangeIntervalMinutes();
    }

    /** Small per-tick nudge so direction/strength don't snap instantly between re-rolls. */
    private void drift() {
        directionRadians += (random.nextDouble() - 0.5) * 0.01;
        strength += (random.nextDouble() - 0.5) * 0.002;
        strength = Math.max(config.windMinStrength(), Math.min(config.windMaxStrength(), strength));
    }

    private void pushPlayers(double effectiveStrength) {
        Vector baseDirection = currentDirection();
        double basePushMagnitude = effectiveStrength * config.windPlayerPushMultiplier();
        boolean weightEnabled = config.windInventoryWeightEnabled();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;
            if (player.isFlying() || player.isInsideVehicle()) continue;
            if (!isExposedToSky(player.getLocation())) continue;
            if (windproofBoots.isWorn(player)) continue; // grounded - immune to wind entirely, on foot or gliding

            double weightMultiplier = 1.0;
            if (weightEnabled) {
                double fullness = inventoryFullness(player);
                weightMultiplier = player.isGliding()
                        // Elytra: a full inventory makes the wind push HARDER (harder to hold a line), not softer.
                        ? lerp(config.windGlidePushMultiplierEmpty(), config.windGlidePushMultiplierFull(), curvedFullness(fullness))
                        // On foot/swimming: a full inventory's extra weight anchors you against being pushed.
                        : lerp(config.windGroundPushMultiplierEmpty(), config.windGroundPushMultiplierFull(), curvedFullness(fullness));
            }

            Vector push = baseDirection.clone().multiply(basePushMagnitude * weightMultiplier);
            player.setVelocity(player.getVelocity().add(push));
        }
    }

    /** 0.0 (nothing in the 36 main-inventory storage slots) to 1.0 (every one of them occupied). Armor and offhand deliberately excluded -- this is about how much you're CARRYING, not what you're wearing/wielding. */
    private double inventoryFullness(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (contents.length == 0) return 0.0;
        int filled = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != Material.AIR) filled++;
        }
        return (double) filled / contents.length;
    }

    private double lerp(double from, double to, double t) {
        double clampedT = Math.max(0.0, Math.min(1.0, t));
        return from + (to - from) * clampedT;
    }

    /**
     * Reshapes raw inventory fullness (a straight 0.0-1.0 fraction of 36 slots) before it's used
     * in the lerp above -- a plain linear curve meant a player needed EVERY single slot filled to
     * get real wind resistance, and most "my inventory is full" players in practice keep a few
     * slots open for pickups, so they were only ever getting a small fraction of the intended
     * protection. This exponent (wind.inventory-weight.fullness-curve, under 1.0) makes fullness
     * ramp toward 1.0 much faster -- e.g. at the default 0.4, being 70% full already counts as
     * ~87% full for push-resistance purposes, so a genuinely well-stocked player gets real
     * protection well before every last slot is occupied.
     */
    private double curvedFullness(double fullness) {
        double clamped = Math.max(0.0, Math.min(1.0, fullness));
        return Math.pow(clamped, config.windInventoryWeightFullnessCurve());
    }

    /** Whether a location has open sky directly above it -- exposed to real wind vs. sheltered indoors/underground. Public because WeatherHudManager reuses this exact check so its wind-meter reading reflects where the player actually is. */
    public boolean isExposedToSky(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        return world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) <= location.getBlockY();
    }

    private void maybeDislodgeBlock() {
        if (random.nextDouble() >= config.windDislodgeChancePerTick()) return;

        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) return;
        Player anchor = players.stream().skip(random.nextInt(players.size())).findFirst().orElse(null);
        if (anchor == null || anchor.getWorld().getEnvironment() != World.Environment.NORMAL) return;

        int radius = config.windDislodgeSearchRadius();
        Location center = anchor.getLocation();
        int x = center.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
        int z = center.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
        Block block = center.getWorld().getHighestBlockAt(x, z).getRelative(BlockFace.DOWN);

        if (!isFragile(block.getType())) return;
        if (!isLooselySupported(block)) return;
        if (SpawnProtection.isProtected(block.getWorld(), x, z, config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks())) return;

        blowAway(block);
    }

    private boolean isFragile(Material material) {
        return config.windFragileMaterials().contains(material);
    }

    /** Cheap heuristic, not real structural analysis: fewer than 2 solid horizontal neighbors counts as "sitting by itself." */
    private boolean isLooselySupported(Block block) {
        int solidNeighbors = 0;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (block.getRelative(face).getType().isSolid()) solidNeighbors++;
        }
        return solidNeighbors < 2;
    }

    private void blowAway(Block block) {
        ItemStack drop = new ItemStack(block.getType());
        Location origin = block.getLocation().add(0.5, 0.5, 0.5);
        block.setType(Material.AIR);

        Item item = block.getWorld().dropItem(origin, drop);
        item.setPickupDelay(100);
        Vector velocity = currentDirection().multiply(0.4 + random.nextDouble() * 0.3);
        velocity.setY(0.2 + random.nextDouble() * 0.15);
        item.setVelocity(velocity);
    }

    private double randomBetween(double min, double max) {
        if (max <= min) return Math.max(0, min);
        return min + random.nextDouble() * (max - min);
    }
}
