package com.nexusuniverse.seasons.weather;

/** Test shim: the real FogManager needs far more of the Bukkit API than the sandbox stubs model. */
public class FogManager {
    public boolean active;

    public boolean isActive() {
        return active;
    }
}
