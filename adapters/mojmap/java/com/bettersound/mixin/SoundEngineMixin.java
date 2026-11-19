package com.bettersound.mixin;

import com.bettersound.core.StepDynamics;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sprint footstep loudness and subtle per-step pitch variation (sneak steps are played by BetterSoundClient). */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @WrapOperation(
            method = {"play", "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"),
            require = 2, allow = 2)
    private float bettersound$stepVolume(SoundEngine engine, float volume, SoundSource category,
                                        Operation<Float> original, @Local(argsOnly = true) SoundInstance sound) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && StepDynamics.isStep(sound.getIdentifier().getPath())) {
            double distance = player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ());
            volume = StepDynamics.volume(volume, distance, player.isSprinting());
        }
        return original.call(engine, volume, category);
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
