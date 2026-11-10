package com.nexusuniverse.seasons;

import org.bukkit.ChatColor;

public enum Season {
    SPRING("Spring", ChatColor.GREEN),
    SUMMER("Summer", ChatColor.YELLOW),
    FALL("Fall", ChatColor.GOLD),
    WINTER("Winter", ChatColor.AQUA);

    private final String displayName;
    private final ChatColor color;

    Season(String displayName, ChatColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String coloredName() {
        return color + displayName;
    }

    public Season next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
