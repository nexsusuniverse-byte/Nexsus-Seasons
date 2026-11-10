package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.Season;

import java.util.HashSet;
import java.util.Set;

import static com.nexusuniverse.seasons.climate.Kit.expect;
import static com.nexusuniverse.seasons.climate.Kit.near;

/** The climate maths: seasons, time of day, humidity, how it feels, sun, pollen, and the word tables. */
public class ModelTest {

    static final ClimateParams P = ClimateParams.defaults(424242L, 30);

    public static void main(String[] args) {
        seasons();
        timeOfDay();
        continuity();
        humidity();
        feels();
        sun();
        pollen();
        words();
        Kit.finish();
    }

    static double dayMean(Season s, int day) {
        double sum = 0;
        for (int t = 0; t < 24000; t += 1000) {
            double x = Climate.dayCoord(300, s, day, 30, t);
            sum += Climate.baseMeanF(P, x);
        }
        return sum / 24;
    }

    static void seasons() {
        double summer = dayMean(Season.SUMMER, 15);
        double winter = dayMean(Season.WINTER, 15);
        double spring = dayMean(Season.SPRING, 15);
        double fall = dayMean(Season.FALL, 15);
        expect(summer > spring && summer > fall && summer > winter, "summer is the warmest: " + summer);
        expect(winter < spring && winter < fall, "winter is the coldest: " + winter);
        near(summer, 80, 8, "mid summer near 80F");
        near(winter, 28, 8, "mid winter near 28F");
        // the whole year average sits near the configured mean
        double sum = 0;
        int n = 0;
        for (Season s : Season.values()) {
            for (int d = 1; d <= 30; d++) {
                sum += dayMean(s, d);
                n++;
            }
        }
        near(sum / n, 54, 2.5, "year average near the annual mean");
        // spring warms through the season
        expect(dayMean(Season.SPRING, 28) > dayMean(Season.SPRING, 2) + 10, "spring warms up");
        expect(dayMean(Season.FALL, 28) < dayMean(Season.FALL, 2) - 10, "fall cools down");
        // another climate really is different
        ClimateParams hot = new ClimateParams(1L, 30, 75, 15, 6, 20, 11, -5, -8, -24, -2, false);
        double hx = Climate.dayCoord(300, Season.WINTER, 15, 30, 9000);
        expect(Climate.baseMeanF(hot, hx) > Climate.baseMeanF(P, hx) + 20, "a hot climate config is warmer in winter");
        // different seeds wander differently, the same seed repeats
        ClimateParams other = ClimateParams.defaults(7L, 30);
        double x = Climate.dayCoord(300, Season.SUMMER, 3, 30, 5000);
        expect(Climate.baseMeanF(P, x) == Climate.baseMeanF(ClimateParams.defaults(424242L, 30), x), "same seed, same weather");
        boolean differs = false;
        for (int d = 1; d <= 30 && !differs; d++) {
            double xx = Climate.dayCoord(300, Season.SUMMER, d, 30, 0);
            differs = Math.abs(Climate.spellF(P, xx) - Climate.spellF(other, xx)) > 0.5;
        }
        expect(differs, "a different seed gives different spells");
        // spells stay inside the configured swing
        double maxSpell = 0;
        for (int d = 0; d < 400; d++) {
            maxSpell = Math.max(maxSpell, Math.abs(Climate.spellF(P, d * 0.37)));
        }
        expect(maxSpell <= 6.0 + 1e-9 && maxSpell > 2.0, "spells vary but stay within the swing: " + maxSpell);
        // a 40 day season still puts summer on top
        ClimateParams longer = ClimateParams.defaults(424242L, 40);
        double s40 = Climate.seasonalMeanF(longer, Climate.dayCoord(300, Season.SUMMER, 20, 40, 0));
        double w40 = Climate.seasonalMeanF(longer, Climate.dayCoord(300, Season.WINTER, 20, 40, 0));
        expect(s40 > 75 && w40 < 33, "days-per-season is honoured: " + s40 + " / " + w40);
    }

