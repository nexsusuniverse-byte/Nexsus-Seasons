package com.nexusuniverse.seasons;

/**
 * Public read-only surface for other plugins to query the current season
 * state without a hard compile-time dependency on this plugin --
 * NexusSurvival's seasonal disease/mob danger multipliers use this, and
 * it's designed for NexusKairos to hook into later for storyline purposes
 * the same way.
 *
 * Registered via Bukkit's ServicesManager on enable. Other plugins look
 * it up with:
 *
 *   RegisteredServiceProvider&lt;NexusSeasonsAPI&gt; provider =
 *       Bukkit.getServicesManager().getRegistration(NexusSeasonsAPI.class);
 *   if (provider != null) {
 *       NexusSeasonsAPI api = provider.getProvider();
 *       Season current = api.getCurrentSeason();
 *   }
 *
 * A plugin with no compile-time dependency on NexusSeasons (like
 * NexusSurvival, built as a fully separate jar) instead does this via
 * reflection -- see NexusSurvival's SeasonBridge for that pattern.
 */
public interface NexusSeasonsAPI {

    Season getCurrentSeason();

    int getCurrentYear();

    int getDayOfSeason();

    int getDaysPerSeason();
}
