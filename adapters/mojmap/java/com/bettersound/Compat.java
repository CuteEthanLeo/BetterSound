package com.bettersound;

import net.minecraft.client.multiplayer.ClientLevel;

/** Small spots where 26.x point releases differ. */
final class Compat {
    private Compat() {}

    /** Overworld time of day in ticks (26.x replaced getDayTime() with world clocks). */
    static long dayTime(ClientLevel w) {
        return w.getOverworldClockTime();
    }
}
