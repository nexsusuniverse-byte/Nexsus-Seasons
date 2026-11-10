package com.nexusuniverse.seasons.weather;

/** Test shim: the real HurricaneManager needs far more of the Bukkit API than the sandbox stubs model. */
public class HurricaneManager {
    public boolean active;

    public boolean isActive() {
        return active;
    }
}
