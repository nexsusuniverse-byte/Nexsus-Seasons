package com.nexusuniverse.seasons.weather;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Test shim for the real WindManager: the three calls the sidebar and climate code use. */
public class WindManager {
    public double strength = 0.2;
    public boolean exposed = true;

    public double currentStrength() {
        return strength;
    }

    public Vector currentDirection() {
        return new Vector(1, 0, 0);
    }

    public boolean isExposedToSky(Location location) {
        return exposed;
    }
}
