package com.nexusuniverse.seasons.weather;

/** Test shim: the real BlizzardManager needs far more of the Bukkit API than the sandbox stubs model. */
public class BlizzardManager {
    public boolean active;

    public boolean isActive() {
        return active;
    }
}
