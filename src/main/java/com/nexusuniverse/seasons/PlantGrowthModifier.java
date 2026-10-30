package com.nexusuniverse.seasons;

import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.Random;

/**
 * Crops grow faster in Spring/Summer, normally in Fall, and are
 * suppressed in Winter. Bukkit has no clean "grow twice as fast" hook --
 * BlockGrowEvent fires once a natural growth tick is ALREADY happening,
 * so a below-1.0 multiplier probabilistically vetoes that growth
 * (net effect: slower), and an above-1.0 multiplier has a chance of
 * granting one extra growth stage on top of the natural one it already
 * got (net effect: faster).
 */
public class PlantGrowthModifier implements Listener {

    private final SeasonClock clock;
    private final SeasonsConfig config;
    private final Random random = new Random();

    public PlantGrowthModifier(SeasonClock clock, SeasonsConfig config) {
        this.clock = clock;
        this.config = config;
    }

    @EventHandler
    public void onGrow(BlockGrowEvent event) {
        double multiplier = config.plantGrowthMultiplier(clock.season());

        if (multiplier < 1.0) {
            if (random.nextDouble() >= multiplier) {
                event.setCancelled(true);
            }
            return;
        }

        if (multiplier > 1.0 && event.getNewState().getBlockData() instanceof Ageable ageable) {
            double bonusChance = multiplier - 1.0;
            if (random.nextDouble() < bonusChance && ageable.getAge() < ageable.getMaximumAge()) {
                ageable.setAge(ageable.getAge() + 1);
                event.getNewState().setBlockData(ageable);
            }
        }
    }
}
