package com.bettersound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Non-positional looping ambience layer whose volume follows the AmbientEngine. */
public class LoopSound extends AbstractTickableSoundInstance {
    private final String soundId;
    private int silentTicks;

    public LoopSound(String soundId) {
        super(BetterSoundClient.event(soundId), SoundSource.AMBIENT, RandomSource.create());
        this.soundId = soundId;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = Math.max(0.03f, BetterSoundClient.ENGINE.loopVolume(soundId));
    }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        float v = BetterSoundClient.ENGINE.loopVolume(soundId);
        this.volume = Math.max(v, 0.0001f);
        if (v <= 0.002f) {
            if (++silentTicks > 40) stop();
        } else {
            silentTicks = 0;
        }
    }
}
