package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * One shared boss bar for the whole server, not per-player state like
 * NexusSurvival's thirst/radiation bars -- the season, year, and day are
 * the same for everyone, so there's exactly one BossBar instance with
 * every online player added to it, kept in sync by update() rather than
 * needing separate tracking per player.
 *
 * HONEST LIMITATION: a boss bar has no icon/image slot -- the API only
 * exposes a title string, a color, a fill style, and a progress
 * fraction. There's no way to show an actual picture here. Everything
 * "elaborate" below is built out of that: bold season name, a
 * seasonal ornament glyph on both sides, color-coded segments, and a
 * bar color that changes with the season -- the real ceiling of what
 * this API can render, not a placeholder for something richer.
 */
public class SeasonBossBar implements Listener {

    private final BossBar bar;

    public SeasonBossBar() {
        this.bar = Bukkit.createBossBar("§7Loading season...", BarColor.WHITE, BarStyle.SOLID);
        this.bar.setProgress(0.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        bar.addPlayer(event.getPlayer());
    }

    public void update(Season season, int year, int dayOfSeason, int daysPerSeason) {
        bar.setTitle(buildTitle(season, year, dayOfSeason, daysPerSeason));
        bar.setColor(barColorFor(season));
        bar.setProgress(Math.max(0.0, Math.min(1.0, dayOfSeason / (double) daysPerSeason)));
    }

    private String buildTitle(Season season, int year, int dayOfSeason, int daysPerSeason) {
        String glyph = glyphFor(season);
        String seasonColor = textColorFor(season);

        return "§f" + glyph + " " + seasonColor + "§l" + season.displayName().toUpperCase() + " §f" + glyph
                + "  §7Year §f§l" + year
                + " §8\u2726 §7Day §f" + dayOfSeason + "§8/§7" + daysPerSeason;
    }

    private String glyphFor(Season season) {
        return switch (season) {
            case SPRING -> "\u273F"; // florette
            case SUMMER -> "\u2600"; // sun
            case FALL -> "\u2741";   // blossom, standing in for a falling leaf
            case WINTER -> "\u2744"; // snowflake
        };
    }

    private String textColorFor(Season season) {
        return switch (season) {
            case SPRING -> "§a";
            case SUMMER -> "§e";
            case FALL -> "§c";
            case WINTER -> "§b";
        };
    }

    private BarColor barColorFor(Season season) {
        return switch (season) {
            case SPRING -> BarColor.GREEN;
            case SUMMER -> BarColor.YELLOW;
            case FALL -> BarColor.RED;
            case WINTER -> BarColor.WHITE;
        };
    }

    public void removeAll() {
        bar.removeAll();
    }
}
