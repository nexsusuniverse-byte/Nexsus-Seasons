package com.nexusuniverse.seasons.climate;

import com.nexusuniverse.seasons.Season;

/**
 * Everything the live reading depends on. Keeping it a plain record means the whole sidebar maths
 * can be tested without a server.
 *
 * @param easedOffsetF the current weather's temperature shift, already eased so it never snaps
 * @param easedCloud   cloud cover 0..1, already eased
 */
public record LiveInput(int year, Season season, int day, double tick, double biomeTemperature,
                        double biomeHumidity, boolean exposed, double windStrength, boolean precip,
                        boolean thunder, boolean fog, boolean blizzard, double easedOffsetF, double easedCloud) {
}
