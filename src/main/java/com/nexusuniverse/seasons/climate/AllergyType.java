package com.nexusuniverse.seasons.climate;

import java.util.Locale;

/** The broad families of pollen (and mould) a player can be allergic to. */
public enum AllergyType {
    TREE("tree pollen"),
    GRASS("grass pollen"),
    WEED("weed pollen"),
    MOLD("mould spores");

    private final String display;

    AllergyType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /** Parses "tree", "grass", "weed", "mold"/"mould"; null for anything else. */
    public static AllergyType parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("mould")) {
            s = "mold";
        }
        for (AllergyType t : values()) {
            if (t.name().toLowerCase(Locale.ROOT).equals(s)) {
                return t;
            }
        }
        return null;
    }
}
