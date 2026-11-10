package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.HeartbeatBridge;
import com.nexusuniverse.seasons.SeasonsConfig;
import com.nexusuniverse.seasons.advisor.SeasonProblemAdvisor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Periodic falling meteors, each animated manually tick-by-tick rather than as a real Fireball
 * entity -- gives full control over the trail visuals and the impact, and keeps it safe/bounded by
 * default rather than inheriting a real Fireball's own explosion/griefing behavior. Each one starts
 * high above a target point (near a random online player, offset within meteor.impact-radius), and
 * descends over meteor.fall-ticks spawning a FLAME/LARGE_SMOKE/LAVA trail along the way, then
 * bursts into an EXPLOSION_EMITTER+CLOUD particle impact with a real explosion sound at the target.
 *
 * Impact is visual-and-sound only by default -- meteor.damage-enabled has to be turned on for it to
 * also apply real knockback/damage via World#createExplosion(..., breakBlocks=false), which is
 * always non-block-destructive regardless, matching this "crazy weather" layer's existing rule of
 * never permanently altering player builds. meteor.ignite-target optionally sets the impact block
 * on fire for a few seconds for extra drama (off by default) -- it always reverts itself afterward,
 * never left behind permanently.
 *
 * Multiple meteors can genuinely be in flight at once (tracked in a list, not a single object) --
 * whether that happens depends on meteor.meteor-interval-ticks vs meteor.fall-ticks.
 */
