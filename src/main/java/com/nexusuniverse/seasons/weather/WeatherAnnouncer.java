package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * One shared chat-warning system for every event in this "crazy weather" layer -- an actual line
 * in chat when something starts or clears, rather than a player only noticing because the sky or
 * HUD changed. Every message is plain config.yml text (weather-announce.messages.*), not
 * hardcoded, so wording is fully editable without touching code -- and a blank/missing message
 * for a given key is how that specific announcement gets silenced, no separate per-event toggle
 * needed on top of the one master weather-announce.enabled switch.
 *
 * Broadcasts only to players in the SAME world the event is happening in, not server-wide --
 * consistent with how every other effect in this layer only ever affects the world it's tied to.
 *
 * Deliberately fires at the moment each event actually starts/ends, not as a multi-second advance
 * countdown before it arrives -- TsunamiManager already has its own genuine pending-warning
 * countdown (a real gap between the warning and the wave actually hitting), which this doesn't
 * change or duplicate. Turning every other event into a full delayed-arrival system too would be
 * a much bigger change than "add chat messages" -- flagged here in case that's what's wanted next.
 */
final class WeatherAnnouncer {

    private WeatherAnnouncer() {
    }

    static void announce(World world, SeasonsConfig config, String messageKey, String defaultMessage) {
        if (!config.weatherAnnounceEnabled()) return;
        String message = config.weatherAnnounceMessage(messageKey, defaultMessage);
        if (message == null || message.isBlank()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * For the events in this layer that aren't tied to one specific Location -- dry thunderstorms,
     * fog, blizzards, sandstorms, earthquakes, meteor showers, and hurricanes are all simple
     * plugin-wide start/stop toggles (forceStart(seconds)/forceStop(), no world or location
     * argument) rather than something spawned at a point the way a tornado or tsunami is, so
     * there's no single world to scope a message to -- this reaches every online player instead.
     */
    static void announceGlobal(SeasonsConfig config, String messageKey, String defaultMessage) {
        if (!config.weatherAnnounceEnabled()) return;
        String message = config.weatherAnnounceMessage(messageKey, defaultMessage);
        if (message == null || message.isBlank()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }
}
