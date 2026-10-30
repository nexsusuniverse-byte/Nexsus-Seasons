package com.nexusuniverse.seasons.climate;

import static com.nexusuniverse.seasons.climate.Kit.expect;

/** The ClimateApi other plugins read: finite numbers in the overworld, NaN elsewhere, honest precipitation. */
public class ApiTest {

    public static void main(String[] args) {
        ManagerTest.Rig r = new ManagerTest.Rig();
        BukkitKit.TestPlayer p = BukkitKit.join("Ann", r.world);
        ClimateApi api = r.manager;
        r.world.setTime(9000);
        for (int i = 0; i < 100; i++) {
            r.tickOnce();
        }
        expect(!Double.isNaN(api.temperatureF(p)) && !Double.isNaN(api.feelsLikeF(p)), "temperature and feels-like are numbers");
        expect(api.windMph(p) >= 0 && api.uvIndex(p) >= 0, "wind and UV are not negative");
        expect(!api.sheltered(p), "outdoors by default");
        expect(api.precipitation(p).equals("NONE"), "dry day: no precipitation");

        r.world.setStorm(true);
        expect(api.precipitation(p).equals("RAIN") || api.precipitation(p).equals("SNOW"), "storm: rain or snow");
        r.world.setThundering(true);
        String t = api.precipitation(p);
        expect(t.equals("STORM") || t.equals("SNOW"), "thunder: a storm (or snow) " + t);
        r.exposed = false;
        expect(api.sheltered(p) && api.precipitation(p).equals("NONE"), "under a roof nothing falls on you");
        r.exposed = true;

        org.bukkit.World nether = BukkitKit.nether("world_nether");
        BukkitKit.TestPlayer n = BukkitKit.join("Nina", nether);
        expect(Double.isNaN(api.temperatureF(n)) && api.precipitation(n).equals("NONE") && !api.sheltered(n),
                "the Nether is not ours to judge: NaN and NONE");

        ManagerTest.Rig cold = new ManagerTest.Rig();
        cold.plugin.getConfig().set("climate.annual-mean-f", -20.0);
        BukkitKit.TestPlayer c = BukkitKit.join("Cy", cold.world);
        cold.world.setTime(9000);
        cold.world.setStorm(true);
        for (int i = 0; i < 200; i++) {
            cold.tickOnce();
        }
        expect(cold.manager.precipitation(c).equals("SNOW"), "below the snow line it snows: " + cold.manager.precipitation(c)
                + " at " + cold.manager.temperatureF(c));

        expect(!api.bodyHeatHandledElsewhere(), "summer heat exhaustion is ours by default");
        api.setBodyHeatHandledElsewhere(true);
        expect(api.bodyHeatHandledElsewhere(), "until a body-temperature plugin claims it");
        Kit.finish();
    }
}
