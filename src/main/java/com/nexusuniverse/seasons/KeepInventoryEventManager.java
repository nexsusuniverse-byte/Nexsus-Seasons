package com.nexusuniverse.seasons;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scheduled keep-inventory events: at each configured server-wall-clock time
 * (keep-inventory-events.schedule in config.yml), warns players in chat, counts down in chat AND
 * on their title screen, then forces the keepInventory gamerule ON the moment it starts. The same
 * warning/countdown/message sequence runs in reverse once the window's duration elapses, forcing
 * it back OFF.
 *
 * "Forces" is the operative word, and it's deliberate -- this mirrors CycleLockManager/
 * CycleLockGuard's exact reasoning (see those two classes' doc comments for the full
 * explanation). A /gamerule command run by a player, the console, or -- most importantly -- a
 * redstone-triggered command block can change keepInventory at any moment, and Bukkit/Paper has
 * never exposed a pre-execution event a plugin can intercept a command block's own command with.
 * So instead of trying to prevent every possible way keepInventory could change, this runs every
 * single tick and re-asserts whatever the schedule currently says it should be, correcting any
 * outside change within about one tick (~50ms) no matter where it came from.
 * KeepInventoryEventGuard additionally cancels a player- or console-typed
 * "/gamerule keepInventory" command outright before it runs, purely so the command visibly fails
 * with an explanation instead of silently no-op'ing a moment later -- the actual guarantee that
 * the rule stays correct comes from this class's per-tick enforcement, not from that guard.
 *
 * All times are this SERVER's own local wall-clock time (whatever timezone the machine itself is
 * set to, via LocalTime.now()), NOT an in-game/NexusSeasons day-night or season value -- a
 * schedule entry like "21:00" fires at 9 PM real time every real day, regardless of what
 * NexusSeasons' own custom day/night cycle or season is doing at that moment.
 */
public class KeepInventoryEventManager {

    private final JavaPlugin plugin;
    private final SeasonsConfig config;
    private BukkitTask enforceTask;
    private BukkitTask scheduleTask;

    private List<KeepInventoryWindow> windows = List.of();
    private final Map<KeepInventoryWindow, WindowState> states = new HashMap<>();

