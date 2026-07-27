package com.godtrident.commands;

import com.godtrident.GodTridentPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GodTridentCommand implements CommandExecutor {

    private final GodTridentPlugin plugin;

    public GodTridentCommand(GodTridentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("godtrident.give")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /godtrident give [player]");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' is not online.");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /godtrident give <player>");
            return true;
        }

        target.getInventory().addItem(plugin.getItemUtil().createGodTrident());
        target.sendMessage(ChatColor.GREEN + "You received the " + ChatColor.RED + "" + ChatColor.BOLD
                + "God Trident" + ChatColor.RESET + ChatColor.GREEN + "!");

        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.GREEN + "Gave the God Trident to " + target.getName() + ".");
        }

        return true;
    }
}
