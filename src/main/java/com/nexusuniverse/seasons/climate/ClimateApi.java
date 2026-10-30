package com.nexusuniverse.seasons.climate;

import org.bukkit.entity.Player;

/**
 * What other plugins may ask NexusSeasons about the weather around a player. Look it up with
 * {@code Bukkit.getServicesManager().load(ClimateApi.class)}, or by reflection on
 * {@code com.nexusuniverse.seasons.climate.ClimateApi} when there is no compile-time dependency.
 *
 * <p>All temperatures are in degrees Fahrenheit, whatever the players' display units are. Every method returns
 * {@code NaN} (or {@code "NONE"}) for a player who is not in an overworld-type world: ask the world yourself there.
 */
public interface ClimateApi {

    /** The air temperature where the player stands (already eased, and settled toward room temperature indoors). */
    double temperatureF(Player player);

    /** What it feels like (wind chill when cold and windy, heat index when hot and humid). */
    double feelsLikeF(Player player);

    double windMph(Player player);

    double uvIndex(Player player);

    /** True under a roof or otherwise out of the sky, wind and rain. */
    boolean sheltered(Player player);

    /** "NONE", "RAIN", "SNOW" or "STORM" (thunderstorm), as it is falling on the player right now. Never rain on a sheltered player. */
    String precipitation(Player player);

    /**
     * A body-temperature plugin (NexusWardrobe) calls this with true so the old summer heat exhaustion in this
     * plugin stands down and the two do not both punish the same player for the same sun.
     */
    void setBodyHeatHandledElsewhere(boolean handled);

    boolean bodyHeatHandledElsewhere();
}
