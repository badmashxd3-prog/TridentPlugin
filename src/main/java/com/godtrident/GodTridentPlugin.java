package com.godtrident;

import com.godtrident.commands.GodTridentCommand;
import com.godtrident.listeners.GodTridentListener;
import com.godtrident.utils.ItemUtil;
import org.bukkit.plugin.java.JavaPlugin;

public class GodTridentPlugin extends JavaPlugin {

    private ItemUtil itemUtil;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.itemUtil = new ItemUtil(this);

        getServer().getPluginManager().registerEvents(new GodTridentListener(this), this);

        GodTridentCommand commandHandler = new GodTridentCommand(this);
        if (getCommand("godtrident") != null) {
            getCommand("godtrident").setExecutor(commandHandler);
        }

        getLogger().info("GodTrident enabled - use /godtrident give <player> to try it out.");
    }

    @Override
    public void onDisable() {
        getLogger().info("GodTrident disabled.");
    }

    public ItemUtil getItemUtil() {
        return itemUtil;
    }
}
