package com.nexusuniverse.seasons.climate;

import java.util.ArrayList;
import java.util.List;

/** Turns a {@link DayPlan} into the lines of the 7:00 AM report. Pure text, no Bukkit. */
public final class ForecastFormatter {

    private static final String RULE = "§6§m                                              ";

    private static final String[] FLAVOR = {
            "Dress for the walk home, not the walk out.",
            "If it looks like weather, it probably is weather.",
            "Your local meteorologist is a mild-mannered villager and he is doing his best.",
            "Forecasts are guarantees only in the sense that something will happen.",
            "Check the sky twice, trust the barometer once.",
            "Today's outlook was brought to you by two buckets of water and a hunch.",
            "Pack a lunch. Pack a coat. Argue about which one later.",
            "No sunburn has ever been cured by bravado."
    };

    private ForecastFormatter() {
    }

    /**
     * @param celsius        show °C and km/h
     * @param coolingCapHint mention the Cooling Cap in heat advisories (when summer heat is switched on)
     */
    public static List<String> report(DayPlan p, boolean celsius, boolean coolingCapHint) {
        List<String> out = new ArrayList<>();
        out.add(RULE);
        out.add("§e§l☀ NEXUS WEATHER SERVICE §7· §fDaily Forecast");
        out.add("§7" + p.season.displayName() + " " + p.day + ", Year " + p.year + " · issued §f"
                + GameClock.clock(p.startTick) + "§7 · next 24 hours");
        out.add(" ");

        String hi = Climate.tempColor(p.highF) + Climate.degrees(p.highF, celsius);
        String lo = Climate.tempColor(p.lowF) + Climate.degrees(p.lowF, celsius);
        String feels = p.feelsHighF - p.highF >= 4.0
                ? " §7· feels like " + Climate.tempColor(p.feelsHighF) + Climate.degrees(p.feelsHighF, celsius) : "";
        out.add("§6High " + hi + " §7at " + GameClock.clockShort(p.highTick) + "  §8|  §9Low " + lo + " §7at "
                + GameClock.clockShort(p.lowTick) + feels);

        out.add("§eSky: §f" + p.skyText);
        out.add("§bPrecipitation: " + precipLine(p));
        out.add("§3Humidity: §f" + Math.round(p.rhMin) + "-" + Math.round(p.rhMax) + "% §8|  §bDew point: "
                + Climate.dewColor(p.dewAvgF) + Climate.degrees(p.dewAvgF, celsius) + " §7" + Climate.dewName(p.dewAvgF));
        out.add("§fWind: §7variable, " + Climate.speed(p.windMinMph, celsius) + " to " + Climate.speed(p.windMaxMph, celsius));
        String uvName = Climate.uvName(p.uvPeak);
        out.add("§dUV index: " + Climate.uvColor(p.uvPeak) + Math.round(p.uvPeak) + " §7" + uvName + " §8(midday peak)");
        out.add("§aPollen: " + Climate.POLLEN_COLORS[p.pollenLevel] + (p.pollenLevel == 0 ? "None"
                : p.pollen.display() + " - " + Climate.POLLEN_NAMES[p.pollenLevel]));
        String note = advisory(p, celsius, coolingCapHint);
        out.add(" ");
        out.add(note);
        out.add(RULE);
        return out;
    }

    static String precipLine(DayPlan p) {
        if (!p.precipKnown) {
            return "§7can't be called (vanilla weather is running)";
        }
        if (p.precipShare <= 0.0) {
            return "§fNone expected";
        }
        long hours = Math.max(1, Math.round(p.wetHours()));
        StringBuilder sb = new StringBuilder("§f").append(p.precipHeadline);
        sb.append(" §7(about ").append(hours).append(hours == 1 ? " wet hour" : " wet hours");
        sb.append(", ").append(p.spells).append(p.spells == 1 ? " spell" : " spells");
        if (p.thunderSpells > 0) {
            sb.append(", §e⚡").append(p.thunderSpells).append(" with thunder§7");
        }
        sb.append(")");
        return sb.toString();
    }

    /** One line of advice: the most pressing warning, or a bit of flavour. */
    static String advisory(DayPlan p, boolean celsius, boolean coolingCapHint) {
        if (p.thunderSpells > 0) {
            return "§e⚡ Thunderstorm watch: " + p.thunderSpells + (p.thunderSpells == 1 ? " storm" : " storms")
                    + " in the area. Stay off the hilltops.";
        }
        if (p.highF >= 95.0 || p.feelsHighF >= 100.0) {
            return "§c☀ Heat advisory: it will feel like " + Climate.degrees(p.feelsHighF, celsius)
                    + " by afternoon. " + (coolingCapHint ? "Wear a Cooling Cap and find shade." : "Find shade and keep moving slowly.");
        }
        if (p.lowF <= 10.0) {
            return "§b❄ Hard freeze tonight (" + Climate.degrees(p.lowF, celsius) + "). Wrap up and keep the furnaces lit.";
        }
        if (p.precipKnown && p.snowy && p.precipShare >= 0.2) {
            return "§f❄ Snow in the forecast. Cold feet are optional.";
        }
        if (p.pollenLevel >= 3) {
            return "§a⚠ Allergy alert: " + p.pollen.display() + " is " + Climate.POLLEN_NAMES[p.pollenLevel].toLowerCase()
                    + " today. Sneezers, you have been warned.";
        }
        if (p.uvPeak >= 8.0) {
            return "§d☀ Strong sun today (UV " + Math.round(p.uvPeak) + "). Seek shade around midday.";
        }
        int pick = (int) (Math.abs((long) p.key.hashCode()) % FLAVOR.length);
        return "§7§o" + FLAVOR[pick];
    }
}
