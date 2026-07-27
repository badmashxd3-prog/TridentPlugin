package com.godtrident.utils;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Sound/Particle enum constant names occasionally change between Minecraft
 * versions. Rather than hard-crashing the whole listener when a name from
 * config.yml doesn't exist on the running server, we look it up safely and
 * just skip (and log once) if it's missing.
 */
public final class EffectUtil {

    private final JavaPlugin plugin;
    private final Set<String> alreadyWarned = new HashSet<>();

    public EffectUtil(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Sound safeSound(String name, Sound fallback) {
        if (name == null) return fallback;
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            warnOnce("sound:" + name, "Unknown Sound '" + name + "' for this server version, skipping.");
            return fallback;
        }
    }

    public Particle safeParticle(String name, Particle fallback) {
        if (name == null) return fallback;
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            warnOnce("particle:" + name, "Unknown Particle '" + name + "' for this server version, skipping.");
            return fallback;
        }
    }

    private void warnOnce(String key, String message) {
        if (alreadyWarned.add(key)) {
            plugin.getLogger().warning("[GodTrident] " + message);
        }
    }
}
