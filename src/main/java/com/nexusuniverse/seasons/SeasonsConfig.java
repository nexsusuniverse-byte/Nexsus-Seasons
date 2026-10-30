package com.nexusuniverse.seasons;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SeasonsConfig {

    private final JavaPlugin plugin;

    public SeasonsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        // saveDefaultConfig() only ever writes config.yml the very first time this plugin is
        // installed -- an update that adds new keys (or changes a default, like day-night's
        // below) would otherwise never reach a server that already has a config.yml on disk.
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public int daysPerSeason() {
        return Math.max(1, plugin.getConfig().getInt("season.days-per-season", 30));
    }

    public int startingYear() {
        return plugin.getConfig().getInt("season.starting-year", 356);
    }

    public String startingSeasonName() {
        return plugin.getConfig().getString("season.starting-season", "SPRING");
    }

    public double plantGrowthMultiplier(Season season) {
        return plugin.getConfig().getDouble("plant-growth." + season.name().toLowerCase(), 1.0);
    }

    public double mobSpawnWeight(Season season, String mobKey) {
        return plugin.getConfig().getDouble("mob-spawns." + season.name().toLowerCase() + "." + mobKey, 1.0);
    }

    public boolean snowEnabled() {
        return plugin.getConfig().getBoolean("visuals.snow-accumulation", true);
    }

    public int snowSweepBlocksPerTick() {
        return plugin.getConfig().getInt("visuals.snow-blocks-per-tick", 64);
    }

    public int sweepIntervalSeconds() {
        return Math.max(1, plugin.getConfig().getInt("visuals.sweep-interval-seconds", 5));
    }

    public boolean transitionMessagesEnabled() {
        return plugin.getConfig().getBoolean("ambiance.transition-enabled", true);
    }

    public boolean ambianceEnabled() {
        return plugin.getConfig().getBoolean("ambiance.enabled", true);
    }

    public int ambianceMinIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("ambiance.min-interval-minutes", 8));
    }

    public int ambianceMaxIntervalMinutes() {
        return Math.max(ambianceMinIntervalMinutes(), plugin.getConfig().getInt("ambiance.max-interval-minutes", 20));
    }

    public boolean musicEnabled() {
        return plugin.getConfig().getBoolean("music.enabled", true);
    }

    public int musicTrackLengthSeconds() {
        return Math.max(20, plugin.getConfig().getInt("music.track-length-seconds", 210));
    }

    public boolean customDayNightEnabled() {
        return plugin.getConfig().getBoolean("day-night.enabled", true);
    }

    public int dayLengthMinutes() {
        return Math.max(1, plugin.getConfig().getInt("day-night.day-length-minutes", 720));
    }

    public int nightLengthMinutes() {
        return Math.max(1, plugin.getConfig().getInt("day-night.night-length-minutes", 720));
    }

    public boolean timeBossBarEnabled() {
        return plugin.getConfig().getBoolean("time-boss-bar.enabled", true);
    }

    public int timeBossBarRefreshIntervalSeconds() {
        return Math.max(1, plugin.getConfig().getInt("time-boss-bar.refresh-interval-seconds", 1));
    }

    public long timeBossBarNightfallWarningLeadTicks() {
        return Math.max(0, plugin.getConfig().getInt("time-boss-bar.nightfall-warning-lead-ticks", 1000));
    }

    public boolean cycleLockEnabled() {
        return plugin.getConfig().getBoolean("cycle-lock.enabled", true);
    }

    // --- keep-inventory events (scheduled, server-wall-clock-time, gamerule-enforced -- see KeepInventoryEventManager) ---

    public boolean keepInventoryEventsEnabled() {
        return plugin.getConfig().getBoolean("keep-inventory-events.enabled", false);
    }

    public boolean keepInventoryBlockManualChanges() {
        return plugin.getConfig().getBoolean("keep-inventory-events.block-manual-changes", true);
    }

    public int keepInventoryWarningLeadMinutes() {
        return Math.max(0, plugin.getConfig().getInt("keep-inventory-events.warning-lead-minutes", 5));
    }

    public int keepInventoryCountdownSeconds() {
        return Math.max(0, plugin.getConfig().getInt("keep-inventory-events.countdown-seconds", 10));
    }

    public String keepInventoryMessage(String key, String defaultMessage) {
        return plugin.getConfig().getString("keep-inventory-events.messages." + key, defaultMessage);
    }

    /**
     * Parses keep-inventory-events.schedule -- a list of {start: "HH:mm", duration-minutes: N}
     * maps. A malformed entry (bad/missing time, non-positive duration) is skipped rather than
     * failing the whole list, same as parseMaterialList() below does for a bad material name.
     */
    public List<KeepInventoryWindow> keepInventorySchedule() {
        List<KeepInventoryWindow> windows = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("keep-inventory-events.schedule")) {
            Object startRaw = entry.get("start");
            Object durationRaw = entry.get("duration-minutes");
            if (startRaw == null || durationRaw == null) continue;
            try {
                LocalTime start = LocalTime.parse(startRaw.toString().trim());
                int duration = Integer.parseInt(durationRaw.toString().trim());
                if (duration <= 0) continue;
                windows.add(new KeepInventoryWindow(start, duration));
            } catch (Exception ignored) {
                // bad entry -- skip it rather than failing the whole schedule
            }
        }
        return windows;
    }

    public boolean weatherCycleEnabled() {
        return plugin.getConfig().getBoolean("weather.enabled", true);
    }

    public int weatherClearMinMinutes() {
        return Math.max(1, plugin.getConfig().getInt("weather.clear-min-minutes", 20));
    }

    public int weatherClearMaxMinutes() {
        return Math.max(weatherClearMinMinutes(), plugin.getConfig().getInt("weather.clear-max-minutes", 45));
    }

    public int weatherRainMinMinutes() {
        return Math.max(1, plugin.getConfig().getInt("weather.rain-min-minutes", 10));
    }

    public int weatherRainMaxMinutes() {
        return Math.max(weatherRainMinMinutes(), plugin.getConfig().getInt("weather.rain-max-minutes", 25));
    }

    public double weatherThunderChance() {
        return Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("weather.thunder-chance", 0.35)));
    }

    /** Persists the toggle immediately -- this is the runtime switch /nexusseasons cyclelock flips, not a config.yml-edit-and-restart setting. */
    public void setCycleLockEnabled(boolean enabled) {
        plugin.getConfig().set("cycle-lock.enabled", enabled);
        plugin.saveConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    // --- wind (continuous ambient) ---

    public boolean windEnabled() {
        return plugin.getConfig().getBoolean("wind.enabled", true);
    }

    public double windMinStrength() {
        return clamp01(plugin.getConfig().getDouble("wind.min-strength", 0.05));
    }

    public double windMaxStrength() {
        return clamp01(plugin.getConfig().getDouble("wind.max-strength", 0.5));
    }

    public int windChangeIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("wind.change-interval-minutes", 10));
    }

    public boolean windPushPlayers() {
        return plugin.getConfig().getBoolean("wind.push-players", true);
    }

    public double windPlayerPushMinStrength() {
        return clamp01(plugin.getConfig().getDouble("wind.player-push-min-strength", 0.3));
    }

    public double windPlayerPushMultiplier() {
        return plugin.getConfig().getDouble("wind.player-push-multiplier", 0.15);
    }

    /**
     * Whether inventory weight modifies how hard wind pushes a player at all -- true by default.
     * When on, an empty inventory and a full one get genuinely different push multipliers below;
     * when off, everyone is pushed the same regardless of what they're carrying (the old behavior).
     */
    public boolean windInventoryWeightEnabled() {
        return plugin.getConfig().getBoolean("wind.inventory-weight.enabled", true);
    }

    /** Ground push multiplier for a completely empty inventory -- higher than 1.0 means an empty-handed player gets shoved around MORE than the old flat behavior did. */
    public double windGroundPushMultiplierEmpty() {
        return Math.max(0.0, plugin.getConfig().getDouble("wind.inventory-weight.ground-push-multiplier-empty", 1.5));
    }

    /** Ground push multiplier for a completely full inventory (all 36 storage slots occupied) -- deliberately near-zero so a genuinely well-stocked player barely gets pushed at all, matching "shouldn't be pushed easily, if at all." Paired with windInventoryWeightFullnessCurve() below, which makes a player reach near-this-level of protection well before every single slot is literally full. */
    public double windGroundPushMultiplierFull() {
        return Math.max(0.0, plugin.getConfig().getDouble("wind.inventory-weight.ground-push-multiplier-full", 0.05));
    }

    /**
     * Reshapes raw inventory fullness before it's used to blend between the empty/full push
     * multipliers -- under 1.0 makes fullness ramp toward "counts as full" much faster than a
     * straight line would. Without this, a player needed literally every one of the 36 storage
     * slots occupied to get real wind resistance, and in practice almost nobody keeps every slot
     * full (people leave room for pickups) -- so most "my inventory is full" players were only
     * ever getting a small fraction of the intended protection. At the default 0.4, being 70%
     * full already counts as ~87% full for push-resistance purposes.
     */
    public double windInventoryWeightFullnessCurve() {
        return Math.max(0.05, plugin.getConfig().getDouble("wind.inventory-weight.fullness-curve", 0.4));
    }

    /**
     * Elytra gliding gets the OPPOSITE relationship from ground movement on purpose -- weight that
     * anchors you on the ground destabilizes you in the air, so a heavily-loaded glider should be
     * MORE at the mercy of the wind, not less.
     */
    public double windGlidePushMultiplierEmpty() {
        return Math.max(0.0, plugin.getConfig().getDouble("wind.inventory-weight.glide-push-multiplier-empty", 0.4));
    }

    public double windGlidePushMultiplierFull() {
        return Math.max(0.0, plugin.getConfig().getDouble("wind.inventory-weight.glide-push-multiplier-full", 1.8));
    }

    public double windSevereThreshold() {
        return clamp01(plugin.getConfig().getDouble("wind.severe-threshold", 0.7));
    }

    public double windDislodgeChancePerTick() {
        return Math.max(0.0, plugin.getConfig().getDouble("wind.dislodge-chance-per-tick", 0.02));
    }

    public int windDislodgeSearchRadius() {
        return Math.max(1, plugin.getConfig().getInt("wind.dislodge-search-radius", 12));
    }

    public List<Material> windFragileMaterials() {
        return parseMaterialList("wind.fragile-materials", DEFAULT_FRAGILE_MATERIALS);
    }

    // --- wind gusts (short, sharper direction/strength bursts layered on the steady drift) ---

    public boolean windGustEnabled() {
        return plugin.getConfig().getBoolean("wind.gust.enabled", true);
    }

    public int windGustCheckIntervalSeconds() {
        return Math.max(1, plugin.getConfig().getInt("wind.gust.check-interval-seconds", 15));
    }

    public double windGustChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("wind.gust.chance", 0.3));
    }

    public int windGustDurationMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("wind.gust.duration-min-seconds", 3));
    }

    public int windGustDurationMaxSeconds() {
        return Math.max(windGustDurationMinSeconds(), plugin.getConfig().getInt("wind.gust.duration-max-seconds", 8));
    }

    /** How far off the steady wind direction a gust can swing, in degrees either side -- 180 allows a near-total reversal. */
    public double windGustDirectionSwingDegrees() {
        return Math.max(0, Math.min(180, plugin.getConfig().getDouble("wind.gust.direction-swing-degrees", 120)));
    }

    /** How much stronger than the current steady wind a gust gets, before the 1.0 hard cap. */
    public double windGustStrengthMultiplier() {
        return Math.max(1.0, plugin.getConfig().getDouble("wind.gust.strength-multiplier", 1.8));
    }

    // --- summer heat exhaustion (see HeatExhaustionManager) ---

    public boolean heatExhaustionEnabled() {
        return plugin.getConfig().getBoolean("summer-heat.enabled", true);
    }

    public int heatExposureSecondsPerStage() {
        return Math.max(1, plugin.getConfig().getInt("summer-heat.exposure-seconds-per-stage", 30));
    }

    public int heatRecoverySecondsPerStage() {
        return Math.max(1, plugin.getConfig().getInt("summer-heat.recovery-seconds-per-stage", 20));
    }

    public int heatMaxStage() {
        return Math.max(0, plugin.getConfig().getInt("summer-heat.max-stage", 4));
    }

    /** Per-second chance of a real damage tick once a player is at max-stage -- not guaranteed every second, so it doesn't feel like a metronome. */
    public double heatDamageChancePerSecond() {
        return clamp01(plugin.getConfig().getDouble("summer-heat.damage-chance-per-second", 0.34));
    }

    public double heatDamageAmount() {
        return Math.max(0.0, plugin.getConfig().getDouble("summer-heat.damage-amount", 1.0));
    }

    // --- in-game "why are you taking damage + what do you do about it" messaging (see SeasonProblemAdvisor) ---

    public boolean messagesEnabled() {
        return plugin.getConfig().getBoolean("messages.enabled", true);
    }

    public int messagesReminderIntervalSeconds() {
        return Math.max(5, plugin.getConfig().getInt("messages.reminder-interval-seconds", 30));
    }

    // --- dry thunderstorm (lightning + thunder, no rain) ---

    public boolean dryThunderEnabled() {
        return plugin.getConfig().getBoolean("dry-thunder.enabled", true);
    }

    public double dryThunderNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("dry-thunder.natural-chance", 0.15));
    }

    public int dryThunderCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("dry-thunder.check-interval-minutes", 20));
    }

    public int dryThunderDurationMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("dry-thunder.duration-min-seconds", 60));
    }

    public int dryThunderDurationMaxSeconds() {
        return Math.max(dryThunderDurationMinSeconds(), plugin.getConfig().getInt("dry-thunder.duration-max-seconds", 180));
    }

    public int dryThunderStrikeMinIntervalSeconds() {
        return Math.max(1, plugin.getConfig().getInt("dry-thunder.strike-min-interval-seconds", 5));
    }

    public int dryThunderStrikeMaxIntervalSeconds() {
        return Math.max(dryThunderStrikeMinIntervalSeconds(), plugin.getConfig().getInt("dry-thunder.strike-max-interval-seconds", 20));
    }

    public int dryThunderStrikeRadius() {
        return Math.max(1, plugin.getConfig().getInt("dry-thunder.strike-radius", 40));
    }

    public boolean dryThunderPlaySound() {
        return plugin.getConfig().getBoolean("dry-thunder.play-sound", true);
    }

    // --- fog (particle-based, see FogManager's doc comment for the honest limits) ---

    public boolean fogEnabled() {
        return plugin.getConfig().getBoolean("fog.enabled", true);
    }

    public double fogNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("fog.natural-chance", 0.15));
    }

    public int fogCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("fog.check-interval-minutes", 25));
    }

    public int fogDurationMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("fog.duration-min-seconds", 60));
    }

    public int fogDurationMaxSeconds() {
        return Math.max(fogDurationMinSeconds(), plugin.getConfig().getInt("fog.duration-max-seconds", 240));
    }

    public double fogRadius() {
        return Math.max(1, plugin.getConfig().getDouble("fog.radius", 6.0));
    }

    public int fogDensity() {
        return Math.max(1, plugin.getConfig().getInt("fog.density", 25));
    }

    public Color fogColor() {
        return parseColor("fog.color", 220, 220, 225);
    }

    // --- tornado ---

    public boolean tornadoEnabled() {
        return plugin.getConfig().getBoolean("tornado.enabled", true);
    }

    /**
     * Whether a tornado forces a REAL vanilla thunderstorm (genuine engine-rendered dark sky --
     * World#setStorm(true)+setThundering(true), no resource pack) on its world for as long as it's
     * active, automatically reverting to WeatherCycleManager's own normal weather cycle the moment
     * it ends (naturally or stopped early). Requires weather.enabled -- if the custom weather
     * cycle is off, there's nothing for a tornado to hand control back to, so this does nothing.
     */
    public boolean tornadoDarkenSkyEnabled() {
        return plugin.getConfig().getBoolean("tornado.darken-sky-during-storm", true);
    }

    public double tornadoNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("tornado.natural-chance", 0.05));
    }

    public int tornadoCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("tornado.check-interval-minutes", 30));
    }

    public int tornadoDurationMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("tornado.duration-min-seconds", 30));
    }

    public int tornadoDurationMaxSeconds() {
        return Math.max(tornadoDurationMinSeconds(), plugin.getConfig().getInt("tornado.duration-max-seconds", 90));
    }

    public double tornadoRadius() {
        return Math.max(1, plugin.getConfig().getDouble("tornado.radius", 11.0));
    }

    public int tornadoHeight() {
        return Math.max(2, plugin.getConfig().getInt("tornado.height", 30));
    }

    public double tornadoMoveSpeed() {
        return Math.max(0.0, plugin.getConfig().getDouble("tornado.move-speed", 0.15));
    }

    public double tornadoSpinSpeed() {
        return plugin.getConfig().getDouble("tornado.spin-speed", 0.45);
    }

    public double tornadoPullStrength() {
        return plugin.getConfig().getDouble("tornado.pull-strength", 0.35);
    }

    public double tornadoLiftStrength() {
        return plugin.getConfig().getDouble("tornado.lift-strength", 0.3);
    }

    public double tornadoSwirlStrength() {
        return plugin.getConfig().getDouble("tornado.swirl-strength", 0.4);
    }

    public double tornadoMaxVelocityPerTick() {
        return Math.max(0.1, plugin.getConfig().getDouble("tornado.max-velocity-per-tick", 1.2));
    }

    public boolean tornadoDestroyFragileBlocks() {
        return plugin.getConfig().getBoolean("tornado.destroy-fragile-blocks", true);
    }

    public int tornadoBlocksPerTick() {
        return Math.max(0, plugin.getConfig().getInt("tornado.blocks-per-tick", 2));
    }

    /**
     * How many extra dense, swirling white-smoke/cloud "body fill" particles get scattered inside
     * the funnel's tapered radius every tick, on top of the existing spinning ring structure -- the
     * same dense-random-scatter-within-a-radius technique FogManager/BlizzardManager use for their
     * whiteout look, applied here to turn the funnel from a sparse wireframe outline into a real,
     * thick, solid-looking cyclone body. Each one gets genuine outward-swirling velocity (not just
     * random jitter) so the density itself visibly spirals, not just sits there. 0 disables the
     * fill and falls back to the plain ring-only look from before. Raised well past the original
     * default for a genuinely dense, cloudy look rather than "a little bit of cloud."
     */
    public int tornadoFunnelFillDensity() {
        return Math.max(0, plugin.getConfig().getInt("tornado.funnel-fill-density", 150));
    }

    /** How many particles make up each individual ring of the funnel structure -- more points per ring reads as a fuller, less see-through circle. */
    public int tornadoRingPointCount() {
        return Math.max(3, plugin.getConfig().getInt("tornado.ring-point-count", 20));
    }

    /** How many blocks apart (vertically) each ring of the funnel structure is -- lower means more rings stacked closer together, a denser overall funnel. */
    public int tornadoRingVerticalStep() {
        return Math.max(1, plugin.getConfig().getInt("tornado.ring-vertical-step", 1));
    }

    /**
     * The funnel's shape, as a fraction of tornado.radius at the very base (near the ground) --
     * kept small for a genuinely pointed base, like a real funnel cloud touching down, not a wide
     * base. Paired with tornadoTopRadiusRatio() below; see funnelRadiusAt()'s own doc for how the
     * two combine.
     */
    public double tornadoBaseRadiusRatio() {
        return Math.max(0.01, plugin.getConfig().getDouble("tornado.base-radius-ratio", 0.15));
    }

    /** The funnel's shape at the very top -- a fraction of tornado.radius, deliberately ABOVE 1.0 so the top is genuinely wider than the configured base radius, opening up like a real funnel cloud rather than tapering to a point at both ends. */
    public double tornadoTopRadiusRatio() {
        return Math.max(tornadoBaseRadiusRatio(), plugin.getConfig().getDouble("tornado.top-radius-ratio", 1.6));
    }

    /** Exponent controlling how the funnel widens from base to top -- under 1.0 keeps it noticeably narrow for a good stretch near the base before flaring out toward the top, rather than widening in a straight line the whole way up. */
    public double tornadoFunnelFlareCurve() {
        return Math.max(0.1, plugin.getConfig().getDouble("tornado.funnel-flare-curve", 0.65));
    }

    /** Whether the ORIGINAL particle-based funnel rings + dense-fill body still render at all -- off by default now that the cobweb funnel (tornado.cobweb-funnel.*) is the default way the trunk itself is drawn. Turn on to layer both together, or to go back to the pre-cobweb look entirely. */
    public boolean tornadoFunnelParticlesEnabled() {
        return plugin.getConfig().getBoolean("tornado.funnel-particles-enabled", false);
    }

    /** Whether the funnel's actual trunk is drawn with real COBWEB blocks instead of (or alongside) particles -- see TornadoManager's own doc comment for the full explanation and the real performance tradeoff involved. */
    public boolean tornadoCobwebFunnelEnabled() {
        return plugin.getConfig().getBoolean("tornado.cobweb-funnel.enabled", true);
    }

    /** How often (ticks) the cobweb funnel's rotated shape gets recomputed -- NOT every tick like the particle version, since placing/removing real blocks is far more expensive than a particle. Lower = smoother-looking spin but more block updates; higher = choppier spin but cheaper. */
    public int tornadoCobwebUpdateIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("tornado.cobweb-funnel.update-interval-ticks", 4));
    }

    /** How many cobweb points make up each ring of the funnel's shape -- kept lower than the particle ring's own point count on purpose, since a real block is much more visually "solid" per-point than a particle is. Raised alongside tornado.radius so point spacing around the now-wider circumference doesn't get sparser. */
    public int tornadoCobwebRingPointCount() {
        return Math.max(3, plugin.getConfig().getInt("tornado.cobweb-funnel.ring-point-count", 18));
    }

    /** Blocks between each ring of the cobweb funnel, vertically -- kept sparser than the particle ring's own vertical step by default, for the same "a block reads as more solid than a particle" reason above. */
    public int tornadoCobwebVerticalStep() {
        return Math.max(1, plugin.getConfig().getInt("tornado.cobweb-funnel.vertical-step", 2));
    }

    /** Hard cap on how many cobweb blocks get PLACED in one rebuild cycle -- a big shape change (e.g. the moment a tornado spawns) fills in progressively over a few cycles instead of as one large synchronous burst of block edits. */
    public int tornadoCobwebMaxBlocksPerUpdate() {
        return Math.max(1, plugin.getConfig().getInt("tornado.cobweb-funnel.max-blocks-per-update", 500));
    }

    /**
     * How many concentric rings deep the funnel wall is, stepping inward from its own outer
     * surface -- 1 = the original single-block-wide shell, higher genuinely thickens the tube.
     * IMPORTANT: this multiplies the block count directly (thickness x rings x points-per-ring),
     * so it's a real, meaningful cost increase, not just "more blocks placed once" -- see this
     * method's doc in TornadoManager for what to turn down if a thick wall is too expensive.
     */
    public int tornadoCobwebWallThickness() {
        return Math.max(1, plugin.getConfig().getInt("tornado.cobweb-funnel.wall-thickness", 9));
    }

    /** Whether a sparse cobweb "halo" surrounds the ENTIRE funnel's outer surface at every height level -- not just the top canopy -- for cobwebs visibly wrapping around the whole cyclone, not just its solid wall and the top disc. */
    public boolean tornadoCobwebBodyHaloEnabled() {
        return plugin.getConfig().getBoolean("tornado.cobweb-funnel.body-halo.enabled", true);
    }

    /** How many cobweb points make up the halo ring at each height level -- separate from (and typically fewer than) the solid wall's own ring-point-count, since the halo is meant to read as a looser swirl around the funnel, not another solid layer. */
    public int tornadoCobwebBodyHaloPointsPerRing() {
        return Math.max(1, plugin.getConfig().getInt("tornado.cobweb-funnel.body-halo.points-per-ring", 12));
    }

    /** How far past the wall's own outer surface the halo sits, in blocks. */
    public double tornadoCobwebBodyHaloOffset() {
        return Math.max(0.5, plugin.getConfig().getDouble("tornado.cobweb-funnel.body-halo.offset", 3.0));
    }

    /** How fast the halo spins relative to the funnel's own angle -- 1.0 = same rate as the wall, higher/lower makes it visibly drift relative to the solid wall underneath instead of looking glued to it. */
    public double tornadoCobwebBodyHaloSpinMultiplier() {
        return plugin.getConfig().getDouble("tornado.cobweb-funnel.body-halo.spin-multiplier", 1.3);
    }

    /** Whether a sparse set of real cobweb blocks also gets scattered through the particle cloud canopy's own log-spiral shape -- now the DOMINANT visual element up there by default, not just a light accent; see canopy.density below, which was lowered to match. */
    public boolean tornadoCobwebCanopyAccentsEnabled() {
        return plugin.getConfig().getBoolean("tornado.cobweb-funnel.canopy-accents.enabled", true);
    }

    /** How many cobweb accent points exist in the canopy at once -- now well above tornado.canopy.density's particle count by default, since cobwebs are meant to be doing most of the visual work in the sky, with particles as a light supporting dusting rather than the main event. */
    public int tornadoCobwebCanopyAccentCount() {
        return Math.max(0, plugin.getConfig().getInt("tornado.cobweb-funnel.canopy-accents.count", 180));
    }

    /** Whether the wide spiraling cloud canopy renders above the funnel at all -- see renderCloudCanopy()'s own doc for what this actually looks like and why it's a separate layer from the funnel itself. */
    public boolean tornadoCanopyEnabled() {
        return plugin.getConfig().getBoolean("tornado.canopy.enabled", true);
    }

    /** How much wider than tornado.radius the canopy disc reaches -- deliberately large (multiple times the funnel's own top width) so it reads as a genuinely separate, broad storm-cloud ceiling, not just a wider version of the funnel. */
    public double tornadoCanopyRadiusMultiplier() {
        return Math.max(1.0, plugin.getConfig().getDouble("tornado.canopy.radius-multiplier", 5.0));
    }

    /** How many spiral arms make up the canopy -- more arms fills in the disc more evenly, fewer arms reads as more distinctly spiral. */
    public int tornadoCanopyArms() {
        return Math.max(1, plugin.getConfig().getInt("tornado.canopy.arms", 4));
    }

    /** Particles spawned for the canopy every tick -- deliberately lowered now that tornado.cobweb-funnel.canopy-accents.count is the dominant visual up there; this is just a light supporting dusting of particles mixed in with the cobwebs, not the main event anymore. */
    public int tornadoCanopyDensity() {
        return Math.max(0, plugin.getConfig().getInt("tornado.canopy.density", 30));
    }

    // --- tornado debris (real floating/spiraling blocks picked up from the natural terrain, not just particles) ---

    public boolean tornadoDebrisEnabled() {
        return plugin.getConfig().getBoolean("tornado.debris.enabled", true);
    }

    /**
     * Which block types are eligible to get picked up as flying debris -- deliberately a SEPARATE
     * list from wind.fragile-materials above, which actually includes wool/fences/torches (things
     * players build with). This list is meant to be natural, world-generated terrain only (grass,
     * dirt, sand, logs, leaves, stone) -- see DEFAULT_DEBRIS_MATERIALS' own comment for the real,
     * important caveat about what "natural" can and can't mean here.
     */
    public List<Material> tornadoDebrisMaterials() {
        return parseMaterialList("tornado.debris.materials", DEFAULT_DEBRIS_MATERIALS);
    }

    /** Chance, each tick a tornado is active, of trying to pick up one more debris block -- high on purpose since the ask was explicitly "lots of blocks in the sky." */
    public double tornadoDebrisSpawnChancePerTick() {
        return Math.max(0.0, plugin.getConfig().getDouble("tornado.debris.spawn-chance-per-tick", 0.5));
    }

    /** Hard cap on how many debris blocks can be spiraling at once -- bounds the cost of tracking/updating them every tick regardless of how generous the spawn chance above is. */
    public int tornadoDebrisMaxActive() {
        return Math.max(0, plugin.getConfig().getInt("tornado.debris.max-active", 70));
    }

    /** How long (ticks) one debris block spirals before it's removed -- long enough by default for several full rotations, not just one pass, since it also needs time to climb toward cloud height (see cloud-height-fraction below) before it's done. */
    public int tornadoDebrisLifetimeTicks() {
        return Math.max(1, plugin.getConfig().getInt("tornado.debris.lifetime-ticks", 220));
    }

    public double tornadoDebrisSwirlSpeed() {
        return Math.max(0.0, plugin.getConfig().getDouble("tornado.debris.swirl-speed", 0.5));
    }

    /** Upward speed a debris block climbs at EARLY in its flight -- tapers off automatically as it nears cloud-height-fraction below, so it doesn't just keep climbing forever; see updateDebris()'s own doc. */
    public double tornadoDebrisLiftSpeed() {
        return Math.max(0.0, plugin.getConfig().getDouble("tornado.debris.lift-speed", 0.15));
    }

    /** How close to the very top of the funnel (as a fraction of tornado.height) a debris block's climb tapers toward and settles at -- 1.0 would be the exact top; kept just under that by default so it visibly reaches up near the cloud canopy without climbing past the funnel's own height entirely. */
    public double tornadoDebrisCloudHeightFraction() {
        return Math.max(0.1, Math.min(1.0, plugin.getConfig().getDouble("tornado.debris.cloud-height-fraction", 0.9)));
    }

    /**
     * Whether the exact original block gets put back once its debris lifetime ends (true), or the
     * pickup is left permanent (false) -- true by default, matching every other effect in this
     * "crazy weather" layer's rule of never leaving lasting terrain damage unless explicitly opted
     * into. A repeatedly-triggered tornado shouldn't slowly strip-mine the landscape by default.
     */
    public boolean tornadoDebrisRestoreTerrain() {
        return plugin.getConfig().getBoolean("tornado.debris.restore-terrain", true);
    }

    // --- world spawn protection (shared across every block-modifying system in this "crazy weather" layer) ---

    /**
     * Whether any of this plugin's own terrain-modifying effects (fragile-block destruction from
     * wind/tornado/earthquake, coastal flooding from shore-break/tsunami/hurricane, wave-train
     * ridges, water leveling, meteor impacts/ignition) are blocked from touching anything within
     * spawnProtectionRadiusChunks() of each world's own spawn point. On by default -- this plugin
     * is meant to add chaos out in the world, not grief the one place every player actually builds
     * around.
     */
    public boolean spawnProtectionEnabled() {
        return plugin.getConfig().getBoolean("spawn-protection.enabled", true);
    }

    /**
     * Chunk radius (a square region, same "radius in chunks" convention NexusRealms' own bulk-claim
     * commands use) around each world's spawn point that every block-modifying system above treats
     * as off-limits. Defaults large (1000) since a real spawn city/build can sprawl a long way past
     * a small buffer -- tune this down once you've found the actual edge of what needs protecting;
     * a smaller number means less of the map is off-limits to the weather effects.
     */
    public int spawnProtectionRadiusChunks() {
        return Math.max(0, plugin.getConfig().getInt("spawn-protection.radius-chunks", 1000));
    }

    // --- earthquake ---

    public boolean earthquakeEnabled() {
        return plugin.getConfig().getBoolean("earthquake.enabled", true);
    }

    public double earthquakeNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("earthquake.natural-chance", 0.05));
    }

    public int earthquakeCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("earthquake.check-interval-minutes", 30));
    }

    public int earthquakeDurationMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("earthquake.duration-min-seconds", 15));
    }

    public int earthquakeDurationMaxSeconds() {
        return Math.max(earthquakeDurationMinSeconds(), plugin.getConfig().getInt("earthquake.duration-max-seconds", 40));
    }

    /** Chance, each tick while active, of a tremor "pulse" (shake/particles/sound/dislodge roll) -- scaled by the current ramp-in/out envelope, so pulses are rarer right as a quake starts or ends. */
    public double earthquakePulseChancePerTick() {
        return Math.max(0.0, plugin.getConfig().getDouble("earthquake.pulse-chance-per-tick", 0.15));
    }

    /** Magnitude of the small random velocity jitter applied on each pulse -- the screen-shake stand-in. */
    public double earthquakeShakeStrength() {
        return Math.max(0.0, plugin.getConfig().getDouble("earthquake.shake-strength", 0.12));
    }

    public double earthquakeRadius() {
        return Math.max(1, plugin.getConfig().getDouble("earthquake.radius", 24.0));
    }

    public boolean earthquakeDestroyFragileBlocks() {
        return plugin.getConfig().getBoolean("earthquake.destroy-fragile-blocks", true);
    }

    public double earthquakeDislodgeChancePerTick() {
        return Math.max(0.0, plugin.getConfig().getDouble("earthquake.dislodge-chance-per-tick", 0.03));
    }

    public double earthquakeAftershockChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("earthquake.aftershock-chance", 0.4));
    }

    public int earthquakeAftershockDelayMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("earthquake.aftershock-delay-min-seconds", 20));
    }

    public int earthquakeAftershockDelayMaxSeconds() {
        return Math.max(earthquakeAftershockDelayMinSeconds(), plugin.getConfig().getInt("earthquake.aftershock-delay-max-seconds", 60));
    }

    public double earthquakeAftershockMagnitudeMultiplier() {
        return Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("earthquake.aftershock-magnitude-multiplier", 0.5)));
    }

    // --- sandstorm ---

    public boolean sandstormEnabled() {
        return plugin.getConfig().getBoolean("sandstorm.enabled", true);
    }

    public double sandstormNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("sandstorm.natural-chance", 0.05));
    }

    public int sandstormCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("sandstorm.check-interval-minutes", 30));
    }

    public int sandstormDurationMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("sandstorm.duration-min-seconds", 45));
    }

    public int sandstormDurationMaxSeconds() {
        return Math.max(sandstormDurationMinSeconds(), plugin.getConfig().getInt("sandstorm.duration-max-seconds", 120));
    }

    public double sandstormRadius() {
        return Math.max(1, plugin.getConfig().getDouble("sandstorm.radius", 6.0));
    }

    public int sandstormDensity() {
        return Math.max(1, plugin.getConfig().getInt("sandstorm.density", 40));
    }

    public boolean sandstormApplyBlindness() {
        return plugin.getConfig().getBoolean("sandstorm.apply-blindness", true);
    }

    public boolean sandstormApplySlowness() {
        return plugin.getConfig().getBoolean("sandstorm.apply-slowness", true);
    }

    public int sandstormSlownessAmplifier() {
        return Math.max(0, plugin.getConfig().getInt("sandstorm.slowness-amplifier", 1));
    }

    public boolean sandstormForceWind() {
        return plugin.getConfig().getBoolean("sandstorm.force-wind", true);
    }

    // --- meteor shower ---

    public boolean meteorShowerEnabled() {
        return plugin.getConfig().getBoolean("meteor-shower.enabled", true);
    }

    public double meteorShowerNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("meteor-shower.natural-chance", 0.03));
    }

    public int meteorShowerCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("meteor-shower.check-interval-minutes", 45));
    }

    public int meteorShowerDurationMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("meteor-shower.duration-min-seconds", 30));
    }

    public int meteorShowerDurationMaxSeconds() {
        return Math.max(meteorShowerDurationMinSeconds(), plugin.getConfig().getInt("meteor-shower.duration-max-seconds", 60));
    }

    /** Real ticks between one meteor launching and the next while a shower is active. */
    public int meteorShowerIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("meteor-shower.meteor-interval-ticks", 40));
    }

    /** Blocks above the target a meteor starts its fall from. */
    public int meteorShowerFallHeight() {
        return Math.max(5, plugin.getConfig().getInt("meteor-shower.fall-height", 40));
    }

    /** How many ticks the descent takes -- lower is a faster, more violent-looking fall. */
    public int meteorShowerFallTicks() {
        return Math.max(1, plugin.getConfig().getInt("meteor-shower.fall-ticks", 30));
    }

    /** How far from a random online player a meteor's target point can land. */
    public int meteorShowerImpactRadius() {
        return Math.max(1, plugin.getConfig().getInt("meteor-shower.impact-radius", 40));
    }

    public double meteorShowerExplosionPower() {
        return Math.max(0.0, plugin.getConfig().getDouble("meteor-shower.explosion-power", 1.5));
    }

    /** Whether impact applies real knockback/damage (via a never-block-destructive explosion) to anyone nearby, on top of the visual/sound that always happens either way. */
    public boolean meteorShowerDamageEnabled() {
        return plugin.getConfig().getBoolean("meteor-shower.damage-enabled", true);
    }

    /** Whether the exact impact block briefly catches fire (always reverts itself a few seconds later -- never left behind permanently). Off by default. */
    public boolean meteorShowerIgniteTarget() {
        return plugin.getConfig().getBoolean("meteor-shower.ignite-target", false);
    }

    // --- blizzard ---

    public boolean blizzardEnabled() {
        return plugin.getConfig().getBoolean("blizzard.enabled", true);
    }

    public double blizzardNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("blizzard.natural-chance", 0.1));
    }

    public int blizzardCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("blizzard.check-interval-minutes", 25));
    }

    public int blizzardDurationMinSeconds() {
        return Math.max(1, plugin.getConfig().getInt("blizzard.duration-min-seconds", 90));
    }

    public int blizzardDurationMaxSeconds() {
        return Math.max(blizzardDurationMinSeconds(), plugin.getConfig().getInt("blizzard.duration-max-seconds", 300));
    }

    public double blizzardRadius() {
        return Math.max(1, plugin.getConfig().getDouble("blizzard.radius", 8.0));
    }

    public int blizzardDensity() {
        return Math.max(1, plugin.getConfig().getInt("blizzard.density", 20));
    }

    public boolean blizzardForceWind() {
        return plugin.getConfig().getBoolean("blizzard.force-wind", true);
    }

    public boolean blizzardApplySlowness() {
        return plugin.getConfig().getBoolean("blizzard.apply-slowness", true);
    }

    public int blizzardSlownessAmplifier() {
        return Math.max(0, plugin.getConfig().getInt("blizzard.slowness-amplifier", 0));
    }

    // --- waves (continuous ambient, near open ocean) ---

    public boolean wavesEnabled() {
        return plugin.getConfig().getBoolean("waves.enabled", true);
    }

    public double wavesBaseAmplitude() {
        return plugin.getConfig().getDouble("waves.base-amplitude", 0.15);
    }

    public double wavesWindAmplitudeMultiplier() {
        return plugin.getConfig().getDouble("waves.wind-amplitude-multiplier", 0.6);
    }

    public double wavesBaseFrequency() {
        return plugin.getConfig().getDouble("waves.base-frequency", 0.02);
    }

    public double wavesWindFrequencyMultiplier() {
        return plugin.getConfig().getDouble("waves.wind-frequency-multiplier", 0.03);
    }

    public boolean wavesPushSwimmers() {
        return plugin.getConfig().getBoolean("waves.push-swimmers", true);
    }

    public double wavesPushMinWindStrength() {
        return clamp01(plugin.getConfig().getDouble("waves.push-min-wind-strength", 0.3));
    }

    public double wavesPushMultiplier() {
        return plugin.getConfig().getDouble("waves.push-multiplier", 0.1);
    }

    // --- shared water-body detection (used by both ambient waves and shore-break) ---

    /** Minimum X/Z span, in blocks, for a body of water to qualify for waves at all -- a lake this size or larger counts the same as the ocean; anything smaller (ponds, moats, decorative water) doesn't. */
    public int wavesMinBodySize() {
        return Math.max(1, plugin.getConfig().getInt("waves.min-body-size", 40));
    }

    /** Hard cap on how many blocks the flood-fill body-size check will visit before giving up -- bounds worst-case cost for a small, oddly-shaped body that never reaches min-body-size. */
    public int wavesBodyDetectionMaxBlocks() {
        return Math.max(wavesMinBodySize() * wavesMinBodySize(), plugin.getConfig().getInt("waves.body-detection-max-blocks", 4000));
    }

    /** Small radius used to cheaply rule out "not near any water at all" before ever attempting the expensive flood-fill size check. */
    public int wavesWaterSearchRadius() {
        return Math.max(1, plugin.getConfig().getInt("waves.water-search-radius", 5));
    }

    /** How long (real seconds) a player's ambient-wave eligibility (are they in a large-enough body of water) is cached before being recomputed -- the flood-fill check is too expensive to run on every tick for every player. */
    public int wavesEligibilityCacheSeconds() {
        return Math.max(1, plugin.getConfig().getInt("waves.eligibility-cache-seconds", 8));
    }

    // --- water leveling (fills low spots to match a nearby area's own dominant water height, run automatically whenever a shore-break/tsunami/hurricane wave finishes near that spot) ---

    public boolean waterLevelingEnabled() {
        return plugin.getConfig().getBoolean("waves.water-leveling.enabled", true);
    }

    /** How far around a finished wave's coastline anchor to scan/level -- kept modest since this runs synchronously on the main thread. */
    public int waterLevelingRadius() {
        return Math.max(1, Math.min(48, plugin.getConfig().getInt("waves.water-leveling.radius", 24)));
    }

    /** Hard cap on how many blocks a single leveling pass will place -- bounds worst-case cost for a badly uneven area. Leftover unevenness beyond this budget just gets caught by the next wave that passes through. */
    public int waterLevelingMaxBlocksPerPass() {
        return Math.max(1, plugin.getConfig().getInt("waves.water-leveling.max-blocks-per-pass", 300));
    }

    // --- shore-break waves (continuous recurring real coastal surges, using CoastalFloodEngine) ---

    public boolean shoreBreakEnabled() {
        return plugin.getConfig().getBoolean("waves.shore-break.enabled", true);
    }

    public int shoreBreakCheckIntervalSeconds() {
        return Math.max(1, plugin.getConfig().getInt("waves.shore-break.check-interval-seconds", 6));
    }

    public int shoreBreakMaxConcurrent() {
        return Math.max(1, plugin.getConfig().getInt("waves.shore-break.max-concurrent", 3));
    }

    public int shoreBreakCoastSearchRadius() {
        return Math.max(4, plugin.getConfig().getInt("waves.shore-break.coast-search-radius", 60));
    }

    /** Wave height at calm wind (0.0 strength) -- scales up to shoreBreakMaxHeight() as wind strength approaches 1.0. */
    public double shoreBreakMinHeight() {
        return Math.max(0, plugin.getConfig().getDouble("waves.shore-break.min-height", 1.0));
    }

    /** Wave height at full (1.0) wind strength -- the "waves up to ten blocks tall" ceiling. */
    public double shoreBreakMaxHeight() {
        return Math.max(shoreBreakMinHeight(), plugin.getConfig().getDouble("waves.shore-break.max-height", 10.0));
    }

    public double shoreBreakMaxInlandDistance() {
        return Math.max(1, plugin.getConfig().getDouble("waves.shore-break.max-inland-distance", 14.0));
    }

    /** Deliberately much slower than tsunami/hurricane's own advance speeds -- this is what actually makes a shore-break wave read as something visibly moving in over a couple of seconds, rather than water just appearing almost instantly. */
    public double shoreBreakAdvanceSpeed() {
        return Math.max(0.05, plugin.getConfig().getDouble("waves.shore-break.advance-speed", 0.35));
    }

    public double shoreBreakFrontWidth() {
        return Math.max(1, plugin.getConfig().getDouble("waves.shore-break.front-width", 24.0));
    }

    public double shoreBreakKnockbackStrength() {
        return plugin.getConfig().getDouble("waves.shore-break.knockback-strength", 0.9);
    }

    public int shoreBreakMaxAffectedBlocks() {
        return Math.max(50, plugin.getConfig().getInt("waves.shore-break.max-affected-blocks", 800));
    }

    /** How long the drain-back-out phase visibly takes, regardless of how many blocks actually got flooded -- a fixed, predictable pace instead of the old size-proportional one that made a modest flood drain almost instantly. */
    public int shoreBreakRecedeDurationSeconds() {
        return Math.max(1, plugin.getConfig().getInt("waves.shore-break.recede-duration-seconds", 4));
    }

    /** How long, after a wave fully finishes, that same spot stays off-limits to a new one -- the real "goes back to normal for a while" gap between waves. */
    public int shoreBreakCooldownSeconds() {
        return Math.max(0, plugin.getConfig().getInt("waves.shore-break.cooldown-seconds", 20));
    }

    /** How far apart (in blocks) two waves -- or a new wave and a spot still on cooldown -- have to be, squared for the cheap distanceSquared() comparisons used everywhere this is checked. */
    public double shoreBreakSpacingRadiusSquared() {
        double radius = Math.max(1, plugin.getConfig().getDouble("waves.shore-break.spacing-radius", 80));
        return radius * radius;
    }

    // --- wave train (rows of raised water continuously scrolling across the open surface) ---

    public boolean waveTrainEnabled() {
        return plugin.getConfig().getBoolean("waves.wave-train.enabled", true);
    }

    /** How often (real ticks) the pattern actually recomputes/repaints -- the wave's travel speed is independent of this, since phase accumulates in real-tick units regardless of how often it's recalculated. */
    public int waveTrainTickInterval() {
        return Math.max(1, plugin.getConfig().getInt("waves.wave-train.tick-interval", 2));
    }

    /** How many blocks wide (along the direction of travel) one ridge is. */
    public int waveTrainRidgeWidth() {
        return Math.max(1, plugin.getConfig().getInt("waves.wave-train.ridge-width", 3));
    }

    /** How many blocks of flat water separate one ridge from the next. */
    public int waveTrainGapWidth() {
        return Math.max(1, plugin.getConfig().getInt("waves.wave-train.gap-width", 5));
    }

    public int waveTrainMinHeight() {
        return Math.max(1, plugin.getConfig().getInt("waves.wave-train.min-height", 1));
    }

    public int waveTrainMaxHeight() {
        return Math.max(waveTrainMinHeight(), plugin.getConfig().getInt("waves.wave-train.max-height", 2));
    }

    /** Blocks per real tick the whole pattern travels -- deliberately slow so it reads as rolling swell, not a flicker. */
    public double waveTrainSpeed() {
        return Math.max(0.01, plugin.getConfig().getDouble("waves.wave-train.speed", 0.15));
    }

    /** How wide (blocks, perpendicular to travel direction) the visible field is around each eligible player. */
    public int waveTrainSpan() {
        return Math.max(2, plugin.getConfig().getInt("waves.wave-train.span", 24));
    }

    /** How far (blocks, along the travel direction, both ahead of and behind the player) the visible field extends. */
    public int waveTrainReach() {
        return Math.max(1, plugin.getConfig().getInt("waves.wave-train.reach", 20));
    }

    /** Hard cap on how many columns get touched per player per pass -- bounds the cost of a wide/deep field. */
    public int waveTrainBlocksPerTick() {
        return Math.max(10, plugin.getConfig().getInt("waves.wave-train.blocks-per-tick", 150));
    }

    /**
     * Whether the same baseline-detect-and-revert sweep behind the manual /nexusseasons wavereset
     * command also runs automatically and periodically near every online player -- a self-healing
     * safety net so leaked/stuck raised water (leftover from before the BlockFromToEvent fix, or
     * any other untracked cause) gets corrected on its own instead of needing an admin to notice it
     * and run the command by hand. On by default given how often this has come up.
     */
    public boolean waveTrainAutoCleanupEnabled() {
        return plugin.getConfig().getBoolean("waves.wave-train.auto-cleanup.enabled", true);
    }

    public int waveTrainAutoCleanupIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("waves.wave-train.auto-cleanup.interval-minutes", 5));
    }

    /** Kept smaller than wavereset's own max radius by default -- this runs on its own schedule near every online player rather than once on demand, so a smaller area per pass keeps the recurring cost down. */
    public int waveTrainAutoCleanupRadius() {
        return Math.max(4, Math.min(64, plugin.getConfig().getInt("waves.wave-train.auto-cleanup.radius", 24)));
    }

    // --- tsunami (one-off event, real warning before it hits) ---

    public boolean tsunamiEnabled() {
        return plugin.getConfig().getBoolean("tsunami.enabled", true);
    }

    public double tsunamiNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("tsunami.natural-chance", 0.02));
    }

    public int tsunamiCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("tsunami.check-interval-minutes", 45));
    }

    public int tsunamiCoastSearchRadius() {
        return Math.max(4, plugin.getConfig().getInt("tsunami.coast-search-radius", 60));
    }

    public int tsunamiWarningSeconds() {
        return Math.max(0, plugin.getConfig().getInt("tsunami.warning-seconds", 15));
    }

    public double tsunamiMaxInlandDistance() {
        return Math.max(1, plugin.getConfig().getDouble("tsunami.max-inland-distance", 40.0));
    }

    public double tsunamiAdvanceSpeed() {
        return Math.max(0.1, plugin.getConfig().getDouble("tsunami.advance-speed", 1.2));
    }

    public double tsunamiFrontWidth() {
        return Math.max(1, plugin.getConfig().getDouble("tsunami.front-width", 40.0));
    }

    public double tsunamiWaveHeight() {
        return Math.max(1, plugin.getConfig().getDouble("tsunami.wave-height", 6.0));
    }

    public double tsunamiKnockbackStrength() {
        return plugin.getConfig().getDouble("tsunami.knockback-strength", 1.4);
    }

    public int tsunamiMaxAffectedBlocks() {
        return Math.max(100, plugin.getConfig().getInt("tsunami.max-affected-blocks", 6000));
    }

    // --- hurricane (orchestrated: wind + real forced rain/thunder + periodic storm surge) ---

    public boolean hurricaneEnabled() {
        return plugin.getConfig().getBoolean("hurricane.enabled", true);
    }

    public double hurricaneNaturalChance() {
        return Math.max(0.0, plugin.getConfig().getDouble("hurricane.natural-chance", 0.03));
    }

    public int hurricaneCheckIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("hurricane.check-interval-minutes", 60));
    }

    public int hurricaneDurationMinMinutes() {
        return Math.max(1, plugin.getConfig().getInt("hurricane.duration-min-minutes", 15));
    }

    public int hurricaneDurationMaxMinutes() {
        return Math.max(hurricaneDurationMinMinutes(), plugin.getConfig().getInt("hurricane.duration-max-minutes", 40));
    }

    public double hurricaneRampFraction() {
        return Math.max(0.01, Math.min(0.49, plugin.getConfig().getDouble("hurricane.ramp-fraction", 0.15)));
    }

    public boolean hurricaneEyeEnabled() {
        return plugin.getConfig().getBoolean("hurricane.eye-enabled", true);
    }

    public double hurricaneEyeWidthFraction() {
        return Math.max(0.01, Math.min(0.5, plugin.getConfig().getDouble("hurricane.eye-width-fraction", 0.1)));
    }

    public double hurricaneEyeIntensity() {
        return clamp01(plugin.getConfig().getDouble("hurricane.eye-intensity", 0.1));
    }

    public double hurricaneMinWindStrength() {
        return clamp01(plugin.getConfig().getDouble("hurricane.min-wind-strength", 0.6));
    }

    public double hurricaneRainThreshold() {
        return clamp01(plugin.getConfig().getDouble("hurricane.rain-threshold", 0.25));
    }

    public double hurricaneThunderThreshold() {
        return clamp01(plugin.getConfig().getDouble("hurricane.thunder-threshold", 0.6));
    }

    public double hurricaneThunderChancePerTick() {
        return Math.max(0.0, plugin.getConfig().getDouble("hurricane.thunder-chance-per-tick", 0.02));
    }

    public boolean hurricaneStormSurgeEnabled() {
        return plugin.getConfig().getBoolean("hurricane.storm-surge-enabled", true);
    }

    public int hurricaneSurgeIntervalSeconds() {
        return Math.max(5, plugin.getConfig().getInt("hurricane.surge-interval-seconds", 45));
    }

    public int hurricaneSurgeCoastSearchRadius() {
        return Math.max(4, plugin.getConfig().getInt("hurricane.surge-coast-search-radius", 60));
    }

    public double hurricaneSurgeMaxInlandDistance() {
        return Math.max(1, plugin.getConfig().getDouble("hurricane.surge-max-inland-distance", 15.0));
    }

    public double hurricaneSurgeAdvanceSpeed() {
        return Math.max(0.1, plugin.getConfig().getDouble("hurricane.surge-advance-speed", 0.6));
    }

    public double hurricaneSurgeFrontWidth() {
        return Math.max(1, plugin.getConfig().getDouble("hurricane.surge-front-width", 30.0));
    }

    public double hurricaneSurgeWaveHeight() {
        return Math.max(1, plugin.getConfig().getDouble("hurricane.surge-wave-height", 3.0));
    }

    public double hurricaneSurgeKnockbackStrength() {
        return plugin.getConfig().getDouble("hurricane.surge-knockback-strength", 0.6);
    }

    public int hurricaneSurgeMaxAffectedBlocks() {
        return Math.max(50, plugin.getConfig().getInt("hurricane.surge-max-affected-blocks", 1500));
    }

    // --- weather HUD (scoreboard sidebar) ---

    public boolean weatherHudEnabled() {
        return plugin.getConfig().getBoolean("weather-hud.enabled", true);
    }

    public int weatherHudRefreshIntervalSeconds() {
        return Math.max(1, plugin.getConfig().getInt("weather-hud.refresh-interval-seconds", 3));
    }

    // --- weather chat announcements (shared across every event in the "crazy weather" layer) ---

    public boolean weatherAnnounceEnabled() {
        return plugin.getConfig().getBoolean("weather-announce.enabled", true);
    }

    /** A blank/missing message for a given key is how that specific announcement gets silenced -- there's no separate per-event enable toggle on top of this. */
    public String weatherAnnounceMessage(String key, String defaultMessage) {
        return plugin.getConfig().getString("weather-announce.messages." + key, defaultMessage);
    }

    // --- shared helpers ---

    private static final List<Material> DEFAULT_FRAGILE_MATERIALS = List.of(
            Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE, Material.JUNGLE_FENCE,
            Material.ACACIA_FENCE, Material.DARK_OAK_FENCE, Material.MANGROVE_FENCE, Material.CHERRY_FENCE,
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL,
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.OAK_SAPLING, Material.TORCH, Material.LADDER, Material.SCAFFOLDING
    );

    /**
     * Deliberately natural/world-generated terrain types ONLY -- no wool, fences, planks, or
     * anything else players commonly build with (that's what DEFAULT_FRAGILE_MATERIALS above is
     * for, a different list for a different purpose). The real, important caveat: vanilla Bukkit
     * has no reliable "was this block placed by a player" API for a block that's already sitting
     * in the world -- that would require this plugin tracking every BlockPlaceEvent server-wide
     * since the day it was installed, which it doesn't do. So this is a material-type allowlist,
     * the same practical approach DEFAULT_FRAGILE_MATERIALS already takes -- a player COULD in
     * principle build a dirt/sand/log structure out of these exact materials and have it get
     * picked up too, same as they could build a wool structure and have that count as "fragile."
     * Restricting to types nobody normally builds finished structures out of (grass, dirt, sand,
     * gravel, natural logs, leaves, stone, snow) keeps that overlap small in ordinary play, but it
     * isn't a real guarantee -- reduce this list in config.yml if a specific build keeps getting hit.
     */
    private static final List<Material> DEFAULT_DEBRIS_MATERIALS = List.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL, Material.MYCELIUM,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
            Material.SNOW_BLOCK, Material.STONE, Material.MOSS_BLOCK
    );

    private List<Material> parseMaterialList(String path, List<Material> defaults) {
        List<String> raw = plugin.getConfig().getStringList(path);
        if (raw.isEmpty()) return defaults;
        List<Material> materials = new ArrayList<>();
        for (String name : raw) {
            try {
                materials.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // skip a bad entry rather than failing the whole list
            }
        }
        return materials.isEmpty() ? defaults : materials;
    }

    private Color parseColor(String path, int defaultR, int defaultG, int defaultB) {
        int r = plugin.getConfig().getInt(path + "-r", defaultR);
        int g = plugin.getConfig().getInt(path + "-g", defaultG);
        int b = plugin.getConfig().getInt(path + "-b", defaultB);
        return Color.fromRGB(clampByte(r), clampByte(g), clampByte(b));
    }

    private int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
