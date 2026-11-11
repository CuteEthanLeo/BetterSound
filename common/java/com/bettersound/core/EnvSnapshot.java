package com.bettersound.core;

/** Version-independent snapshot of the listener's surroundings, filled by the per-version adapter. */
public final class EnvSnapshot {
    public boolean valid;          // in a world, not paused, player alive
    public boolean overworld;
    public String biome = "";      // registry path, e.g. "birch_forest"
    public double y;
    public int seaLevel = 63;
    /** 0..1 rain strength at the player (0 if the biome has no rain, e.g. desert or snowy). */
    public float rain;
    public boolean snowing;
    public float thunder;
    /** 0..1, 1 = full day, 0 = full night (smooth around dusk/dawn). */
    public float daylight;
    /** 0..1 fraction of sampled nearby columns with open sky (1 = outdoors, 0 = enclosed). */
    public float skyExposure;
    public boolean underwater;
    /** 0..1 amount of water near the player (flowing water weighs more). */
    public float waterNearby;
    /** 0..1 amount of leaves near/above the player. */
    public float leavesNearby;
    /** Non-air, non-leaf blocks in the 32-block column straight above the head (a house roof is 1-3, a mountain 12+). */
    public int roofBlocks;

    /** Maps world time (ticks, 0 = sunrise) to 0..1 daylight with ~100 s dusk/dawn ramps. */
    public static float daylight(long timeOfDay) {
        float t = (float) Math.floorMod(timeOfDay, 24000L);
        if (t < 11500f) return 1f;                              // day
        if (t < 13500f) return 1f - (t - 11500f) / 2000f;       // dusk
        if (t < 22000f) return 0f;                              // night
        return Math.min(1f, (t - 22000f) / 1500f);              // dawn
    }
}
