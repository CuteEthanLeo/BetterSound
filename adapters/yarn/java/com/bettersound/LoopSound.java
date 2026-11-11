package com.bettersound;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/** Non-positional looping ambience layer whose volume follows the AmbientEngine. */
public class LoopSound extends MovingSoundInstance {
    private final String soundId;
    private int silentTicks;

    public LoopSound(String soundId) {
        super(BetterSoundClient.event(soundId), SoundCategory.AMBIENT, Random.create());
        this.soundId = soundId;
        this.repeat = true;
        this.repeatDelay = 0;
        this.relative = true;
        this.attenuationType = SoundInstance.AttenuationType.NONE;
        this.volume = Math.max(0.03f, BetterSoundClient.ENGINE.loopVolume(soundId));
    }

    @Override
    public boolean shouldAlwaysPlay() { return true; }

    @Override
    public void tick() {
        float v = BetterSoundClient.ENGINE.loopVolume(soundId);
        this.volume = Math.max(v, 0.0001f);
        if (v <= 0.002f) {
            if (++silentTicks > 40) setDone();
        } else {
            silentTicks = 0;
        }
    }
}
