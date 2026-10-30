package com.nexusuniverse.seasons.climate;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /forecast} and {@code /allergy}. */
public final class ClimateCommand implements CommandExecutor, TabCompleter {

    private final ClimateManager climate;
    private final ClimatePlayers players;
    private final ClimateSettings settings;

    public ClimateCommand(ClimateManager climate, ClimatePlayers players, ClimateSettings settings) {
        this.climate = climate;
        this.players = players;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("allergy")) {
            return allergy(sender, args);
        }
        return forecast(sender, args);
    }

    private boolean forecast(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("show")) {
            climate.sendForecast(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "mute", "unmute" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can do that.");
                    return true;
                }
                boolean mute = sub.equals("mute");
                players.setMuted(player.getUniqueId(), mute);
                sender.sendMessage(mute
                        ? "§7The 7:00 AM forecast will no longer be sent to you. §f/forecast§7 still shows it any time."
                        : "§aYou will get the 7:00 AM forecast again.");
            }
            case "broadcast", "rebuild" -> {
                if (!sender.hasPermission("nexusseasons.admin")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                if (sub.equals("broadcast")) {
                    climate.broadcastNow();
                    sender.sendMessage("§aForecast issued to everyone online.");
                } else {
                    climate.rebuildSilently();
                    sender.sendMessage("§aForecast rebuilt from the weather schedule as it stands now.");
                }
            }
            default -> sender.sendMessage("§cUsage: /forecast [mute|unmute]");
        }
        return true;
    }

    private boolean allergy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players have allergies.");
            return true;
        }
        if (!settings.allergiesEnabled()) {
            sender.sendMessage("§7Seasonal allergies are switched off on this server.");
            return true;
        }
        AllergyType mine = players.allergy(player.getUniqueId());
        if (args.length == 0) {
            sender.sendMessage(mine == null
                    ? "§aAllergies: §7none. Lucky you."
                    : "§aAllergies: §7you are allergic to §f" + mine.display() + "§7. When it is high outside, expect sneezing.");
            DayPlan plan = climate.currentPlan();
            if (plan != null) {
                sender.sendMessage("§7Today's main pollen: §f" + plan.pollen.display() + " §7(" + plan.pollen.family().display() + ")");
            }
            if (settings.allergySelfSet()) {
                sender.sendMessage("§7Change it: §f/allergy set <tree|grass|weed|mold|none>");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length >= 2) {
            if (!settings.allergySelfSet() && !sender.hasPermission("nexusseasons.admin")) {
                sender.sendMessage("§cThat is not allowed on this server.");
                return true;
            }
            if (args[1].equalsIgnoreCase("none")) {
                players.setAllergy(player.getUniqueId(), null);
                sender.sendMessage("§aYou are no longer allergic to anything. The sneezing stops.");
                return true;
            }
            AllergyType type = AllergyType.parse(args[1]);
            if (type == null) {
                sender.sendMessage("§cUnknown type. Options: tree, grass, weed, mold, none.");
                return true;
            }
            players.setAllergy(player.getUniqueId(), type);
            sender.sendMessage("§aYou are now allergic to §f" + type.display() + "§a. Bless you in advance.");
            return true;
        }
        sender.sendMessage("§cUsage: /allergy [set <tree|grass|weed|mold|none>]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("allergy")) {
            if (args.length == 1) {
                out.add("set");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                for (AllergyType t : AllergyType.values()) {
                    out.add(t.name().toLowerCase(Locale.ROOT));
                }
                out.add("none");
            }
        } else if (args.length == 1) {
            out.add("mute");
            out.add("unmute");
            if (sender.hasPermission("nexusseasons.admin")) {
                out.add("broadcast");
                out.add("rebuild");
            }
        }
        List<String> filtered = new ArrayList<>();
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String s : out) {
            if (s.startsWith(typed)) {
                filtered.add(s);
            }
        }
        return filtered;
    }
}
