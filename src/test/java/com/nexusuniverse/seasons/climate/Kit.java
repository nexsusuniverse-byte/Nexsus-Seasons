package com.nexusuniverse.seasons.climate;

/** Shared test helpers. */
final class Kit {

    static int checks;
    static int failures;

    private Kit() {
    }

    static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    static void near(double actual, double wanted, double tolerance, String what) {
        expect(Math.abs(actual - wanted) <= tolerance, what + " (got " + actual + ", wanted " + wanted + ")");
    }

    static void finish() {
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            System.exit(1);
        }
    }

    static String strip(String s) {
        return s.replaceAll("§.", "");
    }
}
