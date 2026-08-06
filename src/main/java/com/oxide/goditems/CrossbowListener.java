package com.oxide.goditems;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CrossbowListener implements Listener {

    private final GodItems plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> handledArrows = new HashSet<>();

    public CrossbowListener(GodItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player shooter = (Player) event.getEntity();

        ItemStack bow = event.getBow();
        if (bow == null || !plugin.items().isGodCrossbow(bow)) return;
        if (!(event.getProjectile() instanceof AbstractArrow)) return;
        AbstractArrow arrow = (AbstractArrow) event.getProjectile();

        if (!shooter.hasPermission("goditems.use.crossbow")) {
            event.setCancelled(true);
            shooter.sendMessage(plugin.msg("no-permission"));
            return;
        }

        int cooldownSeconds = plugin.getConfig().getInt("crossbow.cooldown-seconds", 12);
        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(shooter.getUniqueId(), 0L);
        if (now < readyAt && !shooter.hasPermission("goditems.cooldown.bypass")) {
            long left = (readyAt - now + 999) / 1000L;
            shooter.sendMessage(plugin.msg("cooldown").replace("%time%", String.valueOf(left)));
            return; // normal arrow chalega, ability nahi
        }
        cooldowns.put(shooter.getUniqueId(), now + cooldownSeconds * 1000L);

        arrow.getPersistentDataContainer().set(plugin.arrowKey(), PersistentDataType.BYTE, (byte) 1);

        int delay = Math.max(1, plugin.getConfig().getInt("crossbow.split-delay-ticks", 6));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> split(shooter, arrow), delay);
    }

    private void split(Player shooter, AbstractArrow arrow) {
        if (!shooter.isOnline()) return;

        Location origin = (arrow.isValid() && !arrow.isDead())
                ? arrow.getLocation().clone()
                : shooter.getEyeLocation().clone();

        double radius = plugin.getConfig().getDouble("crossbow.radius", 30.0);
        double radiusSq = radius * radius;

        List<Player> targets = new ArrayList<>();
        for (Player target : origin.getWorld().getPlayers()) {
            if (target.getUniqueId().equals(shooter.getUniqueId())) continue;
            if (target.isDead()) continue;
            if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) continue;
            if (target.hasPermission("goditems.bypass")) continue;
            if (target.getLocation().distanceSquared(origin) > radiusSq) continue;
            targets.add(target);
        }

        if (arrow.isValid() && plugin.getConfig().getBoolean("crossbow.remove-original-arrow", true)) {
            arrow.remove();
        }

        if (targets.isEmpty()) {
            shooter.sendMessage(plugin.msg("crossbow-no-target"));
            return;
        }

        origin.getWorld().playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.4f, 1.3f);
        origin.getWorld().spawnParticle(Particle.FLASH, origin, 2, 0, 0, 0, 0);

        for (Player target : targets) spawnHoming(shooter, origin, target);

        shooter.sendMessage(plugin.msg("crossbow-fired")
                .replace("%count%", String.valueOf(targets.size())));
    }

    private void spawnHoming(Player shooter, Location origin, Player target) {
        final double speed = plugin.getConfig().getDouble("crossbow.arrow-speed", 1.8);
        Vector direction = target.getEyeLocation().toVector().subtract(origin.toVector());
        if (direction.lengthSquared() < 0.001) direction = new Vector(0, 1, 0);
        direction.normalize();

        final Arrow homing = origin.getWorld().spawnArrow(origin, direction, (float) speed, 0f);
        homing.setShooter(shooter);
        homing.setGravity(false);
        homing.setSilent(true);
        homing.setCritical(true);
        homing.setDamage(0.0);
        homing.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        homing.getPersistentDataContainer().set(plugin.homingKey(), PersistentDataType.BYTE, (byte) 1);

        final int maxTicks = plugin.getConfig().getInt("crossbow.homing-lifetime-ticks", 120);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!homing.isValid() || homing.isDead() || ticks > maxTicks
                        || !target.isOnline() || target.isDead()
                        || !target.getWorld().equals(homing.getWorld())) {
                    homing.remove();
                    cancel();
                    return;
                }

                Location arrowLoc = homing.getLocation();
                Vector toTarget = target.getLocation().clone().add(0, 1.0, 0)
                        .toVector().subtract(arrowLoc.toVector());
                double distance = toTarget.length();

                if (distance <= 1.3) {
                    if (handledArrows.add(homing.getUniqueId())) {
                        forget(homing.getUniqueId());
                        strike(target, shooter);
                    }
                    homing.remove();
                    cancel();
                    return;
                }

                homing.setVelocity(toTarget.normalize().multiply(speed));
                arrowLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, arrowLoc, 3, 0.05, 0.05, 0.05, 0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void strike(Player target, Player shooter) {
        Location loc = target.getLocation();

        if (plugin.getConfig().getBoolean("crossbow.real-lightning", false)) {
            loc.getWorld().strikeLightning(loc);
        } else {
            loc.getWorld().strikeLightningEffect(loc);
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.0f);
        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.2);

        target.setNoDamageTicks(0);
        target.setAbsorptionAmount(0);

        double healthLeft = plugin.getConfig().getDouble("crossbow.health-left", 2.0);
        if (target.getHealth() > healthLeft) {
            if (shooter != null) target.damage(0.01, shooter); else target.damage(0.01);
            if (target.isDead()) return;
            target.setHealth(Math.max(0.5, Math.min(target.getHealth(), healthLeft)));
        }

        int duration = plugin.getConfig().getInt("crossbow.effect-seconds", 15) * 20;
        int slowAmp = plugin.getConfig().getInt("crossbow.slowness-amplifier", 2);

        PotionEffectType blindness = effect("BLINDNESS");
        PotionEffectType slowness = effect("SLOWNESS", "SLOW");

        if (blindness != null) target.addPotionEffect(new PotionEffect(blindness, duration, 0, false, true, true));
        if (slowness != null) target.addPotionEffect(new PotionEffect(slowness, duration, slowAmp, false, true, true));

        target.sendMessage(plugin.msg("crossbow-hit")
                .replace("%player%", shooter == null ? "Unknown" : shooter.getName()));
    }

    @SuppressWarnings("deprecation")
    private PotionEffectType effect(String... names) {
        for (String name : names) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) return type;
        }
        return null;
    }

    @EventHandler
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow)) return;
        AbstractArrow arrow = (AbstractArrow) event.getDamager();
        if (!arrow.getPersistentDataContainer().has(plugin.homingKey(), PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        if (event.getEntity() instanceof Player && handledArrows.add(arrow.getUniqueId())) {
            forget(arrow.getUniqueId());
            Player shooter = (arrow.getShooter() instanceof Player) ? (Player) arrow.getShooter() : null;
            strike((Player) event.getEntity(), shooter);
        }
        arrow.remove();
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow)) return;
        AbstractArrow arrow = (AbstractArrow) event.getEntity();
        if (!arrow.getPersistentDataContainer().has(plugin.homingKey(), PersistentDataType.BYTE)) return;

        if (event.getHitBlock() != null
                && plugin.getConfig().getBoolean("crossbow.pass-through-blocks", true)) {
            event.setCancelled(true);
            return;
        }

        if (event.getHitEntity() instanceof Player && handledArrows.add(arrow.getUniqueId())) {
            forget(arrow.getUniqueId());
            Player shooter = (arrow.getShooter() instanceof Player) ? (Player) arrow.getShooter() : null;
            strike((Player) event.getHitEntity(), shooter);
        }
        arrow.remove();
    }

    private void forget(UUID arrowId) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> handledArrows.remove(arrowId), 100L);
    }
}
