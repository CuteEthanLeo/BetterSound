package com.bettersound.mixin;

import com.bettersound.core.StepDynamics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sprint footstep loudness and subtle per-step pitch variation (sneak steps are played by BetterSoundClient). */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void bettersound$stepVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!StepDynamics.isStep(sound.getIdentifier().getPath())) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        double d = p.distanceToSqr(sound.getX(), sound.getY(), sound.getZ());
        cir.setReturnValue(StepDynamics.volume(cir.getReturnValueF(), d, p.isSprinting()));
    }

    @Inject(method = "calculatePitch(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void bettersound$stepPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!StepDynamics.isStep(sound.getIdentifier().getPath())) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        double d = p.distanceToSqr(sound.getX(), sound.getY(), sound.getZ());
        cir.setReturnValue(StepDynamics.pitch(cir.getReturnValueF(), System.identityHashCode(sound), d, p.isSprinting()));
    }
}