    static void timeOfDay() {
        near(Climate.diurnalF(9000, 20), 10, 1e-9, "3 PM is the day's peak");
        near(Climate.diurnalF(23000, 20), -10, 1e-9, "5 AM is the day's low");
        near(Climate.diurnalF(23000 + 24000, 20), -10, 1e-9, "periodic");
        for (int t = 0; t < 24000; t += 100) {
            double v = Climate.diurnalF(t, 20);
            expect(v <= 10.0001 && v >= -10.0001, "within range at " + t);
        }
        expect(Climate.diurnalF(1000, 20) < Climate.diurnalF(5000, 20), "warming through the morning");
        expect(Climate.diurnalF(12000, 20) > Climate.diurnalF(18000, 20), "cooling after dusk");
        // monotone up to the peak and down after
        double prev = -99;
        boolean rising = true;
        for (int t = 23000 - 24000; t <= 9000; t += 250) {
            double v = Climate.diurnalF(t, 20);
            rising &= v >= prev - 1e-9;
            prev = v;
        }
        expect(rising, "only warms between 5 AM and 3 PM");
        prev = 99;
        boolean falling = true;
        for (int t = 9000; t <= 23000; t += 250) {
            double v = Climate.diurnalF(t, 20);
            falling &= v <= prev + 1e-9;
            prev = v;
        }
        expect(falling, "only cools between 3 PM and 5 AM");
        near(Climate.diurnalF(9000, 0), 0, 1e-9, "no range, no swing");
        expect(Climate.dayRangeF(P, 0) == 20 && Climate.dayRangeF(P, 1) == 11, "cloud narrows the swing");
        near(Climate.dayRangeF(P, 0.5), 15.5, 1e-9, "halfway");
        expect(Climate.weatherOffsetF(P, true, false, false, false) == -5, "rain cools");
        expect(Climate.weatherOffsetF(P, true, true, false, false) == -8, "thunder cools more");
        expect(Climate.weatherOffsetF(P, false, false, true, false) == -2, "fog cools a little");
        expect(Climate.weatherOffsetF(P, false, false, false, true) == -24, "blizzard cools a lot");
        expect(Climate.weatherOffsetF(P, true, true, true, true) == -24, "the worst one wins, they don't stack");
        expect(Climate.weatherOffsetF(P, false, false, false, false) == 0, "no weather no shift");
        expect(Climate.biomeOffsetF(0.8) == 0, "plains is the baseline");
        expect(Climate.biomeOffsetF(2.0) > 20, "desert is hot");
        expect(Climate.biomeOffsetF(0.0) < -10, "snowy plains is cold");
        expect(Climate.biomeOffsetF(-0.5) < Climate.biomeOffsetF(0.0), "frozen peaks colder still");
        expect(Climate.biomeOffsetF(100) == 30 && Climate.biomeOffsetF(-100) == -30, "clamped");
        expect(Climate.biomeOffsetF(Double.NaN) == 0, "unknown biome is neutral");
    }

    static void continuity() {
        // nothing jumps when the calendar rolls over a day, a season or a year
        double worst = 0;
        Season[] order = Season.values();
        for (Season s : order) {
            for (int d : new int[]{1, 7, 29}) {
                double before = Climate.baseMeanF(P, Climate.dayCoord(300, s, d, 30, 23999.9));
                int nd = d + 1;
                Season ns = s;
                int ny = 300;
                if (nd > 30) {
                    nd = 1;
                    ns = s.next();
                    if (s == Season.WINTER) {
                        ny = 301;
                    }
                }
                double after = Climate.baseMeanF(P, Climate.dayCoord(ny, ns, nd, 30, 0));
                worst = Math.max(worst, Math.abs(after - before));
            }
        }
        expect(worst < 0.01, "no jump at a day, season or year rollover: " + worst);
        // and it changes gently within a day
        double step = 0;
        for (int t = 0; t < 23999; t += 100) {
            step = Math.max(step, Math.abs(Climate.baseMeanF(P, Climate.dayCoord(300, Season.FALL, 9, 30, t + 100))
                    - Climate.baseMeanF(P, Climate.dayCoord(300, Season.FALL, 9, 30, t))));
        }
        expect(step < 0.5, "smooth through the day: " + step);
        long idx = Climate.dayIndex(300, Season.SUMMER, 5, 30);
        expect(idx == (300L * 4 + 1) * 30 + 4, "dayIndex arithmetic");
        expect(Climate.dayIndex(300, Season.WINTER, 30, 30) + 1 == Climate.dayIndex(301, Season.SPRING, 1, 30), "year rolls into the next index");
    }

