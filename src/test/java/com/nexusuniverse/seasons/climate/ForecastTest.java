package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.Season;

import java.util.List;
import java.util.Map;

import static com.nexusuniverse.seasons.climate.Kit.expect;
import static com.nexusuniverse.seasons.climate.Kit.near;
import static com.nexusuniverse.seasons.climate.Kit.strip;

/** The 7:00 AM report: built from the real schedule, worded sensibly, and saved faithfully. */
public class ForecastTest {

    static final ClimateParams P = ClimateParams.defaults(99L, 30);
    static final GameClock.Rates DEFAULT = new GameClock.Rates(true, 720, 720);
    static final long HOUR = 72000L; // one real hour: a game hour at the default speed

    static DayPlan build(Season s, int day, List<WeatherSpan> spans) {
        return ForecastBuilder.build(P, DEFAULT, 300, s, day, 1000, spans, 3, 30);
    }

    public static void main(String[] args) {
        dry();
        wet();
        words();
        report();
        saving();
        Kit.finish();
    }

    static void dry() {
        DayPlan p = build(Season.SUMMER, 15, List.of());
        expect(p.precipKnown && p.precipShare == 0 && p.spells == 0, "no spans, no rain");
        expect(p.precipHeadline.equals("Dry"), "headline: " + p.precipHeadline);
        near(p.highTick, 9000, 1000, "the high comes mid-afternoon: " + p.highTick);
        near(p.lowTick, 23000, 2000, "the low comes before dawn: " + p.lowTick);
        expect(p.highF > p.lowF + 8, "a real daily range: " + p.highF + "/" + p.lowF);
        expect(p.rhMin < p.rhMax && p.rhMin >= 1 && p.rhMax <= 100, "humidity range");
        expect(p.uvPeak > 6, "summer sun: " + p.uvPeak);
        expect(p.startTick == 1000 && p.key.equals("300-SUMMER-15"), "keyed to the day");
        expect(Climate.pollenLevel(0) == 0 && p.pollenLevel >= 0 && p.pollenLevel <= 4, "pollen in range");
        expect(p.windMinMph == 3 && p.windMaxMph == 30, "wind range carried through");

        // the same forecast twice is identical (deterministic), a different day differs
        DayPlan again = build(Season.SUMMER, 15, List.of());
        expect(again.highF == p.highF && again.lowF == p.lowF && again.pollen == p.pollen, "deterministic");
        DayPlan other = build(Season.WINTER, 15, List.of());
        expect(other.highF < p.highF - 25, "winter is far colder than summer: " + other.highF + " vs " + p.highF);

        // schedule unknown (vanilla weather)
        DayPlan unknown = build(Season.SUMMER, 15, null);
        expect(!unknown.precipKnown && unknown.precipHeadline.equals("Unpredictable"), "unscheduled weather can't be called");
        expect(ForecastFormatter.report(unknown, false, true).stream().anyMatch(l -> strip(l).contains("can't be called")), "and the report says so");
    }

