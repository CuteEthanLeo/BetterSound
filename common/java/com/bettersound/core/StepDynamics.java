package com.bettersound.core;

/** Version-independent footstep shaping applied when the sound engine computes volume/pitch. */
public final class StepDynamics {
    private StepDynamics() {}

    public static boolean isStep(String soundPath) {
        return soundPath.endsWith(".step");
    }

    /**
     * Presence and sprint loudness for steps right at the local player. (Sneaking is not handled here: vanilla emits no
     * footstep at all while sneaking, so BetterSound plays its own quiet ones, see {@link SneakSteps}.)
     *
     * @param distSq squared distance from the local player to the sound
     */
    public static float volume(float vol, double distSq, boolean sprinting) {
        BsConfig c = BsConfig.get();
        if (!c.footstepDynamics || distSq > 4.0) return vol;
        float v = vol * c.ownStepVolume;
        if (sprinting) v *= c.sprintStepVolume;
        return v;
    }

    /** Stable per-instance jitter so a looping/tickable sound never wobbles. */
    public static float pitch(float pitch, int instanceHash, double distSq, boolean sprinting) {
        BsConfig c = BsConfig.get();
        if (!c.footstepDynamics) return pitch;
        int h = instanceHash * 0x9E3779B1;
        float r = ((h >>> 8) & 0xFFFF) / 65535f * 2f - 1f;   // -1..1
        float p = pitch * (1f + r * c.stepPitchJitter);
        if (distSq <= 4.0 && sprinting) p *= 1.03f;
        return p;
    }

    /** Volume for a BetterSound-played sneak footstep, given the block's sound-type volume. */
    public static float sneakVolume(float blockVolume) {
        return blockVolume * 0.15f * BsConfig.get().sneakStepVolume;   // 0.15 = vanilla step scale
    }
}
