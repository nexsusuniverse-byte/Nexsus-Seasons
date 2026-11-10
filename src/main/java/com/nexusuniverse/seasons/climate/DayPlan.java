package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.Season;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the daily report says, worked out once at 7:00 AM from the actual weather schedule.
 * Plain values only, so it can be saved to a file and read back after a restart.
 */
public final class DayPlan {

    public final String key;
    public final int year;
    public final Season season;
    public final int day;
    public final long startTick;
    public final double highF;
    public final long highTick;
    public final double lowF;
    public final long lowTick;
    public final double rhMin;
    public final double rhMax;
    public final double dewAvgF;
    public final double precipShare;
    public final int spells;
    public final int thunderSpells;
    public final boolean snowy;
    public final boolean precipKnown;
    public final String precipHeadline;
    public final String skyText;
    public final double uvPeak;
    public final PollenType pollen;
    public final int pollenLevel;
    public final double feelsHighF;
    public final double windMinMph;
    public final double windMaxMph;

    public DayPlan(String key, int year, Season season, int day, long startTick, double highF, long highTick,
                   double lowF, long lowTick, double rhMin, double rhMax, double dewAvgF, double precipShare,
                   int spells, int thunderSpells, boolean snowy, boolean precipKnown, String precipHeadline,
                   String skyText, double uvPeak, PollenType pollen, int pollenLevel, double feelsHighF,
                   double windMinMph, double windMaxMph) {
        this.key = key;
        this.year = year;
        this.season = season;
        this.day = day;
        this.startTick = startTick;
        this.highF = highF;
        this.highTick = highTick;
        this.lowF = lowF;
        this.lowTick = lowTick;
        this.rhMin = rhMin;
        this.rhMax = rhMax;
        this.dewAvgF = dewAvgF;
        this.precipShare = precipShare;
        this.spells = spells;
        this.thunderSpells = thunderSpells;
        this.snowy = snowy;
        this.precipKnown = precipKnown;
        this.precipHeadline = precipHeadline;
        this.skyText = skyText;
        this.uvPeak = uvPeak;
        this.pollen = pollen;
        this.pollenLevel = pollenLevel;
        this.feelsHighF = feelsHighF;
        this.windMinMph = windMinMph;
        this.windMaxMph = windMaxMph;
    }

    /** Wet hours in game time. */
    public double wetHours() {
        return precipShare * 24.0;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("year", year);
        m.put("season", season.name());
        m.put("day", day);
        m.put("start-tick", startTick);
        m.put("high-f", highF);
        m.put("high-tick", highTick);
        m.put("low-f", lowF);
        m.put("low-tick", lowTick);
        m.put("rh-min", rhMin);
        m.put("rh-max", rhMax);
        m.put("dew-avg-f", dewAvgF);
        m.put("precip-share", precipShare);
        m.put("spells", spells);
        m.put("thunder-spells", thunderSpells);
        m.put("snowy", snowy);
        m.put("precip-known", precipKnown);
        m.put("precip-headline", precipHeadline);
        m.put("sky", skyText);
        m.put("uv-peak", uvPeak);
        m.put("pollen", pollen.name());
        m.put("pollen-level", pollenLevel);
        m.put("feels-high-f", feelsHighF);
        m.put("wind-min-mph", windMinMph);
        m.put("wind-max-mph", windMaxMph);
        return m;
    }

    private static double d(Map<String, Object> m, String k, double fallback) {
        Object o = m.get(k);
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    private static int i(Map<String, Object> m, String k, int fallback) {
        Object o = m.get(k);
        return o instanceof Number n ? n.intValue() : fallback;
    }

    private static boolean b(Map<String, Object> m, String k, boolean fallback) {
        Object o = m.get(k);
        return o instanceof Boolean v ? v : fallback;
    }

    private static String s(Map<String, Object> m, String k, String fallback) {
        Object o = m.get(k);
        return o instanceof String v ? v : fallback;
    }

    /** Reads a saved plan back; returns null if it is missing or damaged. */
    public static DayPlan fromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        try {
            String key = s(m, "key", null);
            if (key == null) {
                return null;
            }
            Season season = Season.valueOf(s(m, "season", "SPRING"));
            PollenType pollen = PollenType.valueOf(s(m, "pollen", "OAK"));
            return new DayPlan(key, i(m, "year", 0), season, i(m, "day", 1), (long) d(m, "start-tick", 1000),
                    d(m, "high-f", 60), (long) d(m, "high-tick", 9000), d(m, "low-f", 40), (long) d(m, "low-tick", 23000),
                    d(m, "rh-min", 40), d(m, "rh-max", 80), d(m, "dew-avg-f", 45), d(m, "precip-share", 0),
                    i(m, "spells", 0), i(m, "thunder-spells", 0), b(m, "snowy", false), b(m, "precip-known", true),
                    s(m, "precip-headline", "Dry"), s(m, "sky", "Partly Cloudy"), d(m, "uv-peak", 0), pollen,
                    Math.max(0, Math.min(4, i(m, "pollen-level", 0))), d(m, "feels-high-f", d(m, "high-f", 60)),
                    d(m, "wind-min-mph", 3), d(m, "wind-max-mph", 30));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
