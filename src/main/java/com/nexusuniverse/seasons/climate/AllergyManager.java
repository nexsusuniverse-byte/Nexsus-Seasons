package com.nexusuniverse.seasons.climate;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A harmless, slightly silly seasonal allergy. New players have a chance of being allergic to one
 * kind of pollen. When that kind is high outside and they are out in it, they sneeze: a panda-sized
 * sound and a chat line, nothing more. No damage, no effects.
 */
public final class AllergyManager implements Listener {

    private static final String[] SNEEZES = {
            "§e*ACHOO!* §7Your nose has opinions about the {pollen}.",
            "§e*Achoo!* §7The {pollen} has found you again.",
            "§e*Ah... ah... ACHOO!* §7Bless you.",
            "§e*Sniff... ACHOO!* §7Maybe stand indoors for a bit.",
            "§e*ACHOO!* §7That was a {level} {pollen} count talking."
    };

    private static final long COOLDOWN_MS = 25_000L;

    private final JavaPlugin plugin;
    private final ClimateSettings settings;
    private final ClimatePlayers players;
    private final ClimateManager climate;
    private final Random random = new Random();
    private final Map<UUID, Long> nextSneeze = new HashMap<>();
    private BukkitTask task;

    public AllergyManager(JavaPlugin plugin, ClimateSettings settings, ClimatePlayers players, ClimateManager climate) {
        this.plugin = plugin;
        this.settings = settings;
        this.players = players;
        this.climate = climate;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    /** Rolls an allergy the first time a player is seen. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.allergiesEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (players.known(id)) {
            return;
        }
        AllergyType rolled = null;
        if (random.nextDouble() < settings.allergyChance()) {
            double r = random.nextDouble();
            rolled = r < 0.30 ? AllergyType.TREE : r < 0.60 ? AllergyType.GRASS : r < 0.90 ? AllergyType.WEED : AllergyType.MOLD;
        }
        players.setAllergy(id, rolled);
        if (rolled != null) {
            AllergyType finalRolled = rolled;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§aSeasonal allergies: §7you seem to be allergic to §f" + finalRolled.display()
                            + "§7. Watch the Pollen line on the weather sidebar, and see §f/allergy§7.");
                }
            }, 100L);
        }
    }

    private void tick() {
        if (!settings.allergiesEnabled() || !settings.sneezing()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            AllergyType allergy = players.allergy(player.getUniqueId());
            if (allergy == null) {
                continue;
            }
            Reading r = climate.reading(player);
            if (r == null || r.sheltered() || r.pollenLevel() < 3 || r.pollen().family() != allergy) {
                continue;
            }
            if (now < nextSneeze.getOrDefault(player.getUniqueId(), 0L)) {
                continue;
            }
            double chance = r.pollenLevel() >= 4 ? 1.0 / 20.0 : 1.0 / 40.0;
            if (random.nextDouble() < chance) {
                nextSneeze.put(player.getUniqueId(), now + COOLDOWN_MS);
                sneeze(player, r);
            }
        }
    }

    private void sneeze(Player player, Reading r) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PANDA_SNEEZE, 1.0f, 1.1f);
        String line = SNEEZES[random.nextInt(SNEEZES.length)]
                .replace("{pollen}", r.pollen().display().toLowerCase())
                .replace("{level}", Climate.POLLEN_NAMES[r.pollenLevel()].toLowerCase());
        player.sendMessage(line);
    }
}
