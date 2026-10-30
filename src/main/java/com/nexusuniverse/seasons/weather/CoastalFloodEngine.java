package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Set;

/**
 * Shared core for any "wall of water sweeps inland, then drains back out" event -- used by
 * TsunamiManager (one big fast surge), HurricaneManager (a gentler, sustained storm surge), and
 * CoastalWaveManager (small, frequent, continuously-recurring shore-break waves).
 *
 * NOT a real fluid simulation -- it's a straight-line advancing front that temporarily converts
 * non-solid surface blocks (air, grass, flowers, snow -- deliberately NEVER anything solid a
 * player might have built) up to a target height into real WATER blocks, remembers exactly what
 * was there before in the order it was flooded, and un-does it in reverse order (farthest inland
 * first) as the front recedes. It genuinely uses real water blocks and applies real velocity
 * push to any player OR boat caught in a flooded cell -- this is about as close as plain plugin
 * code can get to real coastal-flood physics without a resource pack or a proper fluid engine.
 *
 * Deliberately conservative about what it overwrites so it can't casually demolish a player's
 * house near the coast -- water floods around and through open/plant-filled space, never through
 * solid walls, floors, or anything else already built.
 */
class CoastalFloodEngine {

    /** Package-visible (not private) so WaterLevelingEngine fills through exactly this same conservative "safe to turn into water" set -- one definition, never two lists that could drift out of sync. */
    static final Set<Material> FLOODABLE = Set.of(
            Material.AIR, Material.CAVE_AIR, Material.SHORT_GRASS, Material.TALL_GRASS,
            Material.FERN, Material.LARGE_FERN, Material.DEAD_BUSH, Material.SEAGRASS,
            Material.POPPY, Material.DANDELION, Material.SNOW
    );

    private final Deque<AffectedBlock> affected = new ArrayDeque<>();

    private Location coastAnchor;
    private Vector inlandDirection;
    private double frontDistance;
    private double maxDistance;
    private double advanceSpeed;
    private double frontWidth;
    private double waveHeight;
    private double knockbackStrength;
    private boolean receding;
    private boolean finished = true;
    private int maxAffectedBlocks;
    private int recedeDurationTicks;
    private int recedeDrainPerTick = 1; // computed once when receding actually starts (see tick()), not every tick -- affected.size() keeps shrinking as it drains, so recomputing from it every tick would make the drain rate ramp down instead of staying steady
    private boolean spawnProtectionEnabled;
    private int spawnProtectionRadiusChunks;

    boolean isActive() {
        return !finished;
    }

    /** Where this flood's coastline was found -- exposed so callers can run a leveling/cleanup pass scoped to the same spot once the flood fully finishes. Null before findCoastNear() has ever succeeded. */
    Location coastAnchor() {
        return coastAnchor;
    }

    /** Set once by the owning manager (not reset by start()) -- a constant "don't flood inside this zone" setting for this engine instance, independent of any one flood cycle. */
    void setSpawnProtection(boolean enabled, int radiusChunks) {
        this.spawnProtectionEnabled = enabled;
        this.spawnProtectionRadiusChunks = radiusChunks;
    }

    /**
     * Looks outward from origin for a land/water boundary at waterSurfaceY within searchRadius.
     * Returns false if none found nearby -- callers should tell the player (or just skip) rather
     * than silently doing nothing forever.
     *
     * Takes the water surface height as an explicit parameter instead of assuming
     * World#getSeaLevel() -- that hardcoded assumption meant this could only ever find ocean
     * coastlines sitting exactly at the world's configured sea level, and would silently fail to
     * find the shoreline of any lake, pond, or other body of water sitting at a different height
     * (a mountain lake, a below-sea-level pond, etc). Callers that specifically want ocean-at-sea-
     * level behavior (tsunami, hurricane storm surge) pass world.getSeaLevel() themselves; callers
     * that want this to work on any qualifying body of water (continuous shore-break waves) pass
     * whatever height WaterBodyDetector actually found the water at.
     */
    boolean findCoastNear(Location origin, int searchRadius, int waterSurfaceY) {
        World world = origin.getWorld();
        int baseX = origin.getBlockX();
        int baseZ = origin.getBlockZ();

        for (int r = 4; r <= searchRadius; r += 4) {
            for (int angleStep = 0; angleStep < 16; angleStep++) {
                double angle = angleStep * (Math.PI * 2 / 16);
                int x = baseX + (int) (Math.cos(angle) * r);
                int z = baseZ + (int) (Math.sin(angle) * r);
                int inlandX = x + (int) (Math.cos(angle) * 3);
                int inlandZ = z + (int) (Math.sin(angle) * 3);

                boolean hereIsWater = world.getBlockAt(x, waterSurfaceY, z).getType() == Material.WATER;
                Block inlandHighest = world.getHighestBlockAt(inlandX, inlandZ);
                boolean inlandIsLand = inlandHighest.getY() > waterSurfaceY && inlandHighest.getType().isSolid();

                if (hereIsWater && inlandIsLand) {
                    coastAnchor = new Location(world, x, waterSurfaceY, z);
                    inlandDirection = new Vector(Math.cos(angle), 0, Math.sin(angle));
                    return true;
                }
            }
        }
        return false;
    }

    /** Original 6-arg form -- recedes at the old proportional rate (roughly a 5-second drain regardless of size). Used by tsunami/hurricane, unchanged from before. */
    void start(double maxDistance, double advanceSpeed, double frontWidth, double waveHeight,
               double knockbackStrength, int maxAffectedBlocks) {
        start(maxDistance, advanceSpeed, frontWidth, waveHeight, knockbackStrength, maxAffectedBlocks, 100);
    }

