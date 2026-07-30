package com.godtrident.listeners;

import com.godtrident.GodTridentPlugin;
import com.godtrident.utils.EffectUtil;
import com.godtrident.utils.ItemUtil;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GodTridentListener implements Listener {

    /** How long a "pending launch" is allowed to stay active before it's cleared automatically. */
    private static final long PENDING_TIMEOUT_MS = 20_000L;

    private final GodTridentPlugin plugin;
    private final ItemUtil itemUtil;
    private final EffectUtil effectUtil;

    /** Players currently mid-flight from the trident, mapped to the time they launched. */
    private final Map<UUID, Long> pendingLaunch = new HashMap<>();

    /** Simple per-player cooldown tracking. */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public GodTridentListener(GodTridentPlugin plugin) {
        this.plugin = plugin;
        this.itemUtil = plugin.getItemUtil();
        this.effectUtil = new EffectUtil(plugin);

        // Safety net: clear out any stale "pending launch" entries every 5 seconds,
        // in case a player lands in water/lava (no FALL damage event fires there)
        // and would otherwise stay "pending" forever.
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupStalePending, 100L, 100L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!itemUtil.isGodTrident(item)) return;

        // Stop this from throwing the trident / interacting with a block.
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        FileConfiguration cfg = plugin.getConfig();
        long cooldownMillis = cfg.getLong("cooldown-seconds", 5) * 1000L;
        long now = System.currentTimeMillis();

        Long lastUse = cooldowns.get(uuid);
        if (lastUse != null && now - lastUse < cooldownMillis) {
            long remainingSeconds = (cooldownMillis - (now - lastUse)) / 1000L + 1;
            player.sendMessage(ChatColor.RED + "God Trident is recharging (" + remainingSeconds + "s)...");
            return;
        }

        if (pendingLaunch.containsKey(uuid)) {
            // Already mid-flight, ignore repeat triggers.
            return;
        }

        cooldowns.put(uuid, now);
        launchPlayer(player, cfg);
    }

    private void launchPlayer(Player player, FileConfiguration cfg) {
        double power = cfg.getDouble("launch-velocity", 1.9);

        Vector velocity = player.getVelocity();
        velocity.setX(0);
        velocity.setZ(0);
        velocity.setY(power);
        player.setVelocity(velocity);
        player.setFallDistance(0f);

        pendingLaunch.put(player.getUniqueId(), System.currentTimeMillis());

        World world = player.getWorld();
        Location loc = player.getLocation();

        Sound launchSound = effectUtil.safeSound(cfg.getString("sounds.launch"), Sound.ENTITY_PLAYER_LEVELUP);
        Particle launchParticle = effectUtil.safeParticle(cfg.getString("particles.launch"), Particle.FLAME);

        try {
            world.playSound(loc, launchSound, 1.6f, 0.8f);
        } catch (Exception ignored) { }
        try {
            world.spawnParticle(launchParticle, loc, 40, 0.4, 0.2, 0.4, 0.05);
        } catch (Exception ignored) { }

        player.sendMessage(ChatColor.GOLD + "The God Trident hurls you skyward!");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        UUID uuid = player.getUniqueId();

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!pendingLaunch.containsKey(uuid)) return;

        pendingLaunch.remove(uuid);

        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("disable-fall-damage", true)) {
            event.setCancelled(true);
        }

        triggerShockwave(player, cfg);
    }

    private void triggerShockwave(Player center, FileConfiguration cfg) {
        Location loc = center.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        double radius = cfg.getDouble("shockwave-radius", 30);
        double damage = cfg.getDouble("shockwave-damage", 1000);
        boolean excludeSelf = cfg.getBoolean("exclude-self-from-shockwave", true);

        Sound landingSound = effectUtil.safeSound(cfg.getString("sounds.landing"), Sound.ENTITY_GENERIC_EXPLODE);
        Particle landingParticle = effectUtil.safeParticle(cfg.getString("particles.landing"), Particle.LAVA);
        Particle ringParticle = effectUtil.safeParticle(cfg.getString("particles.landing-ring"), Particle.FLAME);

        try {
            world.playSound(loc, landingSound, 2.5f, 0.6f);
        } catch (Exception ignored) { }
        try {
            world.spawnParticle(landingParticle, loc, 4, 0.3, 0.1, 0.3, 0);
        } catch (Exception ignored) { }
        try {
            world.spawnParticle(ringParticle, loc, 12, radius / 5.0, 0.2, radius / 5.0, 0);
        } catch (Exception ignored) { }

        int hitCount = 0;
        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof Player)) continue;
            Player target = (Player) entity;

            if (excludeSelf && target.getUniqueId().equals(center.getUniqueId())) continue;
            if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) continue;

            // A high enough damage value triggers vanilla Totem of Undying handling
            // automatically: if the target holds a totem, it pops and they survive
            // with the usual regen/absorption/fire-resistance effect; otherwise the
            // hit is lethal, exactly like any other fatal damage source.
            target.damage(damage, center);
            hitCount++;
        }

        center.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Sky Judgment lands! "
                + ChatColor.RESET + ChatColor.GRAY + "(" + hitCount + " player(s) caught in the shockwave)");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingLaunch.remove(uuid);
        cooldowns.remove(uuid);
    }

    private void cleanupStalePending() {
        long now = System.currentTimeMillis();
        pendingLaunch.entrySet().removeIf(e -> now - e.getValue() > PENDING_TIMEOUT_MS);
    }
}
