package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Set;

/**
 * The one tangible "the map changes with the season" effect achievable
 * from pure server-side plugin code: real snow blocks accumulate on
 * eligible cold-biome surfaces during Winter, and melt back away once
 * Winter ends. True seasonal re-texturing (browning leaves, shifting
 * grass color) isn't reachable this way -- that needs a resource pack,
 * a different kind of asset than plugin code, and wasn't attempted here.
 *
 * Sweeps a budgeted number of surface blocks per call, stopping the
 * moment the budget runs out rather than trying to process a whole
 * world's loaded chunks in one pass -- the same throttling pattern used
 * for NexusMap's terrain cache, to avoid a lag spike from touching
 * thousands of blocks at once. Unswept terrain simply catches up on the
 * next sweep or the next time that chunk is loaded.
 *
 * PERFORMANCE NOTE: on a server with a very large number of loaded
 * chunks, just listing them every sweep has real cost on top of the
 * budgeted block-touching itself. If this becomes noticeable, raising
 * visuals.sweep-interval-seconds (or lowering snow-blocks-per-tick) in
 * config.yml is the first thing to try before assuming something's wrong.
 */
public class WorldVisualManager {

    private static final Set<Biome> COLD_BIOMES = Set.of(
            Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA, Biome.ICE_SPIKES,
            Biome.FROZEN_OCEAN, Biome.FROZEN_RIVER, Biome.FROZEN_PEAKS,
            Biome.SNOWY_SLOPES, Biome.GROVE, Biome.TAIGA
    );

    private final SeasonsConfig config;

    public WorldVisualManager(SeasonsConfig config) {
        this.config = config;
    }

    /** Called on its own interval (visuals.sweep-interval-seconds) from the main plugin's scheduler. */
    public void tick(Season season) {
        if (!config.snowEnabled()) return;
        boolean shouldHaveSnow = season == Season.WINTER;

        int budget = config.snowSweepBlocksPerTick();
        int processed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (processed >= budget) return;
                processed += sweepChunk(chunk, shouldHaveSnow, budget - processed);
            }
        }
    }

    private int sweepChunk(Chunk chunk, boolean shouldHaveSnow, int remainingBudget) {
        int processed = 0;
        int baseX = chunk.getX() * 16;
        int baseZ = chunk.getZ() * 16;

        for (int x = 0; x < 16 && processed < remainingBudget; x++) {
            for (int z = 0; z < 16 && processed < remainingBudget; z++) {
                Block highest = chunk.getWorld().getHighestBlockAt(baseX + x, baseZ + z);
                if (!COLD_BIOMES.contains(highest.getBiome())) continue;

                processed++;
                applySnow(highest, shouldHaveSnow);
            }
        }
        return processed;
    }

    private void applySnow(Block surfaceBlock, boolean shouldHaveSnow) {
        Block above = surfaceBlock.getRelative(BlockFace.UP);

        if (shouldHaveSnow) {
            if (above.getType() == Material.AIR && surfaceBlock.getType().isSolid()) {
                above.setType(Material.SNOW);
            }
        } else if (above.getType() == Material.SNOW) {
            above.setType(Material.AIR);
        }
    }
}
