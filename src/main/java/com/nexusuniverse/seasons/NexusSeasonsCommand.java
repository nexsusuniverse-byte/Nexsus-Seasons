package com.nexusuniverse.seasons;

import com.nexusuniverse.seasons.weather.BlizzardManager;
import com.nexusuniverse.seasons.weather.CoolingCap;
import com.nexusuniverse.seasons.weather.DryThunderstormManager;
import com.nexusuniverse.seasons.weather.EarthquakeManager;
import com.nexusuniverse.seasons.weather.FogManager;
import com.nexusuniverse.seasons.weather.HurricaneManager;
import com.nexusuniverse.seasons.weather.MeteorShowerManager;
import com.nexusuniverse.seasons.weather.SandstormManager;
import com.nexusuniverse.seasons.weather.TornadoManager;
import com.nexusuniverse.seasons.weather.TsunamiManager;
import com.nexusuniverse.seasons.weather.WaveTrainManager;
import com.nexusuniverse.seasons.weather.WindproofBoots;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NexusSeasonsCommand implements CommandExecutor {

    private final SeasonClock clock;
    private final SeasonsConfig config;
    private final Runnable onSilentChange;
    private final Runnable onAdvance;
    private final DryThunderstormManager dryThunder;
    private final FogManager fog;
    private final TornadoManager tornado;
    private final BlizzardManager blizzard;
    private final TsunamiManager tsunami;
    private final HurricaneManager hurricane;
    private final WaveTrainManager waveTrain;
    private final EarthquakeManager earthquake;
    private final SandstormManager sandstorm;
    private final MeteorShowerManager meteorShower;
    private final WindproofBoots windproofBoots;
    private final KeepInventoryEventManager keepInventoryEvents;
    private final CoolingCap coolingCap;

    public NexusSeasonsCommand(SeasonClock clock, SeasonsConfig config, Runnable onSilentChange, Runnable onAdvance,
                                DryThunderstormManager dryThunder, FogManager fog, TornadoManager tornado, BlizzardManager blizzard,
                                TsunamiManager tsunami, HurricaneManager hurricane, WaveTrainManager waveTrain,
                                EarthquakeManager earthquake, SandstormManager sandstorm, MeteorShowerManager meteorShower,
                                WindproofBoots windproofBoots, KeepInventoryEventManager keepInventoryEvents, CoolingCap coolingCap) {
        this.clock = clock;
        this.config = config;
        this.onSilentChange = onSilentChange;
        this.onAdvance = onAdvance;
        this.dryThunder = dryThunder;
        this.fog = fog;
        this.tornado = tornado;
        this.blizzard = blizzard;
        this.tsunami = tsunami;
        this.hurricane = hurricane;
        this.waveTrain = waveTrain;
        this.earthquake = earthquake;
        this.sandstorm = sandstorm;
        this.meteorShower = meteorShower;
        this.windproofBoots = windproofBoots;
        this.keepInventoryEvents = keepInventoryEvents;
        this.coolingCap = coolingCap;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (!sender.hasPermission("nexusseasons.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setyear" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /nexusseasons setyear <n>");
                    return true;
                }
                try {
                    clock.setYear(Integer.parseInt(args[1]));
                    onSilentChange.run();
                    sender.sendMessage("§aYear set to " + clock.year() + ".");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cThat's not a number.");
                }
            }
            case "setseason" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /nexusseasons setseason <spring|summer|fall|winter>");
                    return true;
                }
                try {
                    clock.setSeason(Season.valueOf(args[1].toUpperCase()));
                    onSilentChange.run();
                    sender.sendMessage("§aSeason set to " + clock.season().coloredName() + "§a.");
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cUnknown season. Options: spring, summer, fall, winter");
                }
            }
            case "setday" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /nexusseasons setday <n>");
                    return true;
                }
                try {
                    clock.setDayOfSeason(Integer.parseInt(args[1]));
                    onSilentChange.run();
                    sender.sendMessage("§aDay set to " + clock.dayOfSeason() + "/" + clock.daysPerSeason() + ".");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cThat's not a number.");
                }
            }
            case "advance" -> {
                onAdvance.run();
                sender.sendMessage("§aAdvanced one day -- now " + clock.season().coloredName()
                        + "§a, day " + clock.dayOfSeason() + "/" + clock.daysPerSeason() + ", year " + clock.year() + ".");
            }
            case "reload" -> {
                config.reload();
                keepInventoryEvents.reloadSchedule();
                sender.sendMessage("§aConfig reloaded.");
            }
            case "cyclelock" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /nexusseasons cyclelock <on|off|status>");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "on" -> {
                        config.setCycleLockEnabled(true);
                        sender.sendMessage("§aCycle lock ON -- day/night and weather will be forced back to their "
                                + "configured state if anyone changes the gamerules, including you.");
                    }
                    case "off" -> {
                        config.setCycleLockEnabled(false);
                        sender.sendMessage("§eCycle lock OFF -- doDaylightCycle/doWeatherCycle can be changed "
                                + "normally now, and NexusSeasons won't fight you on it.");
                    }
                    case "status" -> sender.sendMessage("§fCycle lock: §7" + (config.cycleLockEnabled() ? "ON" : "OFF"));
                    default -> sender.sendMessage("§cUsage: /nexusseasons cyclelock <on|off|status>");
                }
            }
            case "drythunder" -> handleToggle(sender, args, "drythunder", dryThunder.isActive(),
                    seconds -> dryThunder.forceStart(seconds), dryThunder::forceStop);
            case "fog" -> handleToggle(sender, args, "fog", fog.isActive(),
                    seconds -> fog.forceStart(seconds), fog::forceStop);
            case "blizzard" -> handleToggle(sender, args, "blizzard", blizzard.isActive(),
                    seconds -> blizzard.forceStart(seconds), blizzard::forceStop);
            case "tornado" -> handleTornado(sender, args);
            case "tsunami" -> handleTsunami(sender, args);
            case "hurricane" -> handleHurricane(sender, args);
            case "wavereset" -> handleWaveReset(sender, args);
            case "earthquake" -> handleToggle(sender, args, "earthquake", earthquake.isActive(),
                    seconds -> earthquake.forceStart(seconds), earthquake::forceStop);
            case "sandstorm" -> handleToggle(sender, args, "sandstorm", sandstorm.isActive(),
                    seconds -> sandstorm.forceStart(seconds), sandstorm::forceStop);
            case "meteorshower" -> handleToggle(sender, args, "meteorshower", meteorShower.isActive(),
                    seconds -> meteorShower.forceStart(seconds), meteorShower::forceStop);
            case "windboots" -> handleWindBoots(sender, args);
            case "coolingcap" -> handleCoolingCap(sender, args);
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    /** Shared start/stop/status handling for the three simple event managers (dry thunder, fog, blizzard) -- tornado needs its own handler since spawn takes a location, not just a duration. */
    private void handleToggle(CommandSender sender, String[] args, String name, boolean currentlyActive,
                               java.util.function.IntConsumer start, Runnable stop) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusseasons " + name + " <start [seconds]|stop|status>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start" -> {
                int seconds = args.length >= 3 ? parseIntOr(args[2], 120) : 120;
                start.accept(seconds);
                sender.sendMessage("§aStarted " + name + " for " + seconds + " seconds.");
            }
            case "stop" -> {
                stop.run();
                sender.sendMessage("§a" + capitalize(name) + " stopped.");
            }
            case "status" -> sender.sendMessage("§f" + capitalize(name) + ": §7" + (currentlyActive ? "active" : "not active"));
            default -> sender.sendMessage("§cUsage: /nexusseasons " + name + " <start [seconds]|stop|status>");
        }
    }

    private void handleTornado(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusseasons tornado <spawn|stop|status>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly a player can spawn a tornado at their own location -- run this in-game.");
                    return;
                }
                tornado.spawnAt(player.getLocation());
                sender.sendMessage("§aTornado spawned.");
            }
            case "stop" -> {
                tornado.dissipate();
                sender.sendMessage("§aTornado dissipated.");
            }
            case "status" -> sender.sendMessage("§fTornado: §7" + (tornado.isActive() ? "active" : "not active"));
            default -> sender.sendMessage("§cUsage: /nexusseasons tornado <spawn|stop|status>");
        }
    }

    private void handleTsunami(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusseasons tsunami <spawn|stop|status>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly a player can spawn a tsunami near their own location -- run this in-game.");
                    return;
                }
                boolean started = tsunami.spawnNear(player.getLocation());
                if (started) {
                    sender.sendMessage("§aTsunami warning issued -- it'll hit in " + config.tsunamiWarningSeconds() + " seconds.");
                } else if (tsunami.isActive()) {
                    sender.sendMessage("§cA tsunami is already in progress.");
                } else {
                    sender.sendMessage("§cNo coastline found nearby -- try standing closer to open ocean.");
                }
            }
            case "stop" -> {
                tsunami.stopActive();
                sender.sendMessage("§aTsunami stopped.");
            }
            case "status" -> sender.sendMessage("§fTsunami: §7" + (tsunami.isActive() ? "active" : "not active"));
            default -> sender.sendMessage("§cUsage: /nexusseasons tsunami <spawn|stop|status>");
        }
    }

    private void handleHurricane(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /nexusseasons hurricane <start [minutes]|stop|status>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start" -> {
                if (hurricane.isActive()) {
                    sender.sendMessage("§cA hurricane is already in progress.");
                    return;
                }
                int minutes = args.length >= 3 ? parseIntOr(args[2], 20) : 20;
                hurricane.forceStart(minutes);
                sender.sendMessage("§aHurricane started -- expected to last " + minutes + " minutes.");
            }
            case "stop" -> {
                hurricane.forceStop();
                sender.sendMessage("§aHurricane stopped.");
            }
            case "status" -> sender.sendMessage("§fHurricane: §7" + (hurricane.isActive() ? "active" : "not active"));
            default -> sender.sendMessage("§cUsage: /nexusseasons hurricane <start [minutes]|stop|status>");
        }
    }

    /**
     * Manual cleanup for water that's stuck/leaked and won't recede on its own -- see
     * WaveTrainManager#cleanupArea's own doc for exactly how it decides what's safe to remove.
     * Only ever run explicitly by an admin, never automatic.
     */
    private void handleWaveReset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cRun this in-game, standing near the water that needs cleaning up.");
            return;
        }
        int radius = args.length >= 2 ? parseIntOr(args[1], 32) : 32;
        radius = Math.max(4, Math.min(64, radius)); // capped -- this runs synchronously on the main thread, so a huge radius means a real, noticeable pause

        Location center = player.getLocation();
        int cleared = waveTrain.cleanupArea(center, radius);
        if (cleared == 0) {
            sender.sendMessage("§7Nothing to clean up within " + radius + " blocks -- either it's already clear, or there's no water nearby at all.");
        } else {
            sender.sendMessage("§aCleared " + cleared + " leftover water block(s) within " + radius + " blocks.");
        }
    }

    /**
     * /nexusseasons windboots give [player] - hands out a correctly-tagged pair of the custom
     * windproof Netherite Boots (see WindproofBoots). Defaults to the sender if no target is
     * named and the sender is a player; an explicit target must be online (no offline-player
     * mail slot for this - just re-run the command once they're on).
     */
    private void handleWindBoots(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§cUsage: /nexusseasons windboots give [player]");
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§c" + args[2] + " isn't online.");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cConsole needs a player name: /nexusseasons windboots give <player>");
            return;
        }

        var leftover = target.getInventory().addItem(windproofBoots.create());
        if (!leftover.isEmpty()) {
            // inventory was full - drop at their feet rather than silently discard
            leftover.values().forEach(item -> target.getWorld().dropItem(target.getLocation(), item));
            target.sendMessage("§eYour inventory was full - the Stormward Boots dropped at your feet instead.");
        } else {
            target.sendMessage("§aYou received a pair of Stormward Boots - wear them and the wind won't touch you.");
        }
        if (!sender.equals(target)) {
            sender.sendMessage("§aGave Stormward Boots to " + target.getName() + ".");
        }
    }

    /**
     * /nexusseasons coolingcap give [player] - hands out a correctly-tagged Cooling Cap (see
     * CoolingCap), the summer-heat equivalent of windboots give above. Same target-resolution
     * rules: defaults to the sender if no target is named and the sender is a player; an explicit
     * target must be online.
     */
    private void handleCoolingCap(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§cUsage: /nexusseasons coolingcap give [player]");
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§c" + args[2] + " isn't online.");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cConsole needs a player name: /nexusseasons coolingcap give <player>");
            return;
        }

        var leftover = target.getInventory().addItem(coolingCap.create());
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> target.getWorld().dropItem(target.getLocation(), item));
            target.sendMessage("§eYour inventory was full - the Cooling Cap dropped at your feet instead.");
        } else {
            target.sendMessage("§aYou received a Cooling Cap - wear it and summer heat won't touch you.");
        }
        if (!sender.equals(target)) {
            sender.sendMessage("§aGave a Cooling Cap to " + target.getName() + ".");
        }
    }

    private int parseIntOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String capitalize(String raw) {
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("§7--- Season Status ---");
        sender.sendMessage("§fSeason: " + clock.season().coloredName());
        sender.sendMessage("§fDay: §7" + clock.dayOfSeason() + "/" + clock.daysPerSeason());
        sender.sendMessage("§fYear: §7" + clock.year());
        if (config.customDayNightEnabled()) {
            sender.sendMessage("§fDay/Night: §7custom -- " + config.dayLengthMinutes() + "m day / "
                    + config.nightLengthMinutes() + "m night");
        } else {
            sender.sendMessage("§fDay/Night: §7vanilla");
        }
        if (config.weatherCycleEnabled()) {
            sender.sendMessage("§fWeather: §7custom -- " + config.weatherClearMinMinutes() + "-"
                    + config.weatherClearMaxMinutes() + "m clear / " + config.weatherRainMinMinutes() + "-"
                    + config.weatherRainMaxMinutes() + "m rain (" + Math.round(config.weatherThunderChance() * 100)
                    + "% thunder chance)");
        } else {
            sender.sendMessage("§fWeather: §7vanilla");
        }
        sender.sendMessage("§fCycle lock: §7" + (config.cycleLockEnabled() ? "ON" : "OFF"));
        if (config.keepInventoryEventsEnabled()) {
            sender.sendMessage("§fKeep inventory events: §7ON -- " + keepInventoryEvents.windows().size()
                    + " window(s) scheduled, currently §7" + (keepInventoryEvents.shouldBeOnRightNow() ? "§aON" : "§coff"));
        } else {
            sender.sendMessage("§fKeep inventory events: §7OFF §8(disabled in config)");
        }
        sender.sendMessage("§7--- Extreme Weather ---");
        sender.sendMessage("§fDry thunderstorm: §7" + (dryThunder.isActive() ? "ACTIVE" : "off")
                + (config.dryThunderEnabled() ? "" : " §8(disabled in config)"));
        sender.sendMessage("§fFog: §7" + (fog.isActive() ? "ACTIVE" : "off")
                + (config.fogEnabled() ? "" : " §8(disabled in config)"));
        sender.sendMessage("§fBlizzard: §7" + (blizzard.isActive() ? "ACTIVE" : "off")
                + (config.blizzardEnabled() ? "" : " §8(disabled in config)"));
        sender.sendMessage("§fTornado: §7" + (tornado.isActive() ? "ACTIVE" : "off")
                + (config.tornadoEnabled() ? "" : " §8(disabled in config)"));
        sender.sendMessage("§fWind: §7" + (config.windEnabled() ? "ON" : "off"));
        sender.sendMessage("§fWaves: §7" + (config.wavesEnabled() ? "ON" : "off"));
        sender.sendMessage("§fTsunami: §7" + (tsunami.isActive() ? "ACTIVE" : "off")
                + (config.tsunamiEnabled() ? "" : " §8(disabled in config)"));
        sender.sendMessage("§fHurricane: §7" + (hurricane.isActive() ? "ACTIVE" : "off")
                + (config.hurricaneEnabled() ? "" : " §8(disabled in config)"));
        sender.sendMessage("§fEarthquake: §7" + (earthquake.isActive() ? "ACTIVE" : "off")
                + (config.earthquakeEnabled() ? "" : " §8(disabled in config)"));
        sender.sendMessage("§fSandstorm: §7" + (sandstorm.isActive() ? "ACTIVE" : "off")
                + (config.sandstormEnabled() ? "" : " §8(disabled in config)"));
        sender.sendMessage("§fMeteor shower: §7" + (meteorShower.isActive() ? "ACTIVE" : "off")
                + (config.meteorShowerEnabled() ? "" : " §8(disabled in config)"));
    }
}
