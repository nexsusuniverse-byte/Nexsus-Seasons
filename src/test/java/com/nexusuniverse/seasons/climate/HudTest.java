package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.weather.*;
import com.nexusuniverse.seasons.weather.WeatherHudManager;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

import static com.nexusuniverse.seasons.climate.Kit.expect;

/** The sidebar as a player sees it: which lines, in which worlds, and that it tidies up after itself. */
public class HudTest {

    static final class Hud {
        final ManagerTest.Rig rig = new ManagerTest.Rig();
        final WindManager wind = new WindManager();
        final HurricaneManager hurricane = new HurricaneManager();
        final TornadoManager tornado = new TornadoManager();
        final TsunamiManager tsunami = new TsunamiManager();
        final BlizzardManager blizzard = new BlizzardManager();
        final FogManager fog = new FogManager();
        final DryThunderstormManager dry = new DryThunderstormManager();
        final WeatherHudManager hud;

        Hud() {
            hud = new WeatherHudManager(rig.plugin, rig.config, wind, dry, fog, blizzard, tornado, tsunami, hurricane,
                    rig.manager, rig.players, rig.settings);
        }

        List<String> lines(BukkitKit.TestPlayer p) {
            Scoreboard board = p.getScoreboard();
            if (board == null) {
                return new ArrayList<>();
            }
            Objective o = board.getObjective("nexusweather");
            List<String> out = new ArrayList<>();
            if (o == null) {
                return out;
            }
            for (int i = 0; i < 20; i++) {
                String entry = org.bukkit.ChatColor.values()[i].toString();
                Team t = board.getTeam("nwline" + i);
                if (t == null || t.unregistered || !o.scores.containsKey(entry)) {
                    continue;
                }
                out.add(t.getPrefix());
            }
            return out;
        }

        String text(BukkitKit.TestPlayer p) {
            return Kit.strip(String.join("\n", lines(p)));
        }
    }

    static void euroCorner() {
        Hud h = new Hud();
        BukkitKit.TestPlayer p = BukkitKit.join("Ann", h.rig.world);
        h.hud.start();
        int on = h.lines(p).size();

        // Celsius stays Celsius even when the rest of the board is Fahrenheit
        String text = h.text(p);
        expect(text.contains("°F") && text.contains("°C"), "Fahrenheit board, Celsius corner: " + text);

        // a custom joke, trimmed; blank falls back to the default
        h.rig.plugin.getConfig().set("weather-hud.euro-corner.text", "  Tea is not a personality.  ");
        BukkitScheduler_run(h);
        List<String> l = h.lines(p);
        expect(Kit.strip(l.get(l.size() - 2)).equals("Tea is not a personality."), "custom text: " + l);
        h.rig.plugin.getConfig().set("weather-hud.euro-corner.text", "   ");
        BukkitScheduler_run(h);
        l = h.lines(p);
        expect(Kit.strip(l.get(l.size() - 2)).equals(ClimateSettings.DEFAULT_EURO_JAB), "blank text uses the default: " + l);

        // switched off: both lines go and the board shrinks by exactly two
        h.rig.plugin.getConfig().set("weather-hud.euro-corner.enabled", false);
        BukkitScheduler_run(h);
        expect(h.lines(p).size() == on - 2, "off removes exactly two lines: " + on + " -> " + h.lines(p).size());
        expect(!h.text(p).contains("Euro Temp") && !h.text(p).contains("window"), "no corner text when off");

        // Nether and End get the corner too, in Celsius, and the same height as the overworld
        h.rig.plugin.getConfig().set("weather-hud.euro-corner.enabled", true);
        World nether = BukkitKit.nether("world_nether");
        BukkitKit.TestPlayer n = BukkitKit.join("Nina", nether);
        BukkitKit.TestPlayer o = BukkitKit.join("Owen", h.rig.world);
        BukkitScheduler_run(h);
        expect(Kit.strip(h.text(n)).contains("Euro Temp: 47°C"), "the Nether corner: " + h.text(n));
        expect(h.lines(n).size() == h.lines(o).size(), "same height in the Nether");

        // with the whole climate system off there is no corner (nothing to convert)
        h.rig.plugin.getConfig().set("climate.enabled", false);
        BukkitScheduler_run(h);
        expect(!h.text(o).contains("Euro Temp"), "no corner when climate is off: " + h.text(o));
    }

    public static void main(String[] args) {
        euroCorner();
        overworld();
        otherWorlds();
        eventsAndSettings();
        Kit.finish();
    }

