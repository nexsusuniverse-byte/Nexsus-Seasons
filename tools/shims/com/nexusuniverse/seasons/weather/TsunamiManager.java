package com.nexusuniverse.seasons.weather;

/** Test shim: the real TsunamiManager needs far more of the Bukkit API than the sandbox stubs model. */
public class TsunamiManager {
    public boolean active;

    public boolean isActive() {
        return active;
    }
}
