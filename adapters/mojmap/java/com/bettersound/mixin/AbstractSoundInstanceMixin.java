package com.bettersound.mixin;

import com.bettersound.core.PlantSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes block.grass.break/place to BetterSound's plant set when the block at the sound is a non-solid plant
 * (flowers, short/tall grass, ferns, saplings...). Runs when the instance resolves its sound, which for the
 * local player's own breaking happens before the block is removed. The method is resolve() up to 26.2 and
 * getOrResolve() from 26.3.
 */
@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
    @Shadow @Final @Mutable protected Identifier identifier;
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;

    @Inject(method = {"resolve", "getOrResolve"}, at = @At("HEAD"))
    private void bettersound$plantSound(SoundManager manager, CallbackInfoReturnable<WeighedSoundEvents> cir) {
        String r = PlantSounds.replacement(identifier.getNamespace(), identifier.getPath());
        if (r == null) return;
        ClientLevel w = Minecraft.getInstance().level;
        if (w == null) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState s = w.getBlockState(pos);
        if (s.isAir() || !s.getCollisionShape(w, pos).isEmpty()) return;
        identifier = Identifier.parse(r);
    }
}
