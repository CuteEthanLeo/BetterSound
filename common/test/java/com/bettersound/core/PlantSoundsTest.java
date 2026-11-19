package com.bettersound.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantSoundsTest {
    @Test
    void grassBreakAndPlaceAreRouted() {
        assertEquals(PlantSounds.BREAK, PlantSounds.replacement("minecraft", "block.grass.break"));
        assertEquals(PlantSounds.PLACE, PlantSounds.replacement("minecraft", "block.grass.place"));
    }

    @Test
    void everythingElseIsLeftAlone() {
        assertNull(PlantSounds.replacement("minecraft", "block.grass.step"));
        assertNull(PlantSounds.replacement("minecraft", "block.grass.hit"));
        assertNull(PlantSounds.replacement("minecraft", "block.stone.break"));
        assertNull(PlantSounds.replacement("othermod", "block.grass.break"));
        assertNull(PlantSounds.replacement("bettersound", "block.plant.break"));   // already routed: no loop
    }
}
