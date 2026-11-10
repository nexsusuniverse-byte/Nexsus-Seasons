package com.nexusuniverse.seasons.weather;

import com.nexusuniverse.seasons.SeasonsConfig;
import com.nexusuniverse.seasons.climate.AllergyType;
import com.nexusuniverse.seasons.climate.ClimateManager;
import com.nexusuniverse.seasons.climate.ClimatePlayers;
import com.nexusuniverse.seasons.climate.ClimateSettings;
import com.nexusuniverse.seasons.climate.Reading;
import com.nexusuniverse.seasons.climate.Sidebar;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A persistent per-player scoreboard sidebar (upper-right corner) showing the current headline
 * weather condition and a live wind meter.
 *
 * HONEST PLATFORM LIMIT: this is the actual closest real placement to "top-left corner" reachable
 * from plain plugin code -- Bukkit/Paper has no API for arbitrary on-screen HUD positioning
 * without a resource pack or a client mod. Scoreboard sidebar (renders upper-right), action bar
 * (bottom-center), and boss bar (top-center, already used by SeasonBossBar for season/day) are the
 * only three real options; sidebar is what was picked.
 *
 * The wind reading genuinely reflects where the player actually is rather than a flat global
 * number -- it reuses WindManager's own "exposed to open sky" check, so standing indoors or
 * underground correctly reads as sheltered/calm even while conditions outside are severe.
 *
 * Headline weather is a fixed priority order -- hurricane > tsunami > tornado > blizzard > dry
 * thunderstorm > fog > real vanilla thunder/rain > clear. All of those event managers are single,
 * server-wide states (no per-world or per-player tracking, consistent with the rest of this
 * "crazy weather" layer) -- only the final vanilla thunder/rain fallback is actually read per the
 * player's own current world.
 *
 * v0.15.0: the sidebar became a proper weather report. Each line has its own label colour and its value is
 * coloured by how it reads: Sky, Temp (with a feels-like), Humidity, Dew Pt, Wind (mph and direction), Pollen
 * (with a warning mark for your own allergy) and UV. The numbers come from the climate package
 * ({@link ClimateManager}); which lines show is set in config.yml under weather-hud.show.
 *
 * Gives each player their own private Scoreboard instance so this can't collide with anything
 * else -- if another plugin on the server also sets player scoreboards for its own sidebar, only
 * one can be shown at a time (standard Bukkit limit, not specific to this), so weather-hud.enabled
 * exists specifically to turn this off if that conflict comes up.
 */
public class WeatherHudManager implements Listener {

    private static final String OBJECTIVE_NAME = "nexusweather";

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private final WindManager wind;
    private final DryThunderstormManager dryThunder;
    private final FogManager fog;
    private final BlizzardManager blizzard;
    private final TornadoManager tornado;
    private final TsunamiManager tsunami;
    private final HurricaneManager hurricane;
    private final ClimateManager climate;
    private final ClimatePlayers climatePlayers;
    private final ClimateSettings climateSettings;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Integer> lineCounts = new HashMap<>();
    private final Map<UUID, List<String>> lastLines = new HashMap<>();
    private BukkitTask task;

    public WeatherHudManager(JavaPlugin plugin, SeasonsConfig config, WindManager wind, DryThunderstormManager dryThunder,
                              FogManager fog, BlizzardManager blizzard, TornadoManager tornado, TsunamiManager tsunami,
                              HurricaneManager hurricane, ClimateManager climate, ClimatePlayers climatePlayers,
                              ClimateSettings climateSettings) {
        this.plugin = plugin;
        this.config = config;
        this.wind = wind;
        this.dryThunder = dryThunder;
        this.fog = fog;
        this.blizzard = blizzard;
        this.tornado = tornado;
        this.tsunami = tsunami;
        this.hurricane = hurricane;
        this.climate = climate;
        this.climatePlayers = climatePlayers;
        this.climateSettings = climateSettings;
    }

