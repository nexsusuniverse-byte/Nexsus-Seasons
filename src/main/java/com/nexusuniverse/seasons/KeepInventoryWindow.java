package com.nexusuniverse.seasons;

import java.time.LocalTime;

/**
 * One scheduled keep-inventory window, parsed from keep-inventory-events.schedule in config.yml:
 * turns the keepInventory gamerule ON at {@code start} (this server's own local wall-clock time)
 * and automatically back OFF {@code durationMinutes} later. See KeepInventoryEventManager for the
 * warning/countdown messages and the actual per-tick gamerule enforcement built around this.
 */
public record KeepInventoryWindow(LocalTime start, int durationMinutes) {

    /** May be earlier in the clock than {@link #start()} -- that just means the window crosses midnight; callers compare against both accordingly (see KeepInventoryEventManager#isWithin). */
    public LocalTime end() {
        return start.plusMinutes(durationMinutes);
    }
}
