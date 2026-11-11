package com.bettersound.core;

/**
 * Vanilla plays no footstep while a player sneaks on the ground. This tracks the local player's horizontal
 * movement while sneaking and says when BetterSound should play a quiet step of its own, using vanilla's
 * cadence (distance x 0.6 accumulates, one step per unit).
 */
public final class SneakSteps {
    private boolean active;
    private double lastX, lastZ;
    private float dist;

    /**
     * @param sneakingOnGround true while the player is sneaking, on the ground, not riding/flying/swimming
     * @return true when a sneak footstep should be played this tick
     */
    public boolean tick(boolean sneakingOnGround, double x, double z) {
        if (!sneakingOnGround || !BsConfig.get().footstepDynamics) {
            active = false;
            dist = 0f;
            return false;
        }
        if (!active) {
            active = true;
            lastX = x;
            lastZ = z;
            dist = 0f;
            return false;
        }
        double dx = x - lastX, dz = z - lastZ;
        lastX = x;
        lastZ = z;
        dist += (float) (Math.sqrt(dx * dx + dz * dz) * 0.6);
        if (dist >= 1f) {
            dist -= 1f;
            return true;
        }
        return false;
    }
}
