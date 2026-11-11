package com.bettersound.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SneakStepsTest {
    /** Moves the player along x in equal increments and counts steps fired. */
    private static int walk(SneakSteps s, double blocks, int ticks, boolean sneaking) {
        int steps = 0;
        for (int i = 1; i <= ticks; i++) {
            if (s.tick(sneaking, blocks * i / ticks, 0)) steps++;
        }
        return steps;
    }

    @Test
    void firstSneakTickOnlyArmsTheTracker() {
        SneakSteps s = new SneakSteps();
        assertFalse(s.tick(true, 100, 100));
        assertFalse(s.tick(true, 100, 100));   // standing still
    }

    @Test
    void vanillaCadenceOneStepPerOnePointSixSevenBlocks() {
        SneakSteps s = new SneakSteps();
        s.tick(true, 0, 0);
        // 10.5 blocks of sneaking -> 10.5 * 0.6 = 6.3 units -> 6 steps (vanilla: one step per 1/0.6 blocks).
        assertEquals(6, walk(s, 10.5, 210, true));
    }

    @Test
    void noStepsWhileNotSneakingAndCounterResets() {
        SneakSteps s = new SneakSteps();
        s.tick(true, 0, 0);
        walk(s, 1.5, 10, true);                 // 0.9 units accumulated, no step yet
        assertEquals(0, walk(s, 50, 50, false)); // walking normally: never fires
        s.tick(true, 0, 0);                     // re-arm
        // 1.5 more blocks sneaking = 0.9 units; a stale 0.9 would have fired, a reset one must not.
        assertEquals(0, walk(s, 1.5, 10, true));
    }

    @Test
    void teleportDistanceStillCountsOnlyAsMovement() {
        SneakSteps s = new SneakSteps();
        s.tick(true, 0, 0);
        // one huge jump fires at most one step per tick
        assertEquals(1, walk(s, 100, 1, true));
    }
}
