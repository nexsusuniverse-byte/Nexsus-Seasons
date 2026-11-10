package com.nexusuniverse.seasons.weather;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Fills in low spots in an area of water to match that area's own dominant surface height --
 * "leveling" a patch of water that's ended up sitting at several different heights next to each
 * other (a pre-existing world-gen/build quirk, not something any of this plugin's own wave systems
 * cause) rather than clearing anything. Two rules keep this safe:
 *  - the target height is whatever level is found in the MOST columns nearby -- the dominant/base
 *    level for that specific area, not just the highest one found. An isolated deep pocket or one
 *    unusually tall puddle doesn't drag the whole area up to match it.
 *  - a column already AT or ABOVE that level is never touched. This only ever raises water UP TO
 *    the area's own normal level, never past it, and never lowers anything either.
 * Only ever fills through CoastalFloodEngine.FLOODABLE space (air, grass, snow, etc. -- the same
 * conservative "safe to turn into water" set that engine itself floods through) -- a solid block
 * in the way stops that one column rather than tunneling through something a player built.
 */
final class WaterLevelingEngine {

    private WaterLevelingEngine() {
    }

    /**
     * Scans a (2*radius+1)^2 column area around center, finds the water height most columns in
     * that area are actually sitting at, and fills any column currently below that up to match --
     * capped at maxBlocksPerPass so one call can't cause a large, noticeable pause. Columns inside
     * the configured spawn-protection zone are skipped entirely, same as every other block-
     * modifying system in this layer. Returns how many blocks were actually filled.
     */
    static int levelArea(Location center, int radius, int maxBlocksPerPass, boolean spawnProtectionEnabled, int spawnProtectionRadiusChunks) {
        World world = center.getWorld();
        if (world == null) return 0;

        int baseX = center.getBlockX();
        int baseZ = center.getBlockZ();
        int refY = center.getBlockY();
        int span = radius * 2 + 1;

        // pass 1: find every column's current water top nearby, and tally which height shows up
        // in the most columns -- that's this area's own dominant/base level
        Map<Integer, Integer> heightCounts = new HashMap<>();
        Integer[][] tops = new Integer[span][span];
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Integer top = topOfWaterColumn(world, baseX + dx, baseZ + dz, refY);
                tops[dx + radius][dz + radius] = top;
                if (top != null) heightCounts.merge(top, 1, Integer::sum);
            }
        }
        if (heightCounts.isEmpty()) return 0; // no water found anywhere nearby at all

        int baseLevel = Integer.MIN_VALUE;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : heightCounts.entrySet()) {
            int height = entry.getKey();
            int count = entry.getValue();
            // most-common height wins; a tie goes to the higher of the two, matching "whatever
            // the highest level is with the most water" -- both conditions point the same way in
            // the overwhelmingly common case, this only matters on an exact tie
            if (count > bestCount || (count == bestCount && height > baseLevel)) {
                bestCount = count;
                baseLevel = height;
            }
        }

        // pass 2: fill any column currently below baseLevel up to it, through floodable space only
        int filled = 0;
        for (int dx = -radius; dx <= radius && filled < maxBlocksPerPass; dx++) {
            for (int dz = -radius; dz <= radius && filled < maxBlocksPerPass; dz++) {
                Integer top = tops[dx + radius][dz + radius];
                if (top == null || top >= baseLevel) continue; // no water here, or already at/above base -- never touched

                int x = baseX + dx;
                int z = baseZ + dz;
                if (SpawnProtection.isProtected(world, x, z, spawnProtectionEnabled, spawnProtectionRadiusChunks)) continue;
                for (int y = top + 1; y <= baseLevel && filled < maxBlocksPerPass; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!CoastalFloodEngine.FLOODABLE.contains(block.getType())) break; // solid block in the way -- stop this column, don't tunnel through a build
                    block.setType(Material.WATER);
                    filled++;
                }
            }
        }
        return filled;
    }

    /** Topmost block of a contiguous vertical run of water in this column, searching a modest range around refY -- or null if there's no water in this column nearby at all. Same technique WaveTrainManager's own cleanup uses. */
    private static Integer topOfWaterColumn(World world, int x, int z, int refY) {
        for (int dy = -8; dy <= 8; dy++) {
            if (world.getBlockAt(x, refY + dy, z).getType() == Material.WATER) {
                int y = refY + dy;
                while (world.getBlockAt(x, y + 1, z).getType() == Material.WATER) y++;
                return y;
            }
        }
        return null;
    }
}
