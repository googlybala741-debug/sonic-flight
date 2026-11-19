package com.sonicflight;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
public class SonicFlightClient implements ClientModInitializer {
    public static final String MOD_ID = "sonicflight";

    // ---- tweak these ----
    static final double MIN_SPEED = 0.7;          // speed needed before trails show
    static final double BOOM_SPEED = 1.8;         // speed that triggers the boom sound
    static final double DIVE_SCALE = 2.0;         // how much bigger things get when diving
    static final double TRAIL_START = 2.5;        // how far behind you the trails begin
    static final int BOOM_COOLDOWN = 100;         // ticks between booms (20 ticks = 1 second)
    static final float BOOM_MAX_VOLUME = 0.7f;    // boom loudness when the slider is at 100%
    static final int WATER_RANGE = 8;             // blocks above water where spray starts
    static final int WATER_SOUND_COOLDOWN = 30;   // ticks between water swishes
    static final float WATER_MAX_VOLUME = 0.5f;   // water loudness when the slider is at 100%

    public static KeyMapping.Category CATEGORY;
    public static KeyMapping SETTINGS_KEY;

    private static int tickCounter = 0;
    private static int boomCooldown = 0;
    private static int waterSoundCooldown = 0;
    private static boolean wasFast = false;
    private static boolean wasDiving = false;

    @Override
    public void onInitializeClient() {
        SonicFlightConfig.load();

        CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        // Shows up in Options > Controls > Key Binds, so the key can be changed there.
        SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sonicflight.settings", InputConstants.KEY_O, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(SonicFlightClient::onTick);
    }

    private static void resetState() {
        wasFast = false;
        wasDiving = false;
    }

    private static void onTick(Minecraft mc) {
        while (SETTINGS_KEY.consumeClick()) {
            mc.setScreenAndShow(new SonicFlightSettingsScreen());
        }

        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            resetState();
            return;
        }
        tickCounter++;
        if (boomCooldown > 0) boomCooldown--;
        if (waterSoundCooldown > 0) waterSoundCooldown--;

        if (!p.isFallFlying()) {
            resetState();
            return;
        }

