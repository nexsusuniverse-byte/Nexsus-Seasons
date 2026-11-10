package com.nexusuniverse.seasons.climate;

/** What the sidebar shows for one player right now. */
public record Reading(double tempF, double feelsF, double rh, double dewF, double windMph, double uv,
                      PollenType pollen, int pollenLevel, String sky, boolean sheltered) {
}