    static void humidity() {
        // Magnus formula against known values
        near(Climate.relativeHumidity(77, 77), 100, 0.01, "dew point at air temp is 100%");
        near(Climate.relativeHumidity(86, 66.2), 52, 3, "86F with a 66F dew point is about 52%");
        near(Climate.relativeHumidity(50, 32), 48, 4, "50F with 32F dew point is about 48%");
        expect(Climate.relativeHumidity(90, 20) >= 1, "never below 1%");
        expect(Climate.relativeHumidity(50, 70) <= 100, "never above 100% even with a bad dew point");
        // moisture ordering
        double x = Climate.dayCoord(300, Season.SUMMER, 10, 30, 0);
        double desert = Climate.moisture(P, x, Season.SUMMER, 0.0, false, false, false);
        double plains = Climate.moisture(P, x, Season.SUMMER, 0.4, false, false, false);
        double jungle = Climate.moisture(P, x, Season.SUMMER, 0.9, false, false, false);
        expect(desert < plains && plains < jungle, "desert < plains < jungle: " + desert + " " + plains + " " + jungle);
        expect(Climate.moisture(P, x, Season.SUMMER, 0.4, true, false, false) > plains + 0.25, "rain moistens the air");
        expect(Climate.moisture(P, x, Season.SUMMER, 0.4, true, true, false) >= Climate.moisture(P, x, Season.SUMMER, 0.4, true, false, false), "thunder at least as moist");
        expect(Climate.moisture(P, x, Season.SUMMER, 0.4, false, false, true) > plains + 0.1, "fog moistens it");
        expect(Climate.moisture(P, x, Season.SUMMER, 5.0, true, true, true) <= 1.0 && Climate.moisture(P, x, Season.SUMMER, -3, false, false, false) >= 0.0, "clamped");
        expect(Climate.moisture(P, x, Season.SUMMER, Double.NaN, false, false, false) == plains || Math.abs(Climate.moisture(P, x, Season.SUMMER, Double.NaN, false, false, false) - plains) < 1e-9, "unknown biome is plains");

        // through a clear day: humid at dawn, driest mid-afternoon, and never impossible
        double rhDawn = 0;
        double rhAfternoon = 0;
        for (Season s : Season.values()) {
            double xs = Climate.dayCoord(300, s, 12, 30, 0);
            double meanF = Climate.baseMeanF(P, xs + 0.5);
            double m = Climate.moisture(P, xs, s, 0.4, false, false, false);
            double lowT = meanF + Climate.diurnalF(23000, 20);
            double highT = meanF + Climate.diurnalF(9000, 20);
            double dewLow = Climate.dewPointF(meanF, m, lowT, false);
            double dewHigh = Climate.dewPointF(meanF, m, highT, false);
            rhDawn = Climate.relativeHumidity(lowT, dewLow);
            rhAfternoon = Climate.relativeHumidity(highT, dewHigh);
            expect(rhDawn > rhAfternoon, s + ": more humid before dawn than mid-afternoon (" + rhDawn + " vs " + rhAfternoon + ")");
            expect(rhAfternoon >= 15 && rhAfternoon <= 95 && rhDawn <= 100, s + ": plausible numbers");
            expect(dewLow <= lowT - 0.39, s + ": dew point under air temp");
        }
        // rain: dew point close to the temperature, so near saturation
        double meanF = 70;
        double rainT = 65;
        double rainDew = Climate.dewPointF(meanF, 0.9, rainT, true);
        expect(rainT - rainDew <= 3.01, "rain holds the dew point within 3F: " + (rainT - rainDew));
        expect(Climate.relativeHumidity(rainT, rainDew) > 85, "raining means humid: " + Climate.relativeHumidity(rainT, rainDew));
        double deserts = Climate.relativeHumidity(105, Climate.dewPointF(100, 0.1, 105, false));
        double jungles = Climate.relativeHumidity(88, Climate.dewPointF(85, 0.85, 88, false));
        expect(deserts < 25 && jungles > 60, "desert dry, jungle wet: " + deserts + " / " + jungles);
        expect(Climate.typicalRh(0) >= 8 && Climate.typicalRh(1) <= 99, "typical RH clamped");
        near(Climate.fToC(212), 100, 1e-9, "212F is 100C");
        near(Climate.cToF(-40), -40, 1e-9, "-40 is -40");
    }