    /**
     * Full form -- recedeDurationTicks lets the caller control exactly how long the drain-back-out
     * phase takes, in ticks, regardless of how many blocks actually got flooded. The old
     * proportional-to-size approach (still what the 6-arg overload above does) made small floods
     * drain almost instantly and only looked deliberate on genuinely large surges -- shore-break
     * waves need a real, predictable, visible drain every time even when only a modest patch of
     * shoreline got flooded.
     */
    void start(double maxDistance, double advanceSpeed, double frontWidth, double waveHeight,
               double knockbackStrength, int maxAffectedBlocks, int recedeDurationTicks) {
        this.maxDistance = maxDistance;
        this.advanceSpeed = advanceSpeed;
        this.frontWidth = frontWidth;
        this.waveHeight = waveHeight;
        this.knockbackStrength = knockbackStrength;
        this.maxAffectedBlocks = maxAffectedBlocks;
        this.recedeDurationTicks = Math.max(1, recedeDurationTicks);
        this.frontDistance = 0;
        this.receding = false;
        this.finished = false;
        this.affected.clear();
    }

    /** Call once per tick while active. Returns true once fully receded and finished. */
    boolean tick() {
        if (finished) return true;

        if (!receding) {
            advanceFront();
            if (frontDistance >= maxDistance) {
                receding = true;
                // pace the drain against how much there actually is to drain divided by however
                // long it should visibly take, computed once right here rather than every tick
                recedeDrainPerTick = Math.max(1, affected.size() / recedeDurationTicks);
            }
        } else {
            recedeFront();
            if (affected.isEmpty()) {
                finished = true;
                return true;
            }
        }
        return false;
    }

    /** Skips straight to recession -- used when an admin force-stops an active surge early. */
    void forceRecede() {
        if (!receding) {
            receding = true;
            recedeDrainPerTick = Math.max(1, affected.size() / recedeDurationTicks);
        }
    }

    private void advanceFront() {
        World world = coastAnchor.getWorld();
        Vector perpendicular = new Vector(-inlandDirection.getZ(), 0, inlandDirection.getX());
        int halfWidth = (int) (frontWidth / 2);
        // fetched once per tick rather than once per column -- pushEntitiesNear() runs up to
        // frontWidth times a tick, and re-scanning every player/boat in the world that often adds
        // up fast with several concurrent shore-break waves running
        var players = world.getPlayers();
        var boats = world.getEntitiesByClass(Boat.class);

        for (int w = -halfWidth; w <= halfWidth; w++) {
            if (affected.size() >= maxAffectedBlocks) break; // budget cap -- the front still advances (see below), it just stops flooding NEW blocks once full

            double px = coastAnchor.getX() + inlandDirection.getX() * frontDistance + perpendicular.getX() * w;
            double pz = coastAnchor.getZ() + inlandDirection.getZ() * frontDistance + perpendicular.getZ() * w;
            int x = (int) Math.round(px);
            int z = (int) Math.round(pz);

            int groundY = world.getHighestBlockYAt(x, z);
            int targetY = coastAnchor.getBlockY() + (int) waveHeight;
            if (groundY > targetY) continue; // ground here is higher than the wave can reach
            if (SpawnProtection.isProtected(world, x, z, spawnProtectionEnabled, spawnProtectionRadiusChunks)) continue;

            for (int y = groundY + 1; y <= targetY; y++) {
                Block block = world.getBlockAt(x, y, z);
                if (!FLOODABLE.contains(block.getType())) continue;
                affected.addLast(new AffectedBlock(block.getLocation(), block.getType()));
                block.setType(Material.WATER);
            }

            pushEntitiesNear(players, boats, x, z, targetY);
        }

        frontDistance += advanceSpeed;
    }

    private void recedeFront() {
        for (int i = 0; i < recedeDrainPerTick && !affected.isEmpty(); i++) {
            AffectedBlock block = affected.pollLast(); // farthest-inland (most recently flooded) drains first, same direction real water would actually retreat
            Block target = block.location.getBlock();
            if (target.getType() == Material.WATER) target.setType(block.originalType);
        }
    }

    /** Pushes both players AND boats caught near the advancing front -- a boat gets a bit more upward lift than a swimmer so it visibly rides up and over the wave rather than just getting shoved through it. */
    private void pushEntitiesNear(Collection<? extends Player> players, Collection<Boat> boats, int x, int z, int targetY) {
        for (Player player : players) {
            Location loc = player.getLocation();
            if (Math.abs(loc.getBlockX() - x) > 2 || Math.abs(loc.getBlockZ() - z) > 2) continue;
            if (loc.getBlockY() > targetY + 2) continue;

            Vector push = inlandDirection.clone().multiply(knockbackStrength);
            push.setY(0.3);
            player.setVelocity(player.getVelocity().add(push));
        }

        for (Boat boat : boats) {
            Location loc = boat.getLocation();
            if (Math.abs(loc.getBlockX() - x) > 2 || Math.abs(loc.getBlockZ() - z) > 2) continue;
            if (loc.getBlockY() > targetY + 2) continue;

            Vector push = inlandDirection.clone().multiply(knockbackStrength);
            push.setY(0.45); // extra lift over a swimmer -- reads as riding the wave up rather than being dragged
            boat.setVelocity(boat.getVelocity().add(push));
        }
    }

    private static class AffectedBlock {
        final Location location;
        final Material originalType;

        AffectedBlock(Location location, Material originalType) {
            this.location = location;
            this.originalType = originalType;
        }
    }
}