    static void wet() {
        DayPlan dry = build(Season.SPRING, 12, List.of());
        // rain from noon to 6 PM game time: from 7 AM that is 5 to 11 real hours ahead
        DayPlan pm = build(Season.SPRING, 12, List.of(new WeatherSpan(5 * HOUR, 11 * HOUR, false)));
        near(pm.precipShare, 0.25, 0.03, "six wet hours of twenty-four: " + pm.precipShare);
        expect(pm.spells == 1 && pm.thunderSpells == 0, "one spell");
        expect(pm.precipHeadline.equals("Rain this afternoon"), "headline: " + pm.precipHeadline);
        expect(pm.highF < dry.highF, "rain at the warmest time lowers the high: " + pm.highF + " vs " + dry.highF);
        expect(pm.uvPeak < dry.uvPeak, "and the sun: " + pm.uvPeak + " vs " + dry.uvPeak);
        near(pm.wetHours(), 6, 0.6, "about six wet hours");
        expect(pm.pollenLevel <= dry.pollenLevel, "rain does not raise pollen");

        DayPlan day = build(Season.SPRING, 12, List.of(new WeatherSpan(0, 30 * HOUR, false)));
        expect(day.precipShare > 0.97 && day.precipHeadline.equals("Rain all day"), "all day: " + day.precipHeadline);
        expect(day.skyText.equals("Rainy"), "sky: " + day.skyText);
        expect(day.pollenLevel == 0 || day.pollenLevel == 1, "washed out: " + day.pollenLevel);

        DayPlan storm = build(Season.SPRING, 12, List.of(new WeatherSpan(2 * HOUR, 5 * HOUR, true)));
        expect(storm.thunderSpells == 1 && storm.precipHeadline.startsWith("Thunderstorms"), "thunder: " + storm.precipHeadline);
        expect(storm.highF < dry.highF + 0.01 || storm.lowF <= dry.lowF, "storms don't warm the day");

        DayPlan two = build(Season.SPRING, 12, List.of(
                new WeatherSpan(0, 2 * HOUR, false), new WeatherSpan(14 * HOUR, 20 * HOUR, false)));
        expect(two.spells == 2, "two spells");
        expect(two.precipHeadline.contains("this morning") && two.precipHeadline.contains("overnight"), "morning and overnight: " + two.precipHeadline);

        // a spell that only starts after the 24 hours are up is not in today's forecast
        DayPlan later = build(Season.SPRING, 12, List.of(new WeatherSpan(40 * HOUR, 42 * HOUR, false)));
        expect(later.spells == 0 && later.precipShare == 0 && later.precipHeadline.equals("Dry"), "tomorrow's rain is not today's");

        // a sliver of rain is "brief", not a part of the day
        DayPlan brief = build(Season.SPRING, 12, List.of(new WeatherSpan(5 * HOUR, 5 * HOUR + 40000, false)));
        expect(brief.precipHeadline.startsWith("Brief rain") || brief.precipHeadline.startsWith("Rain"), "short spell: " + brief.precipHeadline);

        // winter rain is snow
        DayPlan snow = build(Season.WINTER, 15, List.of(new WeatherSpan(0, 12 * HOUR, false)));
        expect(snow.snowy && snow.precipHeadline.startsWith("Snow"), "winter wet is snow: " + snow.precipHeadline + " snowy=" + snow.snowy);
        expect(snow.skyText.equals("Snowy") || snow.skyText.equals("Rainy"), "sky: " + snow.skyText);
        DayPlan summerRain = build(Season.SUMMER, 15, List.of(new WeatherSpan(0, 12 * HOUR, false)));
        expect(!summerRain.snowy && summerRain.precipHeadline.startsWith("Rain"), "summer wet is rain");

        // a different game speed: with fast days the same server ticks cover more of the game day
        GameClock.Rates fast = new GameClock.Rates(true, 20, 20);
        DayPlan quick = ForecastBuilder.build(P, fast, 300, Season.SPRING, 12, 1000, List.of(new WeatherSpan(0, 20 * 1200L, false)), 3, 30);
        near(quick.precipShare, 0.5, 0.05, "20 real minutes of rain on a 40 minute game day is half of it: " + quick.precipShare);
    }

    static void words() {
        boolean[] wet = new boolean[48];
        boolean[] storm = new boolean[48];
        int[] part = new int[48];
        for (int i = 0; i < 48; i++) {
            part[i] = ForecastBuilder.partOfDay(1000 + i * 500.0, i);
        }
        expect(part[0] == 0 && part[9] == 0, "7 AM to noon is the morning");
        expect(part[10] == 1 && part[21] == 1, "noon to 6 PM is the afternoon");
        expect(part[22] == 2 && part[33] == 2, "6 PM to midnight is the evening");
        expect(part[34] == 3 && part[47] == 3, "midnight to the next dawn is overnight");
        expect(ForecastBuilder.headline(wet, storm, part, false, 0).equals("Dry"), "nothing wet");
        for (int i = 10; i < 22; i++) {
            wet[i] = true;
        }
        expect(ForecastBuilder.headline(wet, storm, part, false, 0.25).equals("Rain this afternoon"), "afternoon only");
        for (int i = 22; i < 34; i++) {
            wet[i] = true;
        }
        expect(ForecastBuilder.headline(wet, storm, part, false, 0.5).equals("Rain this afternoon and this evening"), "two parts");
        for (int i = 34; i < 48; i++) {
            wet[i] = true;
        }
        expect(ForecastBuilder.headline(wet, storm, part, false, 0.7).equals("Rain this afternoon, this evening and overnight"), "three parts");
        for (int i = 0; i < 10; i++) {
            wet[i] = true;
        }
        expect(ForecastBuilder.headline(wet, storm, part, true, 1.0).equals("Snow all day"), "all four: snow all day");
        storm[12] = true;
        expect(ForecastBuilder.headline(wet, storm, part, false, 1.0).equals("Thunderstorms all day"), "thunder wins the word");
        expect(ForecastBuilder.dayKeyOf(356, Season.FALL, 3).equals("356-FALL-3"), "day key");
    }

