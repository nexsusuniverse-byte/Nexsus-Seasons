package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Two kinds of chat message: a real announcement the moment a season
 * actually changes (broadcast, formatted, unmissable), and a much
 * quieter, randomly-picked ambient flavor line dropped periodically
 * during the season itself -- not gameplay-critical, just texture, the
 * kind of thing a narrator would murmur between beats.
 */
public class SeasonAmbianceManager {

    private static final Map<Season, List<String>> FLAVOR_LINES = Map.of(
            Season.SPRING, List.of(
                    "§7§oNew growth pushes up through the thawing soil.",
                    "§7§oBirdsong returns to the treetops.",
                    "§7§oA warm rain falls, and the world smells of earth.",
                    "§7§oBuds swell on bare branches, waiting to open.",
                    "§7§oThe days are stretching longer again."
            ),
            Season.SUMMER, List.of(
                    "§7§oThe midday sun presses down, relentless.",
                    "§7§oCicadas hum somewhere in the tall grass.",
                    "§7§oHeat shimmers faintly above the open fields.",
                    "§7§oThe nights are short and warm.",
                    "§7§oEverything is in full, heavy bloom."
            ),
            Season.FALL, List.of(
                    "§7§oThe air grows crisp as the first frost approaches.",
                    "§7§oLeaves drift down, gold and brittle.",
                    "§7§oA distant wind carries the smell of woodsmoke.",
                    "§7§oThe days are shortening, quietly.",
                    "§7§oSomething about the season feels like an ending."
            ),
            Season.WINTER, List.of(
                    "§7§oFrost creeps across every still surface.",
                    "§7§oA distant howl echoes through the cold night.",
                    "§7§oYour breath hangs visible in the air.",
                    "§7§oSnow muffles every sound outside.",
                    "§7§oThe world feels smaller, and colder, and quieter."
            )
    );

    private final Random random = new Random();

    /** Broadcast to everyone the moment a season actually changes. */
    public void announceSeasonChange(Season newSeason, int year, boolean yearAlsoChanged) {
        String bar = "§8§m" + "-".repeat(40);
        Bukkit.broadcastMessage(bar);
        Bukkit.broadcastMessage("     " + headlineFor(newSeason, year));
        Bukkit.broadcastMessage(bar);

        if (yearAlsoChanged) {
            Bukkit.broadcastMessage("§7§oA new year begins: §f§lYear " + year + "§7§o.");
        }

        playToAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
    }

    private String headlineFor(Season season, int year) {
        String glyph = switch (season) {
            case SPRING -> "\u273F";
            case SUMMER -> "\u2600";
            case FALL -> "\u2741";
            case WINTER -> "\u2744";
        };
        String color = switch (season) {
            case SPRING -> "§a";
            case SUMMER -> "§e";
            case FALL -> "§c";
            case WINTER -> "§b";
        };
        return "§f" + glyph + " " + color + "§l" + season.displayName().toUpperCase()
                + " §fhas arrived " + glyph + " §7(Year " + year + ")";
    }

    /** Called periodically: drops one random ambient line for the current season into chat. */
    public void ambientTick(Season season) {
        List<String> pool = FLAVOR_LINES.get(season);
        if (pool == null || pool.isEmpty()) return;
        Bukkit.broadcastMessage(pool.get(random.nextInt(pool.size())));
        playToAll(ambientSoundFor(season), 0.35f, 1.0f);
    }

    private Sound ambientSoundFor(Season season) {
        return switch (season) {
            case SPRING -> Sound.ENTITY_PARROT_AMBIENT;   // birdsong
            case SUMMER -> Sound.ENTITY_BEE_LOOP;         // insect hum in the heat
            case FALL -> Sound.BLOCK_AZALEA_LEAVES_STEP;  // dry rustling leaves
            case WINTER -> Sound.AMBIENT_CAVE;            // hollow, cold, distant
        };
    }

    private void playToAll(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
