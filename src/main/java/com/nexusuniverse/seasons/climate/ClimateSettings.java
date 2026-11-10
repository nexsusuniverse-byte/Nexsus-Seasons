package com.nexusuniverse.seasons.climate;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Reads the climate, forecast, allergy and sidebar settings from config.yml (live, so /nexusseasons reload works). */
public final class ClimateSettings {

    private final JavaPlugin plugin;

    public ClimateSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    public boolean climateEnabled() {
        return c().getBoolean("climate.enabled", true);
    }

    public boolean celsius() {
        return "C".equalsIgnoreCase(c().getString("climate.units", "F"));
    }

    public long configuredSeed() {
        return c().getLong("climate.seed", 0L);
    }

    public ClimateParams params(long worldSeed, int daysPerSeason) {
        long seed = configuredSeed() != 0L ? configuredSeed() : worldSeed;
        return new ClimateParams(seed, daysPerSeason,
                c().getDouble("climate.annual-mean-f", 54.0),
                c().getDouble("climate.annual-swing-f", 26.0),
                c().getDouble("climate.spell-swing-f", 6.0),
                c().getDouble("climate.daily-range-clear-f", 20.0),
                c().getDouble("climate.daily-range-cloudy-f", 11.0),
                -Math.abs(c().getDouble("climate.rain-cooling-f", 5.0)),
                -Math.abs(c().getDouble("climate.thunder-cooling-f", 8.0)),
                -Math.abs(c().getDouble("climate.blizzard-cooling-f", 24.0)),
                -Math.abs(c().getDouble("climate.fog-cooling-f", 2.0)),
                celsius());
    }

    // ---- the 7 AM report ---------------------------------------------------------------------------

    public boolean forecastEnabled() {
        return c().getBoolean("forecast.enabled", true);
    }

    /** World tick the report is issued at. 1000 is 7:00 AM (tick 0 is 6:00 AM). */
    public long forecastTick() {
        return Math.max(0, Math.min(23999, c().getLong("forecast.hour-tick", 1000L)));
    }

    public long forecastCatchUpTicks() {
        return Math.max(20, c().getLong("forecast.catch-up-window-ticks", 3000L));
    }

    public boolean forecastBroadcast() {
        return c().getBoolean("forecast.broadcast", true);
    }

    public String forecastWorld() {
        return c().getString("forecast.world", "");
    }

    // ---- allergies ----------------------------------------------------------------------------------------

    public boolean allergiesEnabled() {
        return c().getBoolean("allergies.enabled", true);
    }

    public double allergyChance() {
        return Math.max(0.0, Math.min(1.0, c().getDouble("allergies.chance-per-player", 0.3)));
    }

    public boolean sneezing() {
        return c().getBoolean("allergies.sneezing", true);
    }

    public boolean allergySelfSet() {
        return c().getBoolean("allergies.allow-self-set", true);
    }

    // ---- sidebar ------------------------------------------------------------------------------------------

    public Sidebar.Show show() {
        return new Sidebar.Show(
                c().getBoolean("weather-hud.show.temperature", true),
                c().getBoolean("weather-hud.show.humidity", true),
                c().getBoolean("weather-hud.show.dew-point", true),
                c().getBoolean("weather-hud.show.wind", true),
                c().getBoolean("weather-hud.show.pollen", true),
                c().getBoolean("weather-hud.show.uv-index", true));
    }

    public boolean hideScoreNumbers() {
        return c().getBoolean("weather-hud.hide-score-numbers", true);
    }

    /** The Euro corner at the bottom of the sidebar (a jab plus the temperature in Celsius). */
    public boolean euroCorner() {
        return c().getBoolean("weather-hud.euro-corner.enabled", true);
    }

    /** The jab itself; blank falls back to the default so the corner never shows an empty line. */
    public String euroJab() {
        String text = c().getString("weather-hud.euro-corner.text", DEFAULT_EURO_JAB);
        return text == null || text.isBlank() ? DEFAULT_EURO_JAB : text.trim();
    }

    public static final String DEFAULT_EURO_JAB = "AC? Just open a window.";
}
