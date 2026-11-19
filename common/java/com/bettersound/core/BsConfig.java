package com.bettersound.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** User config at config/bettersound.json. Missing keys fall back to defaults; the file is rewritten with all keys. */
public final class BsConfig {
    /** Master multiplier for all BetterSound ambience layers (0..2). */
    public float ambientVolume = 1.0f;
    public boolean ambientEnabled = true;
    public boolean birds = true;
    public boolean nightInsects = true;
    public boolean wind = true;
    public boolean rain = true;
    public boolean caves = true;
    public boolean water = true;
    /** Sneak/sprint footstep dynamics and per-step pitch variation. */
    public boolean footstepDynamics = true;
    public float sneakStepVolume = 0.45f;
    public float sprintStepVolume = 1.15f;
    /** Max random pitch deviation per footstep (0.04 = +-4%). */
    public float stepPitchJitter = 0.04f;
    /** Loudness of your own footsteps (within 2 blocks of you) relative to vanilla's step level. */
    public float ownStepVolume = 1.5f;
    /** 0..1: how far the ambience beds step back while you walk so your footsteps come through (0 = off). */
    public float footstepFocus = 1.0f;
    /** Volume multiplier for the local player's armor footsteps (0 = off). */
    public float armorStepVolume = 1.0f;
    /** Flowers, grass, ferns, saplings etc. get a light stem-snap/leaf sound instead of the turf crunch. */
    public boolean plantSounds = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BsConfig instance = new BsConfig();

    public static BsConfig get() { return instance; }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("bettersound.json");
        BsConfig cfg = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, BsConfig.class);
            } catch (Exception e) {
                BetterSoundCore.LOG.warn("[BetterSound] Could not read {}, using defaults: {}", path, e.toString());
            }
        }
        if (cfg == null) cfg = new BsConfig();
        cfg.ambientVolume = clamp(cfg.ambientVolume, 0f, 2f);
        cfg.sneakStepVolume = clamp(cfg.sneakStepVolume, 0f, 2f);
        cfg.sprintStepVolume = clamp(cfg.sprintStepVolume, 0f, 2f);
        cfg.stepPitchJitter = clamp(cfg.stepPitchJitter, 0f, 0.2f);
        cfg.ownStepVolume = clamp(cfg.ownStepVolume, 0f, 3f);
        cfg.footstepFocus = clamp(cfg.footstepFocus, 0f, 1f);
        cfg.armorStepVolume = Float.isFinite(cfg.armorStepVolume) ? clamp(cfg.armorStepVolume, 0f, 2f) : 1f;
        instance = cfg;
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { GSON.toJson(cfg, w); }
        } catch (Exception e) {
            BetterSoundCore.LOG.warn("[BetterSound] Could not write {}: {}", path, e.toString());
        }
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
