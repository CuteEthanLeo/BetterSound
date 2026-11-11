package com.bettersound.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomesTest {
    @Test
    void mangroveSwampIsAWarmForest() {
        assertFalse(Biomes.isCold("mangrove_swamp"));
        assertTrue(Biomes.isForest("mangrove_swamp"));
    }

    @Test
    void groveIsColdAndNotAForest() {
        assertTrue(Biomes.isCold("grove"));
        assertFalse(Biomes.isForest("grove"));
    }

    @Test
    void stonyPeaksAndCherryGroveAreNotCold() {
        assertFalse(Biomes.isCold("stony_peaks"));
        assertFalse(Biomes.isCold("cherry_grove"));
        assertTrue(Biomes.isForest("cherry_grove"));
    }

    @Test
    void snowyBiomesAreCold() {
        assertTrue(Biomes.isCold("snowy_plains"));
        assertTrue(Biomes.isCold("frozen_ocean"));
        assertTrue(Biomes.isCold("ice_spikes"));
        assertTrue(Biomes.isCold("jagged_peaks"));
        assertTrue(Biomes.isCold("snowy_taiga"));
        assertTrue(Biomes.isForest("snowy_taiga"));
    }

    @Test
    void openVersusForest() {
        assertTrue(Biomes.isOpen("plains"));
        assertTrue(Biomes.isOpen("windswept_hills"));
        assertTrue(Biomes.isForest("windswept_forest"));
        assertFalse(Biomes.isOpen("windswept_forest"));
        assertTrue(Biomes.isForest("sparse_jungle"));
        assertFalse(Biomes.isOpen("sparse_jungle"));
        assertTrue(Biomes.isForest("dark_forest"));
        assertTrue(Biomes.isForest("pale_garden"));
    }

    @Test
    void waterAndCaveBiomes() {
        assertTrue(Biomes.isOcean("deep_lukewarm_ocean"));
        assertTrue(Biomes.isRiver("frozen_river"));
        assertTrue(Biomes.isCave("lush_caves"));
        assertTrue(Biomes.isCave("deep_dark"));
        assertTrue(Biomes.isArid("desert"));
        assertTrue(Biomes.isArid("eroded_badlands"));
    }
}
