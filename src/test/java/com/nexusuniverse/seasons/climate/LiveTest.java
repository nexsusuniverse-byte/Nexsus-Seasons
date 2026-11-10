package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.Season;

import java.util.List;

import static com.nexusuniverse.seasons.climate.Kit.expect;
import static com.nexusuniverse.seasons.climate.Kit.near;
import static com.nexusuniverse.seasons.climate.Kit.strip;

/** What a player sees: the live reading, and the sidebar lines built from it. */
public class LiveTest {

    static final ClimateParams P = ClimateParams.defaults(2024L, 30);

    static LiveInput in(Season s, int day, double tick, double biomeT, double biomeH, boolean exposed, double wind,
                        boolean precip, boolean thunder, boolean fog, boolean blizzard, double offset, double cloud) {
        return new LiveInput(300, s, day, tick, biomeT, biomeH, exposed, wind, precip, thunder, fog, blizzard, offset, cloud);
    }

    static Reading clear(Season s, int day, double tick) {
        return LiveReader.read(P, in(s, day, tick, 0.8, 0.4, true, 0.2, false, false, false, false, 0, 0.1));
    }

    public static void main(String[] args) {
        reading();
        sidebar();
        Kit.finish();
    }

    static void reading() {
        Reading summerNoon = clear(Season.SUMMER, 15, 8000);
        Reading winterNoon = clear(Season.WINTER, 15, 8000);
        expect(summerNoon.tempF() > winterNoon.tempF() + 30, "summer day is far warmer than winter: " + summerNoon.tempF() + " " + winterNoon.tempF());
        expect(clear(Season.SUMMER, 15, 9000).tempF() > clear(Season.SUMMER, 15, 23000).tempF() + 8, "afternoon warmer than dawn");
        expect(summerNoon.rh() >= 1 && summerNoon.rh() <= 100 && summerNoon.dewF() < summerNoon.tempF(), "humidity sane");
        expect(!summerNoon.sheltered() && summerNoon.windMph() > 0 && summerNoon.uv() > 5, "outside: windy, bright");

        // biomes
        Reading desert = LiveReader.read(P, in(Season.SUMMER, 15, 8000, 2.0, 0.0, true, 0.2, false, false, false, false, 0, 0.1));
        Reading snowy = LiveReader.read(P, in(Season.SUMMER, 15, 8000, 0.0, 0.5, true, 0.2, false, false, false, false, 0, 0.1));
        Reading jungle = LiveReader.read(P, in(Season.SUMMER, 15, 8000, 0.95, 0.9, true, 0.2, false, false, false, false, 0, 0.1));
        expect(desert.tempF() > summerNoon.tempF() + 15, "a desert runs hot: " + desert.tempF());
        expect(snowy.tempF() < summerNoon.tempF() - 10, "a snowy biome runs cold: " + snowy.tempF());
        expect(desert.rh() < summerNoon.rh() && summerNoon.rh() < jungle.rh() + 20, "desert drier than plains: " + desert.rh() + " " + summerNoon.rh());
        expect(jungle.dewF() > summerNoon.dewF(), "jungle has a higher dew point");
        expect(jungle.feelsF() > jungle.tempF() + 3, "humid heat feels worse: " + jungle.feelsF() + " vs " + jungle.tempF());

        // weather
        Reading rain = LiveReader.read(P, in(Season.SPRING, 15, 8000, 0.8, 0.4, true, 0.2, true, false, false, false, -5, 1.0));
        Reading dry = LiveReader.read(P, in(Season.SPRING, 15, 8000, 0.8, 0.4, true, 0.2, false, false, false, false, 0, 0.1));
        expect(rain.sky().equals("Raining") && dry.sky().equals("Sunny"), "sky words: " + rain.sky() + " / " + dry.sky());
        expect(rain.tempF() < dry.tempF() - 4, "rain is cooler: " + rain.tempF() + " vs " + dry.tempF());
        expect(rain.rh() > dry.rh() + 15 && rain.rh() > 85, "rain is humid: " + rain.rh());
        expect(rain.uv() < dry.uv() * 0.3, "rain dims the sun");
        expect(rain.pollenLevel() <= 1, "rain clears the air");
        Reading storm = LiveReader.read(P, in(Season.SPRING, 15, 8000, 0.8, 0.4, true, 0.2, true, true, false, false, -8, 1.0));
        expect(storm.sky().equals("Thunderstorm"), "thunder: " + storm.sky());
        Reading snow = LiveReader.read(P, in(Season.WINTER, 15, 8000, 0.8, 0.4, true, 0.2, true, false, false, false, -5, 1.0));
        expect(snow.sky().equals("Snowing") && snow.tempF() <= Climate.SNOW_LINE_F, "cold rain is snow: " + snow.sky() + " " + snow.tempF());
        Reading thundersnow = LiveReader.read(P, in(Season.WINTER, 15, 8000, 0.8, 0.4, true, 0.2, true, true, false, false, -8, 1.0));
        expect(thundersnow.sky().equals("Thundersnow"), "thundersnow: " + thundersnow.sky());
        Reading fog = LiveReader.read(P, in(Season.FALL, 15, 8000, 0.8, 0.4, true, 0.2, false, false, true, false, -2, 0.3));
        expect(fog.sky().equals("Foggy") && fog.rh() > clear(Season.FALL, 15, 8000).rh(), "fog is damp: " + fog.rh());
        Reading night = clear(Season.SUMMER, 15, 15000);
        expect(night.sky().equals("Clear") && night.uv() == 0, "clear and no sun at night: " + night.sky());

        // the eased offset is what moves the temperature, not the instant flag
        Reading easedHalf = LiveReader.read(P, in(Season.SPRING, 15, 8000, 0.8, 0.4, true, 0.2, false, false, false, false, -2.5, 0.1));
        near(easedHalf.tempF(), dry.tempF() - 2.5, 1e-9, "the eased offset is applied as given");

        // indoors
        Reading in = LiveReader.read(P, in(Season.WINTER, 15, 8000, 0.8, 0.4, false, 0.4, false, false, false, false, 0, 0.1));
        Reading out = LiveReader.read(P, in(Season.WINTER, 15, 8000, 0.8, 0.4, true, 0.4, false, false, false, false, 0, 0.1));
        expect(in.sheltered() && !out.sheltered(), "sheltered flag");
        expect(in.tempF() > out.tempF() + 10 && in.tempF() < LiveReader.ROOM_F + 1, "indoors is milder in winter: " + in.tempF() + " vs " + out.tempF());
        near(in.tempF(), out.tempF() + (LiveReader.ROOM_F - out.tempF()) * 0.5, 1e-9, "halfway to room temperature");
        expect(in.windMph() == 0 && in.uv() == 0, "no wind or sun indoors");
        expect(in.dewF() <= in.tempF() - 0.39, "dew point stays under the air temperature indoors");
        expect(in.rh() < out.rh(), "winter indoor air is drier: " + in.rh() + " vs " + out.rh());
        Reading hotIn = LiveReader.read(P, in(Season.SUMMER, 22, 9000, 0.8, 0.4, false, 0.4, false, false, false, false, 0, 0.1));
        Reading hotOut = LiveReader.read(P, in(Season.SUMMER, 22, 9000, 0.8, 0.4, true, 0.4, false, false, false, false, 0, 0.1));
        expect(hotIn.tempF() < hotOut.tempF() - 5, "indoors is cooler in a heatwave");
        long idx = Climate.dayIndex(300, Season.SPRING, 15, 30);
        PollenType t = Climate.dominantPollen(P, Season.SPRING, 15, idx);
        Reading pIn = LiveReader.read(P, in(Season.SPRING, 15, 8000, 0.8, 0.4, false, 0.2, false, false, false, false, 0, 0.1));
        Reading pOut = LiveReader.read(P, in(Season.SPRING, 15, 8000, 0.8, 0.4, true, 0.2, false, false, false, false, 0, 0.1));
        expect(pOut.pollen() == t && pIn.pollen() == t, "same pollen type in and out");
        expect(pIn.pollenLevel() == Math.max(0, pOut.pollenLevel() - 1), "indoors is one level lower: " + pIn.pollenLevel() + " vs " + pOut.pollenLevel());
        // unknown biome numbers are tolerated
        Reading nan = LiveReader.read(P, in(Season.SPRING, 15, 8000, Double.NaN, Double.NaN, true, 0.2, false, false, false, false, 0, 0.1));
        expect(!Double.isNaN(nan.tempF()) && !Double.isNaN(nan.rh()) && !Double.isNaN(nan.dewF()), "NaN biome is neutral");
        near(nan.tempF(), dry.tempF(), 1e-9, "and equals plains");
        // very cold wind chill appears in the reading
        Reading bitter = LiveReader.read(P, in(Season.WINTER, 8, 23000, 0.0, 0.5, true, 0.6, false, false, false, false, 0, 0.1));
        expect(bitter.feelsF() < bitter.tempF() - 5, "wind chill: " + bitter.feelsF() + " vs " + bitter.tempF());
    }