    static void report() {
        DayPlan p = build(Season.SUMMER, 15, List.of(new WeatherSpan(5 * HOUR, 8 * HOUR, true)));
        List<String> lines = ForecastFormatter.report(p, false, true);
        String all = strip(String.join("\n", lines));
        expect(all.contains("NEXUS WEATHER SERVICE"), "has the masthead");
        expect(all.contains("Summer 15, Year 300") && all.contains("7:00 AM"), "dated and timed: " + lines.get(2));
        expect(all.contains("High " + Math.round(p.highF) + "°F") && all.contains("Low " + Math.round(p.lowF) + "°F"), "high and low shown");
        expect(all.contains("Sky:") && all.contains("Precipitation:") && all.contains("Humidity:") && all.contains("Dew point:")
                && all.contains("Wind:") && all.contains("UV index:") && all.contains("Pollen:"), "every category present");
        expect(all.contains("Thunderstorms") && all.contains("thunder"), "thunder is called out: " + all);
        expect(all.contains("Thunderstorm watch"), "and gets a watch");
        expect(all.contains("variable, 3 mph to 30 mph"), "wind range");
        for (String l : lines) {
            expect(strip(l).length() <= 90, "no line is too long for chat: " + strip(l).length() + " " + strip(l));
        }
        String c = strip(String.join("\n", ForecastFormatter.report(p, true, true)));
        expect(c.contains("°C") && !c.contains("°F") && c.contains("km/h"), "celsius report: " + c);

        DayPlan hot = new DayPlan("k", 300, Season.SUMMER, 15, 1000, 98, 9000, 75, 23000, 40, 80, 62, 0, 0, 0, false, true, "Dry",
                "Sunny", 10, PollenType.TIMOTHY, 1, 106, 3, 30);
        expect(strip(ForecastFormatter.advisory(hot, false, true)).contains("Heat advisory") && strip(ForecastFormatter.advisory(hot, false, true)).contains("Cooling Cap"), "heat advisory mentions the cap");
        expect(!strip(ForecastFormatter.advisory(hot, false, false)).contains("Cooling Cap"), "or not, if heat is off");
        expect(strip(String.join("\n", ForecastFormatter.report(hot, false, true))).contains("feels like 106°F"), "feels-like shown when it differs");
        DayPlan cold = new DayPlan("k", 300, Season.WINTER, 15, 1000, 20, 9000, 5, 23000, 40, 80, 3, 0, 0, 0, false, true, "Dry",
                "Sunny", 1, PollenType.MOLD, 0, 20, 3, 30);
        expect(strip(ForecastFormatter.advisory(cold, false, true)).contains("Hard freeze"), "freeze warning");
        expect(strip(String.join("\n", ForecastFormatter.report(cold, false, true))).contains("Pollen: None"), "no pollen");
        DayPlan snowy = new DayPlan("k", 300, Season.WINTER, 15, 1000, 30, 9000, 20, 23000, 40, 80, 10, 0.4, 3, 0, true, true, "Snow this afternoon",
                "Sunny", 1, PollenType.MOLD, 0, 30, 3, 30);
        expect(strip(ForecastFormatter.advisory(snowy, false, true)).contains("Snow in the forecast"), "snow note");
        DayPlan allergy = new DayPlan("k", 300, Season.SPRING, 15, 1000, 60, 9000, 40, 23000, 40, 80, 40, 0, 0, 0, false, true, "Dry",
                "Sunny", 4, PollenType.OAK, 4, 60, 3, 30);
        expect(strip(ForecastFormatter.advisory(allergy, false, true)).contains("Allergy alert") && strip(ForecastFormatter.advisory(allergy, false, true)).contains("Oak"), "allergy alert");
        DayPlan sun = new DayPlan("k", 300, Season.SUMMER, 15, 1000, 85, 9000, 60, 23000, 40, 80, 50, 0, 0, 0, false, true, "Dry",
                "Sunny", 9, PollenType.MUGWORT, 0, 85, 3, 30);
        expect(strip(ForecastFormatter.advisory(sun, false, true)).contains("Strong sun"), "uv note");
        DayPlan calm = new DayPlan("k2", 300, Season.FALL, 15, 1000, 60, 9000, 45, 23000, 40, 80, 40, 0, 0, 0, false, true, "Dry",
                "Sunny", 3, PollenType.RAGWEED, 1, 60, 3, 30);
        String flavor = ForecastFormatter.advisory(calm, false, true);
        expect(flavor.startsWith("§7§o") && flavor.equals(ForecastFormatter.advisory(calm, false, true)), "a quiet day gets a steady bit of flavour");
        expect(ForecastFormatter.precipLine(calm).contains("None expected"), "no precipitation line");
        DayPlan oneHour = new DayPlan("k", 300, Season.FALL, 15, 1000, 60, 9000, 45, 23000, 40, 80, 40, 1.0 / 24, 1, 0, false, true, "Brief rain possible",
                "Sunny", 3, PollenType.RAGWEED, 1, 60, 3, 30);
        expect(strip(ForecastFormatter.precipLine(oneHour)).contains("1 wet hour,") && strip(ForecastFormatter.precipLine(oneHour)).contains("1 spell)"), "singular words: " + strip(ForecastFormatter.precipLine(oneHour)));
    }

