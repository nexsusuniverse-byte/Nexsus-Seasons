package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.Season;
import com.nexusuniverse.seasons.SeasonClock;
import com.nexusuniverse.seasons.SeasonsConfig;
import com.nexusuniverse.seasons.advisor.SeasonProblemAdvisor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Summer heat exhaustion: standing exposed under open sky (the same isExposedToSky check
 * WindManager already uses, reused here rather than duplicated) during Summer, without an active
 * storm cooling things off, builds heat up in discrete STAGES rather than flipping on instantly --
 * the longer you stay out in it, the worse it gets, and it recedes the same way once you get into
 * shade/indoors. Stage 1: weakness. 2: + mining fatigue. 3: + hunger. max-stage (default 4): all
 * of the above plus a real chance of damage each second -- genuine heatstroke, not just an
 * inconvenience.
 *
 * A Cooling Cap (see CoolingCap) worn in the helmet slot makes a player fully immune -- no
 * accumulation at all while worn, exactly like WindproofBoots makes a player immune to wind.
 *
 * The stage-max damage gets diagnosis+solution messaging through SeasonProblemAdvisor, the same
 * pattern NexusSurvival uses for its own damage-causing systems.
 */
public class HeatExhaustionManager implements Listener {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final SeasonClock clock;
    private final WindManager wind; // reused only for its isExposedToSky() helper, not the wind push itself
    private final CoolingCap coolingCap;
    private final SeasonProblemAdvisor advisor;
    private final Random random = new Random();
    private final Map<UUID, HeatState> states = new HashMap<>();
    private BukkitTask task;
    private java.util.function.BooleanSupplier standDown = () -> false;

    public HeatExhaustionManager(JavaPlugin plugin, SeasonsConfig config, SeasonClock clock,
                                  WindManager wind, CoolingCap coolingCap, SeasonProblemAdvisor advisor) {
        this.plugin = plugin;
        this.config = config;
        this.clock = clock;
        this.wind = wind;
        this.coolingCap = coolingCap;
        this.advisor = advisor;
    }

    /** Lets a body-temperature plugin take over: while this says true, summer heat exhaustion does nothing new. */
    public void setStandDown(java.util.function.BooleanSupplier standDown) {
        this.standDown = standDown;
    }

    public void start() {
        if (!config.heatExhaustionEnabled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); // once a second is plenty -- this escalates over tens of seconds, not ticks
    }

    public void stop() {
        if (task != null) task.cancel();
        states.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        if (standDown.getAsBoolean()) {
            if (!states.isEmpty()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    HeatState state = states.get(player.getUniqueId());
                    if (state != null) {
                        state.stage = 0;
                        state.tickCounter = 0;
                        applyEffects(player, state);
                    }
                }
                states.clear();
            }
            return;
        }
        boolean summer = clock.season() == Season.SUMMER;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.NORMAL) continue;
            HeatState state = states.get(player.getUniqueId());
            if (state == null) {
                if (!summer) continue; // never been exposed and it's not even summer -- nothing to track yet
                state = new HeatState();
                states.put(player.getUniqueId(), state);
            }

            boolean exposed = summer && !coolingCap.isWorn(player)
                    && wind.isExposedToSky(player.getLocation()) && !player.getWorld().hasStorm();

            if (exposed) {
                advance(player, state);
            } else {
                recede(player, state);
            }

            applyEffects(player, state);
        }
    }

    private void advance(Player player, HeatState state) {
        state.tickCounter++;
        int stageSeconds = Math.max(1, config.heatExposureSecondsPerStage());
        if (state.tickCounter < stageSeconds) return;
        state.tickCounter = 0;

        int maxStage = Math.max(0, config.heatMaxStage());
        if (state.stage < maxStage) {
            state.stage++;
            if (state.stage == 1) {
                player.sendMessage("§eThe summer heat is getting to you...");
            }
        }
    }

    private void recede(Player player, HeatState state) {
        if (state.stage <= 0) {
            state.tickCounter = 0;
            return;
        }
        int recoverySeconds = Math.max(1, config.heatRecoverySecondsPerStage());
        state.tickCounter++;
        if (state.tickCounter >= recoverySeconds) {
            state.tickCounter = 0;
            state.stage--;
            if (state.stage == 0) {
                player.sendMessage("§bYou've cooled back down.");
                advisor.clear(player, "heat-exhaustion");
            }
        }
    }

    private void applyEffects(Player player, HeatState state) {
        if (state.stage <= 0) return;

        if (state.stage >= 1) player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, false));
        if (state.stage >= 2) player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 0, true, false));
        if (state.stage >= 3) player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 0, true, false));

        int maxStage = Math.max(0, config.heatMaxStage());
        if (maxStage > 0 && state.stage >= maxStage && random.nextDouble() < config.heatDamageChancePerSecond()) {
            player.damage(config.heatDamageAmount());
            advisor.notify(player, "heat-exhaustion",
                    "§4§lYou're suffering heatstroke! §cThe summer sun is now dealing real damage.",
                    "§7Solution: get into shade or indoors, or wear a Cooling Cap to stop it completely.");
        }
    }

    private static class HeatState {
        int stage = 0;
        int tickCounter = 0;
    }
}
