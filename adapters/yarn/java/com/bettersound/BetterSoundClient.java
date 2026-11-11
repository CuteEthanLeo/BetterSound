package com.bettersound;

import com.bettersound.core.AmbientEngine;
import com.bettersound.core.BetterSoundCore;
import com.bettersound.core.Biomes;
import com.bettersound.core.BsConfig;
import com.bettersound.core.EnvSnapshot;
import com.bettersound.core.SneakSteps;
import com.bettersound.core.SoundBackend;
import com.bettersound.core.StepDynamics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/** Minecraft 1.21.1 - 1.21.11 (Yarn mappings) adapter. */
public class BetterSoundClient implements ClientModInitializer, SoundBackend {
    public static final AmbientEngine ENGINE = new AmbientEngine();
    private final EnvSnapshot env = new EnvSnapshot();
    private final SneakSteps sneak = new SneakSteps();
    private final Map<String, LoopSound> loops = new HashMap<>();
    private final Random random = Random.create();
    private int tick;

    @Override
    public void onInitializeClient() {
        BsConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        BetterSoundCore.LOG.info("[BetterSound] Loaded: realistic footsteps + dynamic ambience");
    }

    private void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            env.valid = false;
            loops.clear();
            ENGINE.reset();
            return;
        }
        if (mc.isPaused()) return;
        if (tick++ % 10 == 0) sample(mc.world, mc.player);
        ENGINE.tick(env, this);
        sneakStep(mc, mc.world, mc.player);
    }

    /** Vanilla is silent while sneaking; play a quiet step of our own using the block's own step sound. */
    private void sneakStep(MinecraftClient mc, ClientWorld w, ClientPlayerEntity p) {
        boolean active = p.isSneaking() && p.isOnGround() && !p.hasVehicle() && !p.isTouchingWater() && !p.getAbilities().flying;
        if (!sneak.tick(active, p.getX(), p.getZ())) return;
        BlockState state = w.getBlockState(p.getSteppingPos());
        if (state.isAir()) return;
        BlockSoundGroup g = state.getSoundGroup();
        float vol = StepDynamics.sneakVolume(g.getVolume());
        if (vol <= 0.001f) return;
        mc.getSoundManager().play(new PositionedSoundInstance(g.getStepSound(), SoundCategory.PLAYERS, vol, g.getPitch(), random,
                p.getX(), p.getY(), p.getZ()));
    }

    private void sample(ClientWorld w, ClientPlayerEntity p) {
        env.valid = p.isAlive();
        env.overworld = w.getRegistryKey() == World.OVERWORLD;
        BlockPos pos = BlockPos.ofFloored(p.getX(), p.getEyeY(), p.getZ());
        env.biome = w.getBiome(pos).getKey().map(k -> k.getValue().getPath()).orElse("");
        env.y = p.getY();
        env.seaLevel = w.getSeaLevel();
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos);
        float rainGrad = w.getRainGradient(1f);
        boolean rainsHere = w.hasRain(top.up());
        env.rain = rainsHere ? rainGrad : 0f;
        env.snowing = rainGrad > 0.05f && !rainsHere && w.getBiome(pos).value().hasPrecipitation() && Biomes.isCold(env.biome);
        env.thunder = w.getThunderGradient(1f);
        env.daylight = EnvSnapshot.daylight(w.getTimeOfDay());
        env.underwater = p.isSubmergedInWater();

        int sky = 0, n = 0, water = 0, leaves = 0, roof = 0;
        for (int dx = -6; dx <= 6; dx += 3) {
            for (int dz = -6; dz <= 6; dz += 3) {
                n++;
                if (w.isSkyVisible(pos.add(dx, 0, dz))) sky++;
            }
        }
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                for (int dy = -4; dy <= 2; dy += 2) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    FluidState f = w.getFluidState(m);
                    if (f.isIn(FluidTags.WATER)) water += f.isStill() ? 1 : 4;
                }
                m.set(pos.getX() + dx, pos.getY() + 4, pos.getZ() + dz);
                if (w.getBlockState(m).isIn(BlockTags.LEAVES)) leaves++;
            }
        }
        // Solid roof straight above the head: a house is 1-3 blocks, the inside of a mountain 12+.
        for (int dy = 1; dy <= 32; dy++) {
            m.set(pos.getX(), pos.getY() + dy, pos.getZ());
            BlockState s = w.getBlockState(m);
            if (!s.isAir() && !s.isIn(BlockTags.LEAVES)) roof++;
        }
        env.skyExposure = sky / (float) n;
        env.waterNearby = Math.min(1f, water / 60f);
        env.leavesNearby = Math.min(1f, leaves / 20f);
        env.roofBlocks = roof;
    }

    static SoundEvent event(String fullId) { return SoundEvent.of(Identifier.of(fullId)); }

    // ---------------- SoundBackend ----------------
    @Override
    public void startLoop(String soundId) {
        LoopSound s = new LoopSound(soundId);
        loops.put(soundId, s);
        MinecraftClient.getInstance().getSoundManager().play(s);
    }

    @Override
    public boolean isLoopAlive(String soundId) {
        LoopSound s = loops.get(soundId);
        return s != null && !s.isDone() && MinecraftClient.getInstance().getSoundManager().isPlaying(s);
    }

    @Override
    public void playAt(String soundId, double dx, double dy, double dz, float volume, float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(new PositionedSoundInstance(event(soundId), SoundCategory.AMBIENT,
                volume, pitch, random, mc.player.getX() + dx, mc.player.getEyeY() + dy, mc.player.getZ() + dz));
    }
}
