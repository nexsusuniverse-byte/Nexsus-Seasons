package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.Season;

/**
 * The weather model, as pure functions: no Bukkit, nothing stateful, so it can be tested to death.
 *
 * Everything hangs off one continuous "day coordinate" x (whole numbers are dawns, 6:00 AM). Seasonal
 * warmth, multi-day warm and cool spells, cloudiness and moisture are all smooth functions of x, so
 * nothing jumps at midnight or when the calendar rolls over. Temperature is, in order:
 * seasonal mean + a slow random spell + the time-of-day swing + the current weather + local biome.
 * Dew point comes from the day's airmass moisture (not the instantaneous temperature), so humidity
 * falls through the afternoon and climbs towards dawn on its own, the way it does outside.
 */
public final class Climate {

    private Climate() {
    }

    // ---- randomness: deterministic, so a forecast and the weather it describes agree --------------

    static double hash01(long seed, long k) {
        long z = seed + k * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * (1.0 / (1L << 53));
    }

    /** Smooth value noise in [-1, 1]. */
    static double noise(long seed, double x, long salt) {
        double fl = Math.floor(x);
        long i = (long) fl;
        double f = x - fl;
        double s = f * f * (3 - 2 * f);
        double a = hash01(seed ^ (salt * 0x632BE59BD9B4E019L), i) * 2 - 1;
        double b = hash01(seed ^ (salt * 0x632BE59BD9B4E019L), i + 1) * 2 - 1;
        return a + (b - a) * s;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- the calendar as a number -------------------------------------------------------------------

    /** Whole days since year 0, spring day 1, for a calendar date. */
    public static long dayIndex(int year, Season season, int dayOfSeason, int daysPerSeason) {
        return ((long) year * 4 + season.ordinal()) * daysPerSeason + (dayOfSeason - 1);
    }

    /** Continuous day coordinate for a calendar date and world tick. Tick 0 (dawn) starts the day. */
    public static double dayCoord(int year, Season season, int dayOfSeason, int daysPerSeason, double tick) {
        return dayIndex(year, season, dayOfSeason, daysPerSeason) + tick / GameClock.DAY;
    }

    /** At or below this the rain falls as snow. */
    public static final double SNOW_LINE_F = 34.0;

    // ---- temperature -----------------------------------------------------------------------------------

    public static double seasonalMeanF(ClimateParams p, double x) {
        double year = 4.0 * p.daysPerSeason;
        double pos = (x / year) - Math.floor(x / year);
        // warmest in the middle of summer (a third of the way round... the middle of season index 1)
        return p.annualMeanF + p.annualSwingF * Math.cos(2 * Math.PI * (pos - 0.375));
    }

    /** Slow warm and cool spells lasting a few days. */
    public static double spellF(ClimateParams p, double x) {
        return p.anomalyF * noise(p.seed, x / 3.0, 11);
    }

    public static double baseMeanF(ClimateParams p, double x) {
        return seasonalMeanF(p, x) + spellF(p, x);
    }

    /**
     * The time-of-day swing, -range/2 at the coldest (5 AM) to +range/2 at the warmest (3 PM). It
     * warms for ten hours and cools for fourteen, which is what a real afternoon peak looks like.
     */
    public static double diurnalF(double tick, double rangeF) {
        double t = Math.floorMod((long) Math.floor(tick), GameClock.DAY) + (tick - Math.floor(tick));
        if (t >= 23000) {
            t -= GameClock.DAY;
        }
        double v;
        if (t <= 9000) {
            v = -Math.cos(Math.PI * (t + 1000) / 10000.0);
        } else {
            v = Math.cos(Math.PI * (t - 9000) / 14000.0);
        }
        return v * rangeF / 2.0;
    }

    /** Background cloudiness 0..1 with no precipitation. */
    public static double cloudBase(ClimateParams p, double x) {
        return clamp(0.28 + 0.34 * noise(p.seed, x / 1.7, 23), 0.0, 1.0);
    }

    public static double dayRangeF(ClimateParams p, double cloud) {
        return p.rangeClearF + (p.rangeCloudyF - p.rangeClearF) * clamp(cloud, 0, 1);
    }

    /** The temperature shift the current weather causes. */
    public static double weatherOffsetF(ClimateParams p, boolean precip, boolean thunder, boolean fog, boolean blizzard) {
        double o = 0;
        if (blizzard) {
            o = Math.min(o, p.blizzardOffsetF);
        }
        if (thunder) {
            o = Math.min(o, p.thunderOffsetF);
        } else if (precip) {
            o = Math.min(o, p.rainOffsetF);
        }
        if (fog && o == 0) {
            o = p.fogOffsetF;
        }
        return o;
    }

    /** How much warmer or colder than the regional baseline a biome is. 0.8 is plains. */
    public static double biomeOffsetF(double biomeTemperature) {
        if (Double.isNaN(biomeTemperature)) {
            return 0;
        }
        return clamp((biomeTemperature - 0.8) * 18.0, -30.0, 30.0);
    }

    // ---- humidity --------------------------------------------------------------------------------------

    static double seasonHumidity(Season s) {
        return switch (s) {
            case SPRING -> 0.75;
            case SUMMER -> 0.85;
            case FALL -> 0.70;
            case WINTER -> 0.55;
        };
    }

    /** How moist the air mass is, 0 (desert) to 1 (rainforest). */
    public static double moisture(ClimateParams p, double x, Season season, double biomeHumidity,
                                  boolean precip, boolean thunder, boolean fog) {
        double bh = Double.isNaN(biomeHumidity) ? 0.4 : clamp(biomeHumidity, 0, 1);
        double m = 0.80 * bh + 0.20 * seasonHumidity(season) + 0.10 * noise(p.seed, x / 2.2, 31);
        if (thunder) {
            m += 0.40;
        } else if (precip) {
            m += 0.35;
        }
        if (fog) {
            m += 0.20;
        }
        return clamp(m, 0.0, 1.0);
    }

    /** The day's typical relative humidity (percent) for an air mass of the given moisture. */
    public static double typicalRh(double moisture) {
        return clamp(10.0 + 85.0 * moisture, 8.0, 99.0);
    }

    /**
     * Dew point in F. It is set by the day's air mass at the day's average local temperature, so the
     * humidity swings the right way through the day. It is never above the air temperature, and stays
     * within a few degrees of it while it is raining.
     *
     * @param localMeanF the day's average temperature at this spot (seasonal mean, spell and biome)
     */
    public static double dewPointF(double localMeanF, double moisture, double tempF, boolean wet) {
        double tc = fToC(localMeanF);
        double gamma = Math.log(typicalRh(moisture) / 100.0) + 17.625 * tc / (243.04 + tc);
        double td = cToF(243.04 * gamma / (17.625 - gamma));
        if (wet) {
            td = Math.max(td, tempF - 3.0);
        }
        return Math.min(td, tempF - 0.4);
    }

    public static double fToC(double f) {
        return (f - 32.0) * 5.0 / 9.0;
    }

    public static double cToF(double c) {
        return c * 9.0 / 5.0 + 32.0;
    }

    /** Relative humidity (percent) from air temperature and dew point (Magnus formula). */
    public static double relativeHumidity(double tempF, double dewF) {
        double t = fToC(tempF);
        double td = fToC(Math.min(dewF, tempF));
        double rh = 100.0 * Math.exp(17.625 * td / (243.04 + td)) / Math.exp(17.625 * t / (243.04 + t));
        return clamp(rh, 1.0, 100.0);
    }

    // ---- how it feels ---------------------------------------------------------------------------------

    public static double windMph(double strength) {
        return Math.max(0, strength) * 60.0;
    }

    /** Wind chill (cold, windy) or heat index (hot, humid); otherwise just the temperature. */
    public static double feelsLikeF(double tempF, double rh, double windMph) {
        if (tempF <= 50.0 && windMph > 3.0) {
            double v = Math.pow(windMph, 0.16);
            return 35.74 + 0.6215 * tempF - 35.75 * v + 0.4275 * tempF * v;
        }
        if (tempF >= 80.0 && rh >= 40.0) {
            double t = tempF;
            double r = rh;
            double hi = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r - 0.00683783 * t * t
                    - 0.05481717 * r * r + 0.00122874 * t * t * r + 0.00085282 * t * r * r - 0.00000199 * t * t * r * r;
            return Math.max(hi, t);
        }
        return tempF;
    }

    // ---- sun ---------------------------------------------------------------------------------------------

    /** UV index right now (0 at night), from the season, sun height and cloud. */
    public static double uvIndex(ClimateParams p, double x, double tick, double cloud, boolean precip) {
        double t = Math.floorMod((long) Math.floor(tick), GameClock.DAY);
        if (t >= GameClock.HALF) {
            return 0;
        }
        double year = 4.0 * p.daysPerSeason;
        double pos = (x / year) - Math.floor(x / year);
        double seasonFrac = (Math.cos(2 * Math.PI * (pos - 0.375)) + 1) / 2.0;
        double peak = 1.5 + 9.5 * seasonFrac;
        double sun = Math.pow(Math.sin(Math.PI * t / GameClock.HALF), 1.5);
        double shade = precip ? 0.2 : 1.0 - 0.6 * clamp(cloud, 0, 1);
        return peak * sun * shade;
    }

    // ---- pollen ------------------------------------------------------------------------------------------

    /** The thing most in the air on a given day. Changes with the calendar, the same for everyone. */
    public static PollenType dominantPollen(ClimateParams p, Season season, int dayOfSeason, long dayIndex) {
        double through = (dayOfSeason - 0.5) / p.daysPerSeason;
        double h = hash01(p.seed ^ 0x5DEECE66DL, dayIndex);
        switch (season) {
            case SPRING:
                if (through < 0.6) {
                    return h < 0.45 ? PollenType.OAK : h < 0.8 ? PollenType.BIRCH : PollenType.MAPLE;
                }
                return PollenType.TIMOTHY;
            case SUMMER:
                if (through < 0.7) {
                    return h < 0.6 ? PollenType.TIMOTHY : PollenType.BERMUDA;
                }
                return PollenType.MUGWORT;
            case FALL:
                return h < 0.65 ? PollenType.RAGWEED : h < 0.85 ? PollenType.MUGWORT : PollenType.SAGEBRUSH;
            default:
                return h < 0.55 ? PollenType.MOLD : PollenType.CEDAR;
        }
    }

    static double pollenSeasonStrength(Season s) {
        return switch (s) {
            case SPRING -> 1.0;
            case SUMMER -> 0.8;
            case FALL -> 0.9;
            case WINTER -> 0.3;
        };
    }

    /**
     * How much pollen is in the air: roughly 0 to 1.3. Peaks mid-season, is washed out by rain, blown
     * about by wind, and needs warmth (mould excepted).
     */
    public static double pollenScore(ClimateParams p, Season season, int dayOfSeason, long dayIndex,
                                     PollenType type, double tempF, boolean precip, double windStrength) {
        double through = (dayOfSeason - 0.5) / p.daysPerSeason;
        double bell = 0.45 + 0.55 * Math.sin(Math.PI * clamp(through, 0, 1));
        double variation = 0.65 + 0.7 * hash01(p.seed ^ 0x2545F4914F6CDD1DL, dayIndex);
        double warmth = type.family() == AllergyType.MOLD ? 1.0 : clamp((tempF - 30.0) / 30.0, 0.3, 1.1);
        double wash = precip ? 0.15 : 1.0;
        double wind = 0.85 + 0.5 * clamp(windStrength, 0, 1);
        return pollenSeasonStrength(season) * bell * variation * warmth * wash * wind;
    }

    /** 0 None, 1 Low, 2 Moderate, 3 High, 4 Very High. */
    public static int pollenLevel(double score) {
        if (score < 0.12) {
            return 0;
        }
        if (score < 0.40) {
            return 1;
        }
        if (score < 0.70) {
            return 2;
        }
        if (score < 1.00) {
            return 3;
        }
        return 4;
    }

    // ---- words and colours --------------------------------------------------------------------------

    public static final String[] POLLEN_NAMES = {"None", "Low", "Moderate", "High", "Very High"};
    public static final String[] POLLEN_COLORS = {"§7", "§a", "§e", "§6", "§c"};

    public static String uvName(double uv) {
        if (uv < 3) {
            return "Low";
        }
        if (uv < 6) {
            return "Moderate";
        }
        if (uv < 8) {
            return "High";
        }
        if (uv < 11) {
            return "Very High";
        }
        return "Extreme";
    }

    public static String uvColor(double uv) {
        if (uv < 3) {
            return "§a";
        }
        if (uv < 6) {
            return "§e";
        }
        if (uv < 8) {
            return "§6";
        }
        if (uv < 11) {
            return "§c";
        }
        return "§d";
    }

    public static String tempName(double f) {
        if (f <= 10) {
            return "Frigid";
        }
        if (f <= 32) {
            return "Freezing";
        }
        if (f <= 45) {
            return "Cold";
        }
        if (f <= 60) {
            return "Cool";
        }
        if (f <= 75) {
            return "Pleasant";
        }
        if (f <= 85) {
            return "Warm";
        }
        if (f <= 95) {
            return "Hot";
        }
        if (f <= 105) {
            return "Sweltering";
        }
        return "Scorching";
    }

    public static String tempColor(double f) {
        if (f <= 10) {
            return "§9";
        }
        if (f <= 32) {
            return "§b";
        }
        if (f <= 45) {
            return "§3";
        }
        if (f <= 60) {
            return "§a";
        }
        if (f <= 75) {
            return "§2";
        }
        if (f <= 85) {
            return "§e";
        }
        if (f <= 95) {
            return "§6";
        }
        if (f <= 105) {
            return "§c";
        }
        return "§4";
    }

    public static String humidityColor(double rh) {
        if (rh < 30) {
            return "§e";
        }
        if (rh < 60) {
            return "§b";
        }
        if (rh < 80) {
            return "§3";
        }
        return "§9";
    }

    public static String dewName(double dewF) {
        if (dewF < 40) {
            return "Bone dry";
        }
        if (dewF < 50) {
            return "Dry";
        }
        if (dewF < 60) {
            return "Comfortable";
        }
        if (dewF < 65) {
            return "Sticky";
        }
        if (dewF < 70) {
            return "Muggy";
        }
        if (dewF < 75) {
            return "Oppressive";
        }
        return "Miserable";
    }

    public static String dewColor(double dewF) {
        if (dewF < 40) {
            return "§e";
        }
        if (dewF < 50) {
            return "§f";
        }
        if (dewF < 60) {
            return "§a";
        }
        if (dewF < 65) {
            return "§e";
        }
        if (dewF < 70) {
            return "§6";
        }
        return "§c";
    }

    public static String windName(double strength) {
        if (strength < 0.15) {
            return "Calm";
        }
        if (strength < 0.3) {
            return "Breezy";
        }
        if (strength < 0.5) {
            return "Windy";
        }
        if (strength < 0.7) {
            return "Strong";
        }
        return "Severe";
    }

    public static String windColor(double strength) {
        if (strength < 0.15) {
            return "§f";
        }
        if (strength < 0.3) {
            return "§7";
        }
        if (strength < 0.5) {
            return "§e";
        }
        if (strength < 0.7) {
            return "§6";
        }
        return "§c";
    }

    /** A sky description from cloud cover and time of day (no precipitation). */
    public static String skyText(double cloud, boolean day) {
        if (cloud < 0.15) {
            return day ? "Sunny" : "Clear";
        }
        if (cloud < 0.32) {
            return day ? "Mostly Sunny" : "Mostly Clear";
        }
        if (cloud < 0.5) {
            return "Partly Cloudy";
        }
        return "Mostly Cloudy";
    }

    /** Whole degrees in the chosen unit: "78°F" or "26°C". */
    public static String degrees(double f, boolean celsius) {
        return Math.round(celsius ? fToC(f) : f) + (celsius ? "°C" : "°F");
    }

    /** Wind speed in the chosen unit. */
    public static String speed(double mph, boolean metric) {
        return metric ? Math.round(mph * 1.609) + " km/h" : Math.round(mph) + " mph";
    }
}
