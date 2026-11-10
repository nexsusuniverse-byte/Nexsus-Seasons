package com.nexusuniverse.seasons.climate;

import org.bukkit.Bukkit;

import java.util.List;

import static com.nexusuniverse.seasons.climate.Kit.expect;

/** /forecast and /allergy. */
public class CommandTest {

    public static void main(String[] args) {
        forecast();
        allergy();
        completion();
        Kit.finish();
    }

    static ClimateCommand cmd(ManagerTest.Rig r) {
        return new ClimateCommand(r.manager, r.players, r.settings);
    }

    static void forecast() {
        ManagerTest.Rig r = new ManagerTest.Rig();
        ClimateCommand c = cmd(r);
        BukkitKit.TestPlayer p = BukkitKit.join("Ann", r.world);
        r.world.setTime(3000);
        expect(c.onCommand(p, BukkitKit.command("forecast"), "forecast", new String[0]), "/forecast works");
        expect(p.text().contains("High") && p.text().contains("Low"), "it shows the report any time of day");
        expect(p.text().contains("Humidity") && p.text().toLowerCase().contains("pollen"), "with humidity and pollen");

        p.messages.clear();
        c.onCommand(p, BukkitKit.command("forecast"), "forecast", new String[]{"mute"});
        expect(r.players.muted(p.getUniqueId()), "/forecast mute mutes");
        c.onCommand(p, BukkitKit.command("forecast"), "forecast", new String[]{"unmute"});
        expect(!r.players.muted(p.getUniqueId()), "/forecast unmute undoes it");

        p.messages.clear();
        c.onCommand(p, BukkitKit.command("forecast"), "forecast", new String[]{"broadcast"});
        expect(p.text().contains("No permission"), "players cannot broadcast");
        c.onCommand(p, BukkitKit.command("forecast"), "forecast", new String[]{"rebuild"});
        expect(p.text().contains("No permission") && !p.text().contains("rebuilt"), "or rebuild");

        p.perms.add("nexusseasons.admin");
        BukkitKit.TestPlayer other = BukkitKit.join("Bob", r.world);
        p.messages.clear();
        c.onCommand(p, BukkitKit.command("forecast"), "forecast", new String[]{"broadcast"});
        expect(!other.messages.isEmpty(), "an admin's broadcast reaches everyone");
        p.messages.clear();
        c.onCommand(p, BukkitKit.command("forecast"), "forecast", new String[]{"rebuild"});
        expect(p.text().contains("rebuilt"), "an admin can rebuild");

        p.messages.clear();
        c.onCommand(p, BukkitKit.command("forecast"), "forecast", new String[]{"wibble"});
        expect(p.text().contains("Usage"), "unknown subcommand shows usage");

        Bukkit.WORLDS.clear();
        p.messages.clear();
        ManagerTest.Rig empty = new ManagerTest.Rig();
        Bukkit.WORLDS.clear();
        BukkitKit.TestPlayer q = new BukkitKit.TestPlayer("Quin");
        cmd(empty).onCommand(q, BukkitKit.command("forecast"), "forecast", new String[0]);
        expect(q.text().contains("No forecast"), "with no overworld loaded it says so instead of crashing");
    }

    static void allergy() {
        ManagerTest.Rig r = new ManagerTest.Rig();
        ClimateCommand c = cmd(r);
        BukkitKit.TestPlayer p = BukkitKit.join("Ann", r.world);
        r.world.setTime(3000);

        c.onCommand(p, BukkitKit.command("allergy"), "allergy", new String[0]);
        expect(p.text().contains("none"), "no allergy yet: " + p.text());
        expect(p.text().contains("main pollen"), "and it names today's main pollen");

        p.messages.clear();
        c.onCommand(p, BukkitKit.command("allergy"), "allergy", new String[]{"set", "grass"});
        expect(r.players.allergy(p.getUniqueId()) == AllergyType.GRASS, "/allergy set grass");
        c.onCommand(p, BukkitKit.command("allergy"), "allergy", new String[]{"set", "none"});
        expect(r.players.known(p.getUniqueId()) && r.players.allergy(p.getUniqueId()) == null, "/allergy set none");
        p.messages.clear();
        c.onCommand(p, BukkitKit.command("allergy"), "allergy", new String[]{"set", "cheese"});
        expect(p.text().contains("Unknown type"), "rejects nonsense");
        p.messages.clear();
        c.onCommand(p, BukkitKit.command("allergy"), "allergy", new String[]{"banana"});
        expect(p.text().contains("Usage"), "usage on a bad subcommand");

        r.plugin.getConfig().set("allergies.allow-self-set", false);
        p.messages.clear();
        c.onCommand(p, BukkitKit.command("allergy"), "allergy", new String[]{"set", "tree"});
        expect(p.text().contains("not allowed") && r.players.allergy(p.getUniqueId()) == null, "self-set can be forbidden");
        p.perms.add("nexusseasons.admin");
        c.onCommand(p, BukkitKit.command("allergy"), "allergy", new String[]{"set", "tree"});
        expect(r.players.allergy(p.getUniqueId()) == AllergyType.TREE, "but admins may");

        r.plugin.getConfig().set("allergies.enabled", false);
        p.messages.clear();
        c.onCommand(p, BukkitKit.command("allergy"), "allergy", new String[0]);
        expect(p.text().contains("switched off"), "disabled allergies say so");
    }

    static void completion() {
        ManagerTest.Rig r = new ManagerTest.Rig();
        ClimateCommand c = cmd(r);
        BukkitKit.TestPlayer p = BukkitKit.join("Ann", r.world);
        List<String> f = c.onTabComplete(p, BukkitKit.command("forecast"), "forecast", new String[]{""});
        expect(f.contains("mute") && f.contains("unmute"), "forecast completes mute/unmute: " + f);
        expect(!f.contains("broadcast"), "but does not offer admin commands to players");
        p.perms.add("nexusseasons.admin");
        f = c.onTabComplete(p, BukkitKit.command("forecast"), "forecast", new String[]{""});
        expect(f.contains("broadcast") && f.contains("rebuild"), "admins see them: " + f);
        List<String> a = c.onTabComplete(p, BukkitKit.command("allergy"), "allergy", new String[]{"set", ""});
        expect(a.contains("tree") && a.contains("grass") && a.contains("weed") && a.contains("mold") && a.contains("none"), "allergy completes the types: " + a);
        f = c.onTabComplete(p, BukkitKit.command("forecast"), "forecast", new String[]{"mu"});
        expect(f.size() == 1 && f.stream().allMatch(s -> s.startsWith("mu")), "prefix filtering: " + f);
    }
}
