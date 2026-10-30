package com.nexusuniverse.seasons.climate;

/**
 * One stretch of rain (or snow) in the weather schedule, in server ticks counted from now.
 * {@code start} may be 0 (it is raining right now). {@code end} is exclusive.
 */
public record WeatherSpan(long start, long end, boolean thundering) {

    public boolean contains(double serverTick) {
        return serverTick >= start && serverTick < end;
    }
}
