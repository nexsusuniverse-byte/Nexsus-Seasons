package com.nexusuniverse.seasons.climate;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Per-player climate settings: which allergy (if any) and whether they muted the morning broadcast. Saved to climate-players.yml. */
public final class ClimatePlayers {

    private final JavaPlugin plugin;
    private final File file;
    /** UUID -> allergy; a known player with no allergy maps to null. */
    private final Map<UUID, AllergyType> allergies = new HashMap<>();
    private final Set<UUID> muted = new HashSet<>();

    public ClimatePlayers(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "climate-players.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        var section = data.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (section.contains(key + ".allergy")) {
                allergies.put(id, AllergyType.parse(section.getString(key + ".allergy", "NONE")));
            }
            if (section.getBoolean(key + ".muted", false)) {
                muted.add(id);
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, AllergyType> e : allergies.entrySet()) {
            String base = "players." + e.getKey();
            data.set(base + ".allergy", e.getValue() == null ? "NONE" : e.getValue().name());
            if (muted.contains(e.getKey())) {
                data.set(base + ".muted", true);
            }
        }
        for (UUID id : muted) {
            data.set("players." + id + ".muted", true);
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusSeasons: failed to save climate-players.yml", e);
        }
    }

    public boolean known(UUID id) {
        return allergies.containsKey(id);
    }

    public AllergyType allergy(UUID id) {
        return allergies.get(id);
    }

    public void setAllergy(UUID id, AllergyType type) {
        allergies.put(id, type);
        save();
    }

    public boolean muted(UUID id) {
        return muted.contains(id);
    }

    public void setMuted(UUID id, boolean value) {
        if (value) {
            muted.add(id);
        } else {
            muted.remove(id);
        }
        save();
    }
}
