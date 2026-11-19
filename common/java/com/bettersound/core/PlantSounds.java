package com.bettersound.core;

/**
 * Vanilla gives the grass block and every flower, fern, sapling and tuft of grass the same GRASS sound type, so
 * picking a poppy sounds like tearing up turf. BetterSound keeps the turf crunch for solid blocks and routes
 * non-solid plants (no collision box) to a lighter stem-snap + leaf-swish set. The adapters call
 * {@link #replacement} when a sound instance resolves and check the block at the sound's position.
 */
public final class PlantSounds {
    public static final String BREAK = "bettersound:block.plant.break";
    public static final String PLACE = "bettersound:block.plant.place";

    private PlantSounds() {}

    /** The plant event to use instead of a vanilla event, or null if the event is not a grass break/place. */
    public static String replacement(String namespace, String path) {
        if (!"minecraft".equals(namespace) || !BsConfig.get().plantSounds) return null;
        switch (path) {
            case "block.grass.break": return BREAK;
            case "block.grass.place": return PLACE;
            default: return null;
        }
    }
}
