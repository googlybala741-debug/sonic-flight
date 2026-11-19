package com.sonicflight;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class SonicFlightClient implements ClientModInitializer {
    public static final String MOD_ID = "sonicflight";

    // ---- tweak these ----
    static final double MIN_SPEED = 0.7;        // blocks/tick needed before effects show
    static final double DIVE_SCALE = 2.5;       // how much bigger the boom gets when diving straight down
    static final int WATER_RANGE = 8;           // blocks above water where spray starts

    private static int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(SonicFlightClient::onTick);
    }

    private static void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        tickCounter++;

        if (!p.isFallFlying()) return;

        sonicBooms(mc, p);
        waterEffects(mc, p);
    }

    // ------------------------------------------------------------------ sonic booms
    private static void sonicBooms(Minecraft mc, LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        double speed = v.length();
        if (speed < MIN_SPEED) return;

        Vec3 f = v.normalize();
        double dive = Mth.clamp(-f.y, 0.0, 1.0);                 // 0 = level, 1 = straight down
        double speedFactor = Mth.clamp((speed - MIN_SPEED) / 1.5, 0.0, 1.0);
        double scale = 1.0 + dive * DIVE_SCALE + speedFactor * 0.5;

        // two axes perpendicular to the flight direction -> a ring around the player
        Vec3 ref = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = f.cross(ref).normalize();
        Vec3 w = f.cross(u).normalize();
        Vec3 centre = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(f.scale(0.4));

        // cloud ring (every tick): radius and density grow with scale
        double r = 1.0 * scale;
        int count = (int) Math.min(28, 6 + 6 * scale);
        double spin = tickCounter * 0.35;
        for (int i = 0; i < count; i++) {
            double a = spin + (Math.PI * 2.0 * i) / count;
            Vec3 pos = centre.add(u.scale(Math.cos(a) * r)).add(w.scale(Math.sin(a) * r));
            mc.level.addParticle(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, -f.x * 0.05, -f.y * 0.05, -f.z * 0.05);
        }

        // actual sonic-boom flashes (every 3 ticks): more + wider the steeper the dive
        if (tickCounter % 3 == 0) {
            int booms = 2 + (int) Math.round(dive * 4);
            double br = 0.8 * scale;
            for (int i = 0; i < booms; i++) {
                double a = spin + (Math.PI * 2.0 * i) / booms;
                Vec3 pos = centre.add(u.scale(Math.cos(a) * br)).add(w.scale(Math.sin(a) * br));
                mc.level.addParticle(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 0, 0, 0);
            }
            if (dive > 0.5) { // big outer shockwave while diving
                mc.level.addParticle(ParticleTypes.SONIC_BOOM, centre.x, centre.y, centre.z, 0, 0, 0);
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
                // walk up to the top water block of this column
                BlockPos top = bp;
                while (mc.level.getFluidState(top.above()).is(FluidTags.WATER)) top = top.above();
                surfaceY = top.getY() + 0.9;
                break;
            }
            if (!mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) break; // solid ground first
        }
        if (surfaceY == null) return;

        double dist = Math.max(0.0, p.getY() - surfaceY);
        if (dist > WATER_RANGE) return;
        double closeness = 1.0 - dist / WATER_RANGE;                // 1 = skimming the surface
        double power = closeness * Mth.clamp(speed, 0.3, 2.0);

        // spray fanned out behind the player, on the water
        Vec3 back = v.normalize().scale(-1);
        int n = 2 + (int) (power * 8);
        for (int i = 0; i < n; i++) {
            double side = (mc.level.random.nextDouble() - 0.5) * (1.5 + closeness * 2.0);
            double behind = mc.level.random.nextDouble() * (2.0 + power * 3.0);
            double px = x + back.x * behind - back.z * side;
            double pz = z + back.z * behind + back.x * side;
            mc.level.addParticle(ParticleTypes.SPLASH, px, surfaceY, pz, 0, 0.1 + closeness * 0.2, 0);
        }
        if (closeness > 0.6) { // close skim: mist + bubbles, a bit of sound
            mc.level.addParticle(ParticleTypes.CLOUD, x, surfaceY + 0.2, z, back.x * 0.1, 0.05, back.z * 0.1);
            mc.level.addParticle(ParticleTypes.BUBBLE_POP, x, surfaceY, z, 0, 0.05, 0);
            if (tickCounter % 6 == 0) {
                mc.level.playLocalSound(x, surfaceY, z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                        SoundSource.PLAYERS, 0.4f, 1.0f + (float) closeness * 0.3f, false);
            }
        }
    }
}
