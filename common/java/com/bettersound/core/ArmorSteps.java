package com.bettersound.core;

/** Shared sound events and mix level for the local player's armor layer. */
public final class ArmorSteps {
    public static final String LEATHER = "bettersound:armor.foley.leather";
    public static final String HARD = "bettersound:armor.foley.hard";

    private ArmorSteps() {}

    public static float volume(boolean sneaking, boolean sprinting) {
        float base = sneaking ? 0.05f * BsConfig.get().sneakStepVolume : sprinting ? 0.06f : 0.05f;
        return base * BsConfig.get().armorStepVolume;
    }
}
