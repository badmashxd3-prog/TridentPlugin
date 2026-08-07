package com.oxide.goditems;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GodItems extends JavaPlugin {

    private NamespacedKey tridentKey;
    private NamespacedKey crossbowKey;
    private NamespacedKey arrowKey;
    private NamespacedKey homingKey;
    private ItemManager items;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        tridentKey  = new NamespacedKey(this, "god_trident");
        crossbowKey = new NamespacedKey(this, "god_crossbow");
        arrowKey    = new NamespacedKey(this, "god_arrow");
        homingKey   = new NamespacedKey(this, "homing_arrow");

        items = new ItemManager(this);

        getServer().getPluginManager().registerEvents(new TridentListener(this), this);
        getServer().getPluginManager().registerEvents(new CrossbowListener(this), this);

        GodItemsCommand executor = new GodItemsCommand(this);
        PluginCommand cmd = getCommand("goditems");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("GodItems enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("GodItems disabled.");
    }

    public ItemManager items() { return items; }
    public NamespacedKey tridentKey() { return tridentKey; }
    public NamespacedKey crossbowKey() { return crossbowKey; }
    public NamespacedKey arrowKey() { return arrowKey; }
    public NamespacedKey homingKey() { return homingKey; }

    public String msg(String path) {
        String prefix = getConfig().getString("messages.prefix", "");
        String value = getConfig().getString("messages." + path, "");
        return color(prefix + value);
    }

    public static String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }
}
