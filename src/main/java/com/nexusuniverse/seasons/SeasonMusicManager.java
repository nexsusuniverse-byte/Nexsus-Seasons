package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A real, looping seasonal soundtrack -- entirely vanilla Sound.MUSIC_*
 * tracks, no resource pack or custom audio involved. There's one active
 * track for the whole server at a time (like a radio station, not
 * separate per-player playlists): every music.track-length-seconds it
 * stops and starts a new random pick from the current season's pool, and
 * it switches immediately (not waiting for the current track to end)
 * the moment the season itself changes.
 *
 * Played under SoundCategory.MUSIC specifically, so it respects each
 * player's own in-game Music volume slider and gets muted/lowered by
 * whatever they've already set there -- exactly how vanilla background
 * music behaves, not something layered on top of it ignoring their
 * settings.
 *
 * HONEST LIMITATION: Sound.playSound() plays a track once through; there
 * is no true "loop until stopped" flag exposed here. This fakes looping
 * by re-triggering a (usually different) track on a fixed interval. Real
 * vanilla tracks vary in length -- this won't line up perfectly with
 * where any given track "ends," the same tradeoff a radio station's
 * fixed rotation makes.
 */
public class SeasonMusicManager implements Listener {

    private static final Map<Season, List<Sound>> TRACK_POOLS = Map.of(
            Season.SPRING, List.of(
                    Sound.MUSIC_OVERWORLD_FLOWER_FOREST,
                    Sound.MUSIC_OVERWORLD_MEADOW,
                    Sound.MUSIC_OVERWORLD_CHERRY_GROVE
            ),
            Season.SUMMER, List.of(
                    Sound.MUSIC_OVERWORLD_FOREST,
                    Sound.MUSIC_OVERWORLD_JUNGLE,
                    Sound.MUSIC_OVERWORLD_SPARSE_JUNGLE,
                    Sound.MUSIC_OVERWORLD_BAMBOO_JUNGLE
            ),
            Season.FALL, List.of(
                    Sound.MUSIC_OVERWORLD_OLD_GROWTH_TAIGA,
                    Sound.MUSIC_OVERWORLD_SWAMP,
                    Sound.MUSIC_GAME
            ),
            Season.WINTER, List.of(
                    Sound.MUSIC_OVERWORLD_SNOWY_SLOPES,
                    Sound.MUSIC_OVERWORLD_FROZEN_PEAKS,
                    Sound.MUSIC_OVERWORLD_JAGGED_PEAKS,
                    Sound.MUSIC_OVERWORLD_GROVE
            )
    );

    private final SeasonsConfig config;
    private final Random random = new Random();
    private Sound currentTrack;

    public SeasonMusicManager(SeasonsConfig config) {
        this.config = config;
    }

    /** Stops whatever's playing and immediately starts a track for the given (usually just-changed) season. */
    public void switchToSeason(Season season) {
        if (!config.musicEnabled()) return;
        stopCurrentForAll();
        playNewTrack(season);
    }

    /** Called on the track-rotation timer: swaps to another random track from the same season's pool. */
    public void rotateTrack(Season season) {
        if (!config.musicEnabled()) return;
        stopCurrentForAll();
        playNewTrack(season);
    }

    private void playNewTrack(Season season) {
        List<Sound> pool = TRACK_POOLS.get(season);
        if (pool == null || pool.isEmpty()) return;

        currentTrack = pickDifferentTrack(pool);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), currentTrack, SoundCategory.MUSIC, 1.0f, 1.0f);
        }
    }

    private Sound pickDifferentTrack(List<Sound> pool) {
        if (pool.size() == 1) return pool.get(0);
        Sound next;
        do {
            next = pool.get(random.nextInt(pool.size()));
        } while (next == currentTrack);
        return next;
    }

    private void stopCurrentForAll() {
        if (currentTrack == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.stopSound(currentTrack, SoundCategory.MUSIC);
        }
    }

    /** New joiners hear whatever's currently playing rather than silence until the next rotation. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.musicEnabled() || currentTrack == null) return;
        event.getPlayer().playSound(event.getPlayer().getLocation(), currentTrack, SoundCategory.MUSIC, 1.0f, 1.0f);
    }

    public void stopAll() {
        stopCurrentForAll();
        currentTrack = null;
    }
}
