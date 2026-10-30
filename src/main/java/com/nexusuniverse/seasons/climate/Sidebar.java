package com.nexusuniverse.seasons.climate;

import java.util.ArrayList;
import java.util.List;

/**
 * The colourful sidebar lines. Each category has its own label colour, and each value is coloured by
 * how it reads (cold blue to scorching red, green to red pollen, and so on).
 */
public final class Sidebar {

    /** Which lines to show; see {@code weather-hud.show} in config.yml. */
    public record Show(boolean temperature, boolean humidity, boolean dewPoint, boolean wind, boolean pollen, boolean uv) {
        public static Show all() {
            return new Show(true, true, true, true, true, true);
        }
    }

    private Sidebar() {
    }

    /**
     * @param headline    the "Sky" line (events like tornadoes take priority over the plain sky)
     * @param windStrength 0..1 as the wind manager reports it
     * @param arrow       compass arrow, or "" when sheltered
     * @param allergic    the family this player is allergic to, or null
     */
    public static List<String> overworld(Reading r, String headline, double windStrength, String arrow,
                                         AllergyType allergic, Show show, boolean celsius) {
        List<String> lines = new ArrayList<>();
        lines.add("§eSky: §f" + headline);
        if (show.temperature()) {
            String t = Climate.tempColor(r.tempF()) + Climate.degrees(r.tempF(), celsius);
            if (Math.abs(r.feelsF() - r.tempF()) >= 3.0) {
                t += " §7(feels " + Climate.tempColor(r.feelsF()) + Climate.degrees(r.feelsF(), celsius) + "§7)";
            } else {
                t += " §7" + Climate.tempName(r.tempF());
            }
            lines.add("§6Temp: " + t);
        }
        if (show.humidity()) {
            lines.add("§3Humidity: " + Climate.humidityColor(r.rh()) + Math.round(r.rh()) + "%");
        }
        if (show.dewPoint()) {
            lines.add("§bDew Pt: " + Climate.dewColor(r.dewF()) + Climate.degrees(r.dewF(), celsius) + " §7"
                    + Climate.dewName(r.dewF()));
        }
        if (show.wind()) {
            String arrowPart = arrow == null || arrow.isEmpty() ? "" : " " + arrow;
            lines.add("§fWind: " + Climate.windColor(windStrength) + Climate.windName(windStrength) + " §7"
                    + Climate.speed(r.windMph(), celsius) + arrowPart);
        }
        if (show.pollen()) {
            String v;
            if (r.pollenLevel() == 0) {
                v = "§7None";
            } else {
                v = Climate.POLLEN_COLORS[r.pollenLevel()] + r.pollen().display() + " §7- "
                        + Climate.POLLEN_COLORS[r.pollenLevel()] + Climate.POLLEN_NAMES[r.pollenLevel()];
                if (allergic != null && allergic == r.pollen().family() && r.pollenLevel() >= 2) {
                    v += " §c⚠";
                }
            }
            lines.add("§aPollen: " + v);
        }
        if (show.uv()) {
            if (r.uv() < 0.5) {
                lines.add("§dUV: §70");
            } else {
                lines.add("§dUV: " + Climate.uvColor(r.uv()) + Math.round(r.uv()) + " §7" + Climate.uvName(r.uv()));
            }
        }
        lines.add(r.sheltered() ? "§8(sheltered)" : "");
        return lines;
    }

    public static List<String> nether(Show show, boolean celsius) {
        List<String> lines = new ArrayList<>();
        lines.add("§eSky: §cSmoldering");
        if (show.temperature()) {
            lines.add("§6Temp: §4" + Climate.degrees(117, celsius) + " §7Scorching");
        }
        if (show.humidity()) {
            lines.add("§3Humidity: §e4%");
        }
        if (show.dewPoint()) {
            lines.add("§bDew Pt: §7none");
        }
        if (show.wind()) {
            lines.add("§fWind: §7Dead calm");
        }
        if (show.pollen()) {
            lines.add("§aPollen: §7None");
        }
        if (show.uv()) {
            lines.add("§dUV: §7none");
        }
        lines.add("");
        return lines;
    }

    public static List<String> end(Show show, boolean celsius) {
        List<String> lines = new ArrayList<>();
        lines.add("§eSky: §5The Void");
        if (show.temperature()) {
            lines.add("§6Temp: §9" + Climate.degrees(-40, celsius) + " §7Frigid");
        }
        if (show.humidity()) {
            lines.add("§3Humidity: §e0%");
        }
        if (show.dewPoint()) {
            lines.add("§bDew Pt: §7none");
        }
        if (show.wind()) {
            lines.add("§fWind: §7Still");
        }
        if (show.pollen()) {
            lines.add("§aPollen: §7None");
        }
        if (show.uv()) {
            lines.add("§dUV: §7none");
        }
        lines.add("");
        return lines;
    }

    /**
     * The Euro corner: a friendly jab, and right beneath it the temperature in Celsius, whatever
     * climate.units says for the rest of the board. Always exactly two lines.
     */
    public static List<String> euroCorner(String jab, double tempF) {
        List<String> lines = new ArrayList<>();
        lines.add("§7§o" + jab);
        lines.add("§6Euro Temp: " + Climate.tempColor(tempF) + Climate.degrees(tempF, true));
        return lines;
    }
}
