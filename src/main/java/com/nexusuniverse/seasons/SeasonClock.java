package com.nexusuniverse.seasons;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Tracks the live year/season/day-of-season state and advances it once per
 * Minecraft day (every 24000 ticks) -- one season-day is one full
 * day/night cycle, the same unit vanilla already uses, so a season's
 * real-world length depends on how fast days pass on this server, exactly
 * like everything else time-based.
 *
 * State is saved to season-state.yml, separate from config.yml, since
 * this is live save data (where the calendar currently is) rather than a
 * setting (how long a season lasts). config.yml's starting-year/season
 * only matter the very first time this plugin ever runs on a world --
 * after that the state file is the source of truth.
 */
public class SeasonClock {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final File stateFile;

    private int year;
    private Season season;
    private int dayOfSeason; // 1-indexed

    public SeasonClock(JavaPlugin plugin, SeasonsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.stateFile = new File(plugin.getDataFolder(), "season-state.yml");
        load();
    }

    private void load() {
        if (!stateFile.exists()) {
            this.year = config.startingYear();
            this.season = parseSeason(config.startingSeasonName());
            this.dayOfSeason = 1;
            save();
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(stateFile);
        this.year = data.getInt("year", config.startingYear());
        this.season = parseSeason(data.getString("season", "SPRING"));
        this.dayOfSeason = Math.max(1, data.getInt("day-of-season", 1));
    }

    private Season parseSeason(String name) {
        try {
            return Season.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Season.SPRING;
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("year", year);
        data.set("season", season.name());
        data.set("day-of-season", dayOfSeason);
        try {
            data.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusSeasons: failed to save season-state.yml", e);
        }
    }

    /** Reports what a mutation actually changed, so callers (the chat announcer) know whether to say anything. */
    public record ChangeResult(boolean seasonChanged, boolean yearChanged) {
        static final ChangeResult NONE = new ChangeResult(false, false);
    }

    /** Call once per Minecraft day. Advances the day counter, rolling over into the next season/year as needed. */
    public ChangeResult advanceDay() {
        Season previousSeason = season;
        int previousYear = year;

        dayOfSeason++;
        if (dayOfSeason > config.daysPerSeason()) {
            dayOfSeason = 1;
            season = season.next();
            if (previousSeason == Season.WINTER && season == Season.SPRING) {
                year++;
            }
        }
        save();
        return new ChangeResult(season != previousSeason, year != previousYear);
    }

    public int year() {
        return year;
    }

    public Season season() {
        return season;
    }

    public int dayOfSeason() {
        return dayOfSeason;
    }

    public int daysPerSeason() {
        return config.daysPerSeason();
    }

    public void setYear(int year) {
        this.year = year;
        save();
    }

    public ChangeResult setSeason(Season newSeason) {
        Season previousSeason = season;
        this.season = newSeason;
        this.dayOfSeason = 1;
        save();
        return new ChangeResult(season != previousSeason, false);
    }

    public void setDayOfSeason(int day) {
        this.dayOfSeason = Math.max(1, Math.min(config.daysPerSeason(), day));
        save();
    }
}