    static void saving() {
        DayPlan p = build(Season.FALL, 7, List.of(new WeatherSpan(3 * HOUR, 9 * HOUR, true)));
        Map<String, Object> m = p.toMap();
        DayPlan back = DayPlan.fromMap(m);
        expect(back != null && back.key.equals(p.key) && back.season == p.season && back.day == p.day && back.year == p.year, "identity survives");
        expect(back != null && back.highF == p.highF && back.lowF == p.lowF && back.highTick == p.highTick && back.lowTick == p.lowTick, "temperatures survive");
        expect(back != null && back.pollen == p.pollen && back.pollenLevel == p.pollenLevel && back.uvPeak == p.uvPeak, "pollen and sun survive");
        expect(back != null && back.precipHeadline.equals(p.precipHeadline) && back.spells == p.spells && back.thunderSpells == p.thunderSpells
                && back.snowy == p.snowy && back.precipKnown == p.precipKnown && back.precipShare == p.precipShare, "weather survives");
        expect(back != null && back.rhMin == p.rhMin && back.rhMax == p.rhMax && back.dewAvgF == p.dewAvgF && back.feelsHighF == p.feelsHighF
                && back.windMinMph == p.windMinMph && back.windMaxMph == p.windMaxMph && back.skyText.equals(p.skyText), "the rest survives");
        expect(DayPlan.fromMap(null) == null, "nothing saved");
        expect(DayPlan.fromMap(Map.of("year", 1)) == null, "no key, no plan");
        expect(DayPlan.fromMap(Map.of("key", "k", "season", "MONSOON")) == null, "bad season");
        expect(DayPlan.fromMap(Map.of("key", "k", "pollen", "PIXIE_DUST")) == null, "bad pollen");
        DayPlan lenient = DayPlan.fromMap(Map.of("key", "k", "pollen-level", 99, "high-f", "warm"));
        expect(lenient != null && lenient.pollenLevel == 4 && lenient.highF == 60, "junk numbers fall back: " + (lenient == null ? "null" : lenient.pollenLevel + " " + lenient.highF));
    }
}
