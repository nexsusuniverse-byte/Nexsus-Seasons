package com.nexusuniverse.seasons;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Random;

/**
 * Nudges natural mob spawn rates per season -- e.g. thinning out zombies
 * in favor of colder-biome mobs during Winter. Implemented as a
 * probabilistic veto on CreatureSpawnEvent(NATURAL): a weight below 1.0
 * for a mob type in the current season has a chance to cancel that spawn
 * outright. A weight of 1.0 or above is left alone -- a single spawn
 * event can't be made to happen "more," so seasons/mobs meant to feel
 * unusually common work by NOT thinning them while their neighbors are
 * being thinned, not by directly boosting them.
 */
public class SeasonalMobSpawner implements Listener {

    private final SeasonClock clock;
    private final SeasonsConfig config;
    private final Random random = new Random();

    public SeasonalMobSpawner(SeasonClock clock, SeasonsConfig config) {
        this.clock = clock;
        this.config = config;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        EntityType type = event.getEntityType();
        double weight = config.mobSpawnWeight(clock.season(), type.name().toLowerCase());
        if (weight >= 1.0) return;

        if (random.nextDouble() >= weight) {
            event.setCancelled(true);
        }
    }
}
