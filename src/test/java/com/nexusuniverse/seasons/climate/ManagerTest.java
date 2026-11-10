package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.SeasonClock;
import com.nexusuniverse.seasons.SeasonsConfig;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.List;

import static com.nexusuniverse.seasons.climate.BukkitKit.reset;
import static com.nexusuniverse.seasons.climate.Kit.expect;

/** The ClimateManager as a server runs it: the 7 AM report once a day, mute, persistence, live readings. */
public class ManagerTest {

    /** Everything a ClimateManager needs, on a clean fake server. */
    static final class Rig {
        final BukkitKit.TestPlugin plugin = new BukkitKit.TestPlugin();
        final SeasonsConfig config;
        final ClimateSettings settings;
        final SeasonClock clock;
        final ClimatePlayers players;
        final World world;
        final ClimateManager manager;
        boolean exposed = true;
        boolean fog;
        boolean blizzard;
        final List<WeatherSpan> spans = new java.util.ArrayList<>();

        Rig() {
            this(true);
        }

        Rig(boolean fresh) {
            if (fresh) {
                reset();
            }
            world = BukkitKit.overworld("world");
            config = new SeasonsConfig(plugin);
            settings = new ClimateSettings(plugin);
            clock = new SeasonClock(plugin, config);
            players = new ClimatePlayers(plugin);
            manager = new ClimateManager(plugin, config, settings, clock, ticks -> spans, l -> exposed, () -> fog,
                    () -> blizzard, () -> Boolean.TRUE, players, () -> 0.3);
            manager.start();
        }

        void tickOnce() {
            BukkitScheduler.REPEATING.get(BukkitScheduler.REPEATING.size() - 1).run();
        }
    }

    public static void main(String[] args) {
        sevenAm();
        muteAndSettings();
        persistence();
        liveReading();
        stalePlan();
        Kit.finish();
    }

    static void sevenAm() {
        Rig r = new Rig();
        BukkitKit.TestPlayer a = BukkitKit.join("Ann", r.world);
        BukkitKit.TestPlayer b = BukkitKit.join("Bob", r.world);

        r.world.setTime(500); // 6:30 AM
        r.tickOnce();
        expect(a.messages.isEmpty(), "nothing is said before 7:00 AM");

        r.world.setTime(1000); // 7:00 AM
        r.tickOnce();
        String text = a.text();
        expect(!a.messages.isEmpty() && !b.messages.isEmpty(), "everyone online gets the 7 AM report");
        expect(text.contains("High") && text.contains("Low"), "the report names a high and a low");
        int sent = a.messages.size();

        r.world.setTime(1020);
        r.tickOnce();
        r.world.setTime(2500);
        r.tickOnce();
        expect(a.messages.size() == sent, "it is issued once, not every second of the morning");

        r.world.setTime(5000); // past the catch-up window
        r.clock.setDayOfSeason(2);
        r.tickOnce();
        expect(a.messages.size() == sent, "outside the window nothing is issued, even on a new day");

        r.world.setTime(1000);
        r.tickOnce();
        expect(a.messages.size() > sent, "the next day's 7 AM issues the next report");

        // a server that was down at exactly 7:00 still reports if it is back within the catch-up window
        Rig late = new Rig();
        BukkitKit.TestPlayer c = BukkitKit.join("Cy", late.world);
        late.world.setTime(3500);
        late.tickOnce();
        expect(!c.messages.isEmpty(), "a late start inside the catch-up window still reports");

        // after midnight but before 6 AM is still "yesterday's" nothing
        Rig night = new Rig();
        BukkitKit.TestPlayer d = BukkitKit.join("Di", night.world);
        night.world.setTime(18000);
        night.tickOnce();
        expect(d.messages.isEmpty(), "no report at midnight");
    }