    static void feels() {
        near(Climate.feelsLikeF(70, 50, 5), 70, 1e-9, "mild air feels like itself");
        expect(Climate.feelsLikeF(30, 60, 20) < 20, "a cold wind bites: " + Climate.feelsLikeF(30, 60, 20));
        near(Climate.feelsLikeF(30, 60, 20), 17.4, 1.5, "NWS wind chill at 30F, 20mph is about 17F");
        near(Climate.feelsLikeF(30, 60, 2), 30, 1e-9, "calm cold air is just cold");
        expect(Climate.feelsLikeF(95, 60, 5) > 105, "heat index: 95F at 60% feels over 105F: " + Climate.feelsLikeF(95, 60, 5));
        near(Climate.feelsLikeF(90, 70, 0), 105.9, 2.0, "NWS heat index at 90F/70% is about 106");
        near(Climate.feelsLikeF(85, 20, 5), 85, 1e-9, "dry heat is not amplified");
        expect(Climate.feelsLikeF(81, 90, 0) >= 81, "never feels cooler than the air in humid heat");
        near(Climate.windMph(0.5), 30, 1e-9, "0.5 strength is 30 mph");
        near(Climate.windMph(-1), 0, 1e-9, "negative wind is none");
        expect(Climate.speed(30, true).equals("48 km/h") && Climate.speed(30, false).equals("30 mph"), "speed units: " + Climate.speed(30, true));
        expect(Climate.degrees(72.4, false).equals("72°F") && Climate.degrees(72, true).equals("22°C"), "degree units: " + Climate.degrees(72, true));
        expect(Climate.degrees(-3.6, false).equals("-4°F"), "negative temperatures: " + Climate.degrees(-3.6, false));
    }

    static void sun() {
        double x = Climate.dayCoord(300, Season.SUMMER, 15, 30, 0);
        double noon = Climate.uvIndex(P, x, 6000, 0, false);
        expect(noon > 9 && noon <= 11.01, "clear midsummer noon is a high UV: " + noon);
        expect(Climate.uvIndex(P, x, 12000, 0, false) == 0 && Climate.uvIndex(P, x, 18000, 0, false) == 0, "no UV at night");
        expect(Climate.uvIndex(P, x, 100, 0, false) < 1.5, "low sun at dawn");
        expect(Climate.uvIndex(P, x, 6000, 1, false) < noon * 0.5, "cloud cuts it");
        expect(Climate.uvIndex(P, x, 6000, 1, true) < noon * 0.25, "rain cuts it more");
        double wx = Climate.dayCoord(300, Season.WINTER, 15, 30, 0);
        expect(Climate.uvIndex(P, wx, 6000, 0, false) < 3, "winter sun is weak: " + Climate.uvIndex(P, wx, 6000, 0, false));
        expect(Climate.uvIndex(P, x, 4000, 0, false) < noon && Climate.uvIndex(P, x, 8000, 0, false) < noon, "peaks at midday");
        near(Climate.uvIndex(P, x, 4500, 0, false), Climate.uvIndex(P, x, 7500, 0, false), 1e-6, "symmetric around noon");
        expect(Climate.cloudBase(P, x) >= 0 && Climate.cloudBase(P, x) <= 1, "cloud in range");
        double lo = 1;
        double hi = 0;
        for (int d = 0; d < 300; d++) {
            double c = Climate.cloudBase(P, d * 0.9);
            lo = Math.min(lo, c);
            hi = Math.max(hi, c);
        }
        expect(lo < 0.15 && hi > 0.45 && hi <= 1.0, "clouds come and go: " + lo + ".." + hi);
    }

