package com.oxide.goditems;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GodItemsCommand implements CommandExecutor, TabCompleter {

    private final GodItems plugin;

    public GodItemsCommand(GodItems plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("goditems.reload")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("goditems.give")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            if (args.length < 2) {
                sendHelp(sender, label);
                return true;
            }

            Player target;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
            } else if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage(plugin.msg("console-need-player"));
                return true;
            }

            if (target == null) {
                sender.sendMessage(plugin.msg("player-not-found"));
                return true;
            }

            ItemStack item;
            if (args[1].equalsIgnoreCase("trident")) {
                item = plugin.items().createTrident();
            } else if (args[1].equalsIgnoreCase("crossbow")) {
                item = plugin.items().createCrossbow();
            } else {
                sendHelp(sender, label);
                return true;
            }

            target.getInventory().addItem(item);
            sender.sendMessage(plugin.msg("item-given").replace("%player%", target.getName()));
            if (!target.equals(sender)) target.sendMessage(plugin.msg("item-received"));
            return true;
        }

        sendHelp(sender, label);
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(GodItems.color("&8&m----------------------------"));
        sender.sendMessage(GodItems.color("&b&lGodItems &7commands:"));
        sender.sendMessage(GodItems.color("&e/" + label + " give <trident|crossbow> [player]"));
        sender.sendMessage(GodItems.color("&e/" + label + " reload"));
        sender.sendMessage(GodItems.color("&8&m----------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : Arrays.asList("give", "reload")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (String s : Arrays.asList("trident", "crossbow")) {
                if (s.startsWith(args[1].toLowerCase())) out.add(s);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