    public KeepInventoryEventManager(JavaPlugin plugin, SeasonsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        stop();
        reloadSchedule();

        // Per-tick gamerule enforcement -- see class doc for why this has to run this often.
        enforceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::enforceGameRule, 1L, 1L);
        // Once a second is plenty for messages/countdowns -- nobody needs sub-second chat timing.
        scheduleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSchedule, 20L, 20L);
    }

    public void stop() {
        if (enforceTask != null) enforceTask.cancel();
        if (scheduleTask != null) scheduleTask.cancel();
        enforceTask = null;
        scheduleTask = null;
    }

    /** Re-reads keep-inventory-events.schedule from config -- call after /nexusseasons reload so an edited schedule takes effect without a restart. */
    public void reloadSchedule() {
        this.windows = config.keepInventorySchedule();
        Map<KeepInventoryWindow, WindowState> fresh = new HashMap<>();
        for (KeepInventoryWindow window : windows) {
            fresh.put(window, states.getOrDefault(window, new WindowState()));
        }
        states.clear();
        states.putAll(fresh);
    }

    public List<KeepInventoryWindow> windows() {
        return windows;
    }

    /** True right now if any configured window covers the current server wall-clock time. */
    public boolean shouldBeOnRightNow() {
        LocalTime now = LocalTime.now();
        for (KeepInventoryWindow window : windows) {
            if (isWithin(now, window.start(), window.end())) return true;
        }
        return false;
    }

    private void enforceGameRule() {
        if (!config.keepInventoryEventsEnabled() || windows.isEmpty()) return;

        boolean desired = shouldBeOnRightNow();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            Boolean current = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
            if (current == null || current != desired) {
                world.setGameRule(GameRule.KEEP_INVENTORY, desired);
            }
        }
    }

    private void tickSchedule() {
        if (!config.keepInventoryEventsEnabled() || windows.isEmpty()) return;

        LocalTime now = LocalTime.now().withNano(0);
        LocalDate today = LocalDate.now();
        int warningLeadMinutes = config.keepInventoryWarningLeadMinutes();
        int countdownSeconds = config.keepInventoryCountdownSeconds();

        for (KeepInventoryWindow window : windows) {
            WindowState state = states.computeIfAbsent(window, w -> new WindowState());
            state.resetIfNewDay(today);

            if (warningLeadMinutes > 0) {
                checkEdge(state.startWarningFired, now, window.start().minusMinutes(warningLeadMinutes), today,
                        () -> broadcast(fillMinutes(config.keepInventoryMessage("warning",
                                "§e⚠ Keep Inventory turns §a§lON §e in {minutes} minute(s)!"), warningLeadMinutes)));
            }

            checkCountdown(state, now, window.start(), countdownSeconds, true);

            checkEdge(state.startFired, now, window.start(), today, () -> {
                broadcast(config.keepInventoryMessage("start", "§a§lKeep Inventory is now ON! Die without fear."));
                broadcastTitle(config.keepInventoryMessage("start-title", "§a§lKEEP INVENTORY: ON"),
                        config.keepInventoryMessage("start-subtitle", "§7Die without fear."));
            });

            if (warningLeadMinutes > 0) {
                checkEdge(state.endWarningFired, now, window.end().minusMinutes(warningLeadMinutes), today,
                        () -> broadcast(fillMinutes(config.keepInventoryMessage("end-warning",
                                "§e⚠ Keep Inventory turns §c§lOFF §e in {minutes} minute(s)! Play it safe."), warningLeadMinutes)));
            }

            checkCountdown(state, now, window.end(), countdownSeconds, false);

            checkEdge(state.endFired, now, window.end(), today, () -> {
                broadcast(config.keepInventoryMessage("end", "§c§lKeep Inventory is now OFF. Your items will drop on death again."));
                broadcastTitle(config.keepInventoryMessage("end-title", "§c§lKEEP INVENTORY: OFF"),
                        config.keepInventoryMessage("end-subtitle", "§7Your items will drop again."));
            });
        }
    }

    /**
     * Fires `action` exactly once per day, the first pass `now` reaches or passes `target` --
     * marks the edge as handled for today regardless, but only actually runs the message if
     * we're within 5 seconds of the target, so a server that was down or badly lagged past this
     * moment doesn't fire a stale "starting now" announcement hours late once it catches up.
     */
    private void checkEdge(AtomicReference<LocalDate> firedDateRef, LocalTime now, LocalTime target, LocalDate today, Runnable action) {
        if (today.equals(firedDateRef.get())) return;
        if (!now.isBefore(target)) {
            firedDateRef.set(today);
            if (Duration.between(target, now).toSeconds() <= 5) {
                action.run();
            }
        }
    }

    private void checkCountdown(WindowState state, LocalTime now, LocalTime target, int countdownSeconds, boolean isStart) {
        if (countdownSeconds <= 0) return;

        long secondsUntil = ChronoUnit.SECONDS.between(now, target);
        if (secondsUntil < 0) secondsUntil += 86400L; // target wraps past midnight relative to now
        if (secondsUntil > countdownSeconds) return;

        int secondsRemaining = (int) secondsUntil;
        AtomicInteger lastSent = isStart ? state.lastStartCountdownSecond : state.lastEndCountdownSecond;
        if (lastSent.get() == secondsRemaining) return; // already sent this exact second
        lastSent.set(secondsRemaining);

        String key = isStart ? "countdown" : "end-countdown";
        String defaultTemplate = isStart ? "§c§lKeep Inventory ON in {seconds}..." : "§c§lKeep Inventory OFF in {seconds}...";
        String message = config.keepInventoryMessage(key, defaultTemplate).replace("{seconds}", String.valueOf(secondsRemaining));
        broadcast(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§f§l" + secondsRemaining, "", 0, 25, 5);
        }
    }

    private String fillMinutes(String template, int minutes) {
        return template.replace("{minutes}", String.valueOf(minutes));
    }

    /**
     * Whether `now` falls inside [start, end) -- handles a window that crosses midnight (end
     * earlier in raw clock terms than start, e.g. 23:30 -> 00:30) by treating that as "still
     * inside" once now wraps past midnight too, rather than only supporting same-day windows.
     */
    private boolean isWithin(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) return false; // zero-length window, never active
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private void broadcast(String message) {
        if (message == null || message.isBlank()) return;
        Bukkit.broadcastMessage(message);
    }

    private void broadcastTitle(String title, String subtitle) {
        String safeTitle = title == null ? "" : title;
        String safeSubtitle = subtitle == null ? "" : subtitle;
        if (safeTitle.isBlank() && safeSubtitle.isBlank()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(safeTitle, safeSubtitle, 10, 60, 20);
        }
    }

    /**
     * Per-window "have we already fired X today" tracking, so a once-a-second poll doesn't repeat
     * a message all through the matching second, and doesn't resend the same countdown
     * seconds-remaining value twice. Resets automatically at the first tick of a new day.
     */
    private static class WindowState {
        final AtomicReference<LocalDate> startWarningFired = new AtomicReference<>();
        final AtomicReference<LocalDate> startFired = new AtomicReference<>();
        final AtomicReference<LocalDate> endWarningFired = new AtomicReference<>();
        final AtomicReference<LocalDate> endFired = new AtomicReference<>();
        final AtomicInteger lastStartCountdownSecond = new AtomicInteger(Integer.MIN_VALUE);
        final AtomicInteger lastEndCountdownSecond = new AtomicInteger(Integer.MIN_VALUE);
        private LocalDate lastResetDate = LocalDate.now();

        void resetIfNewDay(LocalDate today) {
            if (today.equals(lastResetDate)) return;
            startWarningFired.set(null);
            startFired.set(null);
            endWarningFired.set(null);
            endFired.set(null);
            lastStartCountdownSecond.set(Integer.MIN_VALUE);
            lastEndCountdownSecond.set(Integer.MIN_VALUE);
            lastResetDate = today;
        }
    }
}