    static void pollen() {
        Set<AllergyType> springFam = new HashSet<>();
        Set<AllergyType> fallFam = new HashSet<>();
        Set<PollenType> all = new HashSet<>();
        for (int d = 1; d <= 30; d++) {
            for (Season s : Season.values()) {
                PollenType t = Climate.dominantPollen(P, s, d, Climate.dayIndex(300, s, d, 30));
                all.add(t);
                if (s == Season.SPRING && d <= 15) {
                    springFam.add(t.family());
                }
                if (s == Season.FALL) {
                    fallFam.add(t.family());
                }
            }
        }
        expect(springFam.size() == 1 && springFam.contains(AllergyType.TREE), "early spring is tree pollen: " + springFam);
        expect(fallFam.size() == 1 && fallFam.contains(AllergyType.WEED), "fall is weed pollen: " + fallFam);
        expect(Climate.dominantPollen(P, Season.SPRING, 28, 5).family() == AllergyType.GRASS, "late spring turns to grass");
        expect(Climate.dominantPollen(P, Season.SUMMER, 5, 5).family() == AllergyType.GRASS, "early summer is grass");
        expect(Climate.dominantPollen(P, Season.SUMMER, 29, 5).family() == AllergyType.WEED, "late summer weeds");
        boolean winterOk = true;
        for (int d = 1; d <= 30; d++) {
            AllergyType f = Climate.dominantPollen(P, Season.WINTER, d, d).family();
            winterOk &= f == AllergyType.MOLD || f == AllergyType.TREE;
        }
        expect(winterOk, "winter is mould and cedar");
        expect(all.size() >= 8, "plenty of variety over a year: " + all.size());
        expect(Climate.dominantPollen(P, Season.SPRING, 5, 77) == Climate.dominantPollen(P, Season.SPRING, 5, 77), "same day, same answer");

        long idx = Climate.dayIndex(300, Season.SPRING, 15, 30);
        PollenType oak = PollenType.OAK;
        double dry = Climate.pollenScore(P, Season.SPRING, 15, idx, oak, 62, false, 0.2);
        double wet = Climate.pollenScore(P, Season.SPRING, 15, idx, oak, 62, true, 0.2);
        expect(wet < dry * 0.2, "rain washes pollen out: " + dry + " -> " + wet);
        expect(Climate.pollenScore(P, Season.SPRING, 15, idx, oak, 62, false, 0.5) > dry, "wind carries more pollen");
        expect(Climate.pollenScore(P, Season.SPRING, 15, idx, oak, 35, false, 0.2) < dry, "cold keeps pollen down");
        double mold = Climate.pollenScore(P, Season.SPRING, 15, idx, PollenType.MOLD, 35, false, 0.2);
        expect(mold > Climate.pollenScore(P, Season.SPRING, 15, idx, oak, 35, false, 0.2), "mould ignores the cold");
        double mid = 0;
        double edge = 0;
        for (long i = 0; i < 200; i++) {
            mid += Climate.pollenScore(P, Season.SPRING, 15, i, oak, 62, false, 0.2);
            edge += Climate.pollenScore(P, Season.SPRING, 1, i, oak, 62, false, 0.2);
        }
        expect(mid > edge * 1.2, "mid spring beats the first day of spring: " + mid + " vs " + edge);
        double winter = 0;
        double spring = 0;
        for (long i = 0; i < 200; i++) {
            winter += Climate.pollenScore(P, Season.WINTER, 15, i, PollenType.CEDAR, 40, false, 0.2);
            spring += Climate.pollenScore(P, Season.SPRING, 15, i, oak, 62, false, 0.2);
        }
        expect(winter < spring * 0.4, "winter is quiet");
        expect(Climate.pollenLevel(0) == 0 && Climate.pollenLevel(0.119) == 0, "none");
        expect(Climate.pollenLevel(0.12) == 1 && Climate.pollenLevel(0.399) == 1, "low");
        expect(Climate.pollenLevel(0.40) == 2 && Climate.pollenLevel(0.699) == 2, "moderate");
        expect(Climate.pollenLevel(0.70) == 3 && Climate.pollenLevel(0.999) == 3, "high");
        expect(Climate.pollenLevel(1.0) == 4 && Climate.pollenLevel(3) == 4, "very high");
        // over a spring all five levels appear (None only when wet)
        Set<Integer> levels = new HashSet<>();
        for (int d = 1; d <= 30; d++) {
            long di = Climate.dayIndex(300, Season.SPRING, d, 30);
            PollenType t = Climate.dominantPollen(P, Season.SPRING, d, di);
            levels.add(Climate.pollenLevel(Climate.pollenScore(P, Season.SPRING, d, di, t, 60, false, 0.2)));
            levels.add(Climate.pollenLevel(Climate.pollenScore(P, Season.SPRING, d, di, t, 60, true, 0.2)));
        }
        expect(levels.contains(0) && levels.contains(3), "a spring has quiet and bad days: " + levels);
        expect(AllergyType.parse("Mould") == AllergyType.MOLD && AllergyType.parse(" TREE ") == AllergyType.TREE
                && AllergyType.parse("grass") == AllergyType.GRASS && AllergyType.parse("weed") == AllergyType.WEED, "allergy parsing");
        expect(AllergyType.parse("cats") == null && AllergyType.parse(null) == null && AllergyType.parse("") == null, "bad allergy names");
        for (PollenType t : PollenType.values()) {
            expect(!t.display().isEmpty() && t.family() != null, "pollen " + t + " is named");
        }
    }

