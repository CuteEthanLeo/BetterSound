package com.bettersound.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Version-independent ambience logic. Every client tick the adapter calls {@link #tick}; loops read their
 * current (smoothed) volume through {@link #loopVolume}. All volumes are before the game's "Ambient" slider.
 */
public final class AmbientEngine {
    public static final String LOOP_FOREST = "bettersound:ambient.loop.forest_day";
    public static final String LOOP_PLAINS = "bettersound:ambient.loop.plains_day";
    public static final String LOOP_NIGHT = "bettersound:ambient.loop.night";
    public static final String LOOP_WIND = "bettersound:ambient.loop.wind";
    public static final String LOOP_RAIN = "bettersound:ambient.loop.rain";
    public static final String LOOP_RAIN_INDOOR = "bettersound:ambient.loop.rain_indoor";
    public static final String LOOP_CAVE = "bettersound:ambient.loop.cave";
    public static final String LOOP_STREAM = "bettersound:ambient.loop.stream";
    public static final String LOOP_OCEAN = "bettersound:ambient.loop.ocean";

    public static final String ONESHOT_BIRD = "bettersound:ambient.bird";
    public static final String ONESHOT_DRIP = "bettersound:ambient.drip";
    public static final String ONESHOT_OWL = "bettersound:ambient.owl";

    public static final String[] ALL_LOOPS = {LOOP_FOREST, LOOP_PLAINS, LOOP_NIGHT, LOOP_WIND, LOOP_RAIN,
            LOOP_RAIN_INDOOR, LOOP_CAVE, LOOP_STREAM, LOOP_OCEAN};

    /** Per-tick smoothing: reaches ~95% of a new target in about 3 seconds. */
    private static final float SMOOTH = 0.05f;
    private static final float START_THRESHOLD = 0.02f;
    /**
     * Minecraft has only 8 streaming channels, shared with music and jukeboxes. Never hold more than this many
     * loops at once; the loudest layers win.
     */
    private static final int MAX_LOOPS = 6;
    /** After asking the backend to start a loop, wait this long before trying again (no per-tick retry spam). */
    private static final int RETRY_TICKS = 40;

    /**
     * Combined level of all beds (root of summed squared layer volumes; every loop file is mastered to about
     * the same perceived loudness). Above this the whole bed is scaled down together, so dusk + rain + river +
     * shore don't stack into a wall of noise.
     */
    private static final float BUS_CEILING = 0.9f;
    /** Footstep focus envelope: fast attack (~0.5 s) when you start walking, slow release (~2.5 s) after. */
    private static final float FOCUS_ATTACK = 0.25f, FOCUS_RELEASE = 0.05f;

    private static final class Layer {
        float current, target;
        int retry;
        /** How far this layer dips while the player walks (0..1). Broadband noise masks steps most. */
        final float duck;
        Layer(float duck) { this.duck = duck; }
    }

    private final Map<String, Layer> layers = new LinkedHashMap<>();
    private final List<Map.Entry<String, Layer>> pending = new ArrayList<>();
    private final Random random = new Random();
    private int birdCooldown = 100, dripCooldown = 60, owlCooldown = 400;
    private float focus;
    private boolean hasLast;
    private double lastX, lastZ;

    public AmbientEngine() {
        layers.put(LOOP_FOREST, new Layer(0.35f));
        layers.put(LOOP_PLAINS, new Layer(0.4f));
        layers.put(LOOP_NIGHT, new Layer(0.45f));
        layers.put(LOOP_WIND, new Layer(0.5f));
        layers.put(LOOP_RAIN, new Layer(0.5f));
        layers.put(LOOP_RAIN_INDOOR, new Layer(0.4f));
        layers.put(LOOP_CAVE, new Layer(0.3f));
        layers.put(LOOP_STREAM, new Layer(0.55f));
        layers.put(LOOP_OCEAN, new Layer(0.55f));
    }

    /**
     * Called every client tick with the local player's position. While the player is walking on the ground the
     * beds step back (like a film mix ducking the room tone under foley), so BetterSound's footsteps read
     * clearly; standing still, the full ambience returns.
     *
     * @param grounded on the ground and able to make footsteps (not riding, flying or swimming)
     */
    public void movement(double x, double z, boolean grounded) {
        float want = 0f;
        if (hasLast && grounded) {
            double dx = x - lastX, dz = z - lastZ, d2 = dx * dx + dz * dz;
            if (d2 > 0.02 * 0.02 && d2 < 4.0) want = 1f;               // > 0.4 m/s, ignore teleports
        }
        hasLast = true;
        lastX = x;
        lastZ = z;
        focus += (want - focus) * (want > focus ? FOCUS_ATTACK : FOCUS_RELEASE);
        if (focus < 0.001f) focus = 0f;
    }

    /** 0..1 footstep focus (1 = walking). */
    public float focus() { return focus; }

    /** Forget smoothed volumes and retry timers, e.g. when the player leaves the world. */
    public void reset() {
        for (Layer l : layers.values()) {
            l.current = 0f;
            l.target = 0f;
            l.retry = 0;
        }
        focus = 0f;
        hasLast = false;
    }

    /** Current volume of a loop: smoothed environment level, dipped by footstep focus (0 if unknown). */
    public float loopVolume(String id) {
        Layer l = layers.get(id);
        if (l == null) return 0f;
        return l.current * (1f - l.duck * focus * BsConfig.get().footstepFocus);
    }

    public void tick(EnvSnapshot env, SoundBackend backend) {
        BsConfig cfg = BsConfig.get();
        computeTargets(env, cfg);
        int alive = 0;
        pending.clear();
        for (Map.Entry<String, Layer> e : layers.entrySet()) {
            Layer l = e.getValue();
            l.current += (l.target - l.current) * SMOOTH;
            if (Math.abs(l.target - l.current) < 0.001f) l.current = l.target;
            if (l.retry > 0) l.retry--;
            if (backend.isLoopAlive(e.getKey())) alive++;
            else if (l.current > START_THRESHOLD && l.retry == 0) pending.add(e);
        }
        if (!pending.isEmpty()) {
            pending.sort((a, b) -> Float.compare(b.getValue().current, a.getValue().current));
            for (Map.Entry<String, Layer> e : pending) {
                if (alive >= MAX_LOOPS) break;
                backend.startLoop(e.getKey());
                e.getValue().retry = RETRY_TICKS;
                alive++;
            }
        }
        if (env.valid && cfg.ambientEnabled && env.overworld) oneShots(env, cfg, backend);
    }

    private void set(String id, float v) { layers.get(id).target = Math.max(0f, Math.min(1f, v)); }

    private void computeTargets(EnvSnapshot env, BsConfig cfg) {
        for (Layer l : layers.values()) l.target = 0f;
        if (!env.valid || !cfg.ambientEnabled || !env.overworld) return;
        float master = cfg.ambientVolume;
        String b = env.biome;
        boolean forest = Biomes.isForest(b), open = Biomes.isOpen(b), cold = Biomes.isCold(b),
                arid = Biomes.isArid(b), ocean = Biomes.isOcean(b), river = Biomes.isRiver(b), caveBiome = Biomes.isCave(b);

        float sky = env.skyExposure;
        float depth = (float) (env.seaLevel - 6 - env.y);                       // > 0 when below sea level
        float thickRoof = clamp01((env.roofBlocks - 3) / 9f);                   // 0 under a house roof, 1 inside a mountain
        float underground = sky < 0.15f ? Math.max(clamp01(depth / 12f), thickRoof) : 0f;   // 0..1
        float surface = clamp01(sky * 1.4f) * (1f - underground);                // outdoors-ness
        float muffle = env.underwater ? 0.12f : 1f;
        float dry = 1f - env.rain;                                               // animals hide in rain
        float day = env.daylight, night = 1f - env.daylight;

        if (cfg.birds && !cold && !arid && !ocean && !caveBiome) {
            float shelter = 0.35f + 0.65f * surface;                             // still faintly audible indoors
            if (forest) set(LOOP_FOREST, 0.85f * day * dry * shelter * master * muffle);
            else if (open || river) set(LOOP_PLAINS, 0.7f * day * dry * shelter * master * muffle);
        }
        if (cfg.nightInsects && !cold && !ocean && !caveBiome) {
            float shelter = 0.3f + 0.7f * surface;
            float amt = arid ? 0.35f : (forest ? 0.75f : 0.8f);
            set(LOOP_NIGHT, amt * night * (1f - env.rain * 0.9f) * shelter * master * muffle);
        }
        if (cfg.wind) {
            float height = clamp01((float) (env.y - 90) / 70f);
            float base = 0.08f + (cold ? 0.35f : 0f) + (arid ? 0.2f : 0f) + (ocean ? 0.15f : 0f) + (open ? 0.1f : 0f)
                    + height * 0.65f + env.thunder * 0.35f + env.rain * 0.15f + (env.snowing ? 0.25f : 0f);
            if (forest) base *= 0.6f;                                            // trees break the wind
            float shelter = 0.12f + 0.88f * surface;
            set(LOOP_WIND, clamp01(base) * shelter * master * muffle);
        }
        if (cfg.rain && env.rain > 0.01f && !env.snowing) {
            float outdoor = clamp01(sky * 1.25f) * (1f - underground);
            float leaves = forest ? 1f : 0.8f + 0.2f * env.leavesNearby;
            set(LOOP_RAIN, 0.9f * env.rain * outdoor * leaves * master * muffle);
            // Under a thin roof near the surface: muffled drumming instead. Deep underground or inside a
            // mountain both factors go to zero.
            float indoor = (1f - outdoor) * (1f - underground);
            set(LOOP_RAIN_INDOOR, 0.75f * env.rain * indoor * master * muffle);
        }
        if (cfg.caves) {
            float caveAmt = Math.max(underground, caveBiome ? 0.6f * (1f - sky) : 0f);
            set(LOOP_CAVE, 0.75f * caveAmt * master * muffle);
        }
        if (cfg.water) {
            float stream = Math.max(river ? 0.45f * surface : 0f, env.waterNearby * 0.9f);
            if (ocean) stream *= 0.4f;
            set(LOOP_STREAM, stream * master * muffle);
            float nearSea = clamp01(1f - (float) Math.abs(env.y - env.seaLevel) / 20f);
            boolean beachy = ocean || b.contains("beach") || b.contains("shore");
            set(LOOP_OCEAN, beachy ? 0.85f * nearSea * surface * master * muffle : 0f);
        }
        float power = 0f;
        for (Layer l : layers.values()) power += l.target * l.target;
        float bus = (float) Math.sqrt(power), ceiling = BUS_CEILING * Math.max(1f, master);
        if (bus > ceiling) {
            float g = ceiling / bus;
            for (Layer l : layers.values()) l.target *= g;
        }
    }

    /**
     * Positional one-shots. The audible radius of a Minecraft sound is max(volume, 1) x its attenuation
     * distance (16 by default); the bird/owl entries in sounds.json declare 32 / 48 blocks so calls placed up
     * to 20 / 28 blocks away are actually heard.
     */
    private void oneShots(EnvSnapshot env, BsConfig cfg, SoundBackend backend) {
        float master = cfg.ambientVolume;
        if (master <= 0.01f || env.underwater) return;
        String b = env.biome;
        boolean forest = Biomes.isForest(b), open = Biomes.isOpen(b);

        // Individual bird calls over the bed, from the trees around the player.
        if (--birdCooldown <= 0) {
            birdCooldown = 80 + random.nextInt(220);
            if (cfg.birds && (forest || open) && env.daylight > 0.6f && env.rain < 0.2f && env.skyExposure > 0.3f) {
                double ang = random.nextDouble() * Math.PI * 2, dist = 6 + random.nextDouble() * 14;    // 6..20
                backend.playAt(ONESHOT_BIRD, Math.cos(ang) * dist, 3 + random.nextDouble() * 8, Math.sin(ang) * dist,
                        (forest ? 0.9f : 0.6f) * master, 0.92f + random.nextFloat() * 0.16f);
            }
        }
        if (--owlCooldown <= 0) {
            owlCooldown = 600 + random.nextInt(1400);
            if (cfg.birds && forest && env.daylight < 0.2f && env.rain < 0.2f && env.skyExposure > 0.3f) {
                double ang = random.nextDouble() * Math.PI * 2, dist = 12 + random.nextDouble() * 16;   // 12..28
                backend.playAt(ONESHOT_OWL, Math.cos(ang) * dist, 4 + random.nextDouble() * 6, Math.sin(ang) * dist,
                        0.8f * master, 0.95f + random.nextFloat() * 0.1f);
            }
        }
        // Water drips echoing in caves.
        if (--dripCooldown <= 0) {
            dripCooldown = 30 + random.nextInt(110);
            boolean cave = env.skyExposure < 0.1f && (env.y < env.seaLevel - 4 || env.roofBlocks >= 12 || Biomes.isCave(b));
            if (cfg.caves && cave) {
                backend.playAt(ONESHOT_DRIP, random.nextGaussian() * 6, 1 + random.nextDouble() * 4, random.nextGaussian() * 6,
                        (0.35f + random.nextFloat() * 0.45f) * master, 0.85f + random.nextFloat() * 0.3f);
            }
        }
    }

    static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
