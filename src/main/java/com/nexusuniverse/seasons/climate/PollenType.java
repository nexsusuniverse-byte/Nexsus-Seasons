package com.nexusuniverse.seasons.climate;

/** The specific thing in the air on a given day. Each belongs to one allergy family. */
public enum PollenType {
    OAK("Oak", AllergyType.TREE),
    BIRCH("Birch", AllergyType.TREE),
    MAPLE("Maple", AllergyType.TREE),
    CEDAR("Cedar", AllergyType.TREE),
    TIMOTHY("Timothy Grass", AllergyType.GRASS),
    BERMUDA("Bermuda Grass", AllergyType.GRASS),
    RAGWEED("Ragweed", AllergyType.WEED),
    MUGWORT("Mugwort", AllergyType.WEED),
    SAGEBRUSH("Sagebrush", AllergyType.WEED),
    MOLD("Mould Spores", AllergyType.MOLD);

    private final String display;
    private final AllergyType family;

    PollenType(String display, AllergyType family) {
        this.display = display;
        this.family = family;
    }

    public String display() {
        return display;
    }

    public AllergyType family() {
        return family;
    }
}