    static void overworld() {
        Hud h = new Hud();
        h.rig.world.setTime(9000);
        BukkitKit.TestPlayer p = BukkitKit.join("Ann", h.rig.world);
        h.hud.start();
        Objective o = p.getScoreboard().getObjective("nexusweather");
        expect(o != null, "an objective is on the player's own board");
        expect(o != null && o.getDisplaySlot() == org.bukkit.scoreboard.DisplaySlot.SIDEBAR, "shown in the sidebar");
        expect(org.bukkit.scoreboard.Objective.HIDE_CALLS == 1, "the red score numbers are hidden");
        String text = h.text(p);
        for (String label : new String[]{"Sky", "Temp", "Humidity", "Dew", "Wind", "Pollen", "UV"}) {
            expect(text.contains(label), "the sidebar has a " + label + " line");
        }
        List<String> lines = h.lines(p);
        expect(lines.size() >= 6, "about half a dozen lines, not a wall: " + lines.size());
        expect(lines.size() <= 11, "and not overwhelming (the board plus the two Euro-corner lines): " + lines.size());
        int n = lines.size();
        expect(Kit.strip(lines.get(n - 2)).equals("AC? Just open a window."), "the Euro jab is second from the bottom: " + lines);
        expect(Kit.strip(lines.get(n - 1)).matches("Euro Temp: -?\\d+°C"), "and the Celsius temperature sits right below it: " + lines);
        Sidebar.Show all = Sidebar.Show.all();
        expect(all.temperature() && all.uv(), "defaults show everything");

        // the colour of every label differs, so it is not one grey block
        java.util.Set<String> colours = new java.util.HashSet<>();
        for (String l : lines) {
            if (l.startsWith("§") && l.length() > 1) {
                colours.add(l.substring(0, 2));
            }
        }
        expect(colours.size() >= 5, "each category has its own label colour: " + colours);

        h.rig.world.setStorm(true);
        for (int i = 0; i < 100; i++) {
            h.rig.tickOnce();
        }
        h.hud.start();
        expect(h.text(p).toLowerCase().contains("rain") || h.text(p).toLowerCase().contains("snow"), "the sky line follows the weather");
        h.tornado.active = true;
        h.hud.onJoin(new PlayerJoinEvent(p, ""));
        expect(h.text(p).contains("Tornado"), "an event takes over the sky line");
        h.tornado.active = false;
    }

    static void otherWorlds() {
        Hud h = new Hud();
        World nether = BukkitKit.nether("world_nether");
        BukkitKit.TestPlayer n = BukkitKit.join("Nina", nether);
        BukkitKit.TestPlayer o = BukkitKit.join("Owen", h.rig.world);
        h.hud.start();
        String nt = h.text(n);
        expect(!nt.isEmpty(), "the Nether gets a sidebar too");
        expect(nt.contains("Smoldering") && nt.contains("Scorching"), "a tongue-in-cheek Nether reading: " + nt);
        expect(h.lines(n).size() == h.lines(o).size(), "with as many lines as the overworld, so the board does not jump");
        expect(h.hud != null, "hud built");
    }

    static void eventsAndSettings() {
        Hud h = new Hud();
        BukkitKit.TestPlayer p = BukkitKit.join("Ann", h.rig.world);
        h.hud.start();
        int before = h.lines(p).size();

        h.rig.plugin.getConfig().set("weather-hud.show.pollen", false);
        h.rig.plugin.getConfig().set("weather-hud.show.uv-index", false);
        h.rig.plugin.getConfig().set("weather-hud.show.dew-point", false);
        BukkitScheduler_run(h);
        int after = h.lines(p).size();
        expect(after == before - 3, "turning three lines off removes exactly three: " + before + " -> " + after);
        expect(!h.text(p).contains("Pollen"), "the pollen line is gone");
        Scoreboard b = p.getScoreboard();
        int live = 0;
        for (int i = 0; i < 20; i++) {
            Team t = b.getTeam("nwline" + i);
            if (t != null && !t.unregistered) {
                live++;
            }
        }
        expect(live == after, "leftover teams are unregistered, not left behind: " + live + " vs " + after);

        h.rig.plugin.getConfig().set("weather-hud.show.pollen", true);
        BukkitScheduler_run(h);
        expect(h.text(p).contains("Pollen"), "and it comes back");

        // climate off: the original three-line sidebar
        h.rig.plugin.getConfig().set("climate.enabled", false);
        BukkitScheduler_run(h);
        String old = h.text(p);
        expect(old.contains("Sky") && old.contains("Wind") && !old.contains("Temp"), "climate off gives the original sidebar: " + old);

        // celsius
        h.rig.plugin.getConfig().set("climate.enabled", true);
        h.rig.plugin.getConfig().set("climate.units", "C");
        BukkitScheduler_run(h);
        expect(h.text(p).contains("°C"), "units: C shows Celsius");

        // quitting forgets the board; stopping hands back the main board
        h.hud.onQuit(new PlayerQuitEvent(p, ""));
        h.hud.stop();
        expect(p.getScoreboard() == org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard(), "stop() gives the main board back");

        // hud off in config: nothing is registered
        Hud off = new Hud();
        off.rig.plugin.getConfig().set("weather-hud.enabled", false);
        BukkitKit.TestPlayer q = BukkitKit.join("Quin", off.rig.world);
        off.hud.start();
        expect(off.lines(q).isEmpty(), "weather-hud.enabled: false draws nothing");
    }

    /** Runs the sidebar's own repeating task once (it is the last one registered). */
    static void BukkitScheduler_run(Hud h) {
        var tasks = org.bukkit.scheduler.BukkitScheduler.REPEATING;
        tasks.get(tasks.size() - 1).run();
    }
}