    static void muteAndSettings() {
        Rig r = new Rig();
        BukkitKit.TestPlayer a = BukkitKit.join("Ann", r.world);
        BukkitKit.TestPlayer b = BukkitKit.join("Bob", r.world);
        r.players.setMuted(a.getUniqueId(), true);
        r.world.setTime(1000);
        r.tickOnce();
        expect(a.messages.isEmpty(), "a muted player does not get the broadcast");
        expect(!b.messages.isEmpty(), "everyone else still does");
        r.manager.sendForecast(a);
        expect(!a.messages.isEmpty(), "but /forecast still works for them");

        Rig quiet = new Rig();
        quiet.plugin.getConfig().set("forecast.broadcast", false);
        BukkitKit.TestPlayer c = BukkitKit.join("Cy", quiet.world);
        quiet.world.setTime(1000);
        quiet.tickOnce();
        expect(c.messages.isEmpty(), "forecast.broadcast: false keeps the chat quiet");
        expect(quiet.manager.currentPlan() != null, "yet the plan is still made for the sidebar and /forecast");

        Rig off = new Rig();
        off.plugin.getConfig().set("forecast.enabled", false);
        BukkitKit.TestPlayer d = BukkitKit.join("Di", off.world);
        off.world.setTime(1000);
        off.tickOnce();
        expect(d.messages.isEmpty(), "forecast.enabled: false means no report");

        Rig noClimate = new Rig();
        noClimate.plugin.getConfig().set("climate.enabled", false);
        BukkitKit.TestPlayer e = BukkitKit.join("Ed", noClimate.world);
        noClimate.world.setTime(1000);
        noClimate.tickOnce();
        expect(e.messages.isEmpty(), "climate.enabled: false switches the whole thing off");

        Rig custom = new Rig();
        custom.plugin.getConfig().set("forecast.hour-tick", 12000L); // 6 PM
        BukkitKit.TestPlayer f = BukkitKit.join("Flo", custom.world);
        custom.world.setTime(1000);
        custom.tickOnce();
        expect(f.messages.isEmpty(), "a moved report hour is respected (nothing at 7 AM)");
        custom.world.setTime(12100);
        custom.tickOnce();
        expect(!f.messages.isEmpty(), "and it arrives at the new hour");

        Rig noWorld = new Rig(false);
        // a rig on a server with its overworld removed must not blow up
        org.bukkit.Bukkit.WORLDS.clear();
        noWorld.tickOnce();
        expect(noWorld.manager.referenceWorld() == null, "no overworld: a tick is harmless");
    }

    static void persistence() {
        Rig r = new Rig();
        BukkitKit.TestPlayer a = BukkitKit.join("Ann", r.world);
        r.world.setTime(1000);
        r.tickOnce();
        DayPlan first = r.manager.currentPlan();
        r.manager.stop();

        // "restart": same data folder, a brand new manager
        org.bukkit.Bukkit.PLAYERS.clear();
        BukkitScheduler.REPEATING.clear();
        Rig again = new Rig(false);
        BukkitKit.TestPlayer a2 = BukkitKit.join("Ann", again.world);
        again.world.setTime(1200);
        again.tickOnce();
        expect(a2.messages.isEmpty(), "after a restart the same morning's report is not sent twice");
        DayPlan loaded = again.manager.currentPlan();
        expect(loaded != null && Math.abs(loaded.highF - first.highF) < 0.01 && Math.abs(loaded.lowF - first.lowF) < 0.01,
                "the plan survives a restart");
        expect(loaded != null && loaded.pollen == first.pollen, "including the pollen of the day");
    }

    static void liveReading() {
        Rig r = new Rig();
        BukkitKit.TestPlayer a = BukkitKit.join("Ann", r.world);
        r.world.setTime(9000);
        r.tickOnce();
        Reading dry = r.manager.reading(a);
        expect(dry != null, "an overworld player has a reading");
        for (int i = 0; i < 200; i++) {
            r.tickOnce();
        }
        Reading calm = r.manager.reading(a);
        r.world.setStorm(true);
        for (int i = 0; i < 200; i++) {
            r.tickOnce();
        }
        Reading wet = r.manager.reading(a);
        expect(wet.tempF() < calm.tempF() - 2, "rain cools things down gradually (" + calm.tempF() + " -> " + wet.tempF() + ")");
        expect(wet.rh() > calm.rh(), "and it gets more humid");

        r.exposed = false;
        Reading inside = r.manager.reading(a);
        expect(inside.sheltered(), "indoors the reading says so");

        org.bukkit.World nether = BukkitKit.nether("world_nether");
        BukkitKit.TestPlayer n = BukkitKit.join("Nina", nether);
        expect(r.manager.reading(n) == null, "the Nether has no weather reading");
    }

    static void stalePlan() {
        Rig r = new Rig();
        r.world.setTime(2000);
        DayPlan p = r.manager.currentPlan();
        expect(p != null, "asking for the forecast before 7 AM still gets a plan");
        r.clock.setDayOfSeason(r.clock.dayOfSeason() + 1);
        r.world.setTime(2000);
        DayPlan next = r.manager.currentPlan();
        expect(next != null && next != p, "a plan a full day old is rebuilt");
        expect(r.manager.currentPlan() == next, "and the new one is kept");
    }
}