    static void words() {
        double[] probe = {-20, 20, 40, 50, 70, 80, 90, 100, 110};
        String prevName = "";
        int changes = 0;
        for (double t : probe) {
            String n = Climate.tempName(t);
            if (!n.equals(prevName)) {
                changes++;
            }
            prevName = n;
            expect(Climate.tempColor(t).startsWith("§"), "colour for " + t);
        }
        expect(changes == probe.length, "every probe lands in a different band: " + changes);
        expect(Climate.tempName(10).equals("Frigid") && Climate.tempName(11).equals("Freezing") && Climate.tempName(32).equals("Freezing")
                && Climate.tempName(33).equals("Cold"), "band edges");
        expect(Climate.tempColor(100).equals("§c") && Climate.tempColor(110).equals("§4") && Climate.tempColor(0).equals("§9"), "hot is red, cold is blue");
        expect(Climate.dewName(35).equals("Bone dry") && Climate.dewName(55).equals("Comfortable") && Climate.dewName(67).equals("Muggy")
                && Climate.dewName(80).equals("Miserable"), "dew point words");
        expect(Climate.uvName(0).equals("Low") && Climate.uvName(3).equals("Moderate") && Climate.uvName(6).equals("High")
                && Climate.uvName(8).equals("Very High") && Climate.uvName(11).equals("Extreme"), "UV words");
        expect(Climate.windName(0.1).equals("Calm") && Climate.windName(0.2).equals("Breezy") && Climate.windName(0.4).equals("Windy")
                && Climate.windName(0.6).equals("Strong") && Climate.windName(0.9).equals("Severe"), "wind words match the old sidebar");
        expect(Climate.skyText(0.0, true).equals("Sunny") && Climate.skyText(0.0, false).equals("Clear")
                && Climate.skyText(0.2, true).equals("Mostly Sunny") && Climate.skyText(0.4, false).equals("Partly Cloudy")
                && Climate.skyText(0.9, true).equals("Mostly Cloudy"), "sky words");
        expect(Climate.POLLEN_NAMES.length == 5 && Climate.POLLEN_COLORS.length == 5, "five pollen levels");
        expect(Climate.humidityColor(10).equals("§e") && Climate.humidityColor(90).equals("§9"), "humidity colours");
        expect(Climate.windColor(0.1).equals("§f") && Climate.windColor(0.8).equals("§c"), "wind colours");
        expect(Climate.dewColor(35).equals("§e") && Climate.dewColor(80).equals("§c"), "dew colours");
        expect(Climate.uvColor(1).equals("§a") && Climate.uvColor(12).equals("§d"), "uv colours");
    }
}
