package com.nexusuniverse.seasons.weather;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Finds whether a player is at/near a body of water big enough to count as a real lake or ocean
 * rather than a decorative pond or moat, and at whatever height that water's own surface actually
 * sits -- a mountain lake or a below-sea-level pond works the same as the ocean, since nothing
 * here assumes World#getSeaLevel() is where the water is.
 *
 * A capped, early-exiting flood fill rather than a full one: it stops the instant the bounding box
 * of visited water reaches minSpan x minSpan (a real ocean satisfies this almost immediately
 * without exploring far), and gives up once it's checked maxVisited blocks (bounding worst-case
 * cost for a small, oddly-shaped body that never reaches the size threshold). Deliberately only
 * flood-fills at ONE fixed Y level (whatever findNearbyWater returned) rather than in 3D -- a
 * body of water's surface is flat, so this is both correct and far cheaper than a real 3D fill.
 */
final class WaterBodyDetector {

    private WaterBodyDetector() {}

    /** Scans a small radius around origin (including a few blocks up/down) for any water block, returning its exact position, or null if there's no water nearby at all. Cheap -- meant to be called often to cheaply rule out "not near any water" before ever attempting the more expensive flood fill below. */
    static Location findNearbyWater(Location origin, int searchRadius) {
        World world = origin.getWorld();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    Block block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    if (block.getType() == Material.WATER) {
                        return block.getLocation();
                    }
                }
            }
        }
        return null;
    }

    /** True if the water body containing waterBlock spans at least minSpan blocks in both X and Z. */
    static boolean isLargeBody(Location waterBlock, int minSpan, int maxVisited) {
        World world = waterBlock.getWorld();
        int startX = waterBlock.getBlockX();
        int startY = waterBlock.getBlockY();
        int startZ = waterBlock.getBlockZ();

        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startZ});
        visited.add(key(startX, startZ));

        int minX = startX, maxX = startX, minZ = startZ, maxZ = startZ;

        while (!queue.isEmpty() && visited.size() <= maxVisited) {
            int[] cur = queue.poll();
            int x = cur[0];
            int z = cur[1];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);

            if (maxX - minX >= minSpan && maxZ - minZ >= minSpan) return true;

            int[][] neighbors = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
            for (int[] n : neighbors) {
                long k = key(n[0], n[1]);
                if (visited.contains(k)) continue;
                if (world.getBlockAt(n[0], startY, n[1]).getType() != Material.WATER) continue;
                visited.add(k);
                queue.add(n);
            }
        }
        return false;
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