    public void start() {
        if (!config.weatherHudEnabled()) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player); // don't make anyone already online wait for the first scheduled tick
        }
        long interval = 20L * config.weatherHudRefreshIntervalSeconds();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(main);
        }
        boards.clear();
        lineCounts.clear();
        lastLines.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.weatherHudEnabled()) return;
        update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
        lineCounts.remove(event.getPlayer().getUniqueId());
        lastLines.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard fresh = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(fresh);
            return fresh;
        });

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        List<String> previousLines = lastLines.get(player.getUniqueId());
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, "dummy", "§b§lNexus §9§lWeather");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            if (climateSettings.hideScoreNumbers()) {
                hideScoreNumbers(objective);
            }
            previousLines = null; // brand new objective: nothing on it yet, write every line
        }

        List<String> lines = buildLines(player);
        // Every prefix/score write is a packet to that player (and Geyser has to translate each one for Bedrock),
        // so only send what actually changed since the last refresh. Most refreshes change one line or none.
        if (lines.equals(previousLines)) {
            return;
        }
        boolean sameShape = previousLines != null && previousLines.size() == lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (sameShape && lines.get(i).equals(previousLines.get(i))) {
                continue;
            }
            String entry = lineEntry(i);
            Team team = board.getTeam("nwline" + i);
            if (team == null) team = board.registerNewTeam("nwline" + i);
            if (!team.hasEntry(entry)) team.addEntry(entry);
            team.setPrefix(lines.get(i));
            if (!sameShape) {
                objective.getScore(entry).setScore(lines.size() - i);
            }
        }
        // a config reload (or a world change) can change how many lines there are; clear out the leftovers
        int previous = lineCounts.getOrDefault(player.getUniqueId(), 0);
        for (int i = lines.size(); i < previous; i++) {
            board.resetScores(lineEntry(i));
            Team stale = board.getTeam("nwline" + i);
            if (stale != null) stale.unregister();
        }
        lineCounts.put(player.getUniqueId(), lines.size());
        lastLines.put(player.getUniqueId(), new java.util.ArrayList<>(lines));
    }

    /**
     * Paper (1.20.4+) can hide the red score numbers down the right-hand side of a sidebar. Looked up
     * by name so this still loads on a server or API that does not have it: it just keeps the numbers.
     */
    private void hideScoreNumbers(Objective objective) {
        try {
            Class<?> format = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = format.getMethod("blank").invoke(null);
            Objective.class.getMethod("numberFormat", format).invoke(objective, blank);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // older server: the numbers stay, which is harmless
        }
    }

    /**
     * One entry per sidebar line. The count only changes if weather-hud.show.* is edited and reloaded
     * (then update() cleans up the leftovers); it is the same in every world.
     */
    private List<String> buildLines(Player player) {
        World world = player.getWorld();
        Sidebar.Show show = climateSettings.climateEnabled() ? climateSettings.show() : new Sidebar.Show(false, false, false, false, false, false);
        boolean celsius = climateSettings.celsius();

        boolean euro = climateSettings.climateEnabled() && climateSettings.euroCorner();
        String jab = climateSettings.euroJab();

        if (world.getEnvironment() == World.Environment.NETHER) {
            return withEuroCorner(Sidebar.nether(show, celsius), euro, jab, 117);
        }
        if (world.getEnvironment() == World.Environment.THE_END) {
            return withEuroCorner(Sidebar.end(show, celsius), euro, jab, -40);
        }

        double strength = wind.currentStrength();
        boolean sheltered = !wind.isExposedToSky(player.getLocation());
        Reading reading = climateSettings.climateEnabled() ? climate.reading(player) : null;

        String headline = headlineWeather(world, reading);
        if (reading == null) {
            // climate switched off: the original three lines
            String windLine = "§7Wind: §f" + windDescriptor(strength) + " " + Math.round(strength * 100) + "%"
                    + (sheltered ? "" : " " + compassArrow());
            return List.of("§7Sky: §f" + headline, windLine, sheltered ? "§8(sheltered)" : "");
        }
        AllergyType allergic = climatePlayers.allergy(player.getUniqueId());
        return withEuroCorner(Sidebar.overworld(reading, headline, strength, sheltered ? "" : compassArrow(), allergic, show, celsius),
                euro, jab, reading.tempF());
    }

    /** Adds the two Euro-corner lines (jab, then Celsius) under the board when the corner is on. */
    private static List<String> withEuroCorner(List<String> base, boolean euro, String jab, double tempF) {
        if (!euro) {
            return base;
        }
        List<String> all = new java.util.ArrayList<>(base);
        all.addAll(Sidebar.euroCorner(jab, tempF));
        return all;
    }

    private String headlineWeather(World world, Reading reading) {
        if (hurricane.isActive()) return "Hurricane";
        if (tsunami.isActive()) return "Tsunami";
        if (tornado.isActive()) return "Tornado";
        if (blizzard.isActive()) return "Blizzard";
        if (dryThunder.isActive()) return "Dry Thunderstorm";
        if (fog.isActive()) return "Foggy";
        if (reading != null) return reading.sky();
        if (world.isThundering()) return "Thunderstorm";
        if (world.hasStorm()) return "Raining";
        return "Clear";
    }

    private String windDescriptor(double strength) {
        if (strength < 0.15) return "Calm";
        if (strength < 0.3) return "Breezy";
        if (strength < 0.5) return "Windy";
        if (strength < 0.7) return "Strong";
        return "Severe";
    }

    private String compassArrow() {
        Vector direction = wind.currentDirection();
        double angle = Math.atan2(direction.getZ(), direction.getX());
        if (angle < 0) angle += Math.PI * 2;
        int sector = (int) Math.round(angle / (Math.PI / 4)) % 8;
        String[] arrows = {"→", "↘", "↓", "↙", "←", "↖", "↑", "↗"};
        return arrows[sector];
    }

    /** A short, unique-per-line, effectively-invisible scoreboard entry -- the real visible text comes from that line's Team prefix, not the entry itself. */
    private String lineEntry(int index) {
        return ChatColor.values()[index].toString();
    }
}