public class MeteorShowerManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final SeasonProblemAdvisor advisor;
    private final Random random = new Random();

    private boolean active = false;
    private long remainingTicks;
    private long ticksUntilNextMeteor;
    private long ticksUntilNaturalCheck;
    private final List<Meteor> inFlight = new ArrayList<>();
    private BukkitTask task;

    public MeteorShowerManager(JavaPlugin plugin, SeasonsConfig config, SeasonProblemAdvisor advisor) {
        this.plugin = plugin;
        this.config = config;
        this.advisor = advisor;
    }

    public void start() {
        if (!config.meteorShowerEnabled()) return;
        ticksUntilNaturalCheck = checkIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        inFlight.clear();
    }

    public boolean isActive() {
        return active;
    }

    public void forceStart(int durationSeconds) {
        active = true;
        remainingTicks = 20L * durationSeconds;
        ticksUntilNextMeteor = 0; // launch the first one right away rather than waiting a full interval
        WeatherAnnouncer.announceGlobal(config, "meteor-shower-start", "§d§lStreaks of fire cut across the sky... §fa meteor shower has begun!");
        // NexusHeartbeat integration hook: a cosmetic celestial wonder lifts the world's shared mood.
        HeartbeatBridge.report(plugin, "NATURE", 1, "A meteor shower is lighting up the sky.");
    }

    public void forceStop() {
        boolean wasActive = active;
        active = false;
        inFlight.clear();
        if (wasActive) {
            WeatherAnnouncer.announceGlobal(config, "meteor-shower-end", "§7The meteor shower has ended.");
        }
    }

    private void tick() {
        if (!active) {
            ticksUntilNaturalCheck--;
            if (ticksUntilNaturalCheck <= 0) {
                ticksUntilNaturalCheck = checkIntervalTicks();
                if (random.nextDouble() < config.meteorShowerNaturalChance()) {
                    int duration = randomBetween(config.meteorShowerDurationMinSeconds(), config.meteorShowerDurationMaxSeconds());
                    forceStart(duration);
                }
            }
            advanceInFlight(); // keep animating any meteors still falling from a just-ended shower
            return;
        }

        remainingTicks--;
        if (remainingTicks <= 0) {
            active = false;
            WeatherAnnouncer.announceGlobal(config, "meteor-shower-end", "§7The meteor shower has ended.");
        } else {
            ticksUntilNextMeteor--;
            if (ticksUntilNextMeteor <= 0) {
                ticksUntilNextMeteor = config.meteorShowerIntervalTicks();
                launchMeteor();
            }
        }

        advanceInFlight();
    }

    private void launchMeteor() {
        var players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .toList();
        if (players.isEmpty()) return;
        Player anchor = players.get(random.nextInt(players.size()));

        int offset = config.meteorShowerImpactRadius();
        Location target = anchor.getLocation().clone();
        target.add(random.nextInt(offset * 2 + 1) - offset, 0, random.nextInt(offset * 2 + 1) - offset);
        target.setY(target.getWorld().getHighestBlockYAt(target.getBlockX(), target.getBlockZ()) + 1);

        // skip this launch entirely rather than just suppressing the impact -- a meteor's real
        // explosion damage/knockback (when meteor.damage-enabled is on) is as much a "grief the
        // city" risk as any block change, so the whole thing just doesn't fire this cycle rather
        // than falling toward a protected spot and only pulling its punch at the last second
        if (SpawnProtection.isProtected(target.getWorld(), target.getBlockX(), target.getBlockZ(),
                config.spawnProtectionEnabled(), config.spawnProtectionRadiusChunks())) {
            return;
        }

        Location start = target.clone().add(0, config.meteorShowerFallHeight(), 0);
        inFlight.add(new Meteor(start, target, config.meteorShowerFallTicks()));
    }

    private void advanceInFlight() {
        Iterator<Meteor> iterator = inFlight.iterator();
        while (iterator.hasNext()) {
            Meteor meteor = iterator.next();
            meteor.elapsedTicks++;
            double t = Math.min(1.0, (double) meteor.elapsedTicks / meteor.fallTicks);

            Location current = meteor.start.clone().add(
                    (meteor.target.getX() - meteor.start.getX()) * t,
                    (meteor.target.getY() - meteor.start.getY()) * t,
                    (meteor.target.getZ() - meteor.start.getZ()) * t);

            World world = current.getWorld();
            world.spawnParticle(Particle.FLAME, current, 3, 0.15, 0.15, 0.15, 0.01);
            world.spawnParticle(Particle.LARGE_SMOKE, current, 2, 0.1, 0.1, 0.1, 0.01);
            if (random.nextDouble() < 0.4) {
                world.spawnParticle(Particle.LAVA, current, 1, 0.1, 0.1, 0.1, 0.0);
            }

            if (t >= 1.0) {
                impact(meteor.target);
                iterator.remove();
            }
        }
    }

    private void impact(Location location) {
        World world = location.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 1, 0, 0, 0, 0.0);
        world.spawnParticle(Particle.CLOUD, location, 40, 1.5, 1.0, 1.5, 0.08);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);

        if (config.meteorShowerDamageEnabled()) {
            // breakBlocks=false always -- real knockback/damage to anyone nearby, but never destroys terrain
            world.createExplosion(location, (float) config.meteorShowerExplosionPower(), false, false);

            // notify anyone actually caught in the blast radius -- precise (only players who could
            // plausibly have taken damage from THIS impact), rather than hooking a generic
            // EntityDamageEvent that can't tell a meteor's explosion apart from a creeper's
            double blastRadius = Math.max(3.0, config.meteorShowerExplosionPower() * 2.0);
            for (Entity nearby : world.getNearbyEntities(location, blastRadius, blastRadius, blastRadius)) {
                if (nearby instanceof Player player) {
                    advisor.notify(player, "meteor-impact",
                            "§4§lA meteor just came down near you! §cThat blast can do real damage if you're caught in the open.",
                            "§7Solution: get under a roof during a meteor shower -- it can't reach you indoors.");
                }
            }
        }

        if (config.meteorShowerIgniteTarget()) {
            Block impactBlock = location.getBlock();
            if (impactBlock.getType() == Material.AIR) {
                Block below = impactBlock.getRelative(BlockFace.DOWN);
                if (below.getType().isSolid()) {
                    impactBlock.setType(Material.FIRE);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (impactBlock.getType() == Material.FIRE) impactBlock.setType(Material.AIR);
                    }, 20L * 6);
                }
            }
        }
    }

    private long checkIntervalTicks() {
        return 20L * 60L * config.meteorShowerCheckIntervalMinutes();
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return Math.max(1, min);
        return min + random.nextInt(max - min + 1);
    }

    private static class Meteor {
        final Location start;
        final Location target;
        final int fallTicks;
        int elapsedTicks = 0;

        Meteor(Location start, Location target, int fallTicks) {
            this.start = start;
            this.target = target;
            this.fallTicks = Math.max(1, fallTicks);
        }
    }
}