    static final Sidebar.Show ALL = Sidebar.Show.all();

    static void sidebar() {
        Reading r = new Reading(72, 72, 55, 54, 12, 7, PollenType.OAK, 3, "Sunny", false);
        List<String> lines = Sidebar.overworld(r, "Sunny", 0.2, "↗", null, ALL, false);
        String all = strip(String.join("\n", lines));
        expect(lines.size() == 8, "sky + six categories + shelter line: " + lines.size());
        expect(strip(lines.get(0)).equals("Sky: Sunny"), "sky first: " + strip(lines.get(0)));
        expect(strip(lines.get(1)).equals("Temp: 72°F Pleasant"), "temp: " + strip(lines.get(1)));
        expect(strip(lines.get(2)).equals("Humidity: 55%"), "humidity: " + strip(lines.get(2)));
        expect(strip(lines.get(3)).equals("Dew Pt: 54°F Comfortable"), "dew: " + strip(lines.get(3)));
        expect(strip(lines.get(4)).equals("Wind: Breezy 12 mph ↗"), "wind: " + strip(lines.get(4)));
        expect(strip(lines.get(5)).equals("Pollen: Oak - High"), "pollen: " + strip(lines.get(5)));
        expect(strip(lines.get(6)).equals("UV: 7 High"), "uv: " + strip(lines.get(6)));
        expect(lines.get(7).isEmpty(), "blank when not sheltered");

        // every category has its own label colour
        String[] labelColours = {"§e", "§6", "§3", "§b", "§f", "§a", "§d"};
        for (int i = 0; i < 7; i++) {
            expect(lines.get(i).startsWith(labelColours[i]), "line " + i + " label colour " + labelColours[i] + ": " + lines.get(i));
        }
        expect(lines.get(1).contains("§2") || lines.get(1).contains("§e"), "the temperature value is coloured by how it reads");
        expect(lines.get(5).contains("§6"), "high pollen is orange");
        expect(lines.get(6).contains("§6"), "UV 7 is orange");

        // colour follows the value
        Reading hot = new Reading(101, 112, 70, 72, 4, 11, PollenType.TIMOTHY, 4, "Sunny", false);
        List<String> hl = Sidebar.overworld(hot, "Sunny", 0.1, "", null, ALL, false);
        expect(hl.get(1).contains("§c101°F") && strip(hl.get(1)).contains("(feels 112°F)"), "hot: " + hl.get(1));
        expect(hl.get(1).contains("§4112°F") , "the feels-like has its own colour: " + hl.get(1));
        expect(hl.get(5).contains("§cTimothy Grass") && strip(hl.get(5)).contains("Very High"), "very high pollen is red: " + hl.get(5));
        expect(hl.get(6).contains("§d11"), "extreme UV is purple: " + hl.get(6));
        Reading freezing = new Reading(18, 4, 80, 14, 25, 0, PollenType.MOLD, 0, "Snowing", false);
        List<String> fl = Sidebar.overworld(freezing, "Snowing", 0.5, "←", null, ALL, false);
        expect(fl.get(1).contains("§b18°F") && strip(fl.get(1)).contains("(feels 4°F)"), "freezing: " + fl.get(1));
        expect(strip(fl.get(5)).equals("Pollen: None") && strip(fl.get(6)).equals("UV: 0"), "winter night: " + strip(fl.get(5)) + " / " + strip(fl.get(6)));

        // small differences don't clutter the line with a feels-like
        Reading near = new Reading(72, 74, 55, 54, 12, 7, PollenType.OAK, 1, "Sunny", false);
        expect(!strip(Sidebar.overworld(near, "x", 0.2, "", null, ALL, false).get(1)).contains("feels"), "no feels-like for a 2 degree difference");

        // celsius
        List<String> c = Sidebar.overworld(r, "Sunny", 0.2, "↗", null, ALL, true);
        expect(strip(c.get(1)).startsWith("Temp: 22°C") && strip(c.get(3)).contains("12°C") && strip(c.get(4)).contains("19 km/h"), "celsius: " + strip(String.join(" | ", c)));

        // the Euro corner: exactly two lines, jab first, Celsius right under it
        List<String> ec = Sidebar.euroCorner("AC? Just open a window.", 72);
        expect(ec.size() == 2 && strip(ec.get(0)).equals("AC? Just open a window.") && strip(ec.get(1)).equals("Euro Temp: 22°C"), "euro corner: " + ec);
        expect(strip(Sidebar.euroCorner("x", -40).get(1)).equals("Euro Temp: -40°C"), "minus forty is minus forty");
        expect(strip(Sidebar.euroCorner("x", 117).get(1)).equals("Euro Temp: 47°C"), "nether heat");

        // your own allergy
        List<String> a = Sidebar.overworld(r, "Sunny", 0.2, "", AllergyType.TREE, ALL, false);
        expect(strip(a.get(5)).endsWith("⚠"), "a warning mark for a matching allergy: " + strip(a.get(5)));
        expect(!strip(Sidebar.overworld(r, "Sunny", 0.2, "", AllergyType.GRASS, ALL, false).get(5)).contains("⚠"), "none for a different one");
        Reading low = new Reading(72, 72, 55, 54, 12, 7, PollenType.OAK, 1, "Sunny", false);
        expect(!strip(Sidebar.overworld(low, "Sunny", 0.2, "", AllergyType.TREE, ALL, false).get(5)).contains("⚠"), "none when it is only low");
        expect(strip(Sidebar.overworld(new Reading(72, 72, 55, 54, 12, 7, PollenType.OAK, 2, "S", false), "S", 0.2, "", AllergyType.TREE, ALL, false).get(5)).contains("⚠"), "but moderate is enough");

        // sheltered marker and the show switches
        Reading inside = new Reading(68, 68, 40, 45, 0, 0, PollenType.OAK, 0, "Sunny", true);
        List<String> sl = Sidebar.overworld(inside, "Sunny", 0.2, "", null, ALL, false);
        expect(strip(sl.get(7)).equals("(sheltered)"), "sheltered note: " + sl.get(7));
        expect(strip(sl.get(4)).equals("Wind: Breezy 0 mph"), "no arrow indoors: " + strip(sl.get(4)));
        Sidebar.Show some = new Sidebar.Show(true, false, false, true, false, false);
        List<String> few = Sidebar.overworld(r, "Sunny", 0.2, "", null, some, false);
        expect(few.size() == 4 && strip(few.get(0)).startsWith("Sky") && strip(few.get(1)).startsWith("Temp") && strip(few.get(2)).startsWith("Wind"), "only the chosen lines: " + few);
        expect(Sidebar.overworld(r, "Sunny", 0.2, "", null, new Sidebar.Show(false, false, false, false, false, false), false).size() == 2, "sky and the shelter line are always there");

        // the nether and the end have the same number of lines, so switching worlds never leaves strays
        for (Sidebar.Show s : new Sidebar.Show[]{ALL, some, new Sidebar.Show(false, false, false, false, false, false)}) {
            int ow = Sidebar.overworld(r, "x", 0.2, "", null, s, false).size();
            expect(Sidebar.nether(s, false).size() == ow && Sidebar.end(s, false).size() == ow, "same height in every world");
        }
        expect(strip(Sidebar.nether(ALL, false).get(1)).equals("Temp: 117°F Scorching"), "nether: " + strip(Sidebar.nether(ALL, false).get(1)));
        expect(strip(Sidebar.end(ALL, false).get(1)).equals("Temp: -40°F Frigid"), "end: " + strip(Sidebar.end(ALL, false).get(1)));
        expect(strip(Sidebar.nether(ALL, true).get(1)).startsWith("Temp: 47°C"), "nether in celsius: " + strip(Sidebar.nether(ALL, true).get(1)));

        // the widest line is still a sensible width for a sidebar
        int widest = 0;
        for (String l : lines) {
            widest = Math.max(widest, strip(l).length());
        }
        for (String l : hl) {
            widest = Math.max(widest, strip(l).length());
        }
        for (String l : fl) {
            widest = Math.max(widest, strip(l).length());
        }
        expect(widest <= 34, "no sidebar line is wider than 34 characters: " + widest);
        // no line ends on a dangling colour code
        for (String l : lines) {
            expect(!l.endsWith("§"), "no dangling section sign");
        }
    }
}
