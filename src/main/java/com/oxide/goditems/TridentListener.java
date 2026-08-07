package com.oxide.goditems;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TridentListener implements Listener {

    private final GodItems plugin;
    private final Set<UUID> noFallDamage = new HashSet<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TridentListener(GodItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !plugin.items().isGodTrident(item)) return;
        if (!player.isSneaking()) return;

        event.setCancelled(true);

        if (!player.hasPermission("goditems.use.trident")) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        int cooldownSeconds = plugin.getConfig().getInt("trident.cooldown-seconds", 10);
        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt && !player.hasPermission("goditems.cooldown.bypass")) {
            long left = (readyAt - now + 999) / 1000L;
            player.sendMessage(plugin.msg("cooldown").replace("%time%", String.valueOf(left)));
            return;
        }
        cooldowns.put(player.getUniqueId(), now + cooldownSeconds * 1000L);

        launch(player);
    }

    /** God Trident ko throw hone se rokta hai. */
    @EventHandler
    public void onTridentThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident)) return;
        Trident trident = (Trident) event.getEntity();
        if (!(trident.getShooter() instanceof Player)) return;
        Player shooter = (Player) trident.getShooter();
        ItemStack hand = shooter.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || plugin.items().isGodTrident(hand)) {
            if (shooter.isSneaking()) event.setCancelled(true);
        }
    }

    private void launch(Player player) {
        double min = plugin.getConfig().getDouble("trident.min-height", 10.0);
        double max = plugin.getConfig().getDouble("trident.max-height", 15.0);
        double height = ThreadLocalRandom.current().nextDouble(Math.min(min, max), Math.max(min, max) + 0.001);

        player.setVelocity(new Vector(0, velocityForHeight(height), 0));
        player.setFallDistance(0f);
        noFallDamage.add(player.getUniqueId());

        Location loc = player.getLocation();
        loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 1.0f);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 40, 0.4, 0.2, 0.4, 0.08);

        player.sendMessage(plugin.msg("trident-launched"));

        new LandingWatcher(player).runTaskTimer(plugin, 4L, 1L);
    }

    /** Minecraft physics simulate karke required initial velocity nikalta hai. */
    private double velocityForHeight(double target) {
        double low = 0.1, high = 3.5;
        for (int i = 0; i < 45; i++) {
            double mid = (low + high) / 2.0;
            if (simulateHeight(mid) < target) low = mid; else high = mid;
        }
        return (low + high) / 2.0;
    }

    private double simulateHeight(double initial) {
        double y = 0, v = initial;
        int guard = 0;
        while (v > 0 && guard++ < 400) {
            y += v;
            v = (v - 0.08) * 0.98;
        }
        return y;
    }

    private class LandingWatcher extends BukkitRunnable {
        private final Player player;
        private final UUID uuid;
        private boolean falling = false;
        private int ticks = 0;

        LandingWatcher(Player player) {
            this.player = player;
            this.uuid = player.getUniqueId();
        }

        @Override
        public void run() {
            ticks++;

            if (!player.isOnline() || player.isDead()) {
                noFallDamage.remove(uuid);
                cancel();
                return;
            }

            if (player.getVelocity().getY() < -0.05) falling = true;

            if (falling) {
                Location trail = player.getLocation();
                trail.getWorld().spawnParticle(Particle.CRIT, trail, 4, 0.2, 0.2, 0.2, 0.02);
            }

            if (falling && isGrounded()) {
                slam(player);
                clearLater();
                cancel();
                return;
            }

            if (ticks > plugin.getConfig().getInt("trident.max-air-ticks", 300)) {
                clearLater();
                cancel();
            }
        }

        private boolean isGrounded() {
            if (player.isOnGround()) return true;
            Location below = player.getLocation().clone().subtract(0, 0.15, 0);
            return !below.getBlock().isPassable();
        }

        private void clearLater() {
            player.setFallDistance(0f);
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> noFallDamage.remove(uuid), 20L);
        }
    }

    private void slam(Player caster) {
        Location center = caster.getLocation();
        double radius = plugin.getConfig().getDouble("trident.radius", 30.0);
        double radiusSq = radius * radius;

        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 6, 1.5, 0.5, 1.5, 0);
        center.getWorld().spawnParticle(Particle.CLOUD, center, 120, radius / 6, 0.6, radius / 6, 0.15);

        boolean hitSelf = plugin.getConfig().getBoolean("trident.affect-self", false);
        int hits = 0;

        for (Player target : center.getWorld().getPlayers()) {
            if (!hitSelf && target.getUniqueId().equals(caster.getUniqueId())) continue;
            if (target.isDead()) continue;
            if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) continue;
            if (target.hasPermission("goditems.bypass")) continue;
            if (!target.getWorld().equals(center.getWorld())) continue;
            if (target.getLocation().distanceSquared(center) > radiusSq) continue;

            popTotem(target, caster);
            hits++;
        }

        caster.sendMessage(plugin.msg("trident-slam").replace("%count%", String.valueOf(hits)));
    }

    private void popTotem(Player target, Player caster) {
        boolean hasTotem = isTotem(target.getInventory().getItemInMainHand())
                || isTotem(target.getInventory().getItemInOffHand());

        target.setNoDamageTicks(0);
        target.setAbsorptionAmount(0);

        if (hasTotem || plugin.getConfig().getBoolean("trident.kill-if-no-totem", false)) {
            // Lethal damage -> vanilla khud totem pop karega
            target.damage(1000.0, caster);
        } else {
            double left = plugin.getConfig().getDouble("trident.health-if-no-totem", 2.0);
            target.setHealth(Math.max(0.5, Math.min(target.getHealth(), left)));
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        }
        target.sendMessage(plugin.msg("trident-hit").replace("%player%", caster.getName()));
    }

    private boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (noFallDamage.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFallDistance(0f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        noFallDamage.remove(event.getPlayer().getUniqueId());
    }
}
