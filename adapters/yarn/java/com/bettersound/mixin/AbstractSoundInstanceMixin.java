package com.bettersound.mixin;

import com.bettersound.core.PlantSounds;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
 * local player's own breaking happens before the block is removed.
 */
@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
    @Shadow @Final @Mutable protected Identifier id;
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;

    @Inject(method = "getSoundSet", at = @At("HEAD"))
    private void bettersound$plantSound(SoundManager manager, CallbackInfoReturnable<WeightedSoundSet> cir) {
        String r = PlantSounds.replacement(id.getNamespace(), id.getPath());
        if (r == null) return;
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        BlockState s = w.getBlockState(pos);
        if (s.isAir() || !s.getCollisionShape(w, pos).isEmpty()) return;
        id = Identifier.of(r);
    }
}
