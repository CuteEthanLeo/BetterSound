package com.bettersound.mixin;

import com.bettersound.core.StepDynamics;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sprint footstep loudness and subtle per-step pitch variation (sneak steps are played by BetterSoundClient). */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @WrapOperation(
            method = {"play", "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F"),
            require = 2, allow = 2)
    private float bettersound$stepVolume(SoundSystem engine, float volume, SoundCategory category,
                                        Operation<Float> original, @Local(argsOnly = true) SoundInstance sound) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && StepDynamics.isStep(sound.getId().getPath())) {
            double distance = player.squaredDistanceTo(sound.getX(), sound.getY(), sound.getZ());
            volume = StepDynamics.volume(volume, distance, player.isSprinting());
        }
        return original.call(engine, volume, category);
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