        sonicEffects(mc, p);
        waterEffects(mc, p);
    }

    // ------------------------------------------------------------------ sonic effects
    private static void sonicEffects(Minecraft mc, LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        double speed = v.length();
        if (speed < MIN_SPEED) {
            resetState();
            return;
        }

        Vec3 f = v.normalize();
        double dive = Mth.clamp(-f.y, 0.0, 1.0);                 // 0 = level, 1 = straight down
        double speedFactor = Mth.clamp((speed - MIN_SPEED) / 1.5, 0.0, 1.0);
        double scale = 1.0 + dive * DIVE_SCALE + speedFactor * 0.5;

        // axes perpendicular to the flight direction
        Vec3 ref = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = f.cross(ref).normalize();   // sideways
        Vec3 w = f.cross(u).normalize();     // up-ish
        Vec3 base = p.position().add(0, p.getBbHeight() * 0.5, 0);
        var rng = p.getRandom();

        // ---- boom sound: only when you cross into high speed or start a steep dive
        boolean fast = speed >= BOOM_SPEED;
        boolean diving = dive > 0.6 && speed > 1.2;
        if (boomCooldown <= 0 && ((fast && !wasFast) || (diving && !wasDiving))) {
            float vol = BOOM_MAX_VOLUME * SonicFlightConfig.boom;
            if (vol > 0.001f) {
                mc.level.playLocalSound(base.x, base.y, base.z, SoundEvents.WARDEN_SONIC_BOOM,
                        SoundSource.PLAYERS, vol, 1.3f, false);
            }
            Vec3 flash = base.subtract(f.scale(3.0));
            mc.level.addParticle(ParticleTypes.SONIC_BOOM, flash.x, flash.y, flash.z, 0, 0, 0);
            boomCooldown = BOOM_COOLDOWN;
        }
        wasFast = fast;
        wasDiving = diving;

        // ---- wingtip trails (thin, smoky, off to the sides)
        double lateral = 0.8 + 0.25 * scale;
        int perSide = 1 + (int) (speedFactor * 2);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < perSide; i++) {
                double behind = TRAIL_START + rng.nextDouble() * (2.0 + speedFactor * 3.0);
                Vec3 pos = base.subtract(f.scale(behind))
                        .add(u.scale(side * lateral))
                        .add(w.scale((rng.nextDouble() - 0.5) * 0.3));
                mc.level.addParticle(ParticleTypes.WHITE_SMOKE, pos.x, pos.y, pos.z,
                        -f.x * 0.02, -f.y * 0.02, -f.z * 0.02);
            }
        }
        if (tickCounter % 4 == 0) { // an occasional soft puff for body
            for (int side = -1; side <= 1; side += 2) {
                Vec3 pos = base.subtract(f.scale(TRAIL_START + 1.0)).add(u.scale(side * lateral));
                mc.level.addParticle(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 0, 0, 0);
            }
        }

        // ---- diving: loose cone of wisps that gets bigger the steeper you dive
        if (dive > 0.35) {
            int count = 2 + (int) (dive * 5);
            for (int i = 0; i < count; i++) {
                double ang = rng.nextDouble() * Math.PI * 2.0;
                double dist = TRAIL_START + rng.nextDouble() * (3.0 + dive * 3.0);
                double radius = 1.0 + dist * 0.25 * scale * 0.5;  // always >= 1 so the middle stays clear
                Vec3 pos = base.subtract(f.scale(dist))
                        .add(u.scale(Math.cos(ang) * radius))
                        .add(w.scale(Math.sin(ang) * radius));
                mc.level.addParticle(ParticleTypes.WHITE_SMOKE, pos.x, pos.y, pos.z,
                        -f.x * 0.03, -f.y * 0.03, -f.z * 0.03);
            }
        }
    }

    // ------------------------------------------------------------------ water effects
    private static void waterEffects(Minecraft mc, LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        double speed = v.length();
        if (speed < 0.3) return;

        double x = p.getX(), z = p.getZ();
        int startY = Mth.floor(p.getY());
        Double surfaceY = null;
        for (int dy = 0; dy <= WATER_RANGE; dy++) {
            BlockPos bp = BlockPos.containing(x, startY - dy, z);
            if (mc.level.getFluidState(bp).is(FluidTags.WATER)) {
                BlockPos top = bp;
                while (mc.level.getFluidState(top.above()).is(FluidTags.WATER)) top = top.above();
                surfaceY = top.getY() + 0.9;
                break;
            }
            if (!mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) break;
        }
        if (surfaceY == null) return;

        double dist = Math.max(0.0, p.getY() - surfaceY);
        if (dist > WATER_RANGE) return;
        double closeness = 1.0 - dist / WATER_RANGE;                // 1 = skimming the surface
        double power = closeness * Mth.clamp(speed, 0.3, 2.0);
        var rng = p.getRandom();

        // spray fanned out behind you, on the water
        Vec3 back = v.normalize().scale(-1);
        int n = 1 + (int) (power * 6);
        for (int i = 0; i < n; i++) {
            double side = (rng.nextDouble() - 0.5) * (1.5 + closeness * 2.0);
            double behind = rng.nextDouble() * (2.0 + power * 3.0);
            double px = x + back.x * behind - back.z * side;
            double pz = z + back.z * behind + back.x * side;
            mc.level.addParticle(ParticleTypes.SPLASH, px, surfaceY, pz, 0, 0.1 + closeness * 0.2, 0);
        }

        // close skim: light mist + a soft, occasional swish
        if (closeness > 0.6) {
            mc.level.addParticle(ParticleTypes.WHITE_SMOKE, x, surfaceY + 0.2, z, back.x * 0.1, 0.05, back.z * 0.1);
            float vol = WATER_MAX_VOLUME * SonicFlightConfig.water;
            if (waterSoundCooldown <= 0 && closeness > 0.7 && speed > 0.8 && vol > 0.001f) {
                float pitch = 0.8f + rng.nextFloat() * 0.3f;
                mc.level.playLocalSound(x, surfaceY, z, SoundEvents.PLAYER_SWIM,
                        SoundSource.PLAYERS, vol, pitch, false);
                waterSoundCooldown = WATER_SOUND_COOLDOWN;
            }
        }
    }
}
