package com.bettersound.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvSnapshotTest {
    @Test
    void daylightCurve() {
        assertEquals(1f, EnvSnapshot.daylight(0), 1e-6);
        assertEquals(1f, EnvSnapshot.daylight(6000), 1e-6);
        assertEquals(1f, EnvSnapshot.daylight(11499), 1e-6);
        assertEquals(0.5f, EnvSnapshot.daylight(12500), 1e-6);   // mid dusk
        assertEquals(0f, EnvSnapshot.daylight(13500), 1e-6);
        assertEquals(0f, EnvSnapshot.daylight(18000), 1e-6);     // midnight
        assertEquals(0.5f, EnvSnapshot.daylight(22750), 1e-6);   // mid dawn
        assertEquals(1f, EnvSnapshot.daylight(23500), 1e-6);
    }

    @Test
    void daylightWrapsAcrossDays() {
        assertEquals(EnvSnapshot.daylight(12500), EnvSnapshot.daylight(24000L * 37 + 12500), 1e-6);
        assertEquals(1f, EnvSnapshot.daylight(-100), 1e-6);      // negative time still maps into a day
    }
}
