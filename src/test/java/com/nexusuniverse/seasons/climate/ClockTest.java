package com.nexusuniverse.seasons.climate;

import static com.nexusuniverse.seasons.climate.Kit.expect;
import static com.nexusuniverse.seasons.climate.Kit.near;

/** The in-game clock, and converting between server ticks and game ticks. */
public class ClockTest {

    public static void main(String[] args) {
        clock();
        rates();
        Kit.finish();
    }

    static void clock() {
        expect(GameClock.clock(0).equals("6:00 AM"), "tick 0 is 6:00 AM: " + GameClock.clock(0));
        expect(GameClock.clock(1000).equals("7:00 AM"), "tick 1000 is 7:00 AM: " + GameClock.clock(1000));
        expect(GameClock.clock(6000).equals("12:00 PM"), "tick 6000 is noon: " + GameClock.clock(6000));
        expect(GameClock.clock(9000).equals("3:00 PM"), "tick 9000 is 3 PM: " + GameClock.clock(9000));
        expect(GameClock.clock(18000).equals("12:00 AM"), "tick 18000 is midnight: " + GameClock.clock(18000));
        expect(GameClock.clock(23000).equals("5:00 AM"), "tick 23000 is 5 AM: " + GameClock.clock(23000));
        expect(GameClock.clock(1500).equals("7:30 AM"), "half hours: " + GameClock.clock(1500));
        expect(GameClock.clockShort(9000).equals("3 PM"), "short form drops :00: " + GameClock.clockShort(9000));
        expect(GameClock.clockShort(9500).equals("3:30 PM"), "short form keeps minutes: " + GameClock.clockShort(9500));
        expect(GameClock.clock(24000).equals("6:00 AM"), "wraps at 24000: " + GameClock.clock(24000));
        expect(GameClock.isDay(0) && GameClock.isDay(11999) && !GameClock.isDay(12000) && !GameClock.isDay(23999), "day is ticks 0-11999");
        near(GameClock.hourOf(1000), 7.0, 1e-9, "hourOf 1000");
    }

    static void rates() {
        GameClock.Rates v = GameClock.Rates.vanilla();
        near(v.gameTicksAfter(5000, 300), 300, 1e-9, "vanilla runs one for one");
        near(v.serverTicksFor(5000, 300), 300, 1e-9, "vanilla inverse");

        // default server: 720 real minutes of day and of night, so a game hour is a real hour (72000 ticks)
        GameClock.Rates r = new GameClock.Rates(true, 720, 720);
        near(r.gameTicksAfter(1000, 72000), 1000, 1e-6, "one real hour is one game hour");
        near(r.serverTicksFor(1000, 24000), 24 * 72000.0, 1e-3, "a whole game day is 24 real hours");
        near(r.rateAt(100), 1.0 / 72.0, 1e-9, "rate by day");

        // uneven: 360 minute days, 720 minute nights
        GameClock.Rates u = new GameClock.Rates(true, 360, 720);
        near(u.rateAt(100), 12000.0 / (360 * 1200.0), 1e-9, "faster by day");
        near(u.rateAt(13000), 12000.0 / (720 * 1200.0), 1e-9, "slower by night");
        // from 11000 (an hour before dusk) go across the boundary
        double server = u.serverTicksFor(11000, 3000);
        double expect = 1000 / u.rateAt(100) + 2000 / u.rateAt(13000);
        near(server, expect, 1e-6, "crossing dusk uses both rates");
        near(u.gameTicksAfter(11000, server), 3000, 1e-6, "round trip across dusk");
        // crossing midnight (the 24000 wrap)
        double s2 = u.serverTicksFor(23000, 2000);
        near(u.gameTicksAfter(23000, s2), 2000, 1e-6, "round trip across dawn");
        near(s2, 1000 / u.rateAt(13000) + 1000 / u.rateAt(100), 1e-6, "crossing dawn uses both rates");
        // a long way: three days
        double three = u.serverTicksFor(5000, 72000);
        near(u.gameTicksAfter(5000, three), 72000, 1e-4, "three days round trip");
    }
}
