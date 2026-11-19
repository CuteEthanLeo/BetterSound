package com.bettersound;

import com.bettersound.core.AmbientEngine;
import com.bettersound.core.ArmorSteps;
import com.bettersound.core.BetterSoundCore;
import com.bettersound.core.Biomes;
import com.bettersound.core.BsConfig;
import com.bettersound.core.EnvSnapshot;
import com.bettersound.core.SneakSteps;
import com.bettersound.core.SoundBackend;
import com.bettersound.core.StepDynamics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;

/** Minecraft 26.x (official, unobfuscated names) adapter. */
public class BetterSoundClient implements ClientModInitializer, SoundBackend {
    public static final AmbientEngine ENGINE = new AmbientEngine();
    private static final SoundEvent LEATHER_STEP = event(ArmorSteps.LEATHER);
    private static final SoundEvent HARD_STEP = event(ArmorSteps.HARD);
    private static final Map<EquipmentSlot, TagKey<Item>> ARMOR_TAGS = Map.of(
            EquipmentSlot.FEET, ItemTags.FOOT_ARMOR,
            EquipmentSlot.LEGS, ItemTags.LEG_ARMOR,
            EquipmentSlot.CHEST, ItemTags.CHEST_ARMOR,
            EquipmentSlot.HEAD, ItemTags.HEAD_ARMOR);
    private final EnvSnapshot env = new EnvSnapshot();
    private final SneakSteps sneak = new SneakSteps();
    private final Map<String, LoopSound> loops = new HashMap<>();
    private final RandomSource random = RandomSource.create();
    private int tick;

    @Override
    public void onInitializeClient() {
        BsConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        BetterSoundCore.LOG.info("[BetterSound] Loaded: realistic footsteps + dynamic ambience");
    }

    private void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            env.valid = false;
            loops.clear();
            ENGINE.reset();
            return;
        }
        if (mc.isPaused()) return;
        if (tick++ % 10 == 0) sample(mc.level, mc.player);
        LocalPlayer p = mc.player;
        ENGINE.movement(p.getX(), p.getZ(), p.onGround() && !p.isPassenger() && !p.isInWater() && !p.getAbilities().flying);
        ENGINE.tick(env, this);
        sneakStep(mc, mc.level, mc.player);
    }

    /** Vanilla is silent while sneaking; play a quiet step of our own using the block's own step sound. */
    private void sneakStep(Minecraft mc, ClientLevel w, LocalPlayer p) {
        boolean active = p.isShiftKeyDown() && p.onGround() && !p.isPassenger() && !p.isInWater() && !p.getAbilities().flying;
        if (!sneak.tick(active, p.getX(), p.getZ())) return;
        BlockState state = w.getBlockState(p.getOnPos());
        if (state.isAir()) return;
        SoundType t = state.getSoundType();
        float vol = StepDynamics.sneakVolume(t.getVolume());
        if (vol <= 0.001f) return;
        mc.getSoundManager().play(new SimpleSoundInstance(t.getStepSound(), SoundSource.PLAYERS, vol, t.getPitch(), random,
                p.getX(), p.getY(), p.getZ()));
        playArmorStep(p, true);
    }

    /** Play one armor layer for an actual local surface step. */
    public static void playArmorStep(LocalPlayer player, boolean sneaking) {
        float volume = ArmorSteps.volume(sneaking, player.isSprinting());
        if (volume <= 0f || !player.isAlive() || player.isSpectator() || !player.onGround()
                || player.isPassenger() || player.isInWater() || player.getAbilities().flying) return;

        SoundEvent armorSound = null;
        for (var armor : ARMOR_TAGS.entrySet()) {
            ItemStack stack = player.getItemBySlot(armor.getKey());
            if (!stack.is(armor.getValue())) continue;
            if (stack.is(Items.LEATHER_BOOTS) || stack.is(Items.LEATHER_LEGGINGS)
                    || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_HELMET)) {
                armorSound = LEATHER_STEP;
            } else {
                armorSound = HARD_STEP;
                break;
            }
        }
        if (armorSound != null) player.playSound(armorSound, volume, 1f);
    }

    private void sample(ClientLevel w, LocalPlayer p) {
        env.valid = p.isAlive();
        env.overworld = w.dimension() == Level.OVERWORLD;
        BlockPos pos = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        env.biome = w.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        env.y = p.getY();
        env.seaLevel = w.getSeaLevel();
        BlockPos top = w.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        float rainGrad = w.getRainLevel(1f);
        boolean rainsHere = w.isRainingAt(top.above());
        env.rain = rainsHere ? rainGrad : 0f;
        env.snowing = rainGrad > 0.05f && !rainsHere && w.getBiome(pos).value().hasPrecipitation() && Biomes.isCold(env.biome);
        env.thunder = w.getThunderLevel(1f);
        env.daylight = EnvSnapshot.daylight(Compat.dayTime(w));
        env.underwater = p.isUnderWater();

        int sky = 0, n = 0, water = 0, leaves = 0, roof = 0;
        for (int dx = -6; dx <= 6; dx += 3) {
            for (int dz = -6; dz <= 6; dz += 3) {
                n++;
                if (w.canSeeSky(pos.offset(dx, 0, dz))) sky++;
            }
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                for (int dy = -4; dy <= 2; dy += 2) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    FluidState f = w.getFluidState(m);
                    if (f.is(FluidTags.WATER)) water += f.isSource() ? 1 : 4;
                }
                m.set(pos.getX() + dx, pos.getY() + 4, pos.getZ() + dz);
                if (w.getBlockState(m).is(BlockTags.LEAVES)) leaves++;
            }
        }
        // Solid roof straight above the head: a house is 1-3 blocks, the inside of a mountain 12+.
        for (int dy = 1; dy <= 32; dy++) {
            m.set(pos.getX(), pos.getY() + dy, pos.getZ());
            BlockState s = w.getBlockState(m);
            if (!s.isAir() && !s.is(BlockTags.LEAVES)) roof++;
        }
        env.skyExposure = sky / (float) n;
        env.waterNearby = Math.min(1f, water / 60f);
        env.leavesNearby = Math.min(1f, leaves / 20f);
        env.roofBlocks = roof;
    }

    static SoundEvent event(String fullId) { return SoundEvent.createVariableRangeEvent(Identifier.parse(fullId)); }

    // ---------------- SoundBackend ----------------
    @Override
    public void startLoop(String soundId) {
        LoopSound s = new LoopSound(soundId);
        loops.put(soundId, s);
        Minecraft.getInstance().getSoundManager().play(s);
    }

    @Override
    public boolean isLoopAlive(String soundId) {
        LoopSound s = loops.get(soundId);
        return s != null && !s.isStopped() && Minecraft.getInstance().getSoundManager().isActive(s);
    }

    @Override
    public void playAt(String soundId, double dx, double dy, double dz, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(new SimpleSoundInstance(event(soundId), SoundSource.AMBIENT,
                volume, pitch, random, mc.player.getX() + dx, mc.player.getEyeY() + dy, mc.player.getZ() + dz));
    }
}
