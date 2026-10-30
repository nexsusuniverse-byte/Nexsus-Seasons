package com.nexusuniverse.seasons.weather;

/** Test shim: the real TornadoManager needs far more of the Bukkit API than the sandbox stubs model. */
public class TornadoManager {
    public boolean active;

    public boolean isActive() {
        return active;
    }
}
