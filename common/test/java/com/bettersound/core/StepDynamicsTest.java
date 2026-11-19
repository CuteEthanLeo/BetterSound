package com.bettersound.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StepDynamicsTest {
    @Test
    void ownStepsGetPresenceAndSprintWeight() {
        BsConfig c = BsConfig.get();
        assertEquals(0.15f * c.ownStepVolume, StepDynamics.volume(0.15f, 1.0, false), 1e-6);
        assertEquals(0.15f * c.ownStepVolume * c.sprintStepVolume, StepDynamics.volume(0.15f, 1.0, true), 1e-6);
    }

    @Test
    void distantStepsAreUntouched() {
        assertEquals(0.15f, StepDynamics.volume(0.15f, 25.0, true), 1e-6);
    }
}
