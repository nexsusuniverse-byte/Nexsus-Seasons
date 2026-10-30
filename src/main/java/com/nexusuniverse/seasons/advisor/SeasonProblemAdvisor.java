package com.nexusuniverse.seasons.advisor;

import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One central place for "you're taking damage -- here's what's going on, here's the fix"
 * messaging in NexusSeasons, mirroring NexusSurvival's own ProblemAdvisor (same idea, ported here
 * as its own small class since the two plugins don't share any code). Every time it fires, it
 * sends two lines back to back: a bold diagnosis, then a plain solution right behind it -- the
 * "what's happening" + "what do I do about it" pairing, same as NexusSurvival's version.
 *
 * Doesn't fire on every single damage tick -- fires once the moment a problem starts hurting a
 * player, then at most once every messages.reminder-interval-seconds (config.yml, default 30s)
 * for as long as the problem keeps going. Unlike NexusSurvival (which already has one shared
 * per-player data object every system reads/writes through), NexusSeasons has no equivalent, so
 * this class owns its own small per-player throttle map directly (UUID -> problem key -> last
 * notified timestamp) and cleans a player's entry up on quit so it can't grow unbounded over a
 * long server uptime.
 */
public class SeasonProblemAdvisor implements Listener {

    private final SeasonsConfig config;
    private final Map<UUID, Map<String, Long>> lastMessageAt = new ConcurrentHashMap<>();

    public SeasonProblemAdvisor(SeasonsConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessageAt.remove(event.getPlayer().getUniqueId());
    }

    /**
     * @param key       stable per-problem identifier ("heat-exhaustion", "meteor-impact", ...) --
     *                  scoped per player, so different players/problems never share a throttle
     *                  window.
     * @param diagnosis what's going on, sent first.
     * @param solution  the fix, sent immediately after as its own line.
     */
    public void notify(Player player, String key, String diagnosis, String solution) {
        if (!config.messagesEnabled()) return;

        long now = System.currentTimeMillis();
        long intervalMs = 1000L * config.messagesReminderIntervalSeconds();
        Map<String, Long> perPlayer = lastMessageAt.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        Long last = perPlayer.get(key);
        if (last != null && now - last < intervalMs) return;

        perPlayer.put(key, now);
        player.sendMessage(diagnosis);
        player.sendMessage(solution);
    }

    /**
     * Call the moment a problem actually stops (cooled back down, shower ended, ...) so if it
     * starts again later it's treated as a brand-new onset -- an immediate message -- rather than
     * still being inside the previous reminder window from before it was resolved.
     */
    public void clear(Player player, String key) {
        Map<String, Long> perPlayer = lastMessageAt.get(player.getUniqueId());
        if (perPlayer != null) perPlayer.remove(key);
    }
}
