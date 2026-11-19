package com.bettersound.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbientEngineTest {
    /** Records calls; loops become alive after start unless {@link #refuse} is set (no free channel). */
    static final class FakeBackend implements SoundBackend {
        final Map<String, Integer> starts = new HashMap<>();
        final List<String> oneShots = new ArrayList<>();
        final Map<String, Boolean> alive = new HashMap<>();
        boolean refuse;

        @Override public void startLoop(String id) {
            starts.merge(id, 1, Integer::sum);
            if (!refuse) alive.put(id, true);
        }
        @Override public boolean isLoopAlive(String id) { return alive.getOrDefault(id, false); }
        @Override public void playAt(String id, double dx, double dy, double dz, float v, float p) { oneShots.add(id); }
        int aliveCount() { return (int) alive.values().stream().filter(b -> b).count(); }
    }

    private static EnvSnapshot outdoors(String biome) {
        EnvSnapshot e = new EnvSnapshot();
        e.valid = true;
        e.overworld = true;
        e.biome = biome;
        e.y = 70;
        e.seaLevel = 63;
        e.daylight = 1f;
        e.skyExposure = 1f;
        return e;
    }

    private static void run(AmbientEngine eng, EnvSnapshot env, SoundBackend b, int ticks) {
        for (int i = 0; i < ticks; i++) eng.tick(env, b);
    }

    @Test
    void forestDayStartsBirdBedAndWind() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        run(eng, outdoors("birch_forest"), b, 100);
        assertTrue(b.isLoopAlive(AmbientEngine.LOOP_FOREST));
        assertTrue(b.isLoopAlive(AmbientEngine.LOOP_WIND));
        assertTrue(eng.loopVolume(AmbientEngine.LOOP_FOREST) > 0.5f);
        assertEquals(0f, eng.loopVolume(AmbientEngine.LOOP_NIGHT), 1e-6);
        assertEquals(0f, eng.loopVolume(AmbientEngine.LOOP_CAVE), 1e-6);
    }

    @Test
    void mangroveSwampGetsBirdsNotColdWind() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        run(eng, outdoors("mangrove_swamp"), b, 100);
        assertTrue(b.isLoopAlive(AmbientEngine.LOOP_FOREST));
        assertTrue(eng.loopVolume(AmbientEngine.LOOP_WIND) < 0.2f);
    }

    @Test
    void noOneShotsOutsideTheOverworld() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        EnvSnapshot nether = outdoors("crimson_forest");
        nether.overworld = false;
        nether.skyExposure = 0f;
        nether.seaLevel = 32;
        nether.y = 15;
        run(eng, nether, b, 4000);
        assertTrue(b.oneShots.isEmpty());
        assertTrue(b.starts.isEmpty());
    }

    @Test
    void birdCallsHappenInADaytimeForest() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        run(eng, outdoors("forest"), b, 4000);
        assertTrue(b.oneShots.contains(AmbientEngine.ONESHOT_BIRD));
    }

    @Test
    void startIsRetriedAtMostEveryFortyTicks() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        b.refuse = true;
        run(eng, outdoors("forest"), b, 400);
        for (int n : b.starts.values()) assertTrue(n <= 400 / 40 + 1, "retry spam: " + n);
        assertTrue(b.starts.containsKey(AmbientEngine.LOOP_FOREST));
    }

    @Test
    void thickRoofCountsAsUndergroundEvenAboveSeaLevel() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        EnvSnapshot cave = outdoors("windswept_hills");
        cave.y = 120;                 // high mountain cave
        cave.skyExposure = 0f;
        cave.roofBlocks = 20;
        cave.rain = 1f;
        run(eng, cave, b, 200);
        assertTrue(eng.loopVolume(AmbientEngine.LOOP_CAVE) > 0.5f);
        assertEquals(0f, eng.loopVolume(AmbientEngine.LOOP_RAIN), 1e-6);
        assertEquals(0f, eng.loopVolume(AmbientEngine.LOOP_RAIN_INDOOR), 1e-6);
    }

    @Test
    void thinRoofInRainGivesIndoorDrumming() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        EnvSnapshot house = outdoors("plains");
        house.skyExposure = 0f;
        house.roofBlocks = 1;
        house.rain = 1f;
        run(eng, house, b, 200);
        assertTrue(eng.loopVolume(AmbientEngine.LOOP_RAIN_INDOOR) > 0.5f);
        assertEquals(0f, eng.loopVolume(AmbientEngine.LOOP_RAIN), 1e-6);
        assertEquals(0f, eng.loopVolume(AmbientEngine.LOOP_CAVE), 1e-6);
    }

    @Test
    void neverMoreThanSixLoopsAlive() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        // Dusk, light rain, half sheltered, next to water on a wooded shore: 7 layers want to play
        // (forest birds, night, wind, rain, indoor rain, stream, ocean) but only 6 may.
        EnvSnapshot e = outdoors("forest_beach");
        e.daylight = 0.5f;
        e.rain = 0.5f;
        e.skyExposure = 0.5f;
        e.waterNearby = 1f;
        e.y = 63;
        run(eng, e, b, 200);
        int wanting = 0;
        for (String id : AmbientEngine.ALL_LOOPS) if (eng.loopVolume(id) > 0.02f) wanting++;
        assertEquals(7, wanting, "test scenario should make 7 layers audible");
        assertEquals(6, b.aliveCount(), "alive loops");
        assertTrue(b.isLoopAlive(AmbientEngine.LOOP_STREAM));   // loudest layer must have won a slot
    }

    @Test
    void resetClearsVolumes() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        run(eng, outdoors("forest"), b, 100);
        eng.reset();
        assertEquals(0f, eng.loopVolume(AmbientEngine.LOOP_FOREST), 1e-6);
        assertEquals(0f, eng.loopVolume(AmbientEngine.LOOP_WIND), 1e-6);
    }

    @Test
    void walkingPullsNoisyBedsBackAndStandingStillRestoresThem() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        EnvSnapshot river = outdoors("river");
        river.waterNearby = 1f;
        for (int i = 0; i < 200; i++) { eng.movement(0, 0, true); eng.tick(river, b); }
        float still = eng.loopVolume(AmbientEngine.LOOP_STREAM);
        assertTrue(still > 0.5f, "stream audible while standing: " + still);
        double x = 0;
        for (int i = 0; i < 20; i++) { x += 0.2; eng.movement(x, 0, true); eng.tick(river, b); }   // walking
        float walking = eng.loopVolume(AmbientEngine.LOOP_STREAM);
        assertTrue(walking < still * 0.55f, "stream should dip under footsteps: " + walking + " vs " + still);
        for (int i = 0; i < 200; i++) { eng.movement(x, 0, true); eng.tick(river, b); }            // stopped
        assertEquals(still, eng.loopVolume(AmbientEngine.LOOP_STREAM), 0.01f);
    }

    @Test
    void noFocusWhileFlyingOrAfterATeleport() {
        AmbientEngine eng = new AmbientEngine();
        double x = 0;
        for (int i = 0; i < 40; i++) { x += 0.5; eng.movement(x, 0, false); }    // flying/riding: no steps
        assertEquals(0f, eng.focus(), 1e-6);
        eng.movement(5000, 0, true);                                             // teleport
        eng.movement(5000, 0, true);
        assertEquals(0f, eng.focus(), 1e-6);
    }

    @Test
    void stackedLayersStayUnderTheBusCeiling() {
        AmbientEngine eng = new AmbientEngine();
        FakeBackend b = new FakeBackend();
        EnvSnapshot e = outdoors("forest_beach");     // birds + wind + stream + ocean at once
        e.waterNearby = 1f;
        e.y = 63;
        run(eng, e, b, 300);
        double power = 0;
        int layers = 0;
        for (String id : AmbientEngine.ALL_LOOPS) {
            float v = eng.loopVolume(id);
            power += v * v;
            if (v > 0.02f) layers++;
        }
        assertTrue(layers >= 3, "scenario should stack several layers: " + layers);
        assertTrue(Math.sqrt(power) <= 0.9 + 1e-3, "bus " + Math.sqrt(power));
    }
}
