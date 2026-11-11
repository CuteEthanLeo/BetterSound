package com.bettersound.core;

/** Implemented per Minecraft version: plays sounds by id ("bettersound:ambient.loop.wind"). */
public interface SoundBackend {
    /** Start (or keep) a non-positional looping ambience layer. Volume is pulled each tick via {@link AmbientEngine#loopVolume}. */
    void startLoop(String soundId);
    /** True while a loop started by startLoop is still alive in the sound engine. */
    boolean isLoopAlive(String soundId);
    /** Play a one-shot at listener position + offset. */
    void playAt(String soundId, double dx, double dy, double dz, float volume, float pitch);
}
