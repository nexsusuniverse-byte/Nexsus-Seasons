package com.nexusuniverse.seasons.climate;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitScheduler;

import static com.nexusuniverse.seasons.climate.Kit.expect;

/** Allergies: rolled once per player on first join, sneezes only for the right pollen, outdoors, and only now and then. */
public class AllergyTest {

    static AllergyManager manager(ManagerTest.Rig r) {
        AllergyManager m = new AllergyManager(r.plugin, r.settings, r.players, r.manager);
        m.start();
        return m;
    }

    public static void main(String[] args) {
        rolling();
        sneezing();
        Kit.finish();
    }

    static void rolling() {
        ManagerTest.Rig r = new ManagerTest.Rig();
        r.plugin.getConfig().set("allergies.chance-per-player", 1.0);
        AllergyManager m = manager(r);
        BukkitKit.TestPlayer a = BukkitKit.join("Ann", r.world);
        m.onJoin(new PlayerJoinEvent(a, ""));
        AllergyType got = r.players.allergy(a.getUniqueId());
        expect(got != null, "chance 1.0 always gives an allergy");
        expect(a.messages.isEmpty(), "told a few seconds later, not instantly");
        while (!BukkitScheduler.LATER.isEmpty()) {
            BukkitScheduler.LATER.remove(0).run();
        }
        expect(a.text().contains("allergic"), "the notice says what to watch for: " + a.text());

        m.onJoin(new PlayerJoinEvent(a, ""));
        expect(r.players.allergy(a.getUniqueId()) == got, "rejoining never re-rolls it");

        ManagerTest.Rig none = new ManagerTest.Rig();
        none.plugin.getConfig().set("allergies.chance-per-player", 0.0);
        AllergyManager m2 = manager(none);
        BukkitKit.TestPlayer b = BukkitKit.join("Bob", none.world);
        m2.onJoin(new PlayerJoinEvent(b, ""));
        expect(none.players.known(b.getUniqueId()) && none.players.allergy(b.getUniqueId()) == null, "chance 0 gives none, and remembers that");
        expect(BukkitScheduler.LATER.isEmpty(), "no notice for lucky players");

        ManagerTest.Rig off = new ManagerTest.Rig();
        off.plugin.getConfig().set("allergies.enabled", false);
        off.plugin.getConfig().set("allergies.chance-per-player", 1.0);
        AllergyManager m3 = manager(off);
        BukkitKit.TestPlayer c = BukkitKit.join("Cy", off.world);
        m3.onJoin(new PlayerJoinEvent(c, ""));
        expect(!off.players.known(c.getUniqueId()), "allergies.enabled: false rolls nothing");
    }

    /** Finds a day on which the pollen is at least High and returns its family. */
    static AllergyType findBadDay(ManagerTest.Rig r, BukkitKit.TestPlayer p) {
        for (com.nexusuniverse.seasons.Season s : com.nexusuniverse.seasons.Season.values()) {
            for (int d = 1; d <= r.config.daysPerSeason(); d++) {
                r.clock.setSeason(s);
                r.clock.setDayOfSeason(d);
                r.world.setTime(9000);
                Reading read = r.manager.reading(p);
                if (read != null && read.pollenLevel() >= 3) {
                    return read.pollen().family();
                }
            }
        }
        return null;
    }

    static int sneezes(BukkitKit.TestPlayer p) {
        int n = 0;
        for (String m : p.messages) {
            if (m.contains("chu") || m.contains("CHOO") || m.contains("Achoo") || m.contains("ACHOO")) {
                n++;
            }
        }
        return n;
    }

    static void sneezing() {
        ManagerTest.Rig r = new ManagerTest.Rig();
        AllergyManager m = manager(r);
        Runnable allergyTick = BukkitScheduler.REPEATING.get(BukkitScheduler.REPEATING.size() - 1);
        BukkitKit.TestPlayer p = BukkitKit.join("Ann", r.world);
        AllergyType bad = findBadDay(r, p);
        expect(bad != null, "some day of the year has a High pollen count");
        if (bad == null) {
            return;
        }

        AllergyType other = bad == AllergyType.MOLD ? AllergyType.TREE : AllergyType.MOLD;
        r.players.setAllergy(p.getUniqueId(), other);
        for (int i = 0; i < 3000; i++) {
            allergyTick.run();
        }
        expect(sneezes(p) == 0, "no sneezing for a pollen you are not allergic to");

        r.players.setAllergy(p.getUniqueId(), bad);
        r.exposed = false;
        for (int i = 0; i < 3000; i++) {
            allergyTick.run();
        }
        expect(sneezes(p) == 0, "none indoors either");

        r.exposed = true;
        for (int i = 0; i < 3000; i++) {
            allergyTick.run();
        }
        expect(sneezes(p) >= 1, "outdoors, on a High day, with the right allergy: achoo");
        expect(sneezes(p) <= 1, "but the 25-second cooldown stops a sneezing fit (all of those ticks happened in one real moment)");

        ManagerTest.Rig quiet = new ManagerTest.Rig();
        quiet.plugin.getConfig().set("allergies.sneezing", false);
        manager(quiet);
        Runnable t2 = BukkitScheduler.REPEATING.get(BukkitScheduler.REPEATING.size() - 1);
        BukkitKit.TestPlayer q = BukkitKit.join("Quin", quiet.world);
        AllergyType bad2 = findBadDay(quiet, q);
        if (bad2 != null) {
            quiet.players.setAllergy(q.getUniqueId(), bad2);
            for (int i = 0; i < 3000; i++) {
                t2.run();
            }
        }
        expect(sneezes(q) == 0, "allergies.sneezing: false turns the sneezes off");
    }
}
