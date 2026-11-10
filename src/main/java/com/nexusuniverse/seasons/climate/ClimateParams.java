package com.nexusuniverse.seasons.climate;

/** Every tunable number of the climate model, in degrees Fahrenheit internally. Built from config.yml. */
public final class ClimateParams {

    public final long seed;
    public final int daysPerSeason;
    public final double annualMeanF;
    public final double annualSwingF;
    public final double anomalyF;
    public final double rangeClearF;
    public final double rangeCloudyF;
    public final double rainOffsetF;
    public final double thunderOffsetF;
    public final double blizzardOffsetF;
    public final double fogOffsetF;
    public final boolean celsius;

    public ClimateParams(long seed, int daysPerSeason, double annualMeanF, double annualSwingF, double anomalyF,
                         double rangeClearF, double rangeCloudyF, double rainOffsetF, double thunderOffsetF,
                         double blizzardOffsetF, double fogOffsetF, boolean celsius) {
        this.seed = seed;
        this.daysPerSeason = Math.max(1, daysPerSeason);
        this.annualMeanF = annualMeanF;
        this.annualSwingF = annualSwingF;
        this.anomalyF = anomalyF;
        this.rangeClearF = rangeClearF;
        this.rangeCloudyF = rangeCloudyF;
        this.rainOffsetF = rainOffsetF;
        this.thunderOffsetF = thunderOffsetF;
        this.blizzardOffsetF = blizzardOffsetF;
        this.fogOffsetF = fogOffsetF;
        this.celsius = celsius;
    }

    /** The shipped defaults: a temperate climate, 28F deep winter to 80F high summer on average. */
    public static ClimateParams defaults(long seed, int daysPerSeason) {
        return new ClimateParams(seed, daysPerSeason, 54.0, 26.0, 6.0, 20.0, 11.0, -5.0, -8.0, -24.0, -2.0, false);
    }
}
