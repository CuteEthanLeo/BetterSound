package com.bettersound.mixin;

import com.bettersound.BetterSoundClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityStepMixin {
    @Inject(method = "walkingStepSound", at = @At("RETURN"))
    private void bettersound$armorStep(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && (Object) this == player && !player.isShiftKeyDown()) {
            BetterSoundClient.playArmorStep(player, false);
        }
    }
}
