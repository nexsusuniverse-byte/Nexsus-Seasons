package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Real, continuously scrolling rows of raised water -- a genuine wave TRAIN moving across the
 * open surface of any qualifying body of water, distinct from CoastalWaveManager (which handles
 * a single wave flooding onto land near the shore, then draining back out). This is the other
 * half of the ask: parallel ridge lines of real water blocks, gapped apart, all traveling
 * together across the water's own surface the way actual ocean swell looks from above.
 *
 * NOT a single advance-then-recede event -- a periodic pattern (ridge blocks, then a gap, then
 * the next ridge, repeating every waveTrain.ridge-width + waveTrain.gap-width blocks) that scrolls
 * continuously in the current wind direction. Ridges are genuinely raised WATER blocks (real
 * block placement, one to two blocks above the body's own surface -- never touches anything that
 * isn't already open water or air directly above it), reverted the instant a ridge scrolls past a
 * given column and a gap arrives there instead. Tracked per-column so only blocks that actually
 * change state get touched each pass, not the whole field.
 *
 * IMPORTANT: raised blocks are real, genuine flowing WATER -- and real water spreads. Left alone,
 * a raised ridge would flow sideways into the open air around it via ordinary vanilla fluid
 * physics, faster than this class's own bookkeeping could ever track or revert, which is exactly
 * what looked like "a permanent new layer that never recedes" -- it wasn't the recede logic
 * failing, it was vanilla water spreading beyond the specific blocks this class placed and knows
 * about. Fixed by implementing Listener directly and cancelling BlockFromToEvent specifically for
 * any flow originating FROM a block this class placed (tracked in the global raisedBlocks
 * registry below, shared across every player's field) -- this only stops spreading from water
 * this system itself put there; it never touches normal water anywhere else on the server.
 *
 * On top of that prevention, a self-healing safety net (runAutomaticCleanup(), config
 * waves.wave-train.auto-cleanup) periodically corrects any water that's ALREADY leaked out --
 * from before that fix existed, or any other untracked cause -- without needing an admin to
 * notice and run /nexusseasons wavereset by hand. It only ever clears water it can positively
 * identify as untracked excess (raisedBlocks is checked and skipped), so a currently-active,
 * legitimately-raised ridge is never disturbed by it.
 *
 * Runs per-player (only for players confirmed standing at/in a qualifying body, reusing the same
 * WaterBodyDetector size check the rest of this "crazy weather" layer uses) rather than as one
 * shared whole-world field -- simpler and bounded, at the cost of some redundant work if several
 * players are near the same stretch of water (harmless, just not perfectly efficient).
 *
 * Deliberately does NOT run anywhere a tsunami or hurricane storm surge is currently active --
 * those stay the single, massive, one-big-wave events they already were, rather than being
 * replaced by or fighting with a field of smaller rolling swell.
 */
public class WaveTrainManager implements Listener {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final WindManager wind;
    private final TsunamiManager tsunami;
    private final HurricaneManager hurricane;

    private final Map<UUID, PlayerField> fields = new HashMap<>();
    // every block this system currently has raised, across ALL players -- checked by
    // onBlockFromTo() below so vanilla fluid physics can't spread it beyond the exact blocks this
    // class is actually tracking and will itself revert on schedule
    private final Set<RaisedBlock> raisedBlocks = new HashSet<>();
    private long clockTicks;
    private BukkitTask task;
    private BukkitTask autoCleanupTask;

    public WaveTrainManager(JavaPlugin plugin, SeasonsConfig config, WindManager wind,
                             TsunamiManager tsunami, HurricaneManager hurricane) {
        this.plugin = plugin;
        this.config = config;
        this.wind = wind;
        this.tsunami = tsunami;
        this.hurricane = hurricane;
    }

    public void start() {
        if (!config.waveTrainEnabled()) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int interval = config.waveTrainTickInterval();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);

        if (config.waveTrainAutoCleanupEnabled()) {
            long autoInterval = 20L * 60L * config.waveTrainAutoCleanupIntervalMinutes();
            autoCleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runAutomaticCleanup, autoInterval, autoInterval);
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        if (autoCleanupTask != null) autoCleanupTask.cancel();
        for (PlayerField field : fields.values()) {
            revertAll(field);
        }
        fields.clear();
        raisedBlocks.clear();
    }

    /**
     * The self-healing safety net: runs a lighter version of the wavereset sweep automatically near
     * every currently-online player. Deliberately does NOT do what the manual command's first pass
     * does (force-reverting every currently-tracked PlayerField globally) -- that's appropriate for
     * a deliberate, one-off admin action, but running it automatically every few minutes would
     * interrupt perfectly healthy, actively-scrolling wave trains near OTHER players every single
     * pass, which would look like far more "glitching" than it actually fixes. This only clears
     * genuinely untracked excess water sitting above the detected baseline -- exactly the leaked
     * case this whole safety net exists for -- and leaves any currently-managed wave train alone.
     */
    private void runAutomaticCleanup() {
        int radius = config.waveTrainAutoCleanupRadius();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;
            clearUntrackedExcess(player.getLocation(), radius);
        }
    }

    /** Stops vanilla fluid physics from spreading a raised ridge beyond the exact blocks this class placed and is tracking -- see class doc. Never affects any other water on the server. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event.getBlock().getType() != Material.WATER) return;
        Block source = event.getBlock();
        RaisedBlock key = new RaisedBlock(source.getX(), source.getY(), source.getZ(), source.getWorld().getName());
        if (raisedBlocks.contains(key)) {
            event.setCancelled(true);
        }
    }

    private void tick() {
        clockTicks += config.waveTrainTickInterval();

        // don't run this near an active single-big-wave event -- see class doc
        boolean bigEventActive = tsunami.isActive() || hurricane.isActive();

        Set<UUID> stillEligible = new HashSet<>();
        if (!bigEventActive) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;
                Location feet = player.getLocation();
                Location eye = player.getEyeLocation();
                Location waterSeed = feet.getBlock().getType() == Material.WATER ? feet
                        : eye.getBlock().getType() == Material.WATER ? eye : null;
                if (waterSeed == null) continue;
                if (!WaterBodyDetector.isLargeBody(waterSeed, config.wavesMinBodySize(), config.wavesBodyDetectionMaxBlocks())) continue;

                stillEligible.add(player.getUniqueId());
                PlayerField field = fields.computeIfAbsent(player.getUniqueId(), id -> new PlayerField());
                updateField(field, waterSeed);
            }
        }

        // anyone no longer eligible (left the water, body too small, a big event just started,
        // went offline) gets their ridges reverted and their field dropped entirely
        var iterator = fields.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!stillEligible.contains(entry.getKey())) {
                revertAll(entry.getValue());
                iterator.remove();
            }
        }
    }

    private void updateField(PlayerField field, Location waterSeed) {
        World world = waterSeed.getWorld();
        int surfaceY = waterSeed.getBlockY();

        Vector direction = wind != null ? wind.currentDirection() : new Vector(1, 0, 0);
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());

        int halfSpan = config.waveTrainSpan() / 2;
        int reach = config.waveTrainReach();
        int ridgeWidth = config.waveTrainRidgeWidth();
        int period = ridgeWidth + config.waveTrainGapWidth();
        double phase = (clockTicks * config.waveTrainSpeed()) % period;

        double windStrength = wind != null ? wind.currentStrength() : 0.2;
        int height = (int) Math.round(config.waveTrainMinHeight()
                + windStrength * (config.waveTrainMaxHeight() - config.waveTrainMinHeight()));
        height = Math.max(1, height);

        Set<Long> nowRidge = new HashSet<>();
        int budget = config.waveTrainBlocksPerTick();
        int touched = 0;

        for (int along = -reach; along <= reach && touched < budget; along++) {
            double distanceAlong = along + phase;
            double mod = ((distanceAlong % period) + period) % period;
            if (mod >= ridgeWidth) continue; // this row is in the gap between ridges right now

            for (int across = -halfSpan; across <= halfSpan && touched < budget; across++) {
                int x = (int) Math.round(waterSeed.getX() + direction.getX() * along + perpendicular.getX() * across);
                int z = (int) Math.round(waterSeed.getZ() + direction.getZ() * along + perpendicular.getZ() * across);

                // only ever raise a column that's already genuinely open water at the surface --
                // never touches land or anything that isn't water/air
                if (world.getBlockAt(x, surfaceY, z).getType() != Material.WATER) continue;
                if (SpawnProtection.isProtected(world, x, z, config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks())) continue;

                nowRidge.add(columnKey(x, z));
                for (int dy = 1; dy <= height; dy++) {
                    Block block = world.getBlockAt(x, surfaceY + dy, z);
                    if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
                        block.setType(Material.WATER);
                        RaisedBlock raised = new RaisedBlock(x, surfaceY + dy, z, world.getName());
                        field.raised.add(raised);
                        raisedBlocks.add(raised);
                    }
                }
                touched++;
            }
        }

        // revert any previously-raised block whose column isn't a ridge anymore this pass -- the
        // gap has scrolled into where the ridge used to be
        field.raised.removeIf(raised -> {
            if (nowRidge.contains(columnKey(raised.x, raised.z))) return false;
            World raisedWorld = Bukkit.getWorld(raised.worldName);
            if (raisedWorld != null) {
                Block block = raisedWorld.getBlockAt(raised.x, raised.y, raised.z);
                if (block.getType() == Material.WATER) block.setType(Material.AIR);
            }
            raisedBlocks.remove(raised);
            return true;
        });
    }

    private void revertAll(PlayerField field) {
        for (RaisedBlock raised : field.raised) {
            World world = Bukkit.getWorld(raised.worldName);
            if (world != null) {
                Block block = world.getBlockAt(raised.x, raised.y, raised.z);
                if (block.getType() == Material.WATER) block.setType(Material.AIR);
            }
            raisedBlocks.remove(raised);
        }
        field.raised.clear();
    }

    private long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Manual cleanup tool -- for water that leaked out before the BlockFromToEvent fix existed
     * (or from any other untracked cause), which this system has no record of and can't revert
     * on its own since it only ever tracks blocks it itself placed.
     *
     * Two passes, run on demand from an admin command:
     *  1. Force-reverts every block currently tracked as raised (server-wide, not just near
     *     center), regardless of ridge/gap state -- an instant fix for anything legitimately stuck
     *     in the normal tracking. This step is deliberately NOT part of the lighter automatic
     *     sweep below (see runAutomaticCleanup()'s doc) since it would interrupt other players'
     *     perfectly healthy wave trains every time it ran.
     *  2. clearUntrackedExcess() -- see its own doc; this is the part the automatic sweep also uses.
     *
     * Returns how many blocks it actually cleared.
     */
    public int cleanupArea(Location center, int radius) {
        int cleared = 0;
        for (PlayerField field : fields.values()) {
            cleared += field.raised.size();
            revertAll(field);
        }
        fields.clear();

        return cleared + clearUntrackedExcess(center, radius);
    }

    /**
     * The baseline-detect-and-clear half of cleanupArea(), split out on its own so the automatic
     * safety net (runAutomaticCleanup()) can run just this part without also force-reverting every
     * currently-tracked PlayerField server-wide -- see that method's doc for why. Never touches the
     * fields/raisedBlocks bookkeeping at all; purely a raw-block-level correction for water this
     * class has no tracking record of. Returns how many blocks it actually cleared.
     */
    private int clearUntrackedExcess(Location center, int radius) {
        int cleared = 0;
        World world = center.getWorld();
        int baseX = center.getBlockX();
        int baseZ = center.getBlockZ();
        int startY = center.getBlockY();

        // pass 1: find the baseline -- the lowest top-of-water found anywhere in the sampled
        // area (every other column is plenty for finding a minimum, no need to check all of them)
        Integer baseline = null;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                Integer top = topOfWaterColumn(world, baseX + dx, baseZ + dz, startY);
                if (top != null && (baseline == null || top < baseline)) baseline = top;
            }
        }
        if (baseline == null) return cleared; // no water found anywhere nearby at all

        // pass 2: clear anything above that baseline, full resolution this time -- skipping
        // anything currently tracked in raisedBlocks, since an active ridge is SUPPOSED to sit
        // above the baseline; only genuinely untracked (leaked) water above it counts as excess
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                Integer top = topOfWaterColumn(world, x, z, startY);
                if (top == null || top <= baseline) continue;

                for (int y = baseline + 1; y <= top; y++) {
                    if (raisedBlocks.contains(new RaisedBlock(x, y, z, world.getName()))) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.WATER) {
                        block.setType(Material.AIR);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    /** Finds the topmost block of a contiguous vertical run of water in this column, searching a modest range around startY -- or null if there's no water in this column at all nearby. */
    private Integer topOfWaterColumn(World world, int x, int z, int startY) {
        for (int dy = -8; dy <= 8; dy++) {
            if (world.getBlockAt(x, startY + dy, z).getType() == Material.WATER) {
                int y = startY + dy;
                while (world.getBlockAt(x, y + 1, z).getType() == Material.WATER) y++;
                return y;
            }
        }
        return null;
    }

    private static class PlayerField {
        final Set<RaisedBlock> raised = new HashSet<>();
    }

    private static class RaisedBlock {
        final int x;
        final int y;
        final int z;
        final String worldName;

        RaisedBlock(int x, int y, int z, String worldName) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.worldName = worldName;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RaisedBlock other)) return false;
            return x == other.x && y == other.y && z == other.z && worldName.equals(other.worldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z, worldName);
        }
    }
}
