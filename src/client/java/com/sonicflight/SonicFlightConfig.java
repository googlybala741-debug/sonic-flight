package com.sonicflight;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Slider values, 0.0 to 1.0. Saved to config/sonicflight.properties. */
public final class SonicFlightConfig {
    public static float boom = 0.5f;
    public static float water = 0.5f;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("sonicflight.properties");
    }

    private static float parse(String s, float fallback) {
        try {
            float f = Float.parseFloat(s);
            return Math.max(0f, Math.min(1f, f));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            }
            boom = parse(p.getProperty("boomVolume"), boom);
            water = parse(p.getProperty("waterVolume"), water);
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("boomVolume", Float.toString(boom));
            p.setProperty("waterVolume", Float.toString(water));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Sonic Flight settings");
            }
        } catch (Exception ignored) {
        }
    }

    private SonicFlightConfig() {}
}
