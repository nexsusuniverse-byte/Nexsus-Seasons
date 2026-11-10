package com.nexusuniverse.seasons.climate;

/** Works out a {@link Reading} from a {@link LiveInput}. */
public final class LiveReader {

    /** Indoors the air drifts halfway to this comfortable room temperature. */
    static final double ROOM_F = 68.0;

    private LiveReader() {
    }

    public static Reading read(ClimateParams p, LiveInput in) {
        double x = Climate.dayCoord(in.year(), in.season(), in.day(), p.daysPerSeason, in.tick());
        double biome = Climate.biomeOffsetF(in.biomeTemperature());
        double localMean = Climate.baseMeanF(p, x) + biome;
        double outdoor = Climate.baseMeanF(p, x) + Climate.diurnalF(in.tick(), Climate.dayRangeF(p, in.easedCloud()))
                + in.easedOffsetF() + biome;
        boolean wet = in.precip() || in.thunder() || in.fog();
        double moisture = Climate.moisture(p, x, in.season(), in.biomeHumidity(), in.precip(), in.thunder(), in.fog());
        double dewOutdoor = Climate.dewPointF(localMean, moisture, outdoor, wet);

        double temp = in.exposed() ? outdoor : outdoor + (ROOM_F - outdoor) * 0.5;
        double dew = Math.min(dewOutdoor, temp - 0.4);
        double rh = Climate.relativeHumidity(temp, dew);
        double wind = in.exposed() ? Climate.windMph(in.windStrength()) : 0.0;
        double feels = Climate.feelsLikeF(temp, rh, wind);
        double cloud = in.precip() || in.thunder() ? 1.0 : in.easedCloud();
        double uv = in.exposed() ? Climate.uvIndex(p, x, in.tick(), cloud, in.precip() || in.thunder()) : 0.0;

        long dayIdx = Climate.dayIndex(in.year(), in.season(), in.day(), p.daysPerSeason);
        PollenType type = Climate.dominantPollen(p, in.season(), in.day(), dayIdx);
        double score = Climate.pollenScore(p, in.season(), in.day(), dayIdx, type, outdoor,
                in.precip() || in.thunder(), in.windStrength());
        int level = Climate.pollenLevel(score);
        if (!in.exposed()) {
            level = Math.max(0, level - 1);
        }

        String sky;
        boolean cold = outdoor <= Climate.SNOW_LINE_F;
        if (in.thunder()) {
            sky = cold ? "Thundersnow" : "Thunderstorm";
        } else if (in.precip()) {
            sky = cold ? "Snowing" : "Raining";
        } else if (in.fog()) {
            sky = "Foggy";
        } else {
            sky = Climate.skyText(in.easedCloud(), GameClock.isDay((long) in.tick()));
        }
        return new Reading(temp, feels, rh, dew, wind, uv, type, level, sky, !in.exposed());
    }
}
