package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.Season;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the real weather schedule plus the climate model into the day's plan. The wet spells in it
 * are not guesses: they are read from the same queue that WeatherCycleManager will actually play.
 */
public final class ForecastBuilder {

    private static final int SAMPLES = 48;
    private static final int STEP = GameClock.DAY / SAMPLES;

    private ForecastBuilder() {
    }

    /**
     * @param spans wet spells in server ticks from now, or null if the weather is not scheduled (vanilla weather)
     */
    public static DayPlan build(ClimateParams p, GameClock.Rates rates, int year, Season season, int day,
                                long startTick, List<WeatherSpan> spans, double windMinMph, double windMaxMph) {
        long dayIdx = Climate.dayIndex(year, season, day, p.daysPerSeason);
        double x0 = Climate.dayCoord(year, season, day, p.daysPerSeason, startTick);
        boolean known = spans != null;

        double hi = -1e9;
        double lo = 1e9;
        long hiTick = startTick;
        long loTick = startTick;
        double rhMin = 1e9;
        double rhMax = -1e9;
        double dewSum = 0;
        double cloudSum = 0;
        double uvMax = 0;
        double feelsHi = -1e9;
        int wet = 0;
        int wetCold = 0;
        boolean[] wetAt = new boolean[SAMPLES];
        boolean[] stormAt = new boolean[SAMPLES];
        int[] partOf = new int[SAMPLES];

        for (int i = 0; i < SAMPLES; i++) {
            double g = (double) i * STEP;
            double tick = startTick + g;
            double x = x0 + g / GameClock.DAY;
            double serverOffset = rates.serverTicksFor(startTick, g);
            boolean isWet = false;
            boolean storm = false;
            if (known) {
                for (WeatherSpan s : spans) {
                    if (s.contains(serverOffset)) {
                        isWet = true;
                        storm = s.thundering();
                        break;
                    }
                }
            }
            wetAt[i] = isWet;
            partOf[i] = partOfDay(tick, i);
            stormAt[i] = storm;
            double cloud = isWet ? 1.0 : Climate.cloudBase(p, x);
            double offset = Climate.weatherOffsetF(p, isWet, storm, false, false);
            double t = Climate.baseMeanF(p, x) + Climate.diurnalF(tick, Climate.dayRangeF(p, cloud)) + offset;
            double m = Climate.moisture(p, x, season, 0.4, isWet, storm, false);
            double dew = Climate.dewPointF(Climate.baseMeanF(p, x), m, t, isWet);
            double rh = Climate.relativeHumidity(t, dew);
            double feels = Climate.feelsLikeF(t, rh, Climate.windMph(0.2));
            if (t > hi) {
                hi = t;
                hiTick = Math.floorMod((long) tick, GameClock.DAY);
            }
            if (t < lo) {
                lo = t;
                loTick = Math.floorMod((long) tick, GameClock.DAY);
            }
            rhMin = Math.min(rhMin, rh);
            rhMax = Math.max(rhMax, rh);
            dewSum += dew;
            cloudSum += cloud;
            uvMax = Math.max(uvMax, Climate.uvIndex(p, x, tick, cloud, isWet));
            feelsHi = Math.max(feelsHi, feels);
            if (isWet) {
                wet++;
                if (t <= Climate.SNOW_LINE_F) {
                    wetCold++;
                }
            }
        }

        // count spells that touch the next 24 game hours
        int spellCount = 0;
        int thunderCount = 0;
        if (known) {
            double horizon = rates.serverTicksFor(startTick, GameClock.DAY);
            for (WeatherSpan s : spans) {
                if (s.start() < horizon && s.end() > 0) {
                    spellCount++;
                    if (s.thundering()) {
                        thunderCount++;
                    }
                }
            }
        }

        double share = (double) wet / SAMPLES;
        boolean snowy = wet > 0 && wetCold * 2 >= wet;
        String headline = known ? headline(wetAt, stormAt, partOf, snowy, share) : "Unpredictable";
        double cloudAvg = cloudSum / SAMPLES;
        String sky = known && share >= 0.5 ? (snowy ? "Snowy" : "Rainy") : Climate.skyText(cloudAvg, true);

        PollenType type = Climate.dominantPollen(p, season, day, dayIdx);
        double score = Climate.pollenScore(p, season, day, dayIdx, type, hi, false, 0.2) * (1.0 - 0.85 * share);
        return new DayPlan(dayKeyOf(year, season, day), year, season, day, startTick, hi, hiTick, lo, loTick,
                rhMin, rhMax, dewSum / SAMPLES, share, spellCount, thunderCount, snowy, known, headline, sky, uvMax,
                type, Climate.pollenLevel(score), feelsHi, windMinMph, windMaxMph);
    }

    public static String dayKeyOf(int year, Season season, int day) {
        return year + "-" + season.name() + "-" + day;
    }

    /** 0 morning (6a-12p), 1 afternoon, 2 evening, 3 overnight (12a-6a, and the last hour before the next dawn). */
    static int partOfDay(double tick, int sampleIndex) {
        double h = GameClock.hourOf((long) tick);
        if (h < 6) {
            return 3;
        }
        if (h < 12) {
            return sampleIndex >= SAMPLES * 5 / 6 ? 3 : 0;
        }
        return h < 18 ? 1 : 2;
    }

    private static final String[] PARTS = {"this morning", "this afternoon", "this evening", "overnight"};

    /** "Rain this afternoon and overnight" from which parts of the day are mostly wet. */
    static String headline(boolean[] wetAt, boolean[] stormAt, int[] partOf, boolean snowy, double share) {
        if (share <= 0.0) {
            return "Dry";
        }
        boolean anyStorm = false;
        for (boolean s : stormAt) {
            anyStorm |= s;
        }
        List<String> hit = new ArrayList<>();
        for (int part = 0; part < 4; part++) {
            int total = 0;
            int w = 0;
            for (int i = 0; i < wetAt.length; i++) {
                if (partOf[i] == part) {
                    total++;
                    if (wetAt[i]) {
                        w++;
                    }
                }
            }
            if (total > 0 && w * 10 >= total * 3) {
                hit.add(PARTS[part]);
            }
        }
        String what = anyStorm ? "Thunderstorms" : snowy ? "Snow" : "Rain";
        if (hit.isEmpty()) {
            return "Brief " + what.toLowerCase() + " possible";
        }
        if (share >= 0.85 || hit.size() == 4) {
            return what + " all day";
        }
        if (hit.size() == 1) {
            return what + " " + hit.get(0);
        }
        StringBuilder sb = new StringBuilder(what).append(' ');
        for (int i = 0; i < hit.size(); i++) {
            if (i > 0) {
                sb.append(i == hit.size() - 1 ? " and " : ", ");
            }
            sb.append(hit.get(i));
        }
        return sb.toString();
    }
}
