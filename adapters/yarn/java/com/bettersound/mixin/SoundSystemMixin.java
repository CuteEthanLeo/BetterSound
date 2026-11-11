package com.bettersound.mixin;

import com.bettersound.core.StepDynamics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sprint footstep loudness and subtle per-step pitch variation (sneak steps are played by BetterSoundClient). */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void bettersound$stepVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!StepDynamics.isStep(sound.getId().getPath())) return;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        double d = p.squaredDistanceTo(sound.getX(), sound.getY(), sound.getZ());
        cir.setReturnValue(StepDynamics.volume(cir.getReturnValueF(), d, p.isSprinting()));
    }

    @Inject(method = "getAdjustedPitch(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void bettersound$stepPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!StepDynamics.isStep(sound.getId().getPath())) return;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        double d = p.squaredDistanceTo(sound.getX(), sound.getY(), sound.getZ());
        cir.setReturnValue(StepDynamics.pitch(cir.getReturnValueF(), System.identityHashCode(sound), d, p.isSprinting()));
    }
}
