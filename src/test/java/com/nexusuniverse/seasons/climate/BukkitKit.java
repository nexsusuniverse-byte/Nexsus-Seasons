package com.nexusuniverse.seasons.climate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Test doubles for the parts that touch a server: a plugin, worlds, players that remember what they were told. */
final class BukkitKit {

    private BukkitKit() {
    }

    static final class TestPlugin extends JavaPlugin {
    }

    static class TestPlayer extends Player {
        final String name;
        final List<String> messages = new ArrayList<>();
        final Set<String> perms = new HashSet<>();

        TestPlayer(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public boolean hasPermission(String node) {
            return perms.contains(node);
        }

        @Override
        public boolean isOnline() {
            return true;
        }

        String text() {
            return Kit.strip(String.join("\n", messages));
        }
    }

    /** A clean server: no worlds, no players, no scheduled tasks, an empty data folder. */
    static File reset() {
        Bukkit.WORLDS.clear();
        Bukkit.PLAYERS.clear();
        BukkitScheduler.LATER.clear();
        BukkitScheduler.REPEATING.clear();
        BukkitScheduler.HANDLES.clear();
        BukkitScheduler.DELAYS.clear();
        BukkitScheduler.PERIODS.clear();
        org.bukkit.scoreboard.Objective.HIDE_CALLS = 0;
        try {
            File dir = Files.createTempDirectory("nsclimate").toFile();
            JavaPlugin.DATA_FOLDER = dir;
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static World overworld(String name) {
        return Bukkit.createWorld(new WorldCreator(name));
    }

    static World nether(String name) {
        return Bukkit.createWorld(new WorldCreator(name).environment(World.Environment.NETHER));
    }

    static TestPlayer join(String name, World world) {
        TestPlayer p = new TestPlayer(name);
        p.stubSetLocation(new Location(world, 100, 70, 100));
        Bukkit.PLAYERS.put(p.getUniqueId(), p);
        return p;
    }

    static Command command(String name) {
        return new Command() {
            @Override
            public String getName() {
                return name;
            }
        };
    }
}
