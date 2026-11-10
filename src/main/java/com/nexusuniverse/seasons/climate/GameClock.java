package com.nexusuniverse.seasons.climate;

/**
 * The in-game clock, using the same convention as the time boss bar and vanilla: world tick 0 is
 * 6:00 AM, 1000 ticks is one clock hour, so 7:00 AM is tick 1000 and 3:00 PM is tick 9000.
 *
 * {@link Rates} converts between real server ticks and game ticks. With the custom day/night cycle
 * on, a game tick does not last one server tick: day and night each stretch over their own number
 * of real minutes (720 each by default, so one game hour is one real hour). The forecast needs this
 * to line the weather schedule (kept in server ticks) up against the game clock.
 */
public final class GameClock {

    public static final int DAY = 24000;
    public static final int HALF = 12000;

    private GameClock() {
    }

    /** Hour of day, 0 up to (not including) 24, for a world tick. */
    public static double hourOf(long tick) {
        double h = ((Math.floorMod(tick, DAY)) / 1000.0 + 6.0) % 24.0;
        return h;
    }

    /** "7:00 AM" style clock for a world tick. */
    public static String clock(long tick) {
        int minutesOfDay = (int) Math.round(hourOf(tick) * 60.0) % (24 * 60);
        int h24 = minutesOfDay / 60;
        int m = minutesOfDay % 60;
        int h12 = h24 % 12 == 0 ? 12 : h24 % 12;
        return h12 + ":" + (m < 10 ? "0" : "") + m + (h24 < 12 ? " AM" : " PM");
    }

    /** "3 PM", or "3:20 PM" when it is not on the hour. */
    public static String clockShort(long tick) {
        String full = clock(tick);
        return full.replace(":00 ", " ");
    }

    /** True from sunrise (tick 0) up to sunset (tick 12000). */
    public static boolean isDay(long tick) {
        return Math.floorMod(tick, DAY) < HALF;
    }

    /** How fast the game clock runs, and conversions between server ticks and game ticks. */
    public static final class Rates {
        private final boolean custom;
        private final double dayServerTicks;
        private final double nightServerTicks;

        /** @param custom false means vanilla speed: one game tick per server tick. */
        public Rates(boolean custom, double dayMinutes, double nightMinutes) {
            this.custom = custom;
            this.dayServerTicks = Math.max(1.0, dayMinutes) * 1200.0;
            this.nightServerTicks = Math.max(1.0, nightMinutes) * 1200.0;
        }

        public static Rates vanilla() {
            return new Rates(false, 20, 20);
        }

        /** Game ticks that pass per server tick when the clock reads {@code tick}. */
        public double rateAt(double tick) {
            if (!custom) {
                return 1.0;
            }
            return Math.floorMod((long) Math.floor(tick), DAY) < HALF ? HALF / dayServerTicks : HALF / nightServerTicks;
        }

        /** Game ticks elapsed after {@code serverTicks} real ticks, starting from clock reading {@code startTick}. */
        public double gameTicksAfter(double startTick, double serverTicks) {
            if (!custom) {
                return serverTicks;
            }
            double t = Math.floorMod((long) Math.floor(startTick), DAY) + (startTick - Math.floor(startTick));
            double remaining = serverTicks;
            double total = 0;
            int guard = 0;
            while (remaining > 1e-9 && guard++ < 10000) {
                boolean day = t < HALF;
                double rate = day ? HALF / dayServerTicks : HALF / nightServerTicks;
                double end = day ? HALF : DAY;
                double serverNeeded = (end - t) / rate;
                if (remaining < serverNeeded) {
                    total += remaining * rate;
                    break;
                }
                total += end - t;
                remaining -= serverNeeded;
                t = end >= DAY ? 0 : end;
            }
            return total;
        }

        /** Real server ticks needed for the clock to advance {@code gameTicks} from {@code startTick}. */
        public double serverTicksFor(double startTick, double gameTicks) {
            if (!custom) {
                return gameTicks;
            }
            double t = Math.floorMod((long) Math.floor(startTick), DAY) + (startTick - Math.floor(startTick));
            double remaining = gameTicks;
            double total = 0;
            int guard = 0;
            while (remaining > 1e-9 && guard++ < 10000) {
                boolean day = t < HALF;
                double rate = day ? HALF / dayServerTicks : HALF / nightServerTicks;
                double end = day ? HALF : DAY;
                double room = end - t;
                if (remaining < room) {
                    total += remaining / rate;
                    break;
                }
                total += room / rate;
                remaining -= room;
                t = end >= DAY ? 0 : end;
            }
            return total;
        }
    }
}
