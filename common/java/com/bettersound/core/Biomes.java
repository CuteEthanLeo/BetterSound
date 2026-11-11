package com.bettersound.core;

/** Classifies biomes by registry path so it works across versions (and roughly for modded biomes). */
public final class Biomes {
    private Biomes() {}

    private static boolean any(String b, String... keys) {
        for (String k : keys) if (b.contains(k)) return true;
        return false;
    }

    public static boolean isCave(String b) { return any(b, "cave", "deep_dark"); }
    public static boolean isOcean(String b) { return b.contains("ocean"); }
    public static boolean isRiver(String b) { return b.contains("river"); }
    public static boolean isCold(String b) {
        // "grove" would also match mangrove_swamp, "peaks" the warm stony_peaks, "cherry" is pink not snowy.
        if (any(b, "mangrove", "stony_peaks", "cherry")) return false;
        return any(b, "snowy", "frozen", "ice", "peaks", "grove", "slopes");
    }
    public static boolean isArid(String b) { return any(b, "desert", "badlands", "savanna_plateau", "wooded_badlands"); }
    public static boolean isForest(String b) {
        if (isCold(b) && !b.contains("taiga")) return false;
        return any(b, "forest", "taiga", "jungle", "swamp", "woodland", "cherry", "grove", "bamboo", "birch", "pale_garden");
    }
    public static boolean isOpen(String b) {
        return !isForest(b) && any(b, "plains", "meadow", "savanna", "field", "hills", "windswept", "sparse");
    }
}
