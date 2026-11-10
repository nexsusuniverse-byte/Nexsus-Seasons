package com.nexusuniverse.seasons.weather;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * One shared check, used everywhere in this "crazy weather" layer that's about to actually modify
 * a block -- fragile-block destruction (wind/tornado/earthquake), coastal flooding (shore-break/
 * tsunami/hurricane), wave-train ridges, water leveling, and meteor impact/ignition. Square
 * (chessboard-distance) region around each world's own spawn point, same "radius in chunks"
 * convention NexusRealms' own bulk-claim commands already use elsewhere in this Nexus family --
 * kept consistent rather than inventing a different shape here.
 */
final class SpawnProtection {

    private SpawnProtection() {
    }

    /** True if this block position falls inside the protected zone and spawn-protection.enabled is on. Always false when disabled, regardless of position. */
    static boolean isProtected(World world, int blockX, int blockZ, boolean enabled, int radiusChunks) {
        if (!enabled || world == null) return false;

        Location spawn = world.getSpawnLocation();
        int spawnChunkX = spawn.getBlockX() >> 4;
        int spawnChunkZ = spawn.getBlockZ() >> 4;
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        return Math.abs(chunkX - spawnChunkX) <= radiusChunks && Math.abs(chunkZ - spawnChunkZ) <= radiusChunks;
    }

    /** Convenience overload for callers that already have a Location rather than a raw world/x/z. */
    static boolean isProtected(Location location, boolean enabled, int radiusChunks) {
        return isProtected(location.getWorld(), location.getBlockX(), location.getBlockZ(), enabled, radiusChunks);
    }
}
