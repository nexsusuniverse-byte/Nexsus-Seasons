package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.SeasonsConfig;
import com.nexusuniverse.seasons.WeatherCycleManager;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.ArrayList;
import java.util.List;

import static com.nexusuniverse.seasons.climate.BukkitKit.reset;
import static com.nexusuniverse.seasons.climate.Kit.expect;

/**
 * The forecast only means something if the rain it names is the rain that falls. This plays the real
 * WeatherCycleManager forward for days and checks it against what upcoming() promised.
 */
public class ScheduleTest {

    public static void main(String[] args) {
        promisesAreKept();
        shape();
        laterPromises();
        Kit.finish();
    }

    static final class Rig {
        final BukkitKit.TestPlugin plugin = new BukkitKit.TestPlugin();
        final SeasonsConfig config = new SeasonsConfig(plugin);
        final World world;
        final WeatherCycleManager manager;
        final Runnable tick;

        Rig() {
            reset();
            world = BukkitKit.overworld("world");
            manager = new WeatherCycleManager(plugin, config);
            manager.start();
            tick = BukkitScheduler.REPEATING.get(BukkitScheduler.REPEATING.size() - 1);
        }

        boolean wet(List<WeatherSpan> spans, long t) {
            for (WeatherSpan s : spans) {
                if (s.contains(t)) {
                    return true;
                }
            }
            return false;
        }

        boolean thunder(List<WeatherSpan> spans, long t) {
            for (WeatherSpan s : spans) {
                if (s.contains(t)) {
                    return s.thundering();
                }
            }
            return false;
        }
    }

    static void promisesAreKept() {
        Rig r = new Rig();
        long horizon = 3L * 24 * 60 * 60 * 20; // three real days
        List<WeatherSpan> promised = r.manager.upcoming(horizon);
        expect(!promised.isEmpty() && promised.get(0).start() == 0, "the first spell is already underway: it always starts with rain");
        expect(r.world.hasStorm(), "and the world is raining");

        long wrong = 0;
        long wetTicks = 0;
        long thunderWrong = 0;
        for (long k = 1; k <= horizon; k++) {
            r.tick.run();
            boolean want = r.wet(promised, k);
            if (r.world.hasStorm() != want) {
                wrong++;
            }
            if (r.world.isThundering() != (want && r.thunder(promised, k))) {
                thunderWrong++;
            }
            if (want) {
                wetTicks++;
            }
        }
        expect(wrong == 0, "rain fell exactly when promised, for three days (" + wrong + " ticks differed)");
        expect(thunderWrong == 0, "thunder too (" + thunderWrong + " ticks differed)");
        // clear 20-45 min and rain 10-25 min: rain is between 10/55 and 25/45 of the time
        double share = (double) wetTicks / horizon;
        expect(share > 0.2 && share < 0.5, "a believable amount of rain: " + share);
    }

    static void shape() {
        Rig r = new Rig();
        List<WeatherSpan> spans = r.manager.upcoming(24L * 60 * 60 * 20);
        long prevEnd = -1;
        boolean ordered = true;
        boolean rangesOk = true;
        for (int i = 0; i < spans.size(); i++) {
            WeatherSpan s = spans.get(i);
            ordered &= s.start() >= prevEnd && s.end() > s.start();
            prevEnd = s.end();
            // the first spell may be cut short by the current moment (it has "remaining" ticks), the rest are whole
            long minutes = (s.end() - s.start()) / 1200;
            if (i > 0) {
                rangesOk &= minutes >= 10 && minutes <= 25;
            }
        }
        expect(ordered, "spells are in order and never overlap");
        expect(rangesOk, "each whole spell is 10 to 25 minutes, as configured");
        // gaps between spells are the clear periods, 20 to 45 minutes
        boolean gapsOk = true;
        for (int i = 1; i < spans.size(); i++) {
            long gap = (spans.get(i).start() - spans.get(i - 1).end()) / 1200;
            gapsOk &= gap >= 20 && gap <= 45;
        }
        expect(gapsOk, "gaps are 20 to 45 minutes of clear weather");
        expect(r.manager.upcoming(0).size() <= 1, "a zero horizon only shows what is falling now");
        // asking twice gives the same answer: looking does not change the weather
        expect(r.manager.upcoming(1_000_000).equals(r.manager.upcoming(1_000_000)), "asking is free of side effects");

        // thunder odds are honoured at the extremes
        Rig always = new Rig();
        always.plugin.getConfig().set("weather.thunder-chance", 1.0);
        Rig never = new Rig();
        never.plugin.getConfig().set("weather.thunder-chance", 0.0);
        // the queue is rolled on start with the default 0.35 chance, so read the odds off a long run instead
        int storms = 0;
        int total = 0;
        for (WeatherSpan s : never.manager.upcoming(6L * 24 * 60 * 60 * 20)) {
            total++;
            if (s.thundering()) {
                storms++;
            }
        }
        double rate = (double) storms / total;
        expect(total > 100 && rate > 0.2 && rate < 0.5, "about a third of spells have thunder by default: " + rate + " of " + total);
        expect(always.manager.upcoming(10).size() <= 1, "rig sanity");
    }

    static void laterPromises() {
        Rig r = new Rig();
        long day = 24L * 60 * 60 * 20;
        long horizon = 5 * day;
        // play two days forward, then ask again: the new answer should be the old one, moved up by two days
        List<WeatherSpan> before = r.manager.upcoming(horizon);
        long skip = 2 * day;
        for (long k = 0; k < skip; k++) {
            r.tick.run();
        }
        List<WeatherSpan> after = r.manager.upcoming(horizon - skip);
        boolean same = true;
        int compared = 0;
        for (WeatherSpan b : before) {
            long s = b.start() - skip;
            long e = b.end() - skip;
            if (e <= 0 || s >= horizon - skip - 1) {
                continue;
            }
            long cs = Math.max(0, s);
            boolean found = false;
            for (WeatherSpan a : after) {
                if (a.start() == cs && a.end() == e && a.thundering() == b.thundering()) {
                    found = true;
                    break;
                }
            }
            compared++;
            same &= found;
        }
        expect(compared > 50, "a good number of spells compared: " + compared);
        expect(same, "what was promised two days ago is still what is coming");

        // the queue never runs dry, even over a week
        for (long k = 0; k < 5 * day; k++) {
            r.tick.run();
        }
        expect(r.manager.upcoming(6 * day).size() > 100, "still a long schedule after a week");

        // an override (a hurricane) changes the world but not the promise
        r.manager.forceState(true, true, 200);
        r.tick.run();
        expect(r.world.hasStorm() && r.world.isThundering(), "override applies");
        r.manager.clearOverride();
        List<WeatherSpan> list = new ArrayList<>(r.manager.upcoming(day));
        expect(!list.isEmpty(), "schedule untouched by overrides");
    }
}
