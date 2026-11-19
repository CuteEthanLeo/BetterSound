package com.bettersound.mixin;

import com.bettersound.BetterSoundClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityStepMixin {
    @Inject(method = "playStepSounds", at = @At("RETURN"))
    private void bettersound$armorStep(CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && (Object) this == player && !player.isSneaking()) {
            BetterSoundClient.playArmorStep(player, false);
        }
    }
}
